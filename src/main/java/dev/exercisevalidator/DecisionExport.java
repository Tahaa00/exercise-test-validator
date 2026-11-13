package dev.exercisevalidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.exercisevalidator.check.Report;
import dev.exercisevalidator.check.Verdict;
import dev.exercisevalidator.run.MutationResult;

/** The frozen legacy per-test prediction, keyed by exercise folder name and exact test ID. */
final class DecisionExport {
    private enum Decision {
        OK, REVIEW, INCOMPLETE;

        static Decision combine(Decision first, Decision next) {
            if (first == INCOMPLETE || next == INCOMPLETE) {
                return INCOMPLETE;
            }
            return first == REVIEW || next == REVIEW ? REVIEW : OK;
        }
    }

    private DecisionExport() {
    }

    static Path destination(Path requested, Path exercise) throws IOException {
        Path output = requested.toAbsolutePath();
        if (output.getFileName() == null || Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("--decisions needs a new file path; already exists: " + output);
        }
        Path parent = output.getParent().toRealPath();
        if (!Files.isDirectory(parent)) {
            throw new IllegalArgumentException("--decisions needs an existing parent directory: " + parent);
        }
        Path target = parent.resolve(output.getFileName());
        for (String input : List.of("solution", "tests", "alternatives", "buggy")) {
            Path source = exercise.resolve(input).toAbsolutePath().normalize();
            if (output.normalize().startsWith(source)
                    || (Files.isDirectory(source) && target.startsWith(source.toRealPath()))) {
                throw new IllegalArgumentException("--decisions must be outside exercise inputs: " + source);
            }
        }
        return target;
    }

    /** Publish the predictions and return the grouped evidence and any whole-exercise decision override for the console. */
    static String write(Path target, Report report, ExpectedTests inventory, boolean mutationRequested) throws IOException {
        String incompleteReason = incompleteReason(report, mutationRequested);
        Map<String, Decision> observed = new LinkedHashMap<>();
        for (Verdict verdict : report.verdicts()) {
            Decision decision = incompleteReason != null || verdict.incomplete() ? Decision.INCOMPLETE : switch (verdict.status()) {
                case OK -> Decision.OK;
                case REJECTED, FLAKY, OVER_SPECIFIED -> Decision.REVIEW;
                case NOT_CHECKED -> Decision.INCOMPLETE;
            };
            observed.merge(verdict.test(), decision, (first, duplicate) -> Decision.INCOMPLETE);
        }
        Map<String, Decision> rows = new LinkedHashMap<>();
        Map<String, List<String>> groupedInvocations = new LinkedHashMap<>();
        if (inventory.entries().isEmpty()) {
            rows.putAll(observed);
        } else {
            for (ExpectedTests.Entry entry : inventory.entries()) {
                Decision decision = observed.getOrDefault(entry.invocation(), Decision.INCOMPLETE);
                rows.merge(entry.gradingTest(), decision, Decision::combine);
                if (entry.grouped()) {
                    groupedInvocations.computeIfAbsent(entry.gradingTest(), key -> new ArrayList<>()).add(entry.invocation());
                }
            }
        }
        String exercise = csvField(report.exercise());
        Path pending = Files.createTempFile(target.getParent(), ".decisions-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(pending)) {
                writer.write("exercise,test,decision\n");
                for (var row : rows.entrySet()) {
                    writer.write(exercise + "," + csvField(row.getKey()) + "," + row.getValue() + "\n");
                }
            }
            // Publish the finished file atomically. A concurrent export to the same path must not replace it.
            Files.createLink(target, pending);
        } finally {
            Files.deleteIfExists(pending);
        }
        StringBuilder evidence = new StringBuilder();
        if (incompleteReason != null) {
            evidence.append("\nAll legacy decisions are INCOMPLETE. ").append(incompleteReason).append('\n');
        }
        if (!groupedInvocations.isEmpty()) {
            evidence.append("\nGrouped legacy decisions\n");
            groupedInvocations.forEach((group, members) -> {
                evidence.append("  ").append(group).append(" -> ").append(rows.get(group)).append('\n');
                members.forEach(member -> evidence.append("    ").append(member).append('\n'));
            });
        }
        return evidence.toString();
    }

    private static String incompleteReason(Report report, boolean mutationRequested) {
        if (report.fatal() != null) {
            return "Validation could not finish: " + report.fatal().lines().findFirst().orElse("see the build diagnostic.");
        }
        if (!report.problems().isEmpty()) {
            return report.problems().get(0);
        }
        if (mutationRequested) {
            MutationResult mutation = report.mutation();
            if (mutation == null) {
                return "Requested PIT check produced no result.";
            }
            if (mutation.state() != MutationResult.State.RAN) {
                return "Requested PIT check " + mutation.state() + ": " + mutation.note();
            }
        }
        return null;
    }

    private static String csvField(String value) {
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("Cannot export a blank or untrimmed scorer key: '" + value + "'");
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
