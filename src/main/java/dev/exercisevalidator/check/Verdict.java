package dev.exercisevalidator.check;

import java.util.List;

/**
 * The tool's finding for one test, keyed by the XML test name so it maps onto Artemis tasks and points.
 *
 * @param evidence what was observed, one line each: which variant or seed, and the failure message
 */
public record Verdict(String test, Status status, List<String> evidence) {

    public enum Status {
        /** Passes on the solution, in every random-order run and on every alternative. */
        OK("OK"),
        /** Does not pass on the reference solution: the test (or the solution) is wrong. */
        REJECTED("REJECTED"),
        /** Passes on the solution in the declared order, but not in every random-order run. */
        FLAKY("FLAKY"),
        /**
         * Fails on an alternative solution. The question mark is deliberate: the tool cannot prove that the
         * alternative is correct, so this is either an over-specified test or a wrong alternative. A human decides.
         */
        OVER_SPECIFIED("OVER-SPECIFIED?"),
        /** No failure seen, but some run never reached this test because the test JVM was killed earlier. */
        NOT_CHECKED("NOT CHECKED");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
