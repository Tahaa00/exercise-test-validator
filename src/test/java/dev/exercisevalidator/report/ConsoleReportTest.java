package dev.exercisevalidator.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.exercisevalidator.check.Report;
import dev.exercisevalidator.check.Verdict;
import dev.exercisevalidator.check.Verdict.Status;

class ConsoleReportTest {

    @Test
    void rendersOneLinePerTestWithEvidenceBelow() {
        Report report = new Report("array-max", "25", List.of("alternatives/b"), 10, 7,
                List.of(new Verdict("good()", Status.OK, List.of()),
                        new Verdict("strict()", Status.OVER_SPECIFIED, List.of("FAIL (wrong message) on alternatives/b"))),
                List.of(), List.of(), null);

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
        Report report = new Report("array-max", null, List.of(), 0, 7, List.of(), List.of(), List.of(), "Could not resolve junit");

        String text = ConsoleReport.render(report);

        assertTrue(text.contains("BUILD FAILED  Could not resolve junit"), text);
        assertEquals(2, report.exitCode());
    }
}
