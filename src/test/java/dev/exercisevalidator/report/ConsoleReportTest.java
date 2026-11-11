package dev.exercisevalidator.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.exercisevalidator.check.Report;
import dev.exercisevalidator.check.Verdict;
import dev.exercisevalidator.check.Verdict.Status;
import dev.exercisevalidator.run.Mutant;
import dev.exercisevalidator.run.MutationResult;

class ConsoleReportTest {

    @Test
    void rendersOneLinePerTestWithEvidenceBelow() {
        Report report = new Report("array-max", "25", List.of("alternatives/b"), 10, 7,
                List.of(new Verdict("good()", Status.OK, List.of()),
                        new Verdict("strict()", Status.OVER_SPECIFIED, List.of("FAIL (wrong message) on alternatives/b"))),
                List.of(), List.of(), null, null);

        assertEquals("""
                array-max  (Java 25, 2 tests, 1 alternatives, 10 random-order runs, base seed 7)

                  OK               good()
                  OVER-SPECIFIED?  strict()
                                     FAIL (wrong message) on alternatives/b

                Solution: 2/2 pass   Flaky: 0   Fail on an alternative: 1
                Result: 1 finding
                """, ConsoleReport.render(report));
    }

    @Test
    void fatalReportShowsTheBuildProblem() {
        Report report = new Report("array-max", null, List.of(), 0, 7, List.of(), List.of(), List.of(), null, "Could not resolve junit");

        String text = ConsoleReport.render(report);

        assertTrue(text.contains("BUILD FAILED  Could not resolve junit"), text);
        assertEquals(2, report.exitCode());
    }

    private static Report withMutation(MutationResult mutation) {
        return new Report("array-max", "17", List.of(), 0, 7, List.of(new Verdict("good()", Status.OK, List.of())),
                List.of(), List.of(), mutation, null);
    }

    @Test
    void mutationSectionListsUndetectedMutantsWithTheirSourceLine() {
        Report report = withMutation(MutationResult.ran(List.of(
                new Mutant("ArrayMax.java", "p.ArrayMax", "max", 9, "removed conditional", "SURVIVED", false, "if (values == null) {"),
                new Mutant("ArrayMax.java", "p.ArrayMax", "max", 12, "negated conditional", "KILLED", true, "x"),
                new Mutant("ArrayMax.java", "p.ArrayMax", "max", 13, "changed conditional boundary", "RUN_ERROR", true, "x"),
                new Mutant("ArrayMax.java", "p.ArrayMax", "unused", 30, "replaced int return with 0", "NO_COVERAGE", false, ""))));

        assertEquals("""
                array-max  (Java 17, 1 tests, 0 alternatives, 0 random-order runs, base seed 7)

                  OK               good()

                Mutation check (PIT on the solution, STRONGER mutators)

                  4 mutants: 2 detected (1 of them by timeout or crash), 1 survived, 1 not covered

                  SURVIVED     ArrayMax.java:9   max()     removed conditional
                                 if (values == null) {
                  NOT COVERED  ArrayMax.java:30  unused()  replaced int return with 0

                  These are hints, not errors. SURVIVED: tests ran the changed line and still passed, so a test
                  may be missing or too weak, or the mutant is equivalent (same behaviour as the original).
                  NOT COVERED: no test runs that line.

                Solution: 1/1 pass   Flaky: 0   Fail on an alternative: 0   Mutants not detected: 2
                Result: no findings
                """, ConsoleReport.render(report));
    }

    @Test
    void mutationSectionSaysWhyPitDidNotRun() {
        String skipped = ConsoleReport.render(withMutation(MutationResult.skipped("1 test does not pass on the solution.")));
        String failed = ConsoleReport.render(withMutation(MutationResult.failed("Could not resolve org.pitest:pitest")));

        assertTrue(skipped.contains("  Skipped: 1 test does not pass on the solution.\n"), skipped);
        assertTrue(failed.contains("  Could not run: Could not resolve org.pitest:pitest\n"), failed);
        assertTrue(failed.contains("Result: no findings"), "a PIT failure does not turn a good suite red");
    }
}
