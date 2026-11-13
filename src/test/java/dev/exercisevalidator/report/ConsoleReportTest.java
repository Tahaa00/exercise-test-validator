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
                List.of(), List.of(), null, List.of(), null, 2);

        assertEquals("""
                array-max  (Java 25, 2 tests, 1 alternatives, 10 random-order runs, base seed 7)

                  PASSED CHECKS    good()
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
                List.of(), List.of(), mutation, List.of(), null, 1);
    }

    @Test
    void mutationSectionListsUndetectedMutantsWithTheirSourceLine() {
        Report report = withMutation(MutationResult.ran(List.of(
                new Mutant("ArrayMax.java", "p.ArrayMax", "max", "([I)I", 9, "removed conditional", "SURVIVED", false, "if (values == null) {"),
                new Mutant("ArrayMax.java", "p.ArrayMax", "max", "([I)I", 12, "negated conditional", "KILLED", true, "x"),
                new Mutant("ArrayMax.java", "p.ArrayMax", "max", "([I)I", 14, "negated condition", "TIMED_OUT", true, "x"),
                new Mutant("ArrayMax.java", "p.ArrayMax", "max", "([I)I", 13, "changed conditional boundary", "RUN_ERROR", true, "x"),
                new Mutant("ArrayMax.java", "p.ArrayMax", "unused", "()I", 30, "replaced int return with 0", "NO_COVERAGE", false, ""))));

        assertEquals("""
                array-max  (Java 17, 1 tests, 0 alternatives, 0 random-order runs, base seed 7)

                  PASSED CHECKS    good()

                Mutation check (PIT on the solution, STRONGER mutators)

                  5 mutants: 1 killed, 1 timed out, 1 run error, 1 survived, 1 not covered

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

    /** Seen on a real exercise: 24 of 26 hints came from a demo main and one untested method, burying the two real ones. */
    @Test
    void untestedMethodIsOneLineAndAnUntestedMainIsLeftOut() {
        String mainDescriptor = "([Ljava/lang/String;)V";
        Report report = withMutation(MutationResult.ran(List.of(
                new Mutant("Search.java", "p.Search", "linear", "([I)I", 5, "negated conditional", "KILLED", true, "x"),
                new Mutant("Search.java", "p.Search", "linear", "([I)I", 6, "changed conditional boundary", "SURVIVED", false, "if (a > b) {"),
                new Mutant("Search.java", "p.Search", "binary", "([I)I", 20, "negated conditional", "NO_COVERAGE", false, "x"),
                new Mutant("Search.java", "p.Search", "binary", "([I)I", 24, "replaced int return with 0", "NO_COVERAGE", false, "x"),
                new Mutant("Search.java", "p.Search", "binary", "([I)I", 22, "changed conditional boundary", "NO_COVERAGE", false, "x"),
                new Mutant("Main.java", "p.Main", "main", mainDescriptor, 8, "removed call to println", "NO_COVERAGE", false, "x"),
                new Mutant("Main.java", "p.Main", "main", mainDescriptor, 9, "removed call to println", "NO_COVERAGE", false, "x")),
                "Ares was told to trust PIT."));

        String text = ConsoleReport.render(report);

        assertTrue(text.contains("""
                  5 mutants: 1 killed, 0 timed out, 0 run errors, 1 survived, 3 not covered

                  SURVIVED     Search.java:6      linear()  changed conditional boundary
                                 if (a > b) {
                  NOT COVERED  Search.java:20-24  binary()  3 mutants, no test runs any of these lines

                """), text);
        assertTrue(text.contains("  Left out: 2 mutants in Main.main(String[]). No test runs it, as usual for a demo entry point.\n"), text);
        assertTrue(text.contains("  Note: Ares was told to trust PIT.\n"), text);
        assertTrue(text.contains("Mutants not detected: 4\n"), text);
    }

    @Test
    void mainThatTestsRunIsReportedLikeAnyOtherMethod() {
        String mainDescriptor = "([Ljava/lang/String;)V";
        MutationResult mutation = MutationResult.ran(List.of(
                new Mutant("Main.java", "p.Main", "main", mainDescriptor, 8, "removed call to println", "SURVIVED", false, "x"),
                new Mutant("Main.java", "p.Main", "main", mainDescriptor, 9, "removed call to println", "NO_COVERAGE", false, "x"),
                // Not an entry point: other parameters.
                new Mutant("Tool.java", "p.Tool", "main", "(I)V", 3, "removed call to println", "NO_COVERAGE", false, "x")));

        assertTrue(mutation.leftOut().isEmpty());
        assertEquals(3, mutation.undetected().size());
        assertTrue(!ConsoleReport.render(withMutation(mutation)).contains("Left out"));
    }

    @Test
    void mutationSectionSaysWhyPitDidNotRun() {
        String skipped = ConsoleReport.render(withMutation(MutationResult.skipped("1 test does not pass on the solution.")));
        String failed = ConsoleReport.render(withMutation(MutationResult.failed("Could not resolve org.pitest:pitest")));

        assertTrue(skipped.contains("  Skipped: 1 test does not pass on the solution.\n"), skipped);
        assertTrue(failed.contains("  Could not run: Could not resolve org.pitest:pitest\n"), failed);
        assertTrue(failed.contains("Result: incomplete PIT run"), failed);
        assertEquals(3, withMutation(MutationResult.failed("no report")).exitCode());
    }
}
