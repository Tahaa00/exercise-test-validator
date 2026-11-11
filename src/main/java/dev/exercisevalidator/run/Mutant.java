package dev.exercisevalidator.run;

/**
 * One mutant from PIT's report: a small change to the solution, and whether the suite noticed it.
 *
 * @param sourceFile  file name only, as PIT reports it ("ArrayMax.java")
 * @param className   fully qualified name of the mutated class
 * @param method      name of the mutated method
 * @param descriptor  JVM descriptor of that method, e.g. "([I)I"; tells overloads apart
 * @param line        line of the change in the source file
 * @param description PIT's text for the change, e.g. "changed conditional boundary"
 * @param status      PIT's status: KILLED, SURVIVED, NO_COVERAGE, TIMED_OUT, RUN_ERROR, ...
 * @param detected    PIT's own verdict; true for KILLED, TIMED_OUT and RUN_ERROR among others
 * @param sourceLine  the mutated line of the solution, or "" when the file was not found
 */
public record Mutant(String sourceFile, String className, String method, String descriptor, int line, String description,
                     String status, boolean detected, String sourceLine) {

    /** A failing assertion noticed the mutant. */
    public boolean killed() {
        return status.equals("KILLED");
    }

    /** Every test that ran the mutated line still passed. */
    public boolean survived() {
        return status.equals("SURVIVED");
    }

    /** The mutant sits in {@code main(String[])}, the method a JVM starts a program with. */
    public boolean inEntryPoint() {
        return method.equals("main") && descriptor.equals("([Ljava/lang/String;)V");
    }

    /** No test runs the mutated line at all. */
    public boolean notCovered() {
        return status.equals("NO_COVERAGE");
    }
}
