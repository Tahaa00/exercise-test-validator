package dev.exercisevalidator.report;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.exercisevalidator.check.BuggyResult;
import dev.exercisevalidator.check.Report;
import dev.exercisevalidator.check.Verdict;
import dev.exercisevalidator.check.Verdict.Status;
import dev.exercisevalidator.run.BugDescription;
import dev.exercisevalidator.run.Mutant;
import dev.exercisevalidator.run.MutationResult;
import dev.exercisevalidator.run.TestOutcome;
import dev.exercisevalidator.run.TestResult;

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

        if (!report.buggyVariants().isEmpty()) {
            out.append("Tests on the reference, shuffled reruns and correct alternatives\n\n");
        }
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

        if (!report.buggyVariants().isEmpty()) {
            renderBuggy(report.buggyVariants(), out);
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
        if (!report.buggyVariants().isEmpty()) {
            out.append("   Buggy variants missed: ").append(report.missedBuggyVariants());
        }
        if (mutation != null && mutation.state() == MutationResult.State.RAN) {
            out.append("   Mutants not detected: ").append(mutation.undetected().size());
        }
        out.append('\n');
        long findings = report.findings();
        out.append("Result: ").append(findings == 0 ? "no findings" : findings + (findings == 1 ? " finding" : " findings")).append('\n');
        return out.toString();
    }

    private static void renderBuggy(List<BuggyResult> results, StringBuilder out) {
        out.append("Tutor-written buggy variants\n\n");
        for (BuggyResult result : results) {
            out.append("  ").append(result.status()).append("  ").append(result.run().variant());
            if (result.status() == BuggyResult.Status.MISSED) {
                out.append("  (all ").append(result.run().tests().size()).append(" tests passed)");
            }
            out.append('\n');
            BugDescription description = result.description();
            detail(out, "Requirement", description.requirement());
            detail(out, "Witness input (tutor)", description.input());
            detail(out, "Expected (tutor)", description.expected());
            detail(out, "Actual (tutor)", description.actual());
            if (!description.intendedTest().isEmpty()) {
                detail(out, "Intended test", description.intendedTest() + "  " + result.intendedOutcome());
            }
            for (TestResult test : result.run().tests()) {
                if (test.outcome() != TestOutcome.PASS) {
                    String label = result.catchingTests().contains(test.qualifiedName()) ? "Caught by" : "Observed";
                    detail(out, label, test.qualifiedName() + "  " + test.outcome() + "  " + test.message());
                }
            }
            out.append('\n');
        }
    }

    private static void detail(StringBuilder out, String label, String value) {
        if (!value.isEmpty()) {
            out.append("    ").append(label).append(": ").append(value).append('\n');
        }
    }

    private static void renderMutation(MutationResult mutation, StringBuilder out) {
        out.append("Mutation check (PIT on the solution, STRONGER mutators)\n\n");
        if (mutation.state() != MutationResult.State.RAN) {
            out.append("  ").append(mutation.state() == MutationResult.State.SKIPPED ? "Skipped: " : "Could not run: ")
                    .append(mutation.note().replace("\n", "\n    ")).append("\n\n");
            return;
        }
        List<Mutant> mutants = mutation.counted();
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

        List<Row> rows = rows(mutants, undetected);
        int placeWidth = rows.stream().mapToInt(r -> r.place().length()).max().orElse(0);
        int methodWidth = rows.stream().mapToInt(r -> r.method().length()).max().orElse(0);
        for (Row row : rows) {
            out.append("  ").append(String.format("%-13s", row.label()))
                    .append(String.format("%-" + placeWidth + "s", row.place())).append("  ")
                    .append(String.format("%-" + methodWidth + "s", row.method())).append("  ")
                    .append(row.text()).append('\n');
            if (!row.sourceLine().isEmpty()) {
                out.append("  ").append(String.format("%-13s", "")).append("  ").append(row.sourceLine()).append('\n');
            }
        }
        if (!rows.isEmpty()) {
            out.append('\n');
            out.append("  These are hints, not errors. SURVIVED: tests ran the changed line and still passed, so a test\n")
                    .append("  may be missing or too weak, or the mutant is equivalent (same behaviour as the original).\n")
                    .append("  NOT COVERED: no test runs that line.\n\n");
        }
        List<Mutant> leftOut = mutation.leftOut();
        if (!leftOut.isEmpty()) {
            String mains = leftOut.stream().map(m -> simpleName(m.className()) + ".main(String[])").distinct()
                    .collect(Collectors.joining(", "));
            out.append("  Left out: ").append(leftOut.size()).append(leftOut.size() == 1 ? " mutant in " : " mutants in ").append(mains)
                    .append(". No test runs it, as usual for a demo entry point.\n\n");
        }
        if (mutation.note() != null) {
            out.append("  Note: ").append(mutation.note()).append("\n\n");
        }
    }

    /** One printed line of the mutant list, plus the quoted source line below it. */
    private record Row(String label, String place, String method, String text, String sourceLine) {
    }

    /**
     * One row per undetected mutant. The exception: a method in which no mutant at all is covered gets a single row,
     * because "no test runs this method" is one gap, however many mutants PIT can make in it.
     */
    private static List<Row> rows(List<Mutant> mutants, List<Mutant> undetected) {
        Map<String, List<Mutant>> byMethod = new LinkedHashMap<>();
        mutants.forEach(m -> byMethod.computeIfAbsent(methodKey(m), key -> new ArrayList<>()).add(m));
        List<Row> rows = new ArrayList<>();
        Set<String> collapsed = new HashSet<>();
        for (Mutant mutant : undetected) {
            List<Mutant> method = byMethod.get(methodKey(mutant));
            if (method.size() > 1 && method.stream().allMatch(Mutant::notCovered)) {
                if (collapsed.add(methodKey(mutant))) {
                    int first = method.stream().mapToInt(Mutant::line).min().orElse(mutant.line());
                    int last = method.stream().mapToInt(Mutant::line).max().orElse(mutant.line());
                    rows.add(new Row("NOT COVERED", mutant.sourceFile() + ":" + first + (last > first ? "-" + last : ""),
                            mutant.method() + "()", method.size() + " mutants, no test runs any of these lines", ""));
                }
            } else {
                rows.add(new Row(label(mutant), place(mutant), mutant.method() + "()", mutant.description(), mutant.sourceLine()));
            }
        }
        return rows;
    }

    private static String methodKey(Mutant mutant) {
        return mutant.className() + "#" + mutant.method() + mutant.descriptor();
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
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
