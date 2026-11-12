package dev.exercisevalidator.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.exercisevalidator.check.BuggyResult.Status;
import dev.exercisevalidator.run.BugDescription;
import dev.exercisevalidator.run.RunResult;
import dev.exercisevalidator.run.RunResult.Build;
import dev.exercisevalidator.run.TestOutcome;
import dev.exercisevalidator.run.TestResult;

class BuggyCheckTest {
    private static final TestResult PASS = test("p.First", TestOutcome.PASS);
    private static final TestResult FAIL = test("p.First", TestOutcome.FAIL);
    private static final TestResult OTHER = test("p.Second", TestOutcome.PASS);

    private static TestResult test(String type, TestOutcome outcome) {
        return new TestResult("check()", type, outcome, outcome == TestOutcome.PASS ? "" : "wrong result");
    }

    private static RunResult run(TestResult... tests) {
        return new RunResult("variant", null, Build.RAN, false, "17", List.of(tests), null);
    }

    private static RunResult killed(TestResult... tests) {
        return new RunResult("variant", null, Build.RAN, true, "17", List.of(tests), null);
    }

    private static BugDescription intended(String name) {
        return new BugDescription("", "", "", "", name);
    }

    @Test
    void intendedTestAndSuiteOutcomeAreIndependent() {
        BuggyCheck check = new BuggyCheck(List.of(run(PASS, OTHER)), List.of());
        RunResult rejected = run(FAIL, OTHER);
        BuggyResult caught = check.judge(rejected, intended("p.Second#check()"));

        assertEquals(Status.CAUGHT, caught.status());
        assertEquals(List.of("p.First#check()"), caught.catchingTests());
        assertEquals("PASS", caught.intendedOutcome());
        BuggyResult ambiguous = check.judge(rejected, intended("check()"));
        assertEquals(Status.CAUGHT, ambiguous.status());
        assertEquals("AMBIGUOUS", ambiguous.intendedOutcome());
        BuggyResult partial = check.judge(run(PASS), intended("check()"));
        assertEquals(Status.INCONCLUSIVE, partial.status());
        assertEquals("AMBIGUOUS", partial.intendedOutcome());
        BuggyResult missed = check.judge(run(PASS, OTHER), intended("typo()"));
        assertEquals(Status.MISSED, missed.status());
        assertEquals("NOT RUN", missed.intendedOutcome());
    }

    @Test
    void interruptionsAndMismatchedTestsCannotShowDetectionEvenWithAFailure() {
        BuggyCheck check = new BuggyCheck(List.of(run(PASS, OTHER)), List.of());
        for (RunResult partial : List.of(killed(FAIL, OTHER), run(FAIL),
                run(FAIL, test("p.Second", TestOutcome.SKIPPED)), run(FAIL, test("p.Second", TestOutcome.TIMEOUT)),
                run(FAIL, OTHER, OTHER), run(FAIL, OTHER, test("p.TestExecutor", TestOutcome.FAIL)),
                new RunResult("bad", null, Build.FAILED, false, null, List.of(FAIL, OTHER), "build finalizer failed"),
                new RunResult("bad", null, Build.TIMED_OUT, false, null, List.of(), "deadline"),
                new RunResult("bad", null, Build.NO_TESTS, false, null, List.of(), "no XML"))) {
            BuggyResult result = check.judge(partial, BugDescription.EMPTY);
            assertEquals(Status.INCONCLUSIVE, result.status(), partial.toString());
            assertTrue(result.catchingTests().isEmpty());
            assertEquals(partial.tests(), result.run().tests());
        }
        assertEquals(Status.MISSED, check.judge(run(PASS, OTHER), BugDescription.EMPTY).status());
    }

    @Test
    void onlyFailuresThatPassedInEveryReferenceRunCanBeAttributedToTheVariant() {
        BuggyCheck check = new BuggyCheck(List.of(run(PASS, OTHER), run(FAIL, OTHER)), List.of());
        assertEquals(Status.INCONCLUSIVE, check.judge(run(FAIL, OTHER), BugDescription.EMPTY).status());
        BuggyResult caught = check.judge(run(FAIL, test("p.Second", TestOutcome.FAIL)), BugDescription.EMPTY);
        assertEquals(Status.CAUGHT, caught.status());
        assertEquals(List.of("p.Second#check()"), caught.catchingTests());
    }

    @Test
    void completedReferenceAndAllKnownGradingTestIdsAreNeeded() {
        BuggyCheck interrupted = new BuggyCheck(List.of(killed(PASS)), List.of());
        assertEquals(Status.INCONCLUSIVE, interrupted.judge(run(PASS), BugDescription.EMPTY).status());
        BuggyCheck recovered = new BuggyCheck(List.of(killed(PASS), run(PASS, OTHER)), List.of());
        assertEquals(Status.MISSED, recovered.judge(run(PASS, OTHER), BugDescription.EMPTY).status());
        BuggyCheck discoveredOnAlternative = new BuggyCheck(List.of(run(PASS)), List.of(run(PASS, OTHER)));
        assertEquals(Status.INCONCLUSIVE, discoveredOnAlternative.judge(run(PASS), BugDescription.EMPTY).status());
        BuggyCheck duplicate = new BuggyCheck(List.of(run(PASS, PASS)), List.of());
        assertEquals(Status.INCONCLUSIVE, duplicate.judge(run(PASS), BugDescription.EMPTY).status());
    }
}
