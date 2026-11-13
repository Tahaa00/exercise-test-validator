package dev.exercisevalidator.check;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.exercisevalidator.check.BuggyResult.Status;
import dev.exercisevalidator.run.BugDescription;
import dev.exercisevalidator.run.RunResult;
import dev.exercisevalidator.run.TestOutcome;
import dev.exercisevalidator.run.TestResult;

/** Compares qualified XML test identities within the buggy check, independently of per-test verdicts. */
final class BuggyCheck {
    private final Set<String> expected = new LinkedHashSet<>();
    private final Set<String> ambiguous = new LinkedHashSet<>();
    private final Set<String> referencePasses = new HashSet<>();
    private final boolean hasCompletedReference;

    BuggyCheck(List<RunResult> referenceRuns, List<RunResult> alternatives) {
        this(referenceRuns, alternatives, Set.of());
    }

    BuggyCheck(List<RunResult> referenceRuns, List<RunResult> alternatives, Set<String> known) {
        expected.addAll(known);
        for (RunResult reference : referenceRuns) {
            collectTests(reference);
        }
        alternatives.forEach(this::collectTests);

        hasCompletedReference = referenceRuns.stream().anyMatch(r -> r.ran() && !r.jvmKilled() && !expected.isEmpty()
                && expected.stream().allMatch(id -> r.find(id).isPresent()));
        referencePasses.addAll(expected);
        for (RunResult reference : referenceRuns) {
            Set<String> passed = new HashSet<>();
            if (reference.ran()) {
                reference.tests().stream().filter(t -> t.outcome() == TestOutcome.PASS)
                        .forEach(t -> passed.add(t.qualifiedName()));
            }
            referencePasses.retainAll(passed);
        }
    }

    private void collectTests(RunResult run) {
        Set<String> seen = new HashSet<>();
        for (TestResult test : run.tests()) {
            String id = test.qualifiedName();
            expected.add(id);
            if (!seen.add(id) || test.className().isBlank() || test.name().isBlank()) {
                ambiguous.add(id);
            }
        }
    }

    BuggyResult judge(RunResult run, BugDescription description) {
        String intendedOutcome = intendedOutcome(run, description.intendedTest());
        List<String> catches = new ArrayList<>();
        boolean anyFailure = false;
        for (TestResult test : run.tests()) {
            if (test.outcome() == TestOutcome.FAIL) {
                anyFailure = true;
                if (referencePasses.contains(test.qualifiedName())) {
                    catches.add(test.qualifiedName());
                }
            }
        }
        String problem = incompleteReason(run);
        if (problem == null && anyFailure && catches.isEmpty()) {
            problem = "None of the failing tests passed in every reference run.";
        }
        if (problem != null) {
            return new BuggyResult(run, description, Status.INCONCLUSIVE, intendedOutcome, List.of(), problem);
        }
        return new BuggyResult(run, description, anyFailure ? Status.CAUGHT : Status.MISSED,
                intendedOutcome, List.copyOf(catches), null);
    }

    private String intendedOutcome(RunResult run, String intended) {
        if (intended.isEmpty()) {
            return "";
        }
        // A missing result must not make a display name shared by two classes appear unique.
        Set<String> candidates = new HashSet<>();
        for (String id : expected) {
            if (id.equals(intended) || id.substring(id.indexOf('#') + 1).equals(intended)) {
                candidates.add(id);
            }
        }
        List<TestResult> observed = run.tests().stream()
                .filter(t -> t.qualifiedName().equals(intended) || t.name().equals(intended)).toList();
        observed.forEach(t -> candidates.add(t.qualifiedName()));
        if (candidates.size() > 1 || observed.size() > 1 || candidates.stream().anyMatch(ambiguous::contains)) {
            return "AMBIGUOUS";
        }
        return observed.isEmpty() ? "NOT RUN" : observed.get(0).outcome().name();
    }

    private String incompleteReason(RunResult run) {
        if (!run.ran()) {
            return run.build() + ": " + run.problem();
        }
        if (run.jvmKilled()) {
            return "The test JVM was killed.";
        }
        if (!ambiguous.isEmpty()) {
            return "Incomplete or repeated test identity: " + String.join(", ", ambiguous);
        }
        Set<String> missing = new LinkedHashSet<>(expected);
        for (TestResult test : run.tests()) {
            String id = test.qualifiedName();
            if (!expected.contains(id)) {
                return "Unexpected test result: " + id;
            }
            if (!missing.remove(id)) {
                return "Repeated test identity: " + id;
            }
            if (test.outcome() != TestOutcome.PASS && test.outcome() != TestOutcome.FAIL) {
                return test.qualifiedName() + " was " + test.outcome() + ".";
            }
        }
        if (!missing.isEmpty()) {
            return "Missing test results: " + String.join(", ", missing);
        }
        if (!hasCompletedReference) {
            return "No completed reference run contained every expected test identity.";
        }
        return null;
    }
}
