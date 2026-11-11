package dev.exercisevalidator.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.exercisevalidator.check.Verdict.Status;
import dev.exercisevalidator.run.Exercise;
import dev.exercisevalidator.run.Mutant;
import dev.exercisevalidator.run.MutationResult;
import dev.exercisevalidator.run.RunResult;
import dev.exercisevalidator.run.RunResult.Build;
import dev.exercisevalidator.run.TestOutcome;
import dev.exercisevalidator.run.TestResult;
import dev.exercisevalidator.run.VariantRunner;

/** Tests the verdict logic with scripted run results, so no Gradle is started. */
class ValidatorTest {

    private static TestResult test(String name, TestOutcome outcome) {
        return new TestResult(name, "com.example.SomeTest", outcome, outcome == TestOutcome.PASS ? "" : "boom");
    }

    private static RunResult run(String variant, Long seed, boolean jvmKilled, TestResult... tests) {
        return new RunResult(variant, seed, Build.RAN, jvmKilled, "17", List.of(tests), null);
    }

    private static RunResult solution(TestResult... tests) {
        return run("solution", null, false, tests);
    }

    private static Verdict judge(RunResult solution, List<RunResult> shuffled, List<RunResult> alternatives) {
        return Validator.judge("a()", solution, shuffled, alternatives);
    }

    @Test
    void passingEverywhereIsOk() {
        Verdict verdict = judge(solution(test("a()", TestOutcome.PASS)),
                List.of(run("solution", 1L, false, test("a()", TestOutcome.PASS))),
                List.of(run("alternatives/x", null, false, test("a()", TestOutcome.PASS))));

        assertEquals(Status.OK, verdict.status());
        assertTrue(verdict.evidence().isEmpty());
    }

    @Test
    void failingOnTheSolutionIsRejected() {
        Verdict verdict = judge(solution(test("a()", TestOutcome.FAIL)), List.of(), List.of());

        assertEquals(Status.REJECTED, verdict.status());
        assertEquals("FAIL (boom) on the solution", verdict.evidence().get(0));
    }

    @Test
    void timeoutOnTheSolutionIsReportedAsTimeout() {
        Verdict verdict = judge(solution(test("a()", TestOutcome.TIMEOUT)), List.of(), List.of());

        assertEquals(Status.REJECTED, verdict.status());
        assertTrue(verdict.evidence().get(0).startsWith("TIMEOUT"));
    }

    /** A test that is missing from the solution's XML must never count as passed. */
    @Test
    void missingOnTheSolutionIsRejectedAsNotRun() {
        RunResult killed = run("solution", null, true, test("other()", TestOutcome.TIMEOUT));
        Verdict verdict = judge(killed, List.of(), List.of(run("alternatives/x", null, false, test("a()", TestOutcome.PASS))));

        assertEquals(Status.REJECTED, verdict.status());
        assertTrue(verdict.evidence().get(0).startsWith("NOT RUN"), verdict.evidence().toString());
    }

    @Test
    void differingResultInARandomOrderRunIsFlakyWithItsSeed() {
        Verdict verdict = judge(solution(test("a()", TestOutcome.PASS)),
                List.of(run("solution", 11L, false, test("a()", TestOutcome.PASS)),
                        run("solution", 22L, false, test("a()", TestOutcome.FAIL))),
                List.of());

        assertEquals(Status.FLAKY, verdict.status());
        assertEquals(List.of("passed in 1 of 2 random-order runs", "seed 22: FAIL (boom)"), verdict.evidence());
    }

    @Test
    void failingOnAnAlternativeIsOverSpecifiedAndNamesTheAlternative() {
        Verdict verdict = judge(solution(test("a()", TestOutcome.PASS)), List.of(),
                List.of(run("alternatives/x", null, false, test("a()", TestOutcome.PASS)),
                        run("alternatives/y", null, false, test("a()", TestOutcome.FAIL))));

        assertEquals(Status.OVER_SPECIFIED, verdict.status());
        assertEquals(List.of("FAIL (boom) on alternatives/y"), verdict.evidence());
    }

    @Test
    void flakyOutranksOverSpecified() {
        Verdict verdict = judge(solution(test("a()", TestOutcome.PASS)),
                List.of(run("solution", 1L, false, test("a()", TestOutcome.FAIL))),
                List.of(run("alternatives/y", null, false, test("a()", TestOutcome.FAIL))));

        assertEquals(Status.FLAKY, verdict.status());
        assertEquals(3, verdict.evidence().size());
    }

    /** Tests after a killed JVM are absent from that run: no verdict from it, and no silent pass either. */
    @Test
    void notReachedOnAnAlternativeIsNotChecked() {
        Verdict verdict = judge(solution(test("a()", TestOutcome.PASS)), List.of(),
                List.of(run("alternatives/y", null, true, test("other()", TestOutcome.TIMEOUT))));

        assertEquals(Status.NOT_CHECKED, verdict.status());
    }

