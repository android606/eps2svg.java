package com.convert2web.ps;

import com.convert2web.model.PaintStyle;

/**
 * Illustrator EPS text fill adjustments for SVG.
 *
 * <p>Many labels use {@code 0 0 0 0 cmyk} (process white) while still appearing as dark
 * body text in Illustrator/Inkscape ({@code #231f20}). Meter bar labels on Raleway keep
 * white when that same CMYK is intentional.
 */
public final class IllustratorTextPaint {
    /** Inkscape/Illustrator body text on light backgrounds (Gotham labels). */
    private static final PaintStyle LABEL_GRAY = PaintStyle.rgb(35 / 255.0, 31 / 255.0, 32 / 255.0);

    private IllustratorTextPaint() {
    }

    public static PaintStyle normalizeFillForSvg(PaintStyle fill, String fontName) {
        if (fill == null || fontName == null) {
            return fill;
        }
        if (isProcessWhite(fill) && usesDarkLabelInk(fontName)) {
            return LABEL_GRAY;
        }
        return fill;
    }

    public static boolean isItalicFont(String fontName) {
        if (fontName == null || fontName.isEmpty()) {
            return false;
        }
        return fontName.contains("Italic") || fontName.contains("Oblique");
    }

    private static boolean usesDarkLabelInk(String fontName) {
        if (fontName.contains("Raleway")) {
            return false;
        }
        return fontName.contains("Gotham")
                || fontName.contains("DINOT")
                || fontName.contains("Myriad")
                || fontName.contains("Helvetica");
    }

    private static boolean isProcessWhite(PaintStyle fill) {
        switch (fill.getKind()) {
            case GRAY:
                return fill.getV0() >= 0.99;
            case RGB:
                return fill.getV0() >= 0.99 && fill.getV1() >= 0.99 && fill.getV2() >= 0.99;
            case CMYK:
                return fill.getV0() <= 0.01 && fill.getV1() <= 0.01
                        && fill.getV2() <= 0.01 && fill.getV3() <= 0.01;
            default:
                return false;
        }
    }
}
