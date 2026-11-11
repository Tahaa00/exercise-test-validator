package dev.exercisevalidator.run;

/**
 * One {@code <testcase>} of the JUnit XML.
 *
 * @param name      the XML test name; this is what Artemis binds tasks and points to
 * @param className the test class, used only to spot the same name in two classes
 * @param message   the failure message on one line, empty when the test passed
 */
public record TestResult(String name, String className, TestOutcome outcome, String message) {
}
