package dev.exercisevalidator.report;

import java.util.List;

import dev.exercisevalidator.check.Report;
import dev.exercisevalidator.check.Verdict;
import dev.exercisevalidator.check.Verdict.Status;
import dev.exercisevalidator.run.Mutant;
import dev.exercisevalidator.run.MutationResult;

/** Renders a {@link Report} as plain text: one line per test, evidence indented below it. */
public final class ConsoleReport {

    private static final int LABEL_WIDTH = 17; // "OVER-SPECIFIED?" plus two spaces

    private ConsoleReport() {
    }

    public static String render(Report report) {
        StringBuilder out = new StringBuilder();
        if (report.fatal() != null) {
            out.append(report.exercise()).append('\n').append('\n');
            out.append("  BUILD FAILED  ").append(report.fatal().replace("\n", "\n    ")).append('\n');
            out.append('\n').append("Result: no checks were run").append('\n');
            return out.toString();
        }

        out.append(report.exercise())
                .append("  (Java ").append(report.javaVersion() == null ? "?" : report.javaVersion())
                .append(", ").append(report.verdicts().size()).append(" tests")
                .append(", ").append(report.alternatives().size()).append(" alternatives")
                .append(", ").append(report.shuffledRuns()).append(" random-order runs, base seed ").append(report.baseSeed())
                .append(")\n\n");

        for (Verdict verdict : report.verdicts()) {
            out.append("  ").append(pad(verdict.status().label())).append(verdict.test()).append('\n');
            for (String line : verdict.evidence()) {
                out.append("  ").append(pad("")).append("  ").append(line).append('\n');
            }
        }
        out.append('\n');
        report.problems().forEach(p -> out.append("  PROBLEM  ").append(p).append('\n'));
        report.warnings().forEach(w -> out.append("  WARNING  ").append(w).append('\n'));
        if (!report.problems().isEmpty() || !report.warnings().isEmpty()) {
            out.append('\n');
        }

        MutationResult mutation = report.mutation();
        if (mutation != null) {
            renderMutation(mutation, out);
        }

        long total = report.verdicts().size();
        out.append("Solution: ").append(total - report.count(Status.REJECTED)).append('/').append(total).append(" pass")
                .append("   Flaky: ").append(report.count(Status.FLAKY))
                .append("   Fail on an alternative: ").append(report.count(Status.OVER_SPECIFIED));
        if (report.count(Status.NOT_CHECKED) > 0) {
            out.append("   Not checked: ").append(report.count(Status.NOT_CHECKED));
        }
        if (mutation != null && mutation.state() == MutationResult.State.RAN) {
            out.append("   Mutants not detected: ").append(mutation.undetected().size());
        }
        out.append('\n');
        long findings = report.findings();
        out.append("Result: ").append(findings == 0 ? "no findings" : findings + (findings == 1 ? " finding" : " findings")).append('\n');
        return out.toString();
    }

    private static void renderMutation(MutationResult mutation, StringBuilder out) {
        out.append("Mutation check (PIT on the solution, STRONGER mutators)\n\n");
        if (mutation.state() != MutationResult.State.RAN) {
            out.append("  ").append(mutation.state() == MutationResult.State.SKIPPED ? "Skipped: " : "Could not run: ")
                    .append(mutation.note().replace("\n", "\n    ")).append("\n\n");
            return;
        }
        List<Mutant> mutants = mutation.mutants();
        List<Mutant> undetected = mutation.undetected();
        long other = undetected.stream().filter(m -> !m.survived() && !m.notCovered()).count();
        // Counts only, no percentage: an equivalent mutant can never be killed, so "82%" would look like a defect.
        out.append("  ").append(mutants.size()).append(mutants.size() == 1 ? " mutant: " : " mutants: ")
                .append(mutation.detected()).append(" detected");
        if (mutation.detectedWithoutKill() > 0) {
            out.append(" (").append(mutation.detectedWithoutKill()).append(" of them by timeout or crash)");
        }
        out.append(", ").append(undetected.stream().filter(Mutant::survived).count()).append(" survived")
                .append(", ").append(undetected.stream().filter(Mutant::notCovered).count()).append(" not covered");
        if (other > 0) {
            out.append(", ").append(other).append(" other");
        }
        out.append("\n\n");
        if (undetected.isEmpty()) {
            return;
        }
        int placeWidth = undetected.stream().mapToInt(m -> place(m).length()).max().orElse(0);
        int methodWidth = undetected.stream().mapToInt(m -> m.method().length()).max().orElse(0) + 2;
        for (Mutant mutant : undetected) {
            out.append("  ").append(String.format("%-13s", label(mutant)))
                    .append(String.format("%-" + placeWidth + "s", place(mutant))).append("  ")
                    .append(String.format("%-" + methodWidth + "s", mutant.method() + "()")).append("  ")
                    .append(mutant.description()).append('\n');
            if (!mutant.sourceLine().isEmpty()) {
                out.append("  ").append(String.format("%-13s", "")).append("  ").append(mutant.sourceLine()).append('\n');
            }
        }
        out.append('\n');
        out.append("  These are hints, not errors. SURVIVED: tests ran the changed line and still passed, so a test\n")
                .append("  may be missing or too weak, or the mutant is equivalent (same behaviour as the original).\n")
                .append("  NOT COVERED: no test runs that line.\n\n");
    }

    private static String label(Mutant mutant) {
        return mutant.notCovered() ? "NOT COVERED" : mutant.status().replace('_', ' ');
    }

    private static String place(Mutant mutant) {
        return mutant.sourceFile() + ":" + mutant.line();
    }

    private static String pad(String label) {
        return String.format("%-" + LABEL_WIDTH + "s", label);
    }
}
