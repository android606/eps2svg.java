package com.convert2web.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceSpanTest {

    @Test
    void storesLineColumnAndOffset() {
        SourceSpan span = SourceSpan.of(42, 7, 1234);
        assertEquals(42, span.line());
        assertEquals(7, span.column());
        assertEquals(1234, span.offset());
        assertEquals("42:7@1234", span.toString());
    }

    @Test
    void formatsSvgTraceOutput() {
        SourceSpan span = SourceSpan.of(12, 3, 99);
        assertEquals(" data-eps-line=\"12\" data-eps-column=\"3\" data-eps-offset=\"99\"",
                span.svgDataAttributes());
        assertEquals(
                "<!-- eps-source kind=\"fill\" line=\"12\" column=\"3\" offset=\"99\" -->\n",
                span.svgSourceComment("fill"));
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> SourceSpan.of(0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> SourceSpan.of(1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SourceSpan.of(1, 1, -1));
    }
}
