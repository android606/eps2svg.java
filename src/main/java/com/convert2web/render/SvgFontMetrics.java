package com.convert2web.render;

import com.convert2web.ps.IllustratorTextPaint;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves local font substitutes and measures glyph advances for SVG text spacing.
 */
final class SvgFontMetrics {
    private static final FontRenderContext FONT_RENDER_CONTEXT =
            new FontRenderContext(new AffineTransform(), true, true);
    private static final Set<String> AVAILABLE_FAMILIES = availableFamilies();
    private static final Set<String> AVAILABLE_DISPLAY_FAMILIES = availableDisplayFamilies();

    private SvgFontMetrics() {
    }

    static FontChoice chooseFont(String sourceFontName, double fontSize, boolean resolveSubstitute) {
        String sourceFamily = sourceFamily(sourceFontName);
        if (!resolveSubstitute) {
            return new FontChoice(sourceFamily, null, false);
        }
        FontTraits traits = FontTraits.from(sourceFamily);
        String resolvedFamily = resolveFamily(sourceFamily, traits);
        boolean substituted = !normalized(sourceFamily).equals(normalized(resolvedFamily));
        return new FontChoice(sourceFamily, resolvedFamily, substituted, traits);
    }

    static double[] glyphAdvances(String family, String sourceFontName, double fontSize, String text) {
        if (family == null || family.isEmpty() || text == null || text.isEmpty()) {
            return new double[0];
        }
        FontTraits traits = FontTraits.from(sourceFontName);
        int style = traits.italic || IllustratorTextPaint.isItalicFont(sourceFontName) ? Font.ITALIC : Font.PLAIN;
        if (traits.weight >= 600) {
            style |= Font.BOLD;
        }
        Font font = new Font(family, style, 1).deriveFont((float) fontSize);
        GlyphVector glyphs = font.createGlyphVector(FONT_RENDER_CONTEXT, text);
        double[] advances = new double[Math.max(0, text.length() - 1)];
        int count = Math.min(advances.length, glyphs.getNumGlyphs());
        for (int i = 0; i < count; i++) {
            advances[i] = glyphs.getGlyphMetrics(i).getAdvanceX();
        }
        return advances;
    }

    private static String resolveFamily(String sourceFamily, FontTraits sourceTraits) {
        if (isAvailable(sourceFamily)) {
            return sourceFamily;
        }
        String scored = bestScoredFamily(sourceFamily, sourceTraits);
        if (scored != null) {
            return scored;
        }
        String lower = normalized(sourceFamily);
        if (lower.contains("gotham") || lower.contains("dinot") || lower.contains("abadi")
                || lower.contains("raleway") || lower.contains("helvetica") || lower.contains("arial")) {
            return firstAvailable("Arial", "Helvetica", "Liberation Sans", "DejaVu Sans", Font.SANS_SERIF);
        }
        if (lower.contains("times") || lower.contains("serif")) {
            return firstAvailable("Times New Roman", "Liberation Serif", "DejaVu Serif", Font.SERIF);
        }
        if (lower.contains("courier") || lower.contains("mono")) {
            return firstAvailable("Courier New", "Liberation Mono", "DejaVu Sans Mono", Font.MONOSPACED);
        }
        return firstAvailable("Arial", "Helvetica", "Liberation Sans", "DejaVu Sans", Font.SANS_SERIF);
    }

