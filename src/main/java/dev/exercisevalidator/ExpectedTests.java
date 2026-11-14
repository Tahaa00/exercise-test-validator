package dev.exercisevalidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The tests a tutor intends to grade, read from a UTF-8 file with one {@code class#name} per line,
 * where the name is exactly what the JUnit XML reports. The tool cannot find these out by itself:
 * a disabled or filtered test is missing from every run.
 */
final class ExpectedTests {

    private static final String IDENTIFIER = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final Pattern CLASS_NAME = Pattern.compile(IDENTIFIER + "(?:\\." + IDENTIFIER + ")*");

    private ExpectedTests() {
    }

    static List<String> read(Path file) throws IOException {
        List<String> ids = new ArrayList<>();
        int line = 0;
        for (String row : Files.readAllLines(file)) {
            line++;
            if (line == 1 && row.startsWith("﻿")) {
                row = row.substring(1); // byte order mark, written by some Windows editors
            }
            if (row.isBlank()) {
                continue;
            }
            if (!qualified(row)) {
                throw new IllegalArgumentException(file + ":" + line + ": expected class#testName, for example"
                        + " com.example.FooTest#bar()");
            }
            if (ids.contains(row)) {
                throw new IllegalArgumentException(file + ":" + line + ": duplicate test: " + row);
            }
            ids.add(row);
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(file + ": the list of expected tests is empty");
        }
        return List.copyOf(ids);
    }

    private static boolean qualified(String id) {
        int separator = id.indexOf('#');
        return separator > 0 && CLASS_NAME.matcher(id.substring(0, separator)).matches()
                && !id.substring(separator + 1).isBlank() && id.equals(id.strip()) && !id.contains("\t");
    }
}
