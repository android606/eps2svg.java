package com.convert2web.model;

/**
 * Axis-aligned bounds of a {@link Path} in user space (after optional CTM).
 */
public final class PathBounds {
    private PathBounds() {
    }

    public static double[] transformedBounds(Path path, Matrix ctm) {
        BoundsAccumulator acc = new BoundsAccumulator();
        for (PathSegment segment : path.getSegments()) {
            switch (segment.getType()) {
                case MOVE_TO:
                    PathSegment.MoveTo move = (PathSegment.MoveTo) segment;
                    acc.extend(ctm, move.getX(), move.getY());
                    break;
                case LINE_TO:
                    PathSegment.LineTo line = (PathSegment.LineTo) segment;
                    acc.extend(ctm, line.getX(), line.getY());
                    break;
                case CURVE_TO:
                    PathSegment.CurveTo curve = (PathSegment.CurveTo) segment;
                    acc.extend(ctm, curve.getX1(), curve.getY1());
                    acc.extend(ctm, curve.getX2(), curve.getY2());
                    acc.extend(ctm, curve.getX3(), curve.getY3());
                    break;
                case CLOSE:
                    break;
                default:
                    break;
            }
        }
        return acc.toArray();
    }

    private static final class BoundsAccumulator {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;

        void extend(Matrix ctm, double x, double y) {
            double tx = ctm.transformX(x, y);
            double ty = ctm.transformY(x, y);
            minX = Math.min(minX, tx);
            minY = Math.min(minY, ty);
            maxX = Math.max(maxX, tx);
            maxY = Math.max(maxY, ty);
        }

        double[] toArray() {
            if (minX == Double.POSITIVE_INFINITY) {
                return new double[] {0, 0, 0, 0};
            }
            return new double[] {minX, minY, maxX, maxY};
        }
    }
}
