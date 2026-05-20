package com.convert2web.model;

import java.util.Arrays;
import java.util.Objects;

public final class StrokeStyle {
    private final double lineWidth;
    private final int lineCap;
    private final int lineJoin;
    private final double miterLimit;
    private final double[] dashPattern;
    private final double dashOffset;

    public StrokeStyle(
            double lineWidth,
            int lineCap,
            int lineJoin,
            double miterLimit,
            double[] dashPattern,
            double dashOffset) {
        this.lineWidth = lineWidth;
        this.lineCap = lineCap;
        this.lineJoin = lineJoin;
        this.miterLimit = miterLimit;
        this.dashPattern = dashPattern == null ? new double[0] : dashPattern.clone();
        this.dashOffset = dashOffset;
    }

    public static StrokeStyle defaults() {
        return new StrokeStyle(1.0, 0, 0, 10.0, new double[0], 0.0);
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public int getLineCap() {
        return lineCap;
    }

    public int getLineJoin() {
        return lineJoin;
    }

    public double getMiterLimit() {
        return miterLimit;
    }

    public double[] getDashPattern() {
        return dashPattern.clone();
    }

    public double getDashOffset() {
        return dashOffset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StrokeStyle)) {
            return false;
        }
        StrokeStyle that = (StrokeStyle) o;
        return Double.compare(that.lineWidth, lineWidth) == 0
                && lineCap == that.lineCap
                && lineJoin == that.lineJoin
                && Double.compare(that.miterLimit, miterLimit) == 0
                && Double.compare(that.dashOffset, dashOffset) == 0
                && Arrays.equals(dashPattern, that.dashPattern);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(lineWidth, lineCap, lineJoin, miterLimit, dashOffset);
        result = 31 * result + Arrays.hashCode(dashPattern);
        return result;
    }
}
