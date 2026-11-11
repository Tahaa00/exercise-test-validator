package dev.exercisevalidator.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * An Artemis-shaped exercise folder: {@code solution/src}, {@code tests/} (a Gradle build with its wrapper)
 * and optionally {@code alternatives/<name>/src}.
 */
public record Exercise(Path root) {

    public static Exercise open(Path folder) {
        Path root = folder.toAbsolutePath().normalize();
        require(Files.isDirectory(root), "Not a folder: " + root);
        require(Files.isDirectory(root.resolve("solution/src")), "Missing solution/src in " + root);
        require(Files.isRegularFile(root.resolve("tests/gradlew")),
                "Missing tests/gradlew in " + root + " (only Gradle exercises are supported)");
        return new Exercise(root);
    }

    public String name() {
        return root.getFileName().toString();
    }

    public Path tests() {
        return root.resolve("tests");
    }

    /** Source folder of a variant such as "solution" or "alternatives/a-streams". */
    public Path sourceOf(String variant) {
        return root.resolve(variant).resolve("src");
    }

    /** Alternative solutions as variant names ("alternatives/a-streams"), sorted so reports are stable. */
    public List<String> alternatives() throws IOException {
        Path dir = root.resolve("alternatives");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(p -> Files.isDirectory(p.resolve("src")))
                    .map(p -> "alternatives/" + p.getFileName())
                    .sorted()
                    .toList();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
