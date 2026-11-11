package dev.exercisevalidator.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Reads Gradle's JUnit XML files ({@code build/test-results/test/TEST-*.xml}).
 * The XML is the only thing we read from a run, so the tool does not care how the tests are written.
 */
public final class JUnitXmlReader {

    private static final int MAX_MESSAGE_LENGTH = 200;

    private JUnitXmlReader() {
    }

    /**
     * Reads the XML as it is. Careful with {@code <skipped/>}: Gradle 9 also writes it for the test that was running
     * when the test JVM was killed. {@link KilledJvm} corrects that; this class cannot know it.
     */
    public static List<TestResult> read(Path resultsDir) throws IOException {
        if (!Files.isDirectory(resultsDir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(resultsDir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList();
        }
        List<TestResult> results = new ArrayList<>();
        for (Path file : files) {
            results.addAll(readFile(file));
        }
        return results;
    }

    private static List<TestResult> readFile(Path file) throws IOException {
        Element root;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // The XML comes from running untrusted exercise code, so no DTDs or external entities.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            root = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
        } catch (Exception e) {
            throw new IOException("Cannot read test results in " + file + ": " + e.getMessage(), e);
        }
        List<TestResult> results = new ArrayList<>();
        NodeList cases = root.getElementsByTagName("testcase");
        for (int i = 0; i < cases.getLength(); i++) {
            Element testCase = (Element) cases.item(i);
            results.add(toResult(testCase));
        }
        return results;
    }

    private static TestResult toResult(Element testCase) {
        String name = testCase.getAttribute("name");
        String className = testCase.getAttribute("classname");
        Element problem = firstChild(testCase, "failure");
        if (problem == null) {
            problem = firstChild(testCase, "error");
        }
        if (problem != null) {
            String type = problem.getAttribute("type");
            String message = problem.getAttribute("message");
            TestOutcome outcome = looksLikeTimeout(type, message) ? TestOutcome.TIMEOUT : TestOutcome.FAIL;
            return new TestResult(name, className, outcome, oneLine(message.isBlank() ? type : message));
        }
        if (firstChild(testCase, "skipped") != null) {
            return new TestResult(name, className, TestOutcome.SKIPPED, "skipped (disabled or assumption not met)");
        }
        return new TestResult(name, className, TestOutcome.PASS, "");
    }

    /**
     * Timeouts that leave the JVM alive: JUnit's {@code @Timeout} throws a TimeoutException ("... timed out after 2 seconds"),
     * {@code assertTimeout} fails with "execution exceeded timeout of ...".
     */
    static boolean looksLikeTimeout(String type, String message) {
        String text = (type + " " + message).toLowerCase(Locale.ROOT);
        return text.contains("timeoutexception") || text.contains("timed out after") || text.contains("exceeded timeout");
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    /**
     * Failure message on one line: helpers often put "expected/actual" on following lines, which is the useful part.
     * The leading exception class name is dropped because it says nothing a tutor needs.
     */
    static String oneLine(String message) {
        String line = message.strip().replaceAll("\\s+", " ").replaceFirst("^[\\w.$]+(Error|Exception): ", "");
        return line.length() <= MAX_MESSAGE_LENGTH ? line : line.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }
}
