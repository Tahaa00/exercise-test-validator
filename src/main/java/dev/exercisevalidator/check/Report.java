package dev.exercisevalidator.check;

import java.util.List;

/**
 * Everything the checks found, independent of how it is printed (console now, Markdown/JSON later).
 *
 * @param javaVersion  Java version of the exercise's toolchain, as reported by its build
 * @param alternatives the alternatives that were run
 * @param shuffledRuns number of random-order runs that completed
 * @param baseSeed     seed the random-order run seeds were derived from; the same value reproduces the same runs
 * @param problems     runs that could not be done (an alternative that does not build, an aborted flakiness check)
 * @param warnings     things that are not wrong but will surprise, such as test names Artemis cannot bind
 * @param fatal        set when the solution itself could not be run; then there are no verdicts
 */
public record Report(String exercise, String javaVersion, List<String> alternatives, int shuffledRuns, long baseSeed,
                     List<Verdict> verdicts, List<String> problems, List<String> warnings, String fatal) {

    public long count(Verdict.Status status) {
        return verdicts.stream().filter(v -> v.status() == status).count();
    }

    public long findings() {
        return verdicts.size() - count(Verdict.Status.OK) + problems.size();
    }

    /** 0 = clean, 1 = findings, 2 = the solution could not be run. Lets CI fail on a bad suite. */
    public int exitCode() {
        if (fatal != null) {
            return 2;
        }
        return findings() == 0 ? 0 : 1;
    }
}
