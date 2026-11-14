package dev.exercisevalidator.run;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Optional tutor-supplied context. None of these strings is executed or added to the grading suite. */
public record BugDescription(String requirement, String input, String expected, String actual, String intendedTest) {

    public static final BugDescription EMPTY = new BugDescription("", "", "", "", "");

    public static BugDescription read(Path variant) throws IOException {
        Path path = variant.resolve("bug.properties");
        if (Files.notExists(path)) {
            return EMPTY;
        }
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            values.load(reader);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid bug.properties: " + ex.getMessage(), ex);
        }
        return new BugDescription(values.getProperty("requirement", "").strip(), values.getProperty("input", "").strip(),
                values.getProperty("expected", "").strip(), values.getProperty("actual", "").strip(),
                values.getProperty("intendedTest", "").strip());
    }
}