    private static String bestScoredFamily(String sourceFamily, FontTraits sourceTraits) {
        String sourceNormalized = normalized(sourceFamily);
        int bestScore = Integer.MIN_VALUE;
        String best = null;
        for (String candidate : AVAILABLE_DISPLAY_FAMILIES) {
            String candidateNormalized = normalized(candidate);
            if (candidateNormalized.isEmpty() || candidateNormalized.startsWith(".")) {
                continue;
            }
            FontTraits candidateTraits = FontTraits.from(candidate);
            int score = scoreCandidate(sourceNormalized, sourceTraits, candidateNormalized, candidateTraits);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore >= 40 ? best : null;
    }

    private static int scoreCandidate(
            String sourceNormalized,
            FontTraits sourceTraits,
            String candidateNormalized,
            FontTraits candidateTraits) {
        int score = 0;
        String sourceBase = sourceTraits.baseName;
        String candidateBase = candidateTraits.baseName;
        if (!sourceBase.isEmpty() && candidateBase.contains(sourceBase)) {
            score += 120;
        } else if (!candidateBase.isEmpty() && sourceBase.contains(candidateBase)) {
            score += 80;
        }
        if (sourceNormalized.contains(candidateNormalized) || candidateNormalized.contains(sourceNormalized)) {
            score += 50;
        }
        if (sourceTraits.sans == candidateTraits.sans) {
            score += 20;
        }
        if (sourceTraits.serif == candidateTraits.serif) {
            score += 12;
        }
        if (sourceTraits.mono == candidateTraits.mono) {
            score += 12;
        }
        score -= Math.abs(sourceTraits.weight - candidateTraits.weight) / 20;
        score -= Math.abs(sourceTraits.width - candidateTraits.width) / 10;
        if (sourceTraits.italic == candidateTraits.italic) {
            score += 15;
        } else {
            score -= 20;
        }
        if (sourceTraits.width <= 85 && candidateTraits.width <= 90) {
            score += 35;
        }
        if (sourceTraits.weight <= 300 && candidateTraits.weight <= 350) {
            score += 25;
        }
        if (sourceTraits.weight >= 600 && candidateTraits.weight >= 600) {
            score += 25;
        }
        if (candidateNormalized.contains("emoji") || candidateNormalized.contains("symbol")
                || candidateNormalized.contains("interface") || candidateNormalized.contains("pua")) {
            score -= 100;
        }
        return score;
    }

    private static String firstAvailable(String... families) {
        for (String family : families) {
            if (isAvailable(family)) {
                return family;
            }
        }
        return families[families.length - 1];
    }

    private static boolean isAvailable(String family) {
        return family != null && AVAILABLE_FAMILIES.contains(normalized(family));
    }

    private static Set<String> availableFamilies() {
        Set<String> names = new HashSet<>();
        try {
            for (String family : GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames(Locale.ENGLISH)) {
                names.add(normalized(family));
            }
        } catch (RuntimeException ex) {
            names.add(normalized(Font.SANS_SERIF));
            names.add(normalized(Font.SERIF));
            names.add(normalized(Font.MONOSPACED));
        }
        names.add(normalized(Font.SANS_SERIF));
        names.add(normalized(Font.SERIF));
        names.add(normalized(Font.MONOSPACED));
        return names;
    }

    private static Set<String> availableDisplayFamilies() {
        Set<String> names = new HashSet<>();
        try {
            for (String family : GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames(Locale.ENGLISH)) {
                names.add(family);
            }
        } catch (RuntimeException ex) {
            names.add(Font.SANS_SERIF);
            names.add(Font.SERIF);
            names.add(Font.MONOSPACED);
        }
        names.add(Font.SANS_SERIF);
        names.add(Font.SERIF);
        names.add(Font.MONOSPACED);
        return names;
    }

    static String sourceFamily(String fontName) {
        String name = fontName == null || fontName.isEmpty() ? "Helvetica" : fontName;
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        int subset = name.indexOf('+');
        if (subset >= 0 && subset + 1 < name.length()) {
            name = name.substring(subset + 1);
        }
        int star = name.indexOf('*');
        if (star >= 0) {
            name = name.substring(0, star);
        }
        return name;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    static final class FontChoice {
        private final String sourceFamily;
        private final String resolvedFamily;
        private final boolean substituted;
        private final FontTraits traits;

        FontChoice(String sourceFamily, String resolvedFamily, boolean substituted) {
            this(sourceFamily, resolvedFamily, substituted, FontTraits.from(sourceFamily));
        }

        FontChoice(String sourceFamily, String resolvedFamily, boolean substituted, FontTraits traits) {
            this.sourceFamily = sourceFamily;
            this.resolvedFamily = resolvedFamily;
            this.substituted = substituted;
            this.traits = traits;
        }

        String sourceFamily() {
            return sourceFamily;
        }

        String resolvedFamily() {
            return resolvedFamily;
        }

        boolean substituted() {
            return substituted;
        }

        FontTraits traits() {
            return traits;
        }
    }

    static final class FontTraits {
        private final int weight;
        private final int width;
        private final boolean italic;
        private final boolean serif;
        private final boolean sans;
        private final boolean mono;
        private final String baseName;

        private FontTraits(
                int weight,
                int width,
                boolean italic,
                boolean serif,
                boolean sans,
                boolean mono,
                String baseName) {
            this.weight = weight;
            this.width = width;
            this.italic = italic;
            this.serif = serif;
            this.sans = sans;
            this.mono = mono;
            this.baseName = baseName;
        }

        static FontTraits from(String fontName) {
            String name = sourceFamily(fontName);
            String lower = normalized(name);
            int weight = 400;
            if (lower.contains("thin")) {
                weight = 100;
            } else if (lower.contains("extralight") || lower.contains("ultralight")) {
                weight = 200;
            } else if (lower.contains("light")) {
                weight = 300;
            } else if (lower.contains("medium")) {
                weight = 500;
            } else if (lower.contains("semibold") || lower.contains("demibold")) {
                weight = 600;
            } else if (lower.contains("extrabold") || lower.contains("ultrabold")) {
                weight = 800;
            } else if (lower.contains("black") || lower.contains("heavy")) {
                weight = 900;
            } else if (lower.contains("bold")) {
                weight = 700;
            }

            int width = 100;
            if (lower.contains("xcondensed") || lower.contains("extracondensed")
                    || lower.contains("ultracondensed")) {
                width = 60;
            } else if (lower.contains("condensed") || lower.contains("narrow")
                    || lower.contains("xnarrow")) {
                width = 75;
            } else if (lower.contains("semicondensed")) {
                width = 87;
            } else if (lower.contains("expanded") || lower.contains("extended")) {
                width = 125;
            }

            boolean italic = lower.contains("italic") || lower.contains("oblique");
            boolean mono = lower.contains("mono") || lower.contains("courier") || lower.contains("code");
            boolean serif = lower.contains("serif") || lower.contains("times") || lower.contains("georgia")
                    || lower.contains("garamond");
            boolean sans = !serif && !mono;
            String base = lower
                    .replace("extrabold", "")
                    .replace("ultrabold", "")
                    .replace("semibold", "")
                    .replace("demibold", "")
                    .replace("extralight", "")
                    .replace("ultralight", "")
                    .replace("condensed", "")
                    .replace("semicondensed", "")
                    .replace("extracondensed", "")
                    .replace("ultracondensed", "")
                    .replace("xnarrow", "")
                    .replace("narrow", "")
                    .replace("expanded", "")
                    .replace("extended", "")
                    .replace("medium", "")
                    .replace("regular", "")
                    .replace("roman", "")
                    .replace("book", "")
                    .replace("bold", "")
                    .replace("black", "")
                    .replace("heavy", "")
                    .replace("light", "")
                    .replace("thin", "")
                    .replace("italic", "")
                    .replace("oblique", "");
            return new FontTraits(weight, width, italic, serif, sans, mono, base);
        }

        int weight() {
            return weight;
        }

        int width() {
            return width;
        }
    }
}
