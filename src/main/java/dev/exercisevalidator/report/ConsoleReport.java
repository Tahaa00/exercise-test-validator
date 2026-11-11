package dev.exercisevalidator.report;

import dev.exercisevalidator.check.Report;
import dev.exercisevalidator.check.Verdict;
import dev.exercisevalidator.check.Verdict.Status;

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

        long total = report.verdicts().size();
        out.append("Solution: ").append(total - report.count(Status.REJECTED)).append('/').append(total).append(" pass")
                .append("   Flaky: ").append(report.count(Status.FLAKY))
                .append("   Fail on an alternative: ").append(report.count(Status.OVER_SPECIFIED));
        if (report.count(Status.NOT_CHECKED) > 0) {
            out.append("   Not checked: ").append(report.count(Status.NOT_CHECKED));
        }
        out.append('\n');
        long findings = report.findings();
        out.append("Result: ").append(findings == 0 ? "no findings" : findings + (findings == 1 ? " finding" : " findings")).append('\n');
        return out.toString();
    }

    private static String pad(String label) {
        return String.format("%-" + LABEL_WIDTH + "s", label);
    }
}
