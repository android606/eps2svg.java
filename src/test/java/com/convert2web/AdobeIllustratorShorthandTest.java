package com.convert2web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdobeIllustratorShorthandTest {

    @Test
    void extractsLdfAliasesFromReuseFixtureProlog() throws Exception {
        String ps = Files.readString(Path.of("test/test_images/reuse_1.eps"), StandardCharsets.ISO_8859_1);
        String preamble = AdobeIllustratorShorthand.buildPreamble(ps);
        assertTrue(preamble.contains("/mo { moveto }"), () -> preamble.substring(0, Math.min(500, preamble.length())));
        assertTrue(preamble.contains("/@ { stroke }"), () -> preamble.substring(0, Math.min(500, preamble.length())));
        assertTrue(preamble.contains("/lw { setlinewidth }"));
        assertTrue(preamble.contains("/clp { clip }"));
    }

    @Test
    void staticFallbackWhenNoProlog() {
        String preamble = AdobeIllustratorShorthand.buildPreamble("");
        assertTrue(preamble.contains("/mo { moveto }"));
        assertTrue(preamble.contains("userdict begin"));
    }
}
