package dev.exercisevalidator.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JUnitXmlReaderTest {

    @TempDir
    Path dir;

    private List<TestResult> read(String testcases, boolean jvmKilled) throws IOException {
        Files.writeString(dir.resolve("TEST-com.example.SomeTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.SomeTest" tests="1" skipped="0" failures="0" errors="0">
                  <properties/>
                %s
                  <system-out><![CDATA[]]></system-out>
                </testsuite>
                """.formatted(testcases));
        return JUnitXmlReader.read(dir, jvmKilled);
    }

    @Test
    void readsPassAndFailureWithOneLineMessage() throws IOException {
        List<TestResult> results = read("""
                <testcase name="passes()" classname="com.example.SomeTest" time="0.01"/>
                <testcase name="fails()" classname="com.example.SomeTest" time="0.01">
                  <failure message="org.opentest4j.AssertionFailedError: wrong value.&#10;  expected: 1&#10;  actual: 2"
                           type="org.opentest4j.AssertionFailedError">stack trace</failure>
                </testcase>
                """, false);

        assertEquals(new TestResult("passes()", "com.example.SomeTest", TestOutcome.PASS, ""), results.get(0));
        assertEquals(TestOutcome.FAIL, results.get(1).outcome());
        assertEquals("wrong value. expected: 1 actual: 2", results.get(1).message());
    }

    @Test
    void junitTimeoutIsATimeoutNotAFailure() throws IOException {
        List<TestResult> results = read("""
                <testcase name="slow()" classname="com.example.SomeTest" time="2.0">
                  <error message="java.util.concurrent.TimeoutException: slow() timed out after 2 seconds"
                         type="java.util.concurrent.TimeoutException">stack trace</error>
                </testcase>
                """, false);

        assertEquals(TestOutcome.TIMEOUT, results.get(0).outcome());
    }

    /** The XML Gradle really writes when Ares' @StrictTimeout kills the test JVM: the running test is "skipped". */
    @Test
    void skippedTestInAKilledJvmIsATimeout() throws IOException {
        String xml = """
                <testcase name="maxOfNegativeValues()" classname="com.example.SomeTest" time="0.085"/>
                <testcase name="maxOfSingleValue()" classname="com.example.SomeTest" time="2.411">
                  <skipped/>
                </testcase>
                """;

        assertEquals(TestOutcome.TIMEOUT, read(xml, true).get(1).outcome());
        assertEquals(TestOutcome.SKIPPED, read(xml, false).get(1).outcome(), "with a living JVM, skipped means @Disabled");
    }

    @Test
    void missingResultsFolderGivesNoResults() throws IOException {
        assertTrue(JUnitXmlReader.read(dir.resolve("does-not-exist"), false).isEmpty());
    }

    @Test
    void longMessagesAreCut() {
        assertEquals(200, JUnitXmlReader.oneLine("x".repeat(500)).length());
    }
}
