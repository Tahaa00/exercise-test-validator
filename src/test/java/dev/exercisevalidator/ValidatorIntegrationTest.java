package dev.exercisevalidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.exercisevalidator.check.Report;
import dev.exercisevalidator.check.Validator;
import dev.exercisevalidator.check.Verdict;
import dev.exercisevalidator.check.Verdict.Status;
import dev.exercisevalidator.run.Exercise;
import dev.exercisevalidator.run.GradleRunner;
import dev.exercisevalidator.run.RunResult;
import dev.exercisevalidator.run.VariantRunner;

/**
 * End to end: real Gradle runs of the plain sample and of a flaky fixture. Run with {@code ./gradlew integrationTest}.
 * The Ares samples are not run here because they need JDK 25; they are checked by hand until CI exists (step 4).
 */
@Tag("integration")
class ValidatorIntegrationTest {

    private static final Path PLAIN_SAMPLE = Path.of("exercises/array-max-plain");

    private static Map<String, Status> statuses(Report report) {
        return report.verdicts().stream().collect(Collectors.toMap(Verdict::test, Verdict::status));
    }

    @Test
    void plainSampleHasExactlyOneFindingTheOverSpecifiedTest() throws Exception {
        Validator validator = new Validator(new GradleRunner(Duration.ofMinutes(10), false), step -> { });

        Report report = validator.validate(Exercise.open(PLAIN_SAMPLE), 2, 1);

        assertNull(report.fatal(), report.fatal());
        assertEquals("17", report.javaVersion());
        assertEquals(3, report.alternatives().size());
        Map<String, Status> statuses = statuses(report);
        assertEquals(7, statuses.size());
        assertEquals(Status.OVER_SPECIFIED, statuses.get("emptyArrayIsRejected()"));
        assertEquals(6, report.count(Status.OK));
        Verdict finding = report.verdicts().stream().filter(v -> v.status() == Status.OVER_SPECIFIED).findFirst().orElseThrow();
        assertTrue(finding.evidence().get(0).endsWith("on alternatives/b-other-message"), finding.evidence().toString());
        assertEquals(1, report.exitCode());
    }

    @Test
    void orderDependentTestIsReportedAsFlaky(@TempDir Path temp) throws Exception {
        Exercise fixture = flakyFixture(temp);
        Files.walk(fixture.root().resolve("alternatives")).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        Validator validator = new Validator(new GradleRunner(Duration.ofMinutes(10), false), step -> { });

        Report report = validator.validate(fixture, 10, 1);

        assertNull(report.fatal(), report.fatal());
        Map<String, Status> statuses = statuses(report);
        // Which of the two tests breaks depends on the order; with base seed 1 at least one run flips it.
        assertTrue(statuses.containsValue(Status.FLAKY), statuses.toString());
        assertEquals(0, report.count(Status.REJECTED), "both tests pass in the declared order");
    }

    /** A suite without test timeouts and a hanging solution: the tool's own limit must end the run. */
    @Test
    void hangingRunIsStoppedByTheToolsTimeLimit(@TempDir Path temp) throws Exception {
        Exercise fixture = flakyFixture(temp);
        VariantRunner runner = new GradleRunner(Duration.ofSeconds(45), false);

        RunResult result;
        try (VariantRunner.Session session = runner.open(fixture, "alternatives/a-hangs")) {
            result = session.run();
        }

        assertEquals(RunResult.Build.TIMED_OUT, result.build());
    }

    /** The fixture ships no build files; borrow the plain sample's (wrapper, build.gradle, settings.gradle). */
    private static Exercise flakyFixture(Path temp) throws IOException {
        Path root = temp.resolve("flaky-counter");
        copy(Path.of("src/test/exercises/flaky-counter"), root);
        try (Stream<Path> buildFiles = Files.walk(PLAIN_SAMPLE.resolve("tests"))) {
            for (Path file : buildFiles.filter(Files::isRegularFile).filter(p -> !p.toString().endsWith(".java")).toList()) {
                Path target = root.resolve("tests").resolve(PLAIN_SAMPLE.resolve("tests").relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        }
        return Exercise.open(root);
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = to.resolve(from.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        }
    }
}
