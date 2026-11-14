package dev.exercisevalidator.run;

/**
 * One {@code <testcase>} of the JUnit XML.
 *
 * @param name      the XML test name; this is what Artemis binds tasks and points to
 * @param className the XML test class
 * @param message   the failure message on one line, empty when the test passed
 * @param interruptedByJvmExit whether the Gradle event record uniquely attributed an executor exit to this invocation
 */
public record TestResult(String name, String className, TestOutcome outcome, String message, boolean interruptedByJvmExit) {
    public TestResult(String name, String className, TestOutcome outcome, String message) {
        this(name, className, outcome, message, false);
    }

    public String qualifiedName() {
        return className + "#" + name;
    }
}
