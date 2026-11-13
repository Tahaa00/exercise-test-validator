package dev.exercisevalidator.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExerciseTest {
    @Test
    void namedVariantWithMissingSourcesIsReported(@TempDir Path temp) throws IOException {
        Exercise exercise = new Exercise(temp);
        Files.createDirectories(temp.resolve("alternatives/unfinished"));
        Files.createDirectories(temp.resolve("alternatives/.git"));
        Files.createDirectories(temp.resolve("buggy/missing-sources"));
        assertEquals(List.of("alternatives/unfinished"), exercise.alternatives());
        assertEquals(List.of("buggy/missing-sources"), exercise.buggyVariants());
        GradleRunner runner = new GradleRunner(Duration.ofSeconds(5), false, null);
        assertThrows(IOException.class, () -> runner.open(exercise, "alternatives/unfinished"));
    }
}
