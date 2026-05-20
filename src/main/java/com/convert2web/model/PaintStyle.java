package com.convert2web.model;

import java.util.Objects;

/**
 * Fill or stroke color in renderer-neutral form.
 */
public final class PaintStyle {
    public enum Kind {
        NONE,
        GRAY,
        RGB,
        CMYK
    }

    private final Kind kind;
    private final double v0;
    private final double v1;
    private final double v2;
    private final double v3;

    private PaintStyle(Kind kind, double v0, double v1, double v2, double v3) {
        this.kind = kind;
        this.v0 = v0;
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }

    public static PaintStyle none() {
        return new PaintStyle(Kind.NONE, 0, 0, 0, 0);
    }

    public static PaintStyle gray(double gray) {
        return new PaintStyle(Kind.GRAY, gray, 0, 0, 0);
    }

    public static PaintStyle rgb(double r, double g, double b) {
        return new PaintStyle(Kind.RGB, r, g, b, 0);
    }

    public static PaintStyle cmyk(double c, double m, double y, double k) {
        return new PaintStyle(Kind.CMYK, c, m, y, k);
    }

    public Kind getKind() {
        return kind;
    }

    public double getV0() {
        return v0;
    }

    public double getV1() {
        return v1;
    }

    public double getV2() {
        return v2;
    }

    public double getV3() {
        return v3;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PaintStyle)) {
            return false;
        }
        PaintStyle that = (PaintStyle) o;
        return kind == that.kind
                && Double.compare(that.v0, v0) == 0
                && Double.compare(that.v1, v1) == 0
                && Double.compare(that.v2, v2) == 0
                && Double.compare(that.v3, v3) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, v0, v1, v2, v3);
    }
}
