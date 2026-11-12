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
 * <p>The exercise's build files are never edited. The tool's additions (random test order, PIT) come in through
 * init scripts on the command line, so what we measure is the build that grades students.
 */
public final class GradleRunner implements VariantRunner {

    private static final String INIT_SCRIPT = "validator.init.gradle";
    private static final String PIT_SCRIPT = "pitest.init.gradle";
    private static final String LOG_FILE = "validator-gradle.log";
    private static final String PIT_LOG_FILE = "validator-pitest.log";
    private static final Pattern JAVA_VERSION = Pattern.compile("^VALIDATOR_JAVA_VERSION=(.+)$", Pattern.MULTILINE);
    /** Gradle's message when a forked test JVM exits by itself, e.g. killed by Ares after a strict timeout (exit 124). */
    private static final Pattern TEST_JVM_KILLED =
            Pattern.compile("Process 'Gradle Test Executor \\d+' finished with non-zero exit value");

    /** Printed by pitest.init.gradle when it had to make Ares 1 trust the solution and PIT. */
    private static final String ARES1_TRUSTED = "VALIDATOR_PIT_ARES1_TRUSTED=true";
    private static final Pattern PIT_RED_SUITE = Pattern.compile("(\\d+) tests? did not pass without mutation");
    /** PIT names a failing test as "Description [testClass=..., name=.../[method:name()]] did not pass without mutation". */
    private static final Pattern PIT_FAILED_TEST = Pattern.compile("\\[method:([^\\]]+)]] did not pass without mutation");
    private static final Pattern PIT_ERROR = Pattern.compile("org\\.pitest\\.help\\.PitHelpError: (.+)");

    private final Duration runTimeout;
    private final boolean keepWork;
    private final Path javaHome;

