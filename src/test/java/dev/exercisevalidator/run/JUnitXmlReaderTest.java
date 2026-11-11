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

    private List<TestResult> read(String testcases) throws IOException {
        Files.writeString(dir.resolve("TEST-com.example.SomeTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.SomeTest" tests="1" skipped="0" failures="0" errors="0">
                  <properties/>
                %s
                  <system-out><![CDATA[]]></system-out>
                </testsuite>
                """.formatted(testcases));
        return JUnitXmlReader.read(dir);
    }

    @Test
    void readsPassAndFailureWithOneLineMessage() throws IOException {
        List<TestResult> results = read("""
                <testcase name="passes()" classname="com.example.SomeTest" time="0.01"/>
                <testcase name="fails()" classname="com.example.SomeTest" time="0.01">
                  <failure message="org.opentest4j.AssertionFailedError: wrong value.&#10;  expected: 1&#10;  actual: 2"
                           type="org.opentest4j.AssertionFailedError">stack trace</failure>
                </testcase>
                """);

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
                """);

        assertEquals(TestOutcome.TIMEOUT, results.get(0).outcome());
    }

    @Test
    void missingResultsFolderGivesNoResults() throws IOException {
        assertTrue(JUnitXmlReader.read(dir.resolve("does-not-exist")).isEmpty());
    }

    @Test
    void longMessagesAreCut() {
        assertEquals(200, JUnitXmlReader.oneLine("x".repeat(500)).length());
    }
}
