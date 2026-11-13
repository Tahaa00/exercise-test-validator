package dev.exercisevalidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.exercisevalidator.check.Report;
import dev.exercisevalidator.check.Validator;
import dev.exercisevalidator.check.Verdict;
import dev.exercisevalidator.check.Verdict.Status;
import dev.exercisevalidator.report.ConsoleReport;
import dev.exercisevalidator.run.Exercise;
import dev.exercisevalidator.run.MutationResult;
import dev.exercisevalidator.run.RunResult;
import dev.exercisevalidator.run.TestOutcome;
import dev.exercisevalidator.run.TestResult;
import dev.exercisevalidator.run.VariantRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecisionExportTest {
    @TempDir Path temp;

    @Test
    void groupsOnlyDeclaredInvocationsAndPreservesFrozenDenominator() throws IOException {
        ExpectedTests inventory = inventory("""
                p.First#same()
                p.Second#same()
                p.Cells#[1] quote, "one"	p.Cells#match()
                p.Cells#[2] quote, "two"	p.Cells#match()
                p.Cells#[1] case#1	p.Cells#unseen()
                p.Cells#[2] case#2	p.Cells#unseen()
                p.Cells#red()	p.Cells#allRed()
                p.Cells#unstable()
                p.Cells#partial()
                p.Cells#ambiguous()
                p.Cells#quote, "this"
                """);
        List<Verdict> verdicts = List.of(
                verdict("p.First#same()", Status.OK), verdict("p.Second#same()", Status.REJECTED),
                verdict("p.Cells#[1] quote, \"one\"", Status.OK), verdict("p.Cells#[2] quote, \"two\"", Status.OK),
                verdict("p.Cells#[1] case#1", Status.OVER_SPECIFIED), verdict("p.Cells#red()", Status.OVER_SPECIFIED),
                verdict("p.Cells#unstable()", Status.FLAKY), new Verdict("p.Cells#partial()", Status.FLAKY, List.of(), true),
                verdict("p.Cells#ambiguous()", Status.OK), verdict("p.Cells#ambiguous()", Status.OK),
                verdict("p.Cells#quote, \"this\"", Status.NOT_CHECKED));

        Path output = temp.resolve("grouped.csv");
        String evidence = DecisionExport.write(output, report(verdicts, List.of(), null), inventory, false);
        assertTrue(evidence.contains("p.Cells#match() -> OK\n    p.Cells#[1] quote, \"one\"\n    p.Cells#[2] quote, \"two\""));
        assertTrue(evidence.contains("p.Cells#unseen() -> INCOMPLETE"));
        assertTrue(evidence.contains("p.Cells#allRed() -> REVIEW"));
        assertFalse(evidence.contains("p.First#same()"));
        assertEquals("""
                exercise,test,decision
                "demo, ""unit""\","p.First#same()",OK
                "demo, ""unit""\","p.Second#same()",REVIEW
                "demo, ""unit""\","p.Cells#match()",OK
                "demo, ""unit""\","p.Cells#unseen()",INCOMPLETE
                "demo, ""unit""\","p.Cells#allRed()",REVIEW
                "demo, ""unit""\","p.Cells#unstable()",REVIEW
                "demo, ""unit""\","p.Cells#partial()",INCOMPLETE
                "demo, ""unit""\","p.Cells#ambiguous()",INCOMPLETE
                "demo, ""unit""\","p.Cells#quote, ""this""\",INCOMPLETE
                """, Files.readString(output));
    }

    @Test
    void anUnexpectedFailingInvocationCannotLeaveItsFrozenGroupWithAnOk() throws Exception {
        ExpectedTests inventory = inventory("p.Cells#[1]\tp.Cells#match()\np.Cells#[2]\tp.Cells#match()\np.Other#ok()\n");
        VariantRunner runner = (exercise, variant) -> new VariantRunner.Session() {
            public RunResult run() {
                return new RunResult(variant, null, RunResult.Build.RAN, false, "17", List.of(
                        new TestResult("[1]", "p.Cells", TestOutcome.PASS, ""),
                        new TestResult("[2]", "p.Cells", TestOutcome.PASS, ""),
                        new TestResult("[3]", "p.Cells", TestOutcome.FAIL, "failed"),
                        new TestResult("ok()", "p.Other", TestOutcome.PASS, "")), null);
            }
            public RunResult rerunShuffled(long seed) { throw new AssertionError(); }
            public MutationResult mutate() { throw new AssertionError(); }
            public void close() {}
        };
        Report report = new Validator(runner, step -> {}).validate(new Exercise(temp), 0, 1, false, inventory.invocations());
        assertTrue(ConsoleReport.render(report).contains("p.Cells#[3]"));
        assertTrue(report.problems().stream().anyMatch(problem -> problem.contains("p.Cells#[3]")));
        Path output = temp.resolve("unexpected.csv");
        String evidence = DecisionExport.write(output, report, inventory, false);
        assertTrue(evidence.contains("All legacy decisions are INCOMPLETE."));
        assertTrue(evidence.contains("p.Cells#[3]"), evidence);
        assertEquals(List.of("exercise,test,decision",
                "\"" + temp.getFileName() + "\",\"p.Cells#match()\",INCOMPLETE",
                "\"" + temp.getFileName() + "\",\"p.Other#ok()\",INCOMPLETE"), Files.readAllLines(output));
    }

    @Test
    void failedWholeSuiteChecksOverrideOtherwiseDecidableRows() throws IOException {
        var observed = List.of(verdict("p.First#one()", Status.OK), verdict("p.Other#two()", Status.REJECTED));
        for (var report : List.of(report(observed, List.of("a required build failed"), MutationResult.ran(List.of())),
                report(observed, List.of(), null), report(observed, List.of(), MutationResult.skipped("red suite")),
                report(observed, List.of(), MutationResult.failed("no XML")))) {
            Path output = Files.createTempDirectory(temp, "run-").resolve("predictions.csv");
            String evidence = DecisionExport.write(output, report, new ExpectedTests(List.of()), true);
            assertTrue(evidence.contains("All legacy decisions are INCOMPLETE."));
            if (!report.problems().isEmpty()) {
                assertTrue(evidence.contains("a required build failed"));
            } else {
                assertTrue(evidence.contains("Requested PIT check"));
                if (report.mutation() != null) {
                    assertTrue(evidence.contains(report.mutation().note()));
                }
            }
            var rows = Files.readAllLines(output);
            assertEquals(3, rows.size());
            assertTrue(rows.subList(1, 3).stream().allMatch(row -> row.endsWith(",INCOMPLETE")));
        }
    }

    @Test
    void rejectsMalformedEmptyDuplicateAndConflictingInventoryRows() {
        for (String bad : List.of("", "\n", "p.A\n", "p.A#  \n", "p..A#run\n", "p.A#run \n",
                "p.A#a\t\n", "p.A#a\tgroup\n", "p.A#a\tp.A#b\textra\n", "p.A#a\np.A#a\tp.A#b\n",
                "p.A#a\np.A#x\tp.A#a\n", "p.A#a\tp.A#b\np.A#b\tp.A#c\n")) {
            assertThrows(IllegalArgumentException.class, () -> inventory(bad), bad);
        }
    }

    @Test
    void preservesXmlDisplayNamesAndReadsUtf8WithOrWithoutBom() throws IOException {
        String id = "p.Échantillon$Nested#  entrée #1";
        assertEquals(List.of(id), inventory("\uFEFF" + id + "\r\n").invocations());
        assertEquals(List.of(id), inventory(id).invocations());
    }

    @Test
    void refusesToOverwriteInputsAndConcurrentExports() throws IOException {
        Path input = Files.createDirectories(temp.resolve("exercise/tests"));
        Path inventory = Files.writeString(temp.resolve("inventory.txt"), "p.A#one()\n");
        Path exercise = input.getParent();
        assertThrows(IllegalArgumentException.class, () -> DecisionExport.destination(inventory, exercise));
        assertThrows(IllegalArgumentException.class, () -> DecisionExport.destination(input.resolve("out.csv"), exercise));
        Path alias = Files.createSymbolicLink(temp.resolve("alias"), input);
        assertThrows(IllegalArgumentException.class, () -> DecisionExport.destination(alias.resolve("out.csv"), exercise));
        Path output = DecisionExport.destination(exercise.resolve("predictions.csv"), exercise);
        Files.writeString(output, "another run finished first\n");
        assertThrows(FileAlreadyExistsException.class, () -> DecisionExport.write(output,
                report(List.of(), List.of(), null), ExpectedTests.read(inventory), false));
        assertEquals("another run finished first\n", Files.readString(output));
        try (var children = Files.list(exercise)) {
            assertFalse(children.anyMatch(p -> p.getFileName().toString().startsWith(".decisions-")));
        }
    }

    @Test
    void cliWritesIncompleteForEveryKnownGradingRowWhenExerciseCannotStart() throws IOException {
        Path input = Files.writeString(temp.resolve("inventory"), "p.A#[1]\tp.A#grade()\np.A#[2]\tp.A#grade()\np.B#run()\n");
        Path output = temp.resolve("decisions.csv");
        var console = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int exit = Main.run(new String[]{"validate", temp.resolve("missing-exercise").toString(), "--runs", "0",
                "--no-mutation", "--expected-tests", input.toString(), "--decisions", output.toString()},
                new PrintStream(console), new PrintStream(errors));
        assertEquals(2, exit);
        assertTrue(console.toString().contains("Not a folder"), console.toString());
        assertTrue(console.toString().contains("p.A#[2]"), console.toString());
        assertTrue(console.toString().contains("p.B#run()"), console.toString());
        assertTrue(console.toString().contains("p.A#grade() -> INCOMPLETE\n    p.A#[1]\n    p.A#[2]"));
        assertTrue(console.toString().contains("All legacy decisions are INCOMPLETE. Validation could not finish"));
        assertEquals("""
                exercise,test,decision
                "missing-exercise","p.A#grade()",INCOMPLETE
                "missing-exercise","p.B#run()",INCOMPLETE
                """, Files.readString(output));
    }

    private ExpectedTests inventory(String contents) throws IOException {
        return ExpectedTests.read(Files.writeString(temp.resolve("expected.txt"), contents));
    }

    private static Verdict verdict(String id, Status status) {
        return new Verdict(id, status, List.of());
    }

    private static Report report(List<Verdict> verdicts, List<String> problems, MutationResult mutation) {
        return new Report("demo, \"unit\"", "17", List.of(), 0, 1, verdicts, problems, List.of(), mutation, null);
    }
}
