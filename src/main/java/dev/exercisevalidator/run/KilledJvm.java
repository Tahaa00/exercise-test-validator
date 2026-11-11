package dev.exercisevalidator.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
            if (parts.length < 3) {
                return null;
            }
            boolean end = parts[0].equals("END");
            return new Event(end, parts[1], parts[2], parts.length > 3 ? parts[3] : "", parts.length > 4 ? parts[4] : "");
        }

        String key() {
            return className + "\t" + name;
        }
    }

    static List<TestResult> repair(List<TestResult> fromXml, Path eventsFile) throws IOException {
        List<Event> events = Files.isRegularFile(eventsFile)
                ? Files.readAllLines(eventsFile).stream().map(Event::parse).filter(e -> e != null).toList()
                : List.of();
        if (events.isEmpty()) {
            // No record (should not happen with our init script): fall back to what Gradle 9 writes.
            return fromXml.stream()
                    .map(t -> t.outcome() == TestOutcome.SKIPPED ? new TestResult(t.name(), t.className(), TestOutcome.TIMEOUT, HUNG) : t)
                    .toList();
        }

        Map<String, Event> started = new LinkedHashMap<>();
        Map<String, Event> ended = new LinkedHashMap<>();
        Set<String> skippedSinceLastStart = new HashSet<>();
        for (Event event : events) {
            (event.end() ? ended : started).put(event.key(), event);
            if (!event.end()) {
                skippedSinceLastStart.clear();
            } else if (event.resultType().equals("SKIPPED")) {
                skippedSinceLastStart.add(event.key());
            }
        }
        Set<String> hung = started.keySet().stream().filter(k -> !ended.containsKey(k)).collect(Collectors.toCollection(HashSet::new));
        hung.addAll(skippedSinceLastStart);

        List<TestResult> repaired = new ArrayList<>();
        Set<String> inXml = fromXml.stream().map(t -> t.className() + "\t" + t.name()).collect(Collectors.toSet());
        for (TestResult test : fromXml) {
            boolean isHung = hung.contains(test.className() + "\t" + test.name());
            repaired.add(isHung ? new TestResult(test.name(), test.className(), TestOutcome.TIMEOUT, HUNG) : test);
        }
        // Tests the XML does not have (on Gradle 8: all of them), in the order they started.
        for (Event start : started.values()) {
            if (inXml.contains(start.key())) {
                continue;
            }
            Event end = ended.get(start.key());
            repaired.add(end == null || hung.contains(start.key())
                    ? new TestResult(start.name(), start.className(), TestOutcome.TIMEOUT, HUNG)
                    : fromEvent(end));
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
