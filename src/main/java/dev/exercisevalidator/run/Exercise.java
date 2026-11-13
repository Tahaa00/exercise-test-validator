package dev.exercisevalidator.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * An Artemis-shaped exercise folder: {@code solution/src}, {@code tests/} (a Gradle build with its wrapper)
 * and optionally {@code alternatives/<name>/src} and {@code buggy/<name>/src}.
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

    /** Include named variants with missing sources so the runner can report the incomplete checkout. */
    public List<String> alternatives() throws IOException {
        return variantsIn("alternatives");
    }

    public List<String> buggyVariants() throws IOException {
        return variantsIn("buggy");
    }

    private List<String> variantsIn(String folder) throws IOException {
        Path dir = root.resolve(folder);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith(".") || Files.isDirectory(p.resolve("src")))
                    .map(p -> folder + "/" + p.getFileName())
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
