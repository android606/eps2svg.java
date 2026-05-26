package com.convert2web.image;

import com.convert2web.model.Matrix;

/**
 * EPS-space bounds for an AGM tile using the same corner mapping as {@link com.convert2web.render.SvgRenderer}.
 */
final class AgmImageBounds {
    private AgmImageBounds() {
    }

    static double[] epsBounds(AgmEmbeddedImage image) {
        return epsBounds(image.getCtm(), image.getWidth(), image.getHeight());
    }

    static double[] epsBounds(Matrix display, int width, int height) {
        double[][] pixelCorners = {{0, 0}, {width, 0}, {0, height}, {width, height}};
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (double[] corner : pixelCorners) {
            double[] point = imagePixelToEps(display, width, height, corner[0], corner[1]);
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
            minY = Math.min(minY, point[1]);
            maxY = Math.max(maxY, point[1]);
        }
        return new double[] {minX, minY, maxX, maxY};
    }

    static double displayedArea(AgmEmbeddedImage image) {
        double[] bounds = epsBounds(image);
        return Math.max(0, bounds[2] - bounds[0]) * Math.max(0, bounds[3] - bounds[1]);
    }

    private static double[] imagePixelToEps(Matrix display, int width, int height, double px, double py) {
        double u = px / width;
        double v = 1.0 - py / height;
        return new double[] {display.transformX(u, v), display.transformY(u, v)};
    }
}
