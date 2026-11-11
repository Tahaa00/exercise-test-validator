package dev.exercisevalidator.run;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
