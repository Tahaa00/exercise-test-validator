package dev.exercisevalidator.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GradleRunnerTest {

    @Test
    void extractsGradlesExplanationOfAFailedBuild() {
        String output = """
                > Task :compileJava FAILED

                FAILURE: Build failed with an exception.

                * What went wrong:
                Execution failed for task ':compileJava'.
                > Compilation failed; see the compiler output below.

                * Try:
                > Run with --info option to get more log output.
                """;

        assertEquals("Execution failed for task ':compileJava'.\n> Compilation failed; see the compiler output below.",
                GradleRunner.whatWentWrong(output));
    }

    @Test
    void fallsBackToTheEndOfTheLog() {
        assertEquals("last line", GradleRunner.whatWentWrong("first line\n\nlast line\n").lines().reduce((a, b) -> b).orElseThrow());
    }

    @Test
    void explainsTheJdkTooNewForGradleError() {
        String hint = GradleRunner.jdkHint("> Unsupported class file major version 69");

        assertEquals(true, hint.contains("cannot run on JDK 25") && hint.contains("--java-home"), hint);
        assertEquals("", GradleRunner.jdkHint("Compilation failed"));
    }

    /** Lines as PIT 1.30.2 logs them when the Ares 1 security manager stops its coverage probes. */
    private static final String PIT_RED_SUITE = """
            9:41:30 PM PIT >> INFO : MINION : [WARN] [ajts-to-1] BAD PACKAGE ACCESS: sun.pitest (BL:false, WL:false)
            9:41:31 PM PIT >> SEVERE : Description [testClass=p.MaxTest, name=[engine:junit-jupiter]/[class:p.MaxTest]/[method:maxOfOne()]] did not pass without mutation.
            9:41:31 PM PIT >> SEVERE : Description [testClass=p.MaxTest, name=[engine:junit-jupiter]/[class:p.MaxTest]/[method:maxOfTwo()]] did not pass without mutation.
            Exception in thread "main" org.pitest.help.PitHelpError: 2 tests did not pass without mutation when calculating line coverage. Mutation testing requires a green suite.

            * What went wrong:
            Execution failed for task ':pitest'.
            > Process 'command '/usr/lib/jvm/java-17/bin/java'' finished with non-zero exit value 1
            """;

    @Test
    void namesTheTestsThatFailOnlyInsidePitAndTheAresCause() {
        String problem = GradleRunner.pitProblem(PIT_RED_SUITE);

        assertTrue(problem.startsWith("2 tests that pass in the normal run failed when PIT ran the suite: maxOfOne(), maxOfTwo()."), problem);
        assertTrue(problem.contains("the Ares security manager blocked PIT's probes"), problem);
        assertFalse(problem.contains("non-zero exit value"), problem);
    }

    @Test
    void prefersPitsOwnErrorOverGradlesExitCodeLine() {
        String output = """
                Exception in thread "main" org.pitest.help.PitHelpError: No mutations found. This probably means there is an issue with either the supplied classpath or filters.

                * What went wrong:
                Execution failed for task ':pitest'.
                > Process 'command 'java'' finished with non-zero exit value 1
                """;

        assertTrue(GradleRunner.pitProblem(output).startsWith("PIT stopped: No mutations found."), GradleRunner.pitProblem(output));
        assertEquals("Could not resolve org.pitest:pitest", GradleRunner.pitProblem("* What went wrong:\nCould not resolve org.pitest:pitest\n* Try:"));
    }
}
