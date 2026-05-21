package com.convert2web.render;

import com.convert2web.model.Matrix;
import com.convert2web.model.Path;
import com.convert2web.model.PathSegment;

/**
 * Encodes {@link Path} as an SVG path {@code d} attribute.
 */
public final class SvgPathEncoder {
    private SvgPathEncoder() {
    }

    public static String toPathData(Path path) {
        return toPathData(path, Matrix.identity());
    }

    public static String toPathData(Path path, Matrix matrix) {
        if (path.isEmpty()) {
            return "";
        }
        StringBuilder d = new StringBuilder();
        for (PathSegment segment : path.getSegments()) {
            appendSegment(d, segment, matrix);
        }
        return d.toString();
    }

    private static void appendSegment(StringBuilder d, PathSegment segment, Matrix matrix) {
        switch (segment.getType()) {
            case MOVE_TO:
                PathSegment.MoveTo move = (PathSegment.MoveTo) segment;
                d.append('M')
                        .append(format(matrix.transformX(move.getX(), move.getY())))
                        .append(' ')
                        .append(format(matrix.transformY(move.getX(), move.getY())));
                break;
            case LINE_TO:
                PathSegment.LineTo line = (PathSegment.LineTo) segment;
                d.append('L')
                        .append(format(matrix.transformX(line.getX(), line.getY())))
                        .append(' ')
                        .append(format(matrix.transformY(line.getX(), line.getY())));
                break;
            case CURVE_TO:
                PathSegment.CurveTo curve = (PathSegment.CurveTo) segment;
                d.append('C')
                        .append(format(matrix.transformX(curve.getX1(), curve.getY1()))).append(' ')
                        .append(format(matrix.transformY(curve.getX1(), curve.getY1()))).append(' ')
                        .append(format(matrix.transformX(curve.getX2(), curve.getY2()))).append(' ')
                        .append(format(matrix.transformY(curve.getX2(), curve.getY2()))).append(' ')
                        .append(format(matrix.transformX(curve.getX3(), curve.getY3()))).append(' ')
                        .append(format(matrix.transformY(curve.getX3(), curve.getY3())));
                break;
            case CLOSE:
                d.append('Z');
                break;
            default:
                break;
        }
    }

    public static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.US, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }
}
