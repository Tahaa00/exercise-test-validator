package dev.exercisevalidator;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import dev.exercisevalidator.check.Report;
import dev.exercisevalidator.check.Validator;
import dev.exercisevalidator.check.Verdict;
import dev.exercisevalidator.report.ConsoleReport;
import dev.exercisevalidator.run.Exercise;
import dev.exercisevalidator.run.GradleRunner;

/**
 * Command line entry point: {@code validate <exercise-folder> [options]}.
 * Exit code 0 = no findings, 1 = findings, 2 = usage, file or solution error, 3 = requested PIT run failed.
 */
public final class Main {

    private static final String USAGE = """
            Usage: validate <exercise-folder> [options]

              --runs <n>             random-order runs for the flakiness check (default 10, 0 = off)
              --seed <n>             base seed of the random-order runs (default: random, printed in the report)
              --no-mutation          skip the mutation check (PIT on the solution)
              --timeout-minutes <n>  limit for one Gradle run (default 10)
              --java-home <path>     JDK to start the exercise's Gradle with (default: JAVA_HOME of this process)
              --keep-work            keep the temporary work folders and Gradle logs
              --expected-tests <file>  UTF-8 inventory: one exact class#XML-invocation per line;
                                       optionally append TAB and a class#gradingTest to group invocations
              --decisions <csv>      export legacy decisions to a new file outside exercise inputs;
                                     columns: exercise,test,decision (exercise is the folder name)
              --help                 show this help
            """;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if ((args.length == 1 && args[0].equals("--help"))
                || (args.length == 2 && args[0].equals("validate") && args[1].equals("--help"))) {
            out.print(USAGE);
            return 0;
        }
        int runs = 10;
        long seed = ThreadLocalRandom.current().nextLong(1_000_000);
        long timeoutMinutes = 10;
        boolean keepWork = false;
        boolean mutation = true;
        Path javaHome = null;
        Path expectedFile = null;
        Path decisions = null;
        try {
            if (args.length < 2 || !"validate".equals(args[0])) {
                throw new IllegalArgumentException("Expected: validate <exercise-folder>");
            }
            Path folder = Path.of(args[1]).toAbsolutePath().normalize();
            for (int i = 2; i < args.length; i++) {
                switch (args[i]) {
                    case "--help" -> {
                        out.print(USAGE);
                        return 0;
                    }
                    case "--runs" -> {
                        long count = number(args, ++i, 0);
                        if (count > Integer.MAX_VALUE) {
                            throw new IllegalArgumentException("--runs exceeds the supported range");
                        }
                        runs = (int) count;
                    }
                    case "--seed" -> seed = number(args, ++i, Long.MIN_VALUE);
                    case "--timeout-minutes" -> {
                        timeoutMinutes = number(args, ++i, 1);
                        if (timeoutMinutes > 24 * 60) {
                            throw new IllegalArgumentException("--timeout-minutes exceeds the supported range");
                        }
                    }
                    case "--keep-work" -> keepWork = true;
                    case "--no-mutation" -> mutation = false;
                    case "--java-home" -> {
                        if (++i >= args.length || !Files.isDirectory(Path.of(args[i]))) {
                            throw new IllegalArgumentException("--java-home needs the path of a JDK folder");
                        }
                        javaHome = Path.of(args[i]).toAbsolutePath();
                    }
                    case "--expected-tests" -> expectedFile = fileOption(args, ++i, expectedFile);
                    case "--decisions" -> decisions = fileOption(args, ++i, decisions);
                    default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }
            ExpectedTests inventory = expectedFile == null ? new ExpectedTests(List.of()) : ExpectedTests.read(expectedFile);
            if (decisions != null) {
                decisions = DecisionExport.destination(decisions, folder);
            }
            Validator validator = new Validator(new GradleRunner(Duration.ofMinutes(timeoutMinutes), keepWork, javaHome),
                    step -> err.println("  running " + step)); // progress on stderr keeps stdout a clean report
            Report report;
            List<String> invocations = inventory.invocations();
            try {
                report = validator.validate(Exercise.open(folder), runs, seed, mutation, invocations);
            } catch (IOException | IllegalArgumentException e) {
                if (decisions == null && e instanceof IllegalArgumentException) {
                    throw e; // a wrong folder is a usage error; only an export still needs a report to write
                }
                report = failedReport(folder, seed, invocations, e.getMessage() == null ? e.toString() : e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                report = failedReport(folder, seed, invocations, "Validation was interrupted.");
            }
            out.print(ConsoleReport.render(report));
            if (decisions != null) {
                out.print(DecisionExport.write(decisions, report, inventory, mutation));
            }
            return report.exitCode();
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println();
            err.print(USAGE);
            return 2;
        } catch (IOException | UnsupportedOperationException e) {
            err.println("File error: " + e);
            return 2;
        }
    }

    private static Report failedReport(Path folder, long seed, List<String> invocations, String reason) {
        String name = folder.getFileName() == null ? folder.toString() : folder.getFileName().toString();
        List<Verdict> verdicts = invocations.stream()
                .map(id -> new Verdict(id, Verdict.Status.NOT_CHECKED, List.of("Validation could not finish."))).toList();
        return new Report(name, null, List.of(), 0, seed, verdicts, List.of(), List.of(), null, reason);
    }

    private static Path fileOption(String[] args, int index, Path previous) {
        String option = args[index - 1];
        if (previous != null) {
            throw new IllegalArgumentException(option + " was supplied twice");
        }
        if (index >= args.length || args[index].isBlank() || args[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " needs a file path");
        }
        return Path.of(args[index]);
    }

    private static long number(String[] args, int index, long min) {
        if (index >= args.length) {
            throw new IllegalArgumentException(args[index - 1] + " needs a number");
        }
        try {
            long value = Long.parseLong(args[index]);
            if (value < min) {
                throw new IllegalArgumentException(args[index - 1] + " must be at least " + min);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(args[index - 1] + " needs a number, got: " + args[index]);
        }
    }
}
