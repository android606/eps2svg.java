package com.convert2web.model;

import java.util.Objects;

/**
 * EPS bounding box in PostScript coordinates (llx, lly, urx, ury).
 */
public final class BoundingBox {
    private final double llx;
    private final double lly;
    private final double urx;
    private final double ury;

    public BoundingBox(double llx, double lly, double urx, double ury) {
        this.llx = llx;
        this.lly = lly;
        this.urx = urx;
        this.ury = ury;
    }

    public double getLlx() {
        return llx;
    }

    public double getLly() {
        return lly;
    }

    public double getUrx() {
        return urx;
    }

    public double getUry() {
        return ury;
    }

    public double getWidth() {
        return urx - llx;
    }

    public double getHeight() {
        return ury - lly;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BoundingBox)) {
            return false;
        }
        BoundingBox that = (BoundingBox) o;
        return Double.compare(that.llx, llx) == 0
                && Double.compare(that.lly, lly) == 0
                && Double.compare(that.urx, urx) == 0
                && Double.compare(that.ury, ury) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(llx, lly, urx, ury);
    }

    @Override
    public String toString() {
        return "BoundingBox{" + llx + "," + lly + "," + urx + "," + ury + "}";
    }
}
