package dev.exercisevalidator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import dev.exercisevalidator.check.Report;
import dev.exercisevalidator.check.Validator;
import dev.exercisevalidator.report.ConsoleReport;
import dev.exercisevalidator.run.Exercise;
import dev.exercisevalidator.run.GradleRunner;

/**
 * Command line entry point: {@code validate <exercise-folder> [options]}.
 * Exit code 0 = no findings, 1 = findings, 2 = usage error or the solution could not be run.
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
            """;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Path folder = null;
        int runs = 10;
        long seed = ThreadLocalRandom.current().nextLong(1_000_000);
        long timeoutMinutes = 10;
        boolean keepWork = false;
        boolean mutation = true;
        Path javaHome = null;
        try {
            if (args.length < 2 || !"validate".equals(args[0])) {
                throw new IllegalArgumentException("Expected: validate <exercise-folder>");
            }
            folder = Path.of(args[1]);
            for (int i = 2; i < args.length; i++) {
                switch (args[i]) {
                    case "--runs" -> runs = Math.toIntExact(number(args, ++i, 0));
                    case "--seed" -> seed = number(args, ++i, Long.MIN_VALUE);
                    case "--timeout-minutes" -> timeoutMinutes = number(args, ++i, 1);
                    case "--keep-work" -> keepWork = true;
                    case "--no-mutation" -> mutation = false;
                    case "--java-home" -> {
                        if (++i >= args.length || !java.nio.file.Files.isDirectory(Path.of(args[i]))) {
                            throw new IllegalArgumentException("--java-home needs the path of a JDK folder");
                        }
                        javaHome = Path.of(args[i]).toAbsolutePath();
                    }
                    default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }
            Exercise exercise = Exercise.open(folder);
            Validator validator = new Validator(new GradleRunner(Duration.ofMinutes(timeoutMinutes), keepWork, javaHome),
                    step -> System.err.println("  running " + step)); // progress on stderr keeps stdout a clean report
            Report report = validator.validate(exercise, runs, seed, mutation);
            System.out.print(ConsoleReport.render(report));
            System.exit(report.exitCode());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.print(USAGE);
            System.exit(2);
        }
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
