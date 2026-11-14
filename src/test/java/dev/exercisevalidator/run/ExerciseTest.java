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
    void onlyFoldersWithSourcesAreVariants(@TempDir Path temp) throws IOException {
        Exercise exercise = new Exercise(temp);
        Files.createDirectories(temp.resolve("alternatives/unfinished"));
        Files.createDirectories(temp.resolve("alternatives/.git"));
        Files.createDirectories(temp.resolve("alternatives/a-loop/src"));
        Files.createDirectories(temp.resolve("buggy/notes"));
        Files.createDirectories(temp.resolve("buggy/off-by-one/src"));
        assertEquals(List.of("alternatives/a-loop"), exercise.alternatives());
        assertEquals(List.of("buggy/off-by-one"), exercise.buggyVariants());
        GradleRunner runner = new GradleRunner(Duration.ofSeconds(5), false, null);
        assertThrows(IOException.class, () -> runner.open(exercise, "alternatives/unfinished"));
    }
}
