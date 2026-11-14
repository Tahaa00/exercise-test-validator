package dev.exercisevalidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExpectedTestsTest {
    @TempDir Path temp;

    private List<String> read(String contents) throws IOException {
        return ExpectedTests.read(Files.writeString(temp.resolve("expected.txt"), contents));
    }

    @Test
    void readsOneTestPerLineAndSkipsBlankLines() throws IOException {
        assertEquals(List.of("p.First#same()", "p.Second#same()", "p.Cells#[1] quote, \"one\""),
                read("p.First#same()\n\np.Second#same()\r\np.Cells#[1] quote, \"one\"\n"));
    }

    @Test
    void keepsDisplayNamesAsWrittenAndReadsUtf8WithOrWithoutByteOrderMark() throws IOException {
        String id = "p.Échantillon$Nested#  entrée #1";
        assertEquals(List.of(id), read("﻿" + id + "\r\n"));
        assertEquals(List.of(id), read(id));
    }

    @Test
    void rejectsEmptyMalformedAndDuplicateLines() {
        for (String bad : List.of("", "\n", "p.A\n", "p.A#  \n", "p..A#run\n", "p.A#run \n", "p.A#a\tp.A#b\n",
                "p.A#a\np.A#a\n")) {
            assertThrows(IllegalArgumentException.class, () -> read(bad), bad);
        }
    }

    @Test
    void aWrongFolderOrOptionIsAUsageErrorNotAReport() {
        for (String[] args : List.of(new String[]{"validate", temp.resolve("missing-exercise").toString()},
                new String[]{"validate", temp.toString(), "--expected-tests"},
                new String[]{"validate", temp.toString(), "--timeout-minutes", "9223372036854775807"},
                new String[]{"validate", temp.toString(), "--decisions", "out.csv"})) {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            assertEquals(2, Main.run(args, new PrintStream(out), new PrintStream(err)));
            assertEquals("", out.toString());
            assertTrue(err.toString().contains("Usage: validate"), err.toString());
        }
    }
}
