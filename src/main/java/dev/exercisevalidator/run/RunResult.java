package dev.exercisevalidator.run;

import java.util.List;
import java.util.Optional;

/**
 * Result of one Gradle run of the suite against one variant.
 *
 * @param variant     "solution" or "alternatives/&lt;name&gt;"
 * @param seed        random-order seed, or null for the declared order
 * @param build       whether Gradle got as far as producing test results
 * @param jvmKilled   the test JVM died mid-run, so tests after that point are missing from the XML
 * @param javaVersion Java version of the build's toolchain as reported by the build, or null if unknown
 * @param tests       results in the order they appear in the XML
 * @param problem     for a failed build, Gradle's "What went wrong" text
 */
public record RunResult(String variant, Long seed, Build build, boolean jvmKilled, String javaVersion,
                        List<TestResult> tests, String problem) {

    public enum Build {
        /** Test results exist. Tests may still have failed. */
        RAN,
        /** No test results: compile error, dependency download failure, broken build file. */
        FAILED,
        /** The whole Gradle run exceeded the tool's time limit and was stopped. */
        TIMED_OUT,
        /** Gradle succeeded but wrote no results: the build has no tests. */
        NO_TESTS
    }

    public boolean ran() {
        return build == Build.RAN;
    }

    public Optional<TestResult> find(String testName) {
        return tests.stream().filter(t -> t.name().equals(testName)).findFirst();
    }

    /** Outcome of a test in this run; a test that is not in the XML counts as NOT_RUN, never as passed. */
    public TestOutcome outcomeOf(String testName) {
        return find(testName).map(TestResult::outcome).orElse(TestOutcome.NOT_RUN);
    }
}
