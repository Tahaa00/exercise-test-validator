package dev.exercisevalidator.run;

import java.util.List;

/**
 * Result of the mutation check on one variant.
 *
 * @param state   whether PIT ran
 * @param note    for SKIPPED and FAILED, the reason
 * @param mutants all mutants PIT generated, sorted by class and line; empty unless RAN
 */
public record MutationResult(State state, String note, List<Mutant> mutants) {

    public enum State {
        RAN,
        /** The tool decided not to start PIT, e.g. because the suite is not green on the solution. */
        SKIPPED,
        /** PIT was started but gave no report: download error, time limit, PIT's own failure. */
        FAILED
    }

    public static MutationResult ran(List<Mutant> mutants) {
        return new MutationResult(State.RAN, null, List.copyOf(mutants));
    }

    public static MutationResult skipped(String reason) {
        return new MutationResult(State.SKIPPED, reason, List.of());
    }

    public static MutationResult failed(String reason) {
        return new MutationResult(State.FAILED, reason, List.of());
    }

    public long detected() {
        return mutants.stream().filter(Mutant::detected).count();
    }

    /** Detected without a failing assertion: the mutant ran into a timeout or crashed the test JVM. */
    public long detectedWithoutKill() {
        return mutants.stream().filter(m -> m.detected() && !m.killed()).count();
    }

    public List<Mutant> undetected() {
        return mutants.stream().filter(m -> !m.detected()).toList();
    }
}
