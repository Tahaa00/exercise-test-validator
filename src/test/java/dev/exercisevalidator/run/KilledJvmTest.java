package dev.exercisevalidator.run;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The event records and XML shapes below are what Gradle 8.12 and 9.8.0 really produced for an endless loop. */
class KilledJvmTest {

    private static final String CLASS = "com.example.SomeTest";

    @TempDir
    Path dir;

    private Path events(String content) throws IOException {
        return Files.writeString(dir.resolve("events.tsv"), content);
    }

    private static final String TWO_DONE_ONE_HUNG = """
            START\tcom.example.SomeTest\tfirst()
            END\tcom.example.SomeTest\tfirst()\tSUCCESS\t
            START\tcom.example.SomeTest\tsecond()
            END\tcom.example.SomeTest\tsecond()\tFAILURE\torg.opentest4j.AssertionFailedError: wrong value
            START\tcom.example.SomeTest\thangs()
            END\tcom.example.SomeTest\thangs()\tSKIPPED\t
            """;

    /** Gradle 8.12: no XML at all, so everything comes from the record. */
    @Test
    void withoutXmlTheRecordGivesAllResults() throws IOException {
        List<TestResult> results = KilledJvm.repair(List.of(), events(TWO_DONE_ONE_HUNG));

        assertEquals(List.of(
                new TestResult("first()", CLASS, TestOutcome.PASS, ""),
                new TestResult("second()", CLASS, TestOutcome.FAIL, "wrong value"),
                new TestResult("hangs()", CLASS, TestOutcome.TIMEOUT, "the test JVM was killed while this test was running")),
                results);
    }

    /** Gradle 9.8: partial XML in which the hanging test is "skipped", like a disabled test. */
    @Test
    void skippedInXmlBecomesTimeoutOnlyForTheTestThatNeverEnded() throws IOException {
        List<TestResult> xml = List.of(
                new TestResult("disabled()", CLASS, TestOutcome.SKIPPED, "skipped"),
                new TestResult("hangs()", CLASS, TestOutcome.SKIPPED, "skipped"));

        List<TestResult> results = KilledJvm.repair(xml, events("""
                START\tcom.example.SomeTest\tdisabled()
                END\tcom.example.SomeTest\tdisabled()\tSKIPPED\t
                START\tcom.example.SomeTest\thangs()
                END\tcom.example.SomeTest\thangs()\tSKIPPED\t
                """));

        assertEquals(TestOutcome.SKIPPED, results.get(0).outcome());
        assertEquals(TestOutcome.TIMEOUT, results.get(1).outcome());
        assertEquals(2, results.size());
    }

    @Test
    void aTestThatNeverEndedIsATimeoutToo() throws IOException {
        List<TestResult> results = KilledJvm.repair(List.of(), events("START\tcom.example.SomeTest\thangs()\n"));

        assertEquals(TestOutcome.TIMEOUT, results.get(0).outcome());
    }

    @Test
    void withoutARecordSkippedCountsAsTimeout() throws IOException {
        List<TestResult> xml = List.of(new TestResult("hangs()", CLASS, TestOutcome.SKIPPED, "skipped"));

        assertEquals(TestOutcome.TIMEOUT, KilledJvm.repair(xml, dir.resolve("missing.tsv")).get(0).outcome());
    }
}
