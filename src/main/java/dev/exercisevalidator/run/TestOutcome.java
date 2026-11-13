package dev.exercisevalidator.run;

/** What happened to one test in one Gradle run. */
public enum TestOutcome {
    PASS,
    /** An assertion failed or the test threw. */
    FAIL,
    /** The test hit a time limit. Kept apart from FAIL: it points to a slow machine or an endless loop, not a wrong answer. */
    TIMEOUT,
    /** Reported as skipped while the test JVM was alive, for example {@code @Disabled}. */
    SKIPPED,
    /** Known from other runs but absent from this run's XML, typically because the test JVM was killed earlier. */
    NOT_RUN,
    /** The XML identity is incomplete or repeats within the same run. */
    AMBIGUOUS
}