    @Test
    void warnsAboutNamesArtemisMayNotBind() {
        List<String> warnings = Validator.nameWarnings(solution(
                test("plain()", TestOutcome.PASS),
                test("testMethods[ArrayMax]", TestOutcome.PASS),
                test("Returns the maximum", TestOutcome.PASS),
                new TestResult("plain()", "com.example.OtherTest", TestOutcome.PASS, "")));

        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("\"plain()\" exists in 2 classes"), warnings.toString());
        assertTrue(warnings.get(1).contains("\"Returns the maximum\" is not of the form"), warnings.toString());
    }

    // --- whole flow with a scripted runner ---

    /** Hands out prepared results per variant: first the declared-order run, then the random-order runs. */
    private static final class ScriptedRunner implements VariantRunner {
        final Map<String, List<RunResult>> script = new HashMap<>();
        final List<Long> seeds = new ArrayList<>();
        MutationResult mutation = MutationResult.ran(List.of());
        int mutationRuns;

        @Override
        public Session open(Exercise exercise, String variant) {
            List<RunResult> results = new ArrayList<>(script.get(variant));
            return new Session() {
                @Override
                public RunResult run() {
                    return results.remove(0);
                }

                @Override
                public RunResult rerunShuffled(long seed) {
                    seeds.add(seed);
                    RunResult next = results.remove(0);
                    return new RunResult(next.variant(), seed, next.build(), next.jvmKilled(), next.javaVersion(), next.tests(), next.problem());
                }

                @Override
                public MutationResult mutate() {
                    mutationRuns++;
                    return mutation;
                }

                @Override
                public void close() {
                }
            };
        }
    }

    /** Exercise with no alternatives folder on disk; the scripted runner never touches the file system. */
    private static final Exercise EXERCISE = new Exercise(Path.of("build", "no-such-exercise").toAbsolutePath());

    @Test
    void solutionBuildFailureStopsEverythingWithExitCode2() throws Exception {
        ScriptedRunner runner = new ScriptedRunner();
        runner.script.put("solution", List.of(new RunResult("solution", null, Build.FAILED, false, null, List.of(), "Could not GET ... 429")));

        Report report = new Validator(runner, step -> { }).validate(EXERCISE, 10, 1, false);

        assertNotNull(report.fatal());
        assertTrue(report.fatal().contains("429"));
        assertEquals(2, report.exitCode());
        assertTrue(runner.seeds.isEmpty(), "no flakiness runs after a failed build");
    }

    @Test
    void cleanSuiteHasExitCode0AndReproducibleDistinctSeeds() throws Exception {
        ScriptedRunner runner = new ScriptedRunner();
        RunResult green = solution(test("a()", TestOutcome.PASS));
        runner.script.put("solution", List.of(green, green, green, green));

        Report report = new Validator(runner, step -> { }).validate(EXERCISE, 3, 42, false);
        List<Long> first = List.copyOf(runner.seeds);
        runner.seeds.clear();
        new Validator(runner, step -> { }).validate(EXERCISE, 3, 42, false);

        assertEquals(0, report.exitCode());
        assertEquals(3, report.shuffledRuns());
        assertEquals(3, first.stream().distinct().count());
        assertEquals(first, runner.seeds, "same base seed, same runs");
    }

    @Test
    void flakinessRunWithoutResultsIsAProblemNotAPass() throws Exception {
        ScriptedRunner runner = new ScriptedRunner();
        RunResult green = solution(test("a()", TestOutcome.PASS));
        runner.script.put("solution", List.of(green, green,
                new RunResult("solution", null, Build.TIMED_OUT, false, null, List.of(), "did not finish")));

        Report report = new Validator(runner, step -> { }).validate(EXERCISE, 5, 1, false);

        assertEquals(1, report.shuffledRuns());
        assertEquals(1, report.problems().size());
        assertEquals(1, report.exitCode());
    }

    @Test
    void survivingMutantsAreReportedButAreNotFindings() throws Exception {
        ScriptedRunner runner = new ScriptedRunner();
        RunResult green = solution(test("a()", TestOutcome.PASS));
        runner.script.put("solution", List.of(green, green));
        runner.mutation = MutationResult.ran(List.of(
                new Mutant("A.java", "p.A", "m", "()V", 3, "negated conditional", "KILLED", true, ""),
                new Mutant("A.java", "p.A", "m", "()V", 4, "changed conditional boundary", "SURVIVED", false, "if (a > b) {")));

        Report report = new Validator(runner, step -> { }).validate(EXERCISE, 1, 1, true);

        assertEquals(1, runner.mutationRuns);
        assertEquals(1, report.mutation().undetected().size());
        assertEquals(0, report.findings(), "a survivor may be an equivalent mutant, so it is a hint only");
        assertEquals(0, report.exitCode());
    }

    @Test
    void mutationCheckIsSkippedWhenTheSolutionRunIsNotGreen() throws Exception {
        ScriptedRunner runner = new ScriptedRunner();
        runner.script.put("solution", List.of(solution(test("a()", TestOutcome.PASS), test("b()", TestOutcome.FAIL))));

        Report report = new Validator(runner, step -> { }).validate(EXERCISE, 0, 1, true);

        assertEquals(0, runner.mutationRuns, "PIT cannot start from a failing suite");
        assertEquals(MutationResult.State.SKIPPED, report.mutation().state());
        assertTrue(report.mutation().note().startsWith("1 test does not pass on the solution"), report.mutation().note());
    }

    @Test
    void mutationCheckCanBeSwitchedOff() throws Exception {
        ScriptedRunner runner = new ScriptedRunner();
        runner.script.put("solution", List.of(solution(test("a()", TestOutcome.PASS))));

        Report report = new Validator(runner, step -> { }).validate(EXERCISE, 0, 1, false);

        assertEquals(0, runner.mutationRuns);
        assertNull(report.mutation());
    }
}
