package dev.exercisevalidator.check;

import java.util.List;

import dev.exercisevalidator.run.MutationResult;

/**
 * Everything the checks found, independent of how it is printed (console now, Markdown/JSON later).
 *
 * @param javaVersion  Java version of the exercise's toolchain, as reported by its build
 * @param alternatives the alternatives that were run
 * @param shuffledRuns number of random-order runs that completed
 * @param baseSeed     seed the random-order run seeds were derived from; the same value reproduces the same runs
 * @param problems     runs that could not be done (an alternative that does not build, an aborted flakiness check)
 * @param warnings     things that are not wrong but will surprise, such as test names Artemis cannot bind
 * @param mutation     result of the mutation check, or null when it was switched off or never reached.
 *                     Survivors are hints for a human (some mutants are equivalent to the original),
 *                     so they count neither as findings nor towards the exit code.
 * @param fatal        diagnostic when a reference run could not complete
 * @param referencePasses number of unique, attributable passes in the reference run, or -1 when unavailable
 */
public record Report(String exercise, String javaVersion, List<String> alternatives, int shuffledRuns, long baseSeed,
                     List<Verdict> verdicts, List<String> problems, List<String> warnings, MutationResult mutation,
                     List<BuggyResult> buggyVariants, String fatal, int referencePasses) {

    public Report(String exercise, String javaVersion, List<String> alternatives, int shuffledRuns, long baseSeed,
                  List<Verdict> verdicts, List<String> problems, List<String> warnings, MutationResult mutation, String fatal) {
        this(exercise, javaVersion, alternatives, shuffledRuns, baseSeed, verdicts, problems, warnings, mutation, List.of(), fatal);
    }

    public Report(String exercise, String javaVersion, List<String> alternatives, int shuffledRuns, long baseSeed,
                  List<Verdict> verdicts, List<String> problems, List<String> warnings, MutationResult mutation,
                  List<BuggyResult> buggyVariants, String fatal) {
        this(exercise, javaVersion, alternatives, shuffledRuns, baseSeed, verdicts, problems, warnings, mutation,
                buggyVariants, fatal, -1);
    }

    public long count(Verdict.Status status) {
        return verdicts.stream().filter(v -> v.status() == status).count();
    }

    public long missedBuggyVariants() {
        return buggyVariants.stream().filter(b -> b.status() == BuggyResult.Status.MISSED).count();
    }

    public long findings() {
        return verdicts.size() - count(Verdict.Status.OK) + problems.size() + missedBuggyVariants();
    }

    /** 0 = no findings, 1 = findings, 2 = fatal reference run, 3 = requested PIT run produced no usable report. */
    public int exitCode() {
        if (fatal != null) {
            return 2;
        }
        if (mutation != null && mutation.state() == MutationResult.State.FAILED) {
            return 3;
        }
        return findings() == 0 ? 0 : 1;
    }
}
