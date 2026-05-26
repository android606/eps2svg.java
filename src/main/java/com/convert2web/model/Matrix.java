package com.convert2web.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * PostScript transformation matrix [a b c d e f].
 */
public final class Matrix {
    private final double a;
    private final double b;
    private final double c;
    private final double d;
    private final double e;
    private final double f;

    public Matrix(double a, double b, double c, double d, double e, double f) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
    }

    public static Matrix identity() {
        return new Matrix(1, 0, 0, 1, 0, 0);
    }

    /** PostScript {@code concat}: replaces this with this * other. */
    public Matrix postConcat(Matrix other) {
        return new Matrix(
                a * other.a + c * other.b,
                b * other.a + d * other.b,
                a * other.c + c * other.d,
                b * other.c + d * other.d,
                a * other.e + c * other.f + e,
                b * other.e + d * other.f + f);
    }

    public double transformX(double x, double y) {
        return a * x + c * y + e;
    }

    public double transformY(double x, double y) {
        return b * x + d * y + f;
    }

    public Matrix invert() {
        double det = a * d - b * c;
        if (det == 0) {
            throw new IllegalArgumentException("singular matrix");
        }
        double invDet = 1.0 / det;
        double na = d * invDet;
        double nb = -b * invDet;
        double nc = -c * invDet;
        double nd = a * invDet;
        double ne = -(na * e + nc * f);
        double nf = -(nb * e + nd * f);
        return new Matrix(na, nb, nc, nd, ne, nf);
    }

    public double getA() {
        return a;
    }

    public double getB() {
        return b;
    }

    public double getC() {
        return c;
    }

    public double getD() {
        return d;
    }

    public double getE() {
        return e;
    }

    public double getF() {
        return f;
    }

    public double[] toArray() {
        return new double[] {a, b, c, d, e, f};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Matrix)) {
            return false;
        }
        Matrix matrix = (Matrix) o;
        return Double.compare(matrix.a, a) == 0
                && Double.compare(matrix.b, b) == 0
                && Double.compare(matrix.c, c) == 0
                && Double.compare(matrix.d, d) == 0
                && Double.compare(matrix.e, e) == 0
                && Double.compare(matrix.f, f) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, c, d, e, f);
    }

    @Override
    public String toString() {
        return "Matrix" + Arrays.toString(toArray());
    }
}
