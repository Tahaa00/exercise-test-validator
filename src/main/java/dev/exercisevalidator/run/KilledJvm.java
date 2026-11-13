package dev.exercisevalidator.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Repairs the results of a run whose test JVM was killed (an endless loop under Ares' {@code @StrictTimeout}).
 *
 * <p>The JUnit XML cannot be trusted in that case, and how wrong it is depends on the Gradle version
 * (both verified by running): Gradle 9.8 writes a partial file in which the hanging test is "skipped",
 * Gradle 8.12 writes no file at all. So the tool's init script records every test start and end as Gradle
 * reports them, and this class uses that record to find the test that hung.
 *
 * <p>When the JVM dies, Gradle closes the test that was running with result SKIPPED. So the hanging test is the
 * one that ended as SKIPPED with no test starting after it (a disabled test is followed by the next test's
 * start), or one that never ended at all.
 */
final class KilledJvm {

    static final String EVENTS_FILE = "validator-test-events.tsv";
    private static final String HUNG = "the test JVM was killed while this test was running";

    private KilledJvm() {
    }

    /** One line of the record: {@code START|END <tab> class <tab> name [<tab> resultType <tab> exception]}. */
    private record Event(boolean end, String className, String name, String resultType, String exception) {

        static Event parse(String line) {
            String[] parts = line.split("\t", -1);
            boolean end = parts[0].equals("END") && parts.length == 5;
            boolean start = parts[0].equals("START") && parts.length == 3;
            if (!start && !end) {
                return null;
            }
            try {
                return new Event(end, decode(parts[1]), decode(parts[2]), end ? parts[3] : "", end ? decode(parts[4]) : "");
            } catch (IllegalArgumentException e) {
                return null; // An abruptly terminated write can leave a truncated Base64 field.
            }
        }

        private static String decode(String field) {
            return new String(Base64.getDecoder().decode(field), StandardCharsets.UTF_8);
        }

        String key() {
            return className + "\t" + name;
        }
    }

    static List<TestResult> repair(List<TestResult> fromXml, Path eventsFile) throws IOException {
        List<Event> events = Files.isRegularFile(eventsFile)
                ? Files.readAllLines(eventsFile).stream().map(Event::parse).toList()
                : List.of();
        if (events.isEmpty()) {
            // The XML alone cannot distinguish a disabled test from the one interrupted by the JVM exit.
            return fromXml;
        }

        Map<String, Event> started = new LinkedHashMap<>();
        Map<String, Event> ended = new LinkedHashMap<>();
        Map<String, Integer> startCounts = new LinkedHashMap<>();
        Set<String> skippedSinceLastStart = new HashSet<>();
        boolean damaged = false;
        for (Event event : events) {
            if (event == null) {
                damaged = true;
                continue;
            }
            (event.end() ? ended : started).put(event.key(), event);
            if (!event.end()) {
                startCounts.merge(event.key(), 1, Integer::sum);
                skippedSinceLastStart.clear();
            } else if (event.resultType().equals("SKIPPED")) {
                skippedSinceLastStart.add(event.key());
            }
        }
        Set<String> hung = started.keySet().stream().filter(k -> !ended.containsKey(k)).collect(Collectors.toCollection(HashSet::new));
        hung.addAll(skippedSinceLastStart);
        boolean uniqueInterruption = !damaged && hung.size() == 1;

        List<TestResult> repaired = new ArrayList<>();
        Map<String, Integer> xmlCounts = new LinkedHashMap<>();
        for (TestResult test : fromXml) {
            String key = test.className() + "\t" + test.name();
            xmlCounts.merge(key, 1, Integer::sum);
            boolean isHung = uniqueInterruption && hung.contains(key) && startCounts.getOrDefault(key, 0) == 1;
            repaired.add(isHung ? new TestResult(test.name(), test.className(), TestOutcome.TIMEOUT, HUNG, true) : test);
        }
        // Tests the XML does not have (on Gradle 8: all of them), in the order they started.
        for (Event start : started.values()) {
            int starts = startCounts.get(start.key());
            int xmlRows = xmlCounts.getOrDefault(start.key(), 0);
            if (starts > 1) {
                // Repeated display names have no occurrence identifier to match XML rows to end events.
                for (int i = xmlRows; i < starts; i++) {
                    repaired.add(new TestResult(start.name(), start.className(), TestOutcome.AMBIGUOUS,
                            starts + " starts with this identity in the Gradle event record"));
                }
                continue;
            }
            if (xmlRows > 0) {
                continue;
            }
            Event end = ended.get(start.key());
            if (uniqueInterruption && hung.contains(start.key())) {
                repaired.add(new TestResult(start.name(), start.className(), TestOutcome.TIMEOUT, HUNG, true));
            } else if (end == null) {
                repaired.add(new TestResult(start.name(), start.className(), TestOutcome.AMBIGUOUS,
                        "test started without a final result before the JVM exit"));
            } else {
                repaired.add(fromEvent(end));
            }
        }
        return repaired;
    }

    private static TestResult fromEvent(Event end) {
        return switch (end.resultType()) {
            case "SUCCESS" -> new TestResult(end.name(), end.className(), TestOutcome.PASS, "");
            case "SKIPPED" -> new TestResult(end.name(), end.className(), TestOutcome.SKIPPED, "skipped (disabled or assumption not met)");
            default -> new TestResult(end.name(), end.className(),
                    JUnitXmlReader.looksLikeTimeout("", end.exception()) ? TestOutcome.TIMEOUT : TestOutcome.FAIL,
                    JUnitXmlReader.oneLine(end.exception()));
        };
    }
}
