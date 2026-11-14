package dev.exercisevalidator.run;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The event records and XML shapes below are what Gradle 8.12 and 9.8.0 really produced for an endless loop. */
class KilledJvmTest {

    private static final String CLASS = "com.example.SomeTest";

    @TempDir
    Path dir;

    private Path events(String content) throws IOException {
        String records = content.lines().map(line -> {
            String[] fields = line.split("\t", -1);
            fields[1] = encode(fields[1]);
            fields[2] = encode(fields[2]);
            if (fields.length == 5) {
                fields[4] = encode(fields[4]);
            }
            return String.join("\t", fields);
        }).collect(Collectors.joining("\n"));
        return Files.writeString(dir.resolve("events.tsv"), records);
    }

    private static String encode(String field) {
        return Base64.getEncoder().encodeToString(field.getBytes(StandardCharsets.UTF_8));
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
                new TestResult("hangs()", CLASS, TestOutcome.TIMEOUT, "the test JVM was killed while this test was running", true)),
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
    void eventAndXmlIdentitiesMatchExactlyEvenWithEmbeddedWhitespace() throws IOException {
        String name = "point  2\t↔\nend";
        String fields = encode(CLASS) + "\t" + encode(name);
        Path log = Files.writeString(dir.resolve("events.tsv"), "START\t" + fields + "\nEND\t" + fields + "\tSKIPPED\t\n");
        TestResult skipped = new TestResult(name, CLASS, TestOutcome.SKIPPED, "");
        for (List<TestResult> xml : List.of(List.<TestResult>of(), List.of(skipped))) {
            List<TestResult> rows = KilledJvm.repair(xml, log);
            assertEquals(1, rows.size());
            assertEquals(skipped.qualifiedName(), rows.get(0).qualifiedName());
            assertEquals(TestOutcome.TIMEOUT, rows.get(0).outcome());
        }
    }

    @Test
    void repeatedEventsNeverCollapseIntoOnePassingInvocation() throws IOException {
        TestResult first = new TestResult("repeat", "lab.Trail", TestOutcome.PASS, "");
        Path log = events("""
                START\tlab.Trail\trepeat
                END\tlab.Trail\trepeat\tSUCCESS\t
                START\tlab.Trail\trepeat
                END\tlab.Trail\trepeat\tSKIPPED\t
                """);
        for (List<TestResult> xml : List.of(List.<TestResult>of(), List.of(first))) {
            List<TestResult> rows = KilledJvm.repair(xml, log);
            assertEquals(2, rows.size());
            RunResult run = new RunResult("solution", null, RunResult.Build.RAN, true, null, rows, null);
            assertEquals(TestOutcome.AMBIGUOUS, run.outcomeOf(first.qualifiedName()));
        }
    }

    @Test
    void concurrentUnfinishedTestsCannotEstablishWhichTestKilledTheExecutor() throws IOException {
        Path log = events("""
                START\tlab.Clock\ttick()
                START\tlab.Clock\ttock()
                END\tlab.Clock\ttock()\tSKIPPED\t
                """);
        List<TestResult> rows = KilledJvm.repair(List.of(), log);
        assertEquals(List.of(TestOutcome.AMBIGUOUS, TestOutcome.SKIPPED), rows.stream().map(TestResult::outcome).toList());
        assertEquals(true, new RunResult("solution", null, RunResult.Build.RAN, true, null, rows, null).unattributedJvmExit());
    }

    @Test
    void aTestThatNeverEndedIsATimeoutToo() throws IOException {
        List<TestResult> results = KilledJvm.repair(List.of(), events("START\tcom.example.SomeTest\thangs()\n"));

        assertEquals(TestOutcome.TIMEOUT, results.get(0).outcome());
    }

    @Test
    void skippedXmlWithoutAnEventRecordCannotProveATimeout() throws IOException {
        List<TestResult> xml = List.of(new TestResult("hangs()", CLASS, TestOutcome.SKIPPED, "skipped"));

        assertEquals(TestOutcome.SKIPPED, KilledJvm.repair(xml, dir.resolve("missing.tsv")).get(0).outcome());
    }
}
