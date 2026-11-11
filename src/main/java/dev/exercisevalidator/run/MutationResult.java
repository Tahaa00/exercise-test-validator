package dev.exercisevalidator.run;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Result of the mutation check on one variant.
 *
 * @param state   whether PIT ran
 * @param note    for SKIPPED and FAILED, the reason. For RAN, a remark on how PIT had to be run, or null.
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
        return ran(mutants, null);
    }

    public static MutationResult ran(List<Mutant> mutants, String remark) {
        return new MutationResult(State.RAN, remark, List.copyOf(mutants));
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

    /** Undetected mutants a tutor should look at; the ones in {@link #leftOut()} are not among them. */
    public List<Mutant> undetected() {
        List<Mutant> leftOut = leftOut();
        return mutants.stream().filter(m -> !m.detected() && !leftOut.contains(m)).toList();
    }

    /** The mutants the counts are about: all of them except {@link #leftOut()}. */
    public List<Mutant> counted() {
        List<Mutant> leftOut = leftOut();
        return mutants.stream().filter(m -> !leftOut.contains(m)).toList();
    }

    /**
     * Mutants in a {@code main(String[])} that no test runs at all. Exercises often ship a demo {@code main} that
     * is not graded; its mutants would bury the real gaps. A {@code main} with even one covered mutant is tested
     * on purpose and stays in the counts.
     */
    public List<Mutant> leftOut() {
        Set<String> testedMains = mutants.stream().filter(m -> m.inEntryPoint() && !m.notCovered())
                .map(Mutant::className).collect(Collectors.toSet());
        return mutants.stream().filter(m -> m.inEntryPoint() && !testedMains.contains(m.className())).toList();
    }
}
