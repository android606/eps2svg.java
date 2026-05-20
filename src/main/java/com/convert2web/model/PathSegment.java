package com.convert2web.model;

/**
 * One segment of a PostScript path in user space.
 */
public abstract class PathSegment {
    public enum Type {
        MOVE_TO,
        LINE_TO,
        CURVE_TO,
        CLOSE
    }

    public abstract Type getType();

    public static final class MoveTo extends PathSegment {
        private final double x;
        private final double y;

        public MoveTo(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        @Override
        public Type getType() {
            return Type.MOVE_TO;
        }
    }

    public static final class LineTo extends PathSegment {
        private final double x;
        private final double y;

        public LineTo(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        @Override
        public Type getType() {
            return Type.LINE_TO;
        }
    }

    public static final class CurveTo extends PathSegment {
        private final double x1;
        private final double y1;
        private final double x2;
        private final double y2;
        private final double x3;
        private final double y3;

        public CurveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.x3 = x3;
            this.y3 = y3;
        }

        public double getX1() {
            return x1;
        }

        public double getY1() {
            return y1;
        }

        public double getX2() {
            return x2;
        }

        public double getY2() {
            return y2;
        }

        public double getX3() {
            return x3;
        }

        public double getY3() {
            return y3;
        }

        @Override
        public Type getType() {
            return Type.CURVE_TO;
        }
    }

    public static final class Close extends PathSegment {
        @Override
        public Type getType() {
            return Type.CLOSE;
        }
    }
}
