package dev.exercisevalidator.run;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Runs a variant the way Artemis CI does: copy {@code tests/} to a fresh folder, put the variant's {@code src}
 * into {@code assignment/src}, run the exercise's own Gradle wrapper, read the JUnit XML.
 *
 * <p>The exercise's build files are never edited. The tool's additions (random test order) come in through an
 * init script on the command line, so what we measure is the build that grades students.
 */
public final class GradleRunner implements VariantRunner {

    private static final String INIT_SCRIPT = "validator.init.gradle";
    private static final String LOG_FILE = "validator-gradle.log";
    private static final Pattern JAVA_VERSION = Pattern.compile("^VALIDATOR_JAVA_VERSION=(.+)$", Pattern.MULTILINE);
    /** Gradle's message when a forked test JVM exits by itself, e.g. killed by Ares after a strict timeout (exit 124). */
    private static final Pattern TEST_JVM_KILLED =
            Pattern.compile("Process 'Gradle Test Executor \\d+' finished with non-zero exit value");

    private final Duration runTimeout;
    private final boolean keepWork;

    /**
     * @param runTimeout limit for one whole Gradle run; protects against a hanging test in a suite without timeouts
     * @param keepWork   keep the work folders (with the Gradle log) for debugging
     */
    public GradleRunner(Duration runTimeout, boolean keepWork) {
        this.runTimeout = runTimeout;
        this.keepWork = keepWork;
    }

    @Override
    public Session open(Exercise exercise, String variant) throws IOException {
        Path source = exercise.sourceOf(variant);
        if (!Files.isDirectory(source)) {
            throw new IOException("No src folder for " + variant + " at " + source);
        }
        Path work = Files.createTempDirectory("validator-" + exercise.name() + "-");
        copyTree(exercise.tests(), work, true);
        copyTree(source, work.resolve("assignment/src"), false);
        try (InputStream script = GradleRunner.class.getResourceAsStream("/dev/exercisevalidator/" + INIT_SCRIPT)) {
            Files.copy(script, work.resolve(INIT_SCRIPT), StandardCopyOption.REPLACE_EXISTING);
        }
        return new GradleSession(variant, work);
    }

    private final class GradleSession implements Session {

        private final String variant;
        private final Path work;

        GradleSession(String variant, Path work) {
            this.variant = variant;
            this.work = work;
        }

        @Override
        public RunResult run() throws IOException, InterruptedException {
            return gradle(null, "clean", "test");
        }

        @Override
        public RunResult rerunShuffled(long seed) throws IOException, InterruptedException {
            // "--rerun" forces only the test task to run again; the compiled (and Ares-woven) classes are reused.
            // So every flakiness run executes the same binaries, and costs seconds instead of a full build.
            return gradle(seed, "test", "--rerun", "-PvalidatorSeed=" + seed);
        }

        private RunResult gradle(Long seed, String... tasks) throws IOException, InterruptedException {
            Path results = work.resolve("build/test-results/test");
            deleteTree(results); // never read XML left over from a previous run

            List<String> command = new ArrayList<>(wrapper());
            command.addAll(List.of(tasks));
            // --no-daemon: the build runs in a child process of the wrapper instead of a shared background daemon.
            // That costs a few seconds per run, but a hanging build can then be killed without "gradlew --stop",
            // which would also stop the user's other Gradle builds (and did stop this tool's own test run).
            command.addAll(List.of("--no-daemon", "--console=plain", "--init-script", INIT_SCRIPT));
            Path log = work.resolve(LOG_FILE);
            Process process = new ProcessBuilder(command)
                    .directory(work.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile()) // to a file: a full pipe nobody reads would block Gradle
                    .start();
            if (!process.waitFor(runTimeout.toSeconds(), TimeUnit.SECONDS)) {
                stopBuild(process);
                return new RunResult(variant, seed, RunResult.Build.TIMED_OUT, false, null, List.of(),
                        "The Gradle run did not finish within " + runTimeout.toMinutes() + " min and was stopped. "
                                + "A test probably hangs and the suite has no timeout.");
            }
            int exit = process.exitValue();
            String output = Files.readString(log);
            Matcher version = JAVA_VERSION.matcher(output);
            String javaVersion = version.find() ? version.group(1).strip() : null;
            boolean jvmKilled = TEST_JVM_KILLED.matcher(output).find();

            List<TestResult> tests = JUnitXmlReader.read(results, jvmKilled);
            if (tests.isEmpty()) {
                // No XML at all: this is a build problem (compile error, HTTP 429 from Maven Central), not a test result.
                return exit == 0
                        ? new RunResult(variant, seed, RunResult.Build.NO_TESTS, false, javaVersion, tests,
                                "Gradle succeeded but wrote no test results.")
                        : new RunResult(variant, seed, RunResult.Build.FAILED, jvmKilled, javaVersion, tests, whatWentWrong(output));
            }
            return new RunResult(variant, seed, RunResult.Build.RAN, jvmKilled, javaVersion, tests, null);
        }

        private List<String> wrapper() {
            boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
            // "sh gradlew" instead of "./gradlew": works even when the copy lost the executable bit.
            return windows ? List.of("cmd", "/c", "gradlew.bat") : List.of("sh", "gradlew");
        }

        /** Thanks to --no-daemon the build JVM and the hanging test JVM are descendants of the wrapper process. */
        private void stopBuild(Process client) {
            client.descendants().forEach(ProcessHandle::destroyForcibly);
            client.destroyForcibly();
        }

        @Override
        public void close() throws IOException {
            if (keepWork) {
                System.err.println("  kept work folder for " + variant + ": " + work);
            } else {
                deleteTree(work);
            }
        }
    }

    /** Gradle's own explanation of a failed build: the text between "* What went wrong:" and "* Try:". */
    static String whatWentWrong(String output) {
        int start = output.indexOf("* What went wrong:");
        if (start < 0) {
            List<String> lines = output.lines().filter(l -> !l.isBlank()).toList();
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 15), lines.size()));
        }
        int end = output.indexOf("* Try:", start);
        String text = output.substring(start + "* What went wrong:".length(), end < 0 ? output.length() : end);
        return text.lines().map(String::stripTrailing).filter(l -> !l.isBlank()).limit(25).collect(Collectors.joining("\n"));
    }

    private static void copyTree(Path from, Path to, boolean skipBuildOutput) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String name = dir.equals(from) ? "" : dir.getFileName().toString();
                // Left-overs of a manual run inside tests/ must not leak into our run.
                if (skipBuildOutput && from.relativize(dir).getNameCount() == 1
                        && (name.equals("build") || name.equals(".gradle") || name.equals("assignment"))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