    /**
     * @param runTimeout limit for one whole Gradle run; protects against a hanging test in a suite without timeouts
     * @param keepWork   keep the work folders (with the Gradle log) for debugging
     * @param javaHome   JDK to start the exercise's Gradle with, or null for the tool's own environment.
     *                   This is the JDK Gradle itself runs on; the tests still run on the build's toolchain.
     */
    public GradleRunner(Duration runTimeout, boolean keepWork, Path javaHome) {
        this.runTimeout = runTimeout;
        this.keepWork = keepWork;
        this.javaHome = javaHome;
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
        for (String name : List.of(INIT_SCRIPT, PIT_SCRIPT)) {
            try (InputStream script = GradleRunner.class.getResourceAsStream("/dev/exercisevalidator/" + name)) {
                Files.copy(script, work.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
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

            Path log = work.resolve(LOG_FILE);
            List<String> arguments = new ArrayList<>(List.of(tasks));
            arguments.addAll(List.of("--init-script", INIT_SCRIPT));
            Integer exitOrNull = execute(arguments, log);
            if (exitOrNull == null) {
                return new RunResult(variant, seed, RunResult.Build.TIMED_OUT, false, null, List.of(),
                        "The Gradle run did not finish within " + runTimeout.toMinutes() + " min and was stopped. "
                                + "A test probably hangs and the suite has no timeout.");
            }
            int exit = exitOrNull;
            String output = Files.readString(log);
            Matcher version = JAVA_VERSION.matcher(output);
            String javaVersion = version.find() ? version.group(1).strip() : null;
            boolean jvmKilled = TEST_JVM_KILLED.matcher(output).find();

            List<TestResult> tests = JUnitXmlReader.read(results);
            if (jvmKilled) {
                tests = KilledJvm.repair(tests, work.resolve(KilledJvm.EVENTS_FILE));
            }
            if (tests.isEmpty()) {
                // No XML at all: this is a build problem (compile error, HTTP 429 from Maven Central), not a test result.
                return exit == 0
                        ? new RunResult(variant, seed, RunResult.Build.NO_TESTS, false, javaVersion, tests,
                                "Gradle succeeded but wrote no test results.")
                        : new RunResult(variant, seed, RunResult.Build.FAILED, jvmKilled, javaVersion, tests,
                                whatWentWrong(output) + jdkHint(output));
            }
            // XML can already exist when a finalizer or another build task fails. A red Gradle exit
            // is an ordinary completed test run only when failing tests account for that exit.
            if (exit != 0 && !jvmKilled && !failedOnlyOnTests(output, tests)) {
                return new RunResult(variant, seed, RunResult.Build.FAILED, false, javaVersion, tests, whatWentWrong(output));
            }
            return new RunResult(variant, seed, RunResult.Build.RAN, jvmKilled, javaVersion, tests, null);
        }

        @Override
        public MutationResult mutate() throws IOException, InterruptedException {
            Path report = work.resolve("build/reports/pitest");
            deleteTree(report);
            Path log = work.resolve(PIT_LOG_FILE);
            // No "clean": PIT mutates the classes the solution run compiled (and Ares wove), in memory, one at a time.
            Integer exit = execute(List.of("pitest", "--init-script", PIT_SCRIPT), log);
            if (exit == null) {
                return MutationResult.failed("PIT did not finish within " + runTimeout.toMinutes()
                        + " min and was stopped. Raise the limit with --timeout-minutes.");
            }
            String output = Files.readString(log);
            Path xml = report.resolve("mutations.xml");
            if (exit != 0 || !Files.isRegularFile(xml)) {
                return MutationResult.failed(pitProblem(output));
            }
            String remark = output.contains(ARES1_TRUSTED) ? "This build uses Ares 1, whose security manager blocks PIT. "
                    + "For this PIT run only, Ares was told to trust the solution's classes and PIT." : null;
            return MutationResult.ran(PitXmlReader.read(xml, work.resolve("assignment/src")), remark);
        }

        /** Runs the exercise's Gradle wrapper with the output in a log file. Returns the exit code, or null when the time limit hit. */
        private Integer execute(List<String> arguments, Path log) throws IOException, InterruptedException {
            List<String> command = new ArrayList<>(wrapper());
            command.addAll(arguments);
            // --no-daemon: the build runs in a child process of the wrapper instead of a shared background daemon.
            // That costs a few seconds per run, but a hanging build can then be killed without "gradlew --stop",
            // which would also stop the user's other Gradle builds (and did stop this tool's own test run).
            command.addAll(List.of("--no-daemon", "--console=plain"));
            ProcessBuilder builder = new ProcessBuilder(command);
            if (javaHome != null) {
                builder.environment().put("JAVA_HOME", javaHome.toString());
            }
            Process process = builder
                    .directory(work.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile()) // to a file: a full pipe nobody reads would block Gradle
                    .start();
            if (!process.waitFor(runTimeout.toSeconds(), TimeUnit.SECONDS)) {
                stopBuild(process);
                return null;
            }
            return process.exitValue();
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

    static boolean failedOnlyOnTests(String output, List<TestResult> tests) {
        String marker = "* What went wrong:";
        String reason = whatWentWrong(output);
        return output.indexOf(marker) >= 0 && output.indexOf(marker) == output.lastIndexOf(marker)
                && reason.startsWith("Execution failed for task ':test'.\n> There were failing tests")
                && tests.stream().anyMatch(t -> t.outcome() == TestOutcome.FAIL || t.outcome() == TestOutcome.TIMEOUT);
    }

    /**
     * Why PIT gave no report. PIT's own reason beats Gradle's, which is only "java finished with non-zero exit value 1".
     */
    static String pitProblem(String output) {
        Matcher redSuite = PIT_RED_SUITE.matcher(output);
        if (redSuite.find()) {
            List<String> tests = PIT_FAILED_TEST.matcher(output).results().map(r -> r.group(1)).distinct().toList();
            String count = redSuite.group(1) + (redSuite.group(1).equals("1") ? " test that passes" : " tests that pass");
            String message = count + " in the normal run failed when PIT ran the suite"
                    + (tests.isEmpty() ? "" : ": " + String.join(", ", tests)) + ".\n"
                    + "PIT needs a green suite before it makes mutants. It starts the tests itself and adds coverage probes "
                    + "to the solution's classes, so a sandbox or a test that relies on the Gradle test task can fail there.";
            if (output.contains("BAD PACKAGE ACCESS: sun.pitest")) {
                message += "\nHere the Ares security manager blocked PIT's probes (\"BAD PACKAGE ACCESS: sun.pitest\")"
                        + (output.contains(ARES1_TRUSTED) ? ", although the tool asked Ares to trust them. This Ares version"
                        + " probably does not know the ares.security.trustedpackages property." : ".");
            }
            return message + "\nDetails are in " + PIT_LOG_FILE + " in the work folder (--keep-work).";
        }
        Matcher pitError = PIT_ERROR.matcher(output);
        if (pitError.find()) {
            return "PIT stopped: " + pitError.group(1).strip() + "\nDetails are in " + PIT_LOG_FILE + " in the work folder (--keep-work).";
        }
        return whatWentWrong(output);
    }

    /**
     * An old Gradle cannot read the class files of a newer JDK (Gradle 8.12 on JDK 25: "Unsupported class file
     * major version 69"). The message says nothing about the cure, so add it.
     */
    static String jdkHint(String output) {
        Matcher matcher = Pattern.compile("Unsupported class file major version (\\d+)").matcher(output);
        if (!matcher.find()) {
            return "";
        }
        int java = Integer.parseInt(matcher.group(1)) - 44; // class file version 61 = Java 17, 69 = Java 25
        return "\nHint: the Gradle version of this exercise cannot run on JDK " + java + ". Start it on an older JDK with"
                + " --java-home <path to JDK> (or set JAVA_HOME). The tests still run on the Java version the build declares.";
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
