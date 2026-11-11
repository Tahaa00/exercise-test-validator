package dev.exercisevalidator.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads PIT's {@code mutations.xml}. Each {@code <mutation>} element carries {@code detected} and {@code status}
 * attributes and child elements for the place and kind of the change.
 */
public final class PitXmlReader {

    private PitXmlReader() {
    }

    /**
     * @param xml        PIT's mutations.xml
     * @param sourceRoot folder with the mutated sources, used to quote the mutated line; may be null
     */
    public static List<Mutant> read(Path xml, Path sourceRoot) throws IOException {
        NodeList nodes;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            nodes = factory.newDocumentBuilder().parse(xml.toFile()).getElementsByTagName("mutation");
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Cannot read PIT report " + xml + ": " + e.getMessage(), e);
        }
        Map<String, List<String>> sources = new HashMap<>();
        List<Mutant> mutants = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String sourceFile = text(element, "sourceFile");
            String className = text(element, "mutatedClass");
            int line = Integer.parseInt(text(element, "lineNumber"));
            List<String> lines = sources.computeIfAbsent(className + "/" + sourceFile,
                    key -> sourceLines(sourceRoot, className, sourceFile));
            mutants.add(new Mutant(sourceFile, className, text(element, "mutatedMethod"),
                    text(element, "methodDescription"), line, text(element, "description"),
                    element.getAttribute("status"), Boolean.parseBoolean(element.getAttribute("detected")),
                    line >= 1 && line <= lines.size() ? lines.get(line - 1).strip() : ""));
        }
        // PIT's order follows its own run order; by class and line is what a reader expects.
        mutants.sort(Comparator.comparing(Mutant::className).thenComparingInt(Mutant::line).thenComparing(Mutant::description));
        return mutants;
    }

    private static String text(Element mutation, String tag) {
        NodeList children = mutation.getElementsByTagName(tag);
        return children.getLength() == 0 ? "" : children.item(0).getTextContent().strip();
    }

    /**
     * PIT gives the class name and the bare file name, not a path. The file is the one below the source root whose
     * path ends with package folders plus file name, which also holds for layouts like {@code src/main/java}.
     */
    private static List<String> sourceLines(Path sourceRoot, String className, String sourceFile) {
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        int lastDot = className.lastIndexOf('.');
        Path wanted = Path.of(lastDot < 0 ? "" : className.substring(0, lastDot).replace('.', '/')).resolve(sourceFile);
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            Path file = files.filter(p -> p.endsWith(wanted) && Files.isRegularFile(p)).sorted().findFirst().orElse(null);
            return file == null ? List.of() : Files.readAllLines(file);
        } catch (IOException | java.io.UncheckedIOException e) {
            return List.of(); // the quoted line is a convenience; the mutant is still reported without it
        }
    }
}
