package dev.exercisevalidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Each expected XML invocation belongs to one independently declared grading row. */
record ExpectedTests(List<Entry> entries) {
    record Entry(String invocation, String gradingTest, boolean grouped) {}

    private static final String IDENTIFIER = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final Pattern CLASS_NAME = Pattern.compile(IDENTIFIER + "(?:\\." + IDENTIFIER + ")*");

    ExpectedTests {
        entries = List.copyOf(entries);
    }

    List<String> invocations() {
        return entries.stream().map(Entry::invocation).toList();
    }

    static ExpectedTests read(Path file) throws IOException {
        Map<String, String> byInvocation = new LinkedHashMap<>();
        Set<String> groupIds = new HashSet<>();
        Set<String> individualIds = new HashSet<>();
        try (var reader = Files.newBufferedReader(file)) {
            String row;
            int line = 0;
            while ((row = reader.readLine()) != null) {
                line++;
                if (line == 1 && row.startsWith("\uFEFF")) {
                    row = row.substring(1);
                }
                if (row.isBlank()) {
                    continue; // a trailing or separating blank line is not a test
                }
                String[] fields = row.split("\t", -1);
                if (fields.length > 2 || !qualified(fields[0]) || (fields.length == 2 && !qualified(fields[1]))) {
                    throw new IllegalArgumentException(file + ":" + line + ": expected class#invocation"
                            + " optionally followed by TAB and class#gradingTest");
                }
                String invocation = fields[0];
                String gradingTest = fields.length == 2 ? fields[1] : invocation;
                if (byInvocation.putIfAbsent(invocation, gradingTest) != null) {
                    throw new IllegalArgumentException(file + ":" + line + ": duplicate test ID: " + invocation);
                }
                if (fields.length == 2) {
                    groupIds.add(gradingTest);
                } else {
                    individualIds.add(invocation);
                }
            }
        }
        if (byInvocation.isEmpty()) {
            throw new IllegalArgumentException(file + ": expected-test inventory is empty");
        }
        for (String group : groupIds) {
            if (individualIds.contains(group) || (byInvocation.containsKey(group) && !byInvocation.get(group).equals(group))) {
                throw new IllegalArgumentException(file + ": group ID collides with a separately graded invocation: " + group);
            }
        }
        List<Entry> entries = new ArrayList<>();
        byInvocation.forEach((id, group) -> entries.add(new Entry(id, group, groupIds.contains(group))));
        return new ExpectedTests(entries);
    }

    private static boolean qualified(String id) {
        int separator = id.indexOf('#');
        return separator > 0 && CLASS_NAME.matcher(id.substring(0, separator)).matches()
                && !id.substring(separator + 1).isBlank() && id.equals(id.strip());
    }
}
