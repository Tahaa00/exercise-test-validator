package dev.exercisevalidator.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PitXmlReaderTest {

    /** Shape of PIT 1.30.2's mutations.xml, shortened to the elements the reader uses plus two it must ignore. */
    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <mutations partial="true">
            <mutation detected='false' status='SURVIVED' numberOfTestsRun='7'><sourceFile>Calc.java</sourceFile><mutatedClass>com.example.Calc</mutatedClass><mutatedMethod>max</mutatedMethod><methodDescription>([I)I</methodDescription><lineNumber>5</lineNumber><mutator>org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator</mutator><killingTest/><description>changed conditional boundary</description></mutation>
            <mutation detected='true' status='RUN_ERROR' numberOfTestsRun='1'><sourceFile>Calc.java</sourceFile><mutatedClass>com.example.Calc</mutatedClass><mutatedMethod>max</mutatedMethod><methodDescription>([I)I</methodDescription><lineNumber>4</lineNumber><mutator>x</mutator><killingTest/><description>removed conditional - replaced comparison check with true</description></mutation>
            <mutation detected='true' status='KILLED' numberOfTestsRun='1'><sourceFile>Calc.java</sourceFile><mutatedClass>com.example.Calc</mutatedClass><mutatedMethod>max</mutatedMethod><methodDescription>([I)I</methodDescription><lineNumber>3</lineNumber><mutator>x</mutator><killingTest>t</killingTest><description>negated conditional</description></mutation>
            </mutations>
            """;

    @Test
    void readsStatusPlaceAndSourceLineSortedByLine(@TempDir Path temp) throws Exception {
        Path xml = Files.writeString(temp.resolve("mutations.xml"), XML);
        Path source = temp.resolve("src/com/example/Calc.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.example;\nclass Calc {\n  // 3\n  // 4\n        if (v > max) {\n}\n");
        // Same file name in another package must not be picked.
        Files.createDirectories(temp.resolve("src/other"));
        Files.writeString(temp.resolve("src/other/Calc.java"), "1\n2\n3\n4\nwrong file\n");

        List<Mutant> mutants = PitXmlReader.read(xml, temp.resolve("src"));

        assertEquals(List.of(3, 4, 5), mutants.stream().map(Mutant::line).toList());
        Mutant survivor = mutants.get(2);
        assertEquals("Calc.java", survivor.sourceFile());
        assertEquals("max", survivor.method());
        assertEquals("changed conditional boundary", survivor.description());
        assertEquals("if (v > max) {", survivor.sourceLine());
        assertTrue(survivor.survived());
        assertFalse(survivor.detected());
        assertTrue(mutants.get(1).detected(), "RUN_ERROR counts as detected, as PIT says");
        assertFalse(mutants.get(1).killed());
    }

    @Test
    void malformedReportCannotSupplyMutationEvidence(@TempDir Path temp) throws Exception {
        Path xml = temp.resolve("mutations.xml");
        for (String invalid : List.of("<results/>", XML.replace("<lineNumber>5", "<lineNumber>x"),
                XML.replace("detected='false'", "detected='unknown'"), XML.replace("status='KILLED'", ""))) {
            Files.writeString(xml, invalid);
            assertThrows(IOException.class, () -> PitXmlReader.read(xml, temp));
        }
    }

    @Test
    void missingSourceFileLeavesTheLineEmpty(@TempDir Path temp) throws Exception {
        Path xml = Files.writeString(temp.resolve("mutations.xml"), XML);

        List<Mutant> mutants = PitXmlReader.read(xml, temp.resolve("no-such-folder"));

        assertEquals(3, mutants.size());
        assertEquals("", mutants.get(0).sourceLine());
    }
}
