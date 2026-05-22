package com.convert2web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpsFormatDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsAsciiEps() throws IOException {
        Path eps = tempDir.resolve("ok.eps");
        Files.writeString(eps, "%!PS-Adobe-3.0 EPSF-3.0\n%%BoundingBox: 0 0 10 10\n");
        assertEquals(EpsFormatDetector.Format.ASCII_EPS, EpsFormatDetector.detectFormat(eps.toString()));
        assertDoesNotThrow(() -> EpsFormatDetector.validateEpsFile(eps.toString()));
    }

    @Test
    void rejectsPdf() throws IOException {
        Path pdf = tempDir.resolve("fake.eps");
        Files.write(pdf, "%PDF-1.6\n".getBytes());
        assertEquals(EpsFormatDetector.Format.PDF, EpsFormatDetector.detectFormat(pdf.toString()));
        IOException ex = assertThrows(IOException.class, () -> EpsFormatDetector.validateEpsFile(pdf.toString()));
        assertTrue(ex.getMessage().contains("PDF"));
    }

    @Test
    void rejectsPlainText() throws IOException {
        Path txt = tempDir.resolve("notes.eps");
        Files.writeString(txt, "This is not PostScript.\n");
        assertEquals(EpsFormatDetector.Format.OTHER, EpsFormatDetector.detectFormat(txt.toString()));
        assertThrows(IOException.class, () -> EpsFormatDetector.validateEpsFile(txt.toString()));
    }
}
