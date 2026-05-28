package com.convert2web.ps;

/**
 * Maps Illustrator EPS string bytes to Unicode for SVG text output.
 *
 * <p>Subset-font strings often use octal escapes for characters that are not the
 * standard PostScript space ({@code \\040}). For example {@code (In\\312Range)}
 * uses octal {@code 312} (decimal 202), which is a word separator in the source
 * file but would render as {@code Ê} if emitted literally in UTF-8 SVG.
 */
public final class IllustratorTextEncoding {
    /** Octal {@code 312}: Illustrator word separator in many LifeScan EPS labels. */
    private static final char ILLUSTRATOR_WORD_SEPARATOR = (char) 202;

    /** Octal {@code 240}: non-breaking space in Latin-1 / Illustrator strings. */
    private static final char NON_BREAKING_SPACE = (char) 160;

    private IllustratorTextEncoding() {
    }

    public static String normalizeForSvg(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        boolean changed = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ILLUSTRATOR_WORD_SEPARATOR || c == NON_BREAKING_SPACE) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ILLUSTRATOR_WORD_SEPARATOR || c == NON_BREAKING_SPACE) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
