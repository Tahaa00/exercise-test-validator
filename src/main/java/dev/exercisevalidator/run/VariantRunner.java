package dev.exercisevalidator.run;

import java.io.IOException;

/**
 * Runs the exercise's suite against one variant. The checks only see this interface,
 * so they can be tested without starting Gradle.
 */
public interface VariantRunner {

    /** A variant (solution or one alternative) set up for running; closing it removes the work folder. */
    interface Session extends AutoCloseable {

        /** Full run in the declared order: {@code clean test}, exactly what Artemis CI does. */
        RunResult run() throws IOException, InterruptedException;

        /** Re-runs only the tests, in random method and class order with the given seed. Needs a previous {@link #run()}. */
        RunResult rerunShuffled(long seed) throws IOException, InterruptedException;

        /**
         * Runs PIT on this variant with the suite as the judge. Needs a previous {@link #run()} in which every
         * test passed: PIT refuses to start when the suite fails without any mutation.
         */
        MutationResult mutate() throws IOException, InterruptedException;

        @Override
        void close() throws IOException;
    }

    Session open(Exercise exercise, String variant) throws IOException;
}
