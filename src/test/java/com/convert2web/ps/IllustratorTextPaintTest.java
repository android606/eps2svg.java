package com.convert2web.ps;

import com.convert2web.model.PaintStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IllustratorTextPaintTest {

    @Test
    void mapsProcessWhiteGothamFillToLabelGray() {
        PaintStyle fill = PaintStyle.cmyk(0, 0, 0, 0);
        PaintStyle normalized = IllustratorTextPaint.normalizeFillForSvg(fill, "GothamXNarrow-BookItalic");
        assertEquals(PaintStyle.Kind.RGB, normalized.getKind());
        assertEquals(35 / 255.0, normalized.getV0(), 1e-6);
        assertEquals(31 / 255.0, normalized.getV1(), 1e-6);
        assertEquals(32 / 255.0, normalized.getV2(), 1e-6);
    }

    @Test
    void keepsProcessWhiteRalewayFill() {
        PaintStyle fill = PaintStyle.cmyk(0, 0, 0, 0);
        PaintStyle normalized = IllustratorTextPaint.normalizeFillForSvg(fill, "Raleway-SemiBold");
        assertEquals(fill, normalized);
    }

    @Test
    void detectsItalicFontNames() {
        assertTrue(IllustratorTextPaint.isItalicFont("GothamXNarrow-BookItalic"));
        assertFalse(IllustratorTextPaint.isItalicFont("GothamXNarrow-Book"));
    }
}
