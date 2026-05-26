package com.convert2web.render;

import com.convert2web.model.Length;

/**
 * Computes root SVG {@code width}/{@code height} from viewBox size and {@link SvgRenderOptions}.
 * ViewBox dimensions are unchanged; only display attributes are scaled proportionally.
 */
final class SvgDisplaySize {
    private final String widthAttribute;
    private final String heightAttribute;

    private SvgDisplaySize(String widthAttribute, String heightAttribute) {
        this.widthAttribute = widthAttribute;
        this.heightAttribute = heightAttribute;
    }

    String widthAttribute() {
        return widthAttribute;
    }

    String heightAttribute() {
        return heightAttribute;
    }

    static SvgDisplaySize fromViewBox(double viewBoxWidth, double viewBoxHeight, SvgRenderOptions options) {
        if (viewBoxWidth <= 0 || viewBoxHeight <= 0) {
            viewBoxWidth = 100;
            viewBoxHeight = 100;
        }
        if (!options.hasDisplayLimits()) {
            return bare(viewBoxWidth, viewBoxHeight);
        }

        Length minW = options.minWidth().orElse(null);
        Length minH = options.minHeight().orElse(null);
        Length maxW = options.maxWidth().orElse(null);
        Length maxH = options.maxHeight().orElse(null);

        minW = conflictDropMin(minW, maxW, viewBoxWidth);
        minH = conflictDropMin(minH, maxH, viewBoxHeight);

        double w = viewBoxWidth;
        double h = viewBoxHeight;

        double maxScale = maxDownScale(w, h, maxW, maxH, viewBoxWidth, viewBoxHeight);
        if (maxScale < 1.0) {
            w *= maxScale;
            h *= maxScale;
        }

        double minScale = minUpScale(w, h, minW, minH, viewBoxWidth, viewBoxHeight);
        if (minScale > 1.0) {
            w *= minScale;
            h *= minScale;
        }

        return bare(w, h);
    }

    /** When min limit exceeds max limit in pixels, max takes precedence (drop min). */
    private static Length conflictDropMin(Length min, Length max, double viewBoxReference) {
        if (min == null || max == null) {
            return min;
        }
        if (min.toPixels(viewBoxReference) > max.toPixels(viewBoxReference)) {
            return null;
        }
        return min;
    }

    private static double maxDownScale(
            double w, double h, Length maxW, Length maxH, double refW, double refH) {
        double scale = 1.0;
        if (maxW != null) {
            double limit = maxW.toPixels(refW);
            if (limit > 0 && w > limit) {
                scale = Math.min(scale, limit / w);
            }
        }
        if (maxH != null) {
            double limit = maxH.toPixels(refH);
            if (limit > 0 && h > limit) {
                scale = Math.min(scale, limit / h);
            }
        }
        return scale;
    }

    private static double minUpScale(
            double w, double h, Length minW, Length minH, double refW, double refH) {
        double scale = 1.0;
        if (minW != null) {
            double limit = minW.toPixels(refW);
            if (limit > 0 && w < limit) {
                scale = Math.max(scale, limit / w);
            }
        }
        if (minH != null) {
            double limit = minH.toPixels(refH);
            if (limit > 0 && h < limit) {
                scale = Math.max(scale, limit / h);
            }
        }
        return scale;
    }

    private static SvgDisplaySize bare(double w, double h) {
        return new SvgDisplaySize(Length.formatPixels(w, Length.Unit.PX, w),
                Length.formatPixels(h, Length.Unit.PX, h));
    }
}
