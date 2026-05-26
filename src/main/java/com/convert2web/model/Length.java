package com.convert2web.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CSS/SVG absolute or percentage length for layout and sizing.
 * Percent values resolve against a caller-supplied reference (e.g. viewBox width).
 */
public final class Length {
    private static final Pattern LENGTH_PATTERN = Pattern.compile(
            "^([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*([a-z%]*)$",
            Pattern.CASE_INSENSITIVE);
    private static final double PX_PER_IN = 96.0;
    private static final double PX_PER_PT = PX_PER_IN / 72.0;
    private static final double PX_PER_PC = PX_PER_PT * 12.0;
    private static final double PX_PER_CM = PX_PER_IN / 2.54;
    private static final double PX_PER_MM = PX_PER_IN / 25.4;
    private static final double PX_PER_EM = 16.0;
    private static final double PX_PER_EX = 8.0;

    public enum Unit {
        /** Unitless or explicit pixels (SVG user units / CSS px at 96 dpi). */
        PX(""),
        PT("pt"),
        PC("pc"),
        IN("in"),
        CM("cm"),
        MM("mm"),
        EM("em"),
        EX("ex"),
        PERCENT("%");

        private final String suffix;

        Unit(String suffix) {
            this.suffix = suffix;
        }

        public String suffix() {
            return suffix;
        }

        static Unit fromToken(String token) {
            if (token == null || token.isEmpty()) {
                return PX;
            }
            switch (token.toLowerCase(Locale.ROOT)) {
                case "px":
                    return PX;
                case "pt":
                    return PT;
                case "pc":
                    return PC;
                case "in":
                    return IN;
                case "cm":
                    return CM;
                case "mm":
                    return MM;
                case "em":
                    return EM;
                case "ex":
                    return EX;
                case "%":
                    return PERCENT;
                default:
                    throw new IllegalArgumentException("Unknown length unit: " + token);
            }
        }
    }

    private final double value;
    private final Unit unit;

    public Length(double value, Unit unit) {
        this.value = value;
        this.unit = unit;
    }

    public double value() {
        return value;
    }

    public Unit unit() {
        return unit;
    }

    public static Length parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Length is null");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Length is empty");
        }
        Matcher matcher = LENGTH_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid length: " + text);
        }
        double value = Double.parseDouble(matcher.group(1));
        Unit unit = Unit.fromToken(matcher.group(2));
        return new Length(value, unit);
    }

    public static Length pixels(double px) {
        return new Length(px, Unit.PX);
    }

    /**
     * @param referencePx 100% size for {@link Unit#PERCENT} (e.g. viewBox width for width limits)
     */
    public double toPixels(double referencePx) {
        switch (unit) {
            case PX:
                return value;
            case PT:
                return value * PX_PER_PT;
            case PC:
                return value * PX_PER_PC;
            case IN:
                return value * PX_PER_IN;
            case CM:
                return value * PX_PER_CM;
            case MM:
                return value * PX_PER_MM;
            case EM:
                return value * PX_PER_EM;
            case EX:
                return value * PX_PER_EX;
            case PERCENT:
                return value * 0.01 * referencePx;
            default:
                throw new IllegalStateException("Unknown unit: " + unit);
        }
    }

    /**
     * Formats this length for an SVG/CSS attribute (e.g. {@code 50}, {@code 8.5in}).
     */
    public String format() {
        if (unit == Unit.PX) {
            return formatNumber(value);
        }
        return formatNumber(value) + unit.suffix;
    }

    /**
     * Formats a pixel value, optionally using the given unit.
     */
    public static String formatPixels(double px, Unit unit, double referencePx) {
        if (unit == Unit.PX) {
            return formatNumber(px);
        }
        if (unit == Unit.PERCENT) {
            if (referencePx <= 0) {
                return formatNumber(px);
            }
            return formatNumber(px * 100.0 / referencePx) + unit.suffix;
        }
        double factor = pixelsPerUnit(unit);
        return formatNumber(px / factor) + unit.suffix;
    }

    private static double pixelsPerUnit(Unit unit) {
        switch (unit) {
            case PX:
                return 1.0;
            case PT:
                return PX_PER_PT;
            case PC:
                return PX_PER_PC;
            case IN:
                return PX_PER_IN;
            case CM:
                return PX_PER_CM;
            case MM:
                return PX_PER_MM;
            case EM:
                return PX_PER_EM;
            case EX:
                return PX_PER_EX;
            case PERCENT:
            default:
                throw new IllegalArgumentException("Use formatPixels(px, PERCENT, reference)");
        }
    }

    private static String formatNumber(double n) {
        if (Math.abs(n - Math.rint(n)) < 1e-6) {
            return String.valueOf((long) Math.rint(n));
        }
        String s = String.format(Locale.ROOT, "%.4f", n);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && s.charAt(end - 1) == '.') {
            end--;
        }
        return s.substring(0, end);
    }
}
