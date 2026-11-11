package dev.exercisevalidator.run;

/**
 * One mutant from PIT's report: a small change to the solution, and whether the suite noticed it.
 *
 * @param sourceFile  file name only, as PIT reports it ("ArrayMax.java")
 * @param className   fully qualified name of the mutated class
 * @param line        line of the change in the source file
 * @param description PIT's text for the change, e.g. "changed conditional boundary"
 * @param status      PIT's status: KILLED, SURVIVED, NO_COVERAGE, TIMED_OUT, RUN_ERROR, ...
 * @param detected    PIT's own verdict; true for KILLED, TIMED_OUT and RUN_ERROR among others
 * @param sourceLine  the mutated line of the solution, or "" when the file was not found
 */
public record Mutant(String sourceFile, String className, String method, int line, String description,
                     String status, boolean detected, String sourceLine) {

    /** A failing assertion noticed the mutant. */
    public boolean killed() {
        return status.equals("KILLED");
    }

    /** Every test that ran the mutated line still passed. */
    public boolean survived() {
        return status.equals("SURVIVED");
    }

    /** No test runs the mutated line at all. */
    public boolean notCovered() {
        return status.equals("NO_COVERAGE");
    }
}
