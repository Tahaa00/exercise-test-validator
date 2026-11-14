package dev.exercisevalidator.run;

import java.util.List;
import java.util.Optional;

/**
 * Result of one Gradle run of the suite against one variant.
 *
 * @param variant     source path relative to the exercise, for example "solution" or "buggy/missing-null-guard"
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
        /** The build failed outside ordinary test failures. Any available XML is retained as evidence. */
        FAILED,
        /** The whole Gradle run exceeded the tool's time limit and was stopped. */
        TIMED_OUT,
        /** Gradle succeeded but wrote no results: the build has no tests. */
        NO_TESTS
    }

    public boolean ran() {
        return build == Build.RAN;
    }

    /** An unexplained executor death leaves the grading run incomplete, even if its surviving rows passed. */
    public boolean unattributedJvmExit() {
        if (!jvmKilled) {
            return false;
        }
        List<TestResult> interrupted = tests.stream().filter(TestResult::interruptedByJvmExit).toList();
        return interrupted.size() != 1 || outcomeOf(interrupted.get(0).qualifiedName()) != TestOutcome.TIMEOUT;
    }

    public List<TestResult> matches(String qualifiedName) {
        return tests.stream().filter(t -> t.qualifiedName().equals(qualifiedName)).toList();
    }

    /** Raw XML evidence is attributable only when exactly one row matches the qualified identity. */
    public Optional<TestResult> find(String qualifiedName) {
        List<TestResult> rows = matches(qualifiedName);
        return rows.size() == 1 ? Optional.of(rows.get(0)) : Optional.empty();
    }

    /** Only a unique, fully identified row in a grading run can supply an attributable outcome. */
    public TestOutcome outcomeOf(String qualifiedName) {
        return outcomeOf(matches(qualifiedName));
    }

    public TestOutcome outcomeOf(List<TestResult> rows) {
        if (!ran() || rows.isEmpty()) {
            return TestOutcome.NOT_RUN;
        }
        TestResult row = rows.get(0);
        if (rows.size() > 1 || row.className().isBlank() || row.name().isBlank()) {
            return TestOutcome.AMBIGUOUS;
        }
        return row.outcome();
    }
}
