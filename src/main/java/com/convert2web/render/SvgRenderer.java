package com.convert2web.render;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.Path;
import com.convert2web.model.PathBounds;
import com.convert2web.model.StrokeStyle;
import com.convert2web.model.WindingRule;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Renders {@link EpsDocument} to SVG XML without depending on Batik.
 */
public final class SvgRenderer {
    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String RASTER_PLACEHOLDER_PATTERN_ID = "raster-placeholder-hatch";

    public String render(EpsDocument document) {
        BoundingBox viewport = visibleViewport(document);
        double llx = viewport.getLlx();
        double lly = viewport.getLly();
        double width = viewport.getWidth();
        double height = viewport.getHeight();
        if (width <= 0 || height <= 0) {
            width = 100;
            height = 100;
            llx = 0;
            lly = 0;
        }

        String pageTransform = pageTransform(document, llx, lly, height);

        StringBuilder defs = new StringBuilder();
        StringBuilder body = new StringBuilder();
        Deque<String> clipIds = new ArrayDeque<>();
        int clipCounter = 0;
        boolean rasterPlaceholderPattern = false;

        for (GraphicsCommand command : document.getCommands()) {
            if (command instanceof GraphicsCommand.RasterPlaceholder) {
                rasterPlaceholderPattern = true;
            }
            if (command instanceof GraphicsCommand.Clip) {
                GraphicsCommand.Clip clip = (GraphicsCommand.Clip) command;
                String clipId = "clip" + clipCounter++;
                appendClipPath(defs, clipId, clip);
                clipIds.push(clipId);
                body.append("<g clip-path=\"url(#").append(clipId).append(")\">\n");
                continue;
            }
            body.append(renderPaintCommand(command));
        }

        if (rasterPlaceholderPattern) {
            appendRasterPlaceholderPatternDef(defs);
        }

        while (!clipIds.isEmpty()) {
            body.append("</g>\n");
            clipIds.pop();
        }

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"").append(SVG_NS).append("\"");
        svg.append(" width=\"").append(SvgPathEncoder.format(width)).append("\"");
        svg.append(" height=\"").append(SvgPathEncoder.format(height)).append("\"");
        // viewBox is 0..size; page <g> maps EPS coords (llx,lly..urx,ury) into that space.
        svg.append(" viewBox=\"0 0 ")
                .append(SvgPathEncoder.format(width)).append(' ')
                .append(SvgPathEncoder.format(height)).append("\">\n");
        if (defs.length() > 0) {
            svg.append("<defs>\n").append(defs).append("</defs>\n");
        }
        if (pageTransform.isEmpty()) {
            svg.append(body);
        } else {
            svg.append("<g transform=\"").append(escapeAttr(pageTransform)).append("\">\n");
            svg.append(body);
            svg.append("</g>\n");
        }
        svg.append("</svg>\n");
        return svg.toString();
    }

    public void write(EpsDocument document, java.nio.file.Path outputFile) throws IOException {
        Files.writeString(outputFile, render(document), StandardCharsets.UTF_8);
    }

    public void write(EpsDocument document, Writer writer) throws IOException {
        writer.write(render(document));
    }

    private static void appendClipPath(StringBuilder defs, String id, GraphicsCommand.Clip clip) {
        Matrix ctm = clip.getCtm();
        String d = ctm.equals(Matrix.identity())
                ? SvgPathEncoder.toPathData(clip.getPath())
                : SvgPathEncoder.toPathData(clip.getPath(), ctm);
        if (d.isEmpty()) {
            return;
        }
        defs.append("<clipPath id=\"").append(id).append("\">\n");
        defs.append("  <path d=\"").append(escapeAttr(d)).append("\"");
        defs.append(" clip-rule=\"").append(windingRuleAttr(clip.getWindingRule())).append("\"/>\n");
        defs.append("</clipPath>\n");
    }

    private static String renderPaintCommand(GraphicsCommand command) {
        Matrix ctm = command.getCtm();
        String transform = matrixTransform(ctm);
        if (command instanceof GraphicsCommand.Fill) {
            GraphicsCommand.Fill fill = (GraphicsCommand.Fill) command;
            return pathElement(fill.getPath(), transform, paintAttrs(fill.getFill(), true)
                    + " fill-rule=\"" + windingRuleAttr(fill.getWindingRule()) + "\"");
        }
        if (command instanceof GraphicsCommand.Stroke) {
            GraphicsCommand.Stroke stroke = (GraphicsCommand.Stroke) command;
            return pathElement(stroke.getPath(), transform, paintAttrs(stroke.getStrokeColor(), false)
                    + strokeAttrs(stroke.getStrokeStyle()));
        }
        if (command instanceof GraphicsCommand.RasterPlaceholder) {
            GraphicsCommand.RasterPlaceholder placeholder = (GraphicsCommand.RasterPlaceholder) command;
            return rasterPlaceholderElement(placeholder, transform);
        }
        return "";
    }

    private static void appendRasterPlaceholderPatternDef(StringBuilder defs) {
        defs.append("<pattern id=\"").append(RASTER_PLACEHOLDER_PATTERN_ID).append("\"");
        defs.append(" patternUnits=\"userSpaceOnUse\" width=\"8\" height=\"8\"");
        defs.append(" patternTransform=\"rotate(45)\">\n");
        defs.append("  <line x1=\"0\" y1=\"0\" x2=\"0\" y2=\"8\" stroke=\"#999\" stroke-width=\"1\"/>\n");
        defs.append("</pattern>\n");
    }

    private static String rasterPlaceholderElement(
            GraphicsCommand.RasterPlaceholder placeholder, String transform) {
        BoundingBox region = placeholder.getRegion();
        double x = region.getLlx();
        double y = region.getLly();
        double w = region.getWidth();
        double h = region.getHeight();
        StringBuilder element = new StringBuilder();
        element.append("<g");
        if (!transform.isEmpty()) {
            element.append(" transform=\"").append(escapeAttr(transform)).append('"');
        }
        element.append(">\n  <rect x=\"").append(SvgPathEncoder.format(x)).append('"');
        element.append(" y=\"").append(SvgPathEncoder.format(y)).append('"');
        element.append(" width=\"").append(SvgPathEncoder.format(w)).append('"');
        element.append(" height=\"").append(SvgPathEncoder.format(h)).append('"');
        element.append(" fill=\"url(#").append(RASTER_PLACEHOLDER_PATTERN_ID).append(")\"");
        element.append(" fill-opacity=\"0.35\"");
        element.append(" stroke=\"#666\" stroke-width=\"0.75\" stroke-dasharray=\"4 3\"");
        element.append(">\n    <title>Raster region (placeholder)</title>\n  </rect>\n</g>\n");
        return element.toString();
    }

    private static BoundingBox visibleViewport(EpsDocument document) {
        Bounds preferred = new Bounds();
        Bounds fallbackPaint = new Bounds();
        Bounds page = Bounds.from(document.getBoundingBox());
        Bounds activeClip = page.copy();
        for (GraphicsCommand command : document.getCommands()) {
            if (command instanceof GraphicsCommand.Clip) {
                GraphicsCommand.Clip clip = (GraphicsCommand.Clip) command;
                activeClip.intersect(Bounds.from(
                        PathBounds.transformedBounds(clip.getPath(), command.getCtm())));
            } else if (command instanceof GraphicsCommand.Fill) {
                GraphicsCommand.Fill fill = (GraphicsCommand.Fill) command;
                double[] bounds = PathBounds.transformedBounds(fill.getPath(), command.getCtm());
                bounds = page.clamp(activeClip.clamp(bounds));
                fallbackPaint.include(bounds);
                if (!isWhiteOrNone(fill.getFill())) {
                    preferred.include(bounds);
                }
            } else if (command instanceof GraphicsCommand.Stroke) {
                GraphicsCommand.Stroke stroke = (GraphicsCommand.Stroke) command;
                double[] bounds = PathBounds.transformedBounds(stroke.getPath(), command.getCtm());
                double pad = Math.max(0.5, stroke.getStrokeStyle().getLineWidth() / 2.0);
                bounds = page.clamp(activeClip.clamp(bounds, pad));
                fallbackPaint.include(bounds);
                preferred.include(bounds);
            } else if (command instanceof GraphicsCommand.RasterPlaceholder) {
                GraphicsCommand.RasterPlaceholder placeholder = (GraphicsCommand.RasterPlaceholder) command;
                BoundingBox region = placeholder.getRegion();
                double[] bounds = new double[] {
                        region.getLlx(), region.getLly(), region.getUrx(), region.getUry()
                };
                bounds = page.clamp(activeClip.clamp(bounds));
                fallbackPaint.include(bounds);
                preferred.include(bounds);
            }
        }
        if (!preferred.isEmpty()) {
            return preferred.toBoundingBox();
        }
        if (!fallbackPaint.isEmpty()) {
            return fallbackPaint.toBoundingBox();
        }
        return document.getBoundingBox();
    }

    private static boolean isWhiteOrNone(PaintStyle paint) {
        switch (paint.getKind()) {
            case NONE:
                return true;
            case GRAY:
                return paint.getV0() >= 0.99;
            case RGB:
                return paint.getV0() >= 0.99 && paint.getV1() >= 0.99 && paint.getV2() >= 0.99;
            case CMYK:
                return paint.getV0() <= 0.01 && paint.getV1() <= 0.01
                        && paint.getV2() <= 0.01 && paint.getV3() <= 0.01;
            default:
                return false;
        }
    }

    private static final class Bounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;

        void include(double[] bounds) {
            include(bounds, 0);
        }

        void include(double[] bounds, double padding) {
            if (bounds == null || bounds.length < 4 || bounds[2] <= bounds[0] || bounds[3] <= bounds[1]) {
                return;
            }
            minX = Math.min(minX, bounds[0] - padding);
            minY = Math.min(minY, bounds[1] - padding);
            maxX = Math.max(maxX, bounds[2] + padding);
            maxY = Math.max(maxY, bounds[3] + padding);
        }

        boolean isEmpty() {
            return minX == Double.POSITIVE_INFINITY;
        }

        BoundingBox toBoundingBox() {
            return new BoundingBox(minX, minY, maxX, maxY);
        }

        static Bounds from(BoundingBox bbox) {
            Bounds bounds = new Bounds();
            if (bbox != null) {
                bounds.include(new double[] {bbox.getLlx(), bbox.getLly(), bbox.getUrx(), bbox.getUry()});
            }
            return bounds;
        }

        static Bounds from(double[] values) {
            Bounds bounds = new Bounds();
            bounds.include(values);
            return bounds;
        }

        Bounds copy() {
            Bounds copy = new Bounds();
            copy.minX = minX;
            copy.minY = minY;
            copy.maxX = maxX;
            copy.maxY = maxY;
            return copy;
        }

        void intersect(Bounds other) {
            if (isEmpty() || other.isEmpty()) {
                minX = Double.POSITIVE_INFINITY;
                minY = Double.POSITIVE_INFINITY;
                maxX = Double.NEGATIVE_INFINITY;
                maxY = Double.NEGATIVE_INFINITY;
                return;
            }
            minX = Math.max(minX, other.minX);
            minY = Math.max(minY, other.minY);
            maxX = Math.min(maxX, other.maxX);
            maxY = Math.min(maxY, other.maxY);
            if (maxX <= minX || maxY <= minY) {
                minX = Double.POSITIVE_INFINITY;
                minY = Double.POSITIVE_INFINITY;
                maxX = Double.NEGATIVE_INFINITY;
                maxY = Double.NEGATIVE_INFINITY;
            }
        }

        double[] clamp(double[] values) {
            return clamp(values, 0);
        }

        double[] clamp(double[] values, double padding) {
            if (isEmpty() || values == null || values.length < 4) {
                return values;
            }
            double x0 = Math.max(minX, values[0] - padding);
            double y0 = Math.max(minY, values[1] - padding);
            double x1 = Math.min(maxX, values[2] + padding);
            double y1 = Math.min(maxY, values[3] + padding);
            if (x1 <= x0 || y1 <= y0) {
                return new double[] {0, 0, 0, 0};
            }
            return new double[] {x0, y0, x1, y1};
        }
    }

    private static String pathElement(Path path, String transform, String paintAttributes) {
        String d = SvgPathEncoder.toPathData(path);
        if (d.isEmpty()) {
            return "";
        }
        StringBuilder element = new StringBuilder();
        element.append("<g");
        if (!transform.isEmpty()) {
            element.append(" transform=\"").append(escapeAttr(transform)).append('"');
        }
        element.append(">\n  <path d=\"").append(escapeAttr(d)).append('"');
        element.append(paintAttributes);
        element.append("/>\n</g>\n");
        return element.toString();
    }

    private static String pageTransform(EpsDocument document, double llx, double lly, double height) {
        if ("false".equals(document.metadata().get("svg.pageYFlip"))) {
            return "translate(" + SvgPathEncoder.format(-llx) + "," + SvgPathEncoder.format(-lly) + ")";
        }
        return "translate(0," + SvgPathEncoder.format(height) + ") scale(1,-1) translate("
                + SvgPathEncoder.format(-llx) + "," + SvgPathEncoder.format(-lly) + ")";
    }

    private static String matrixTransform(Matrix matrix) {
        if (matrix.equals(Matrix.identity())) {
            return "";
        }
        return "matrix("
                + SvgPathEncoder.format(matrix.getA()) + ' '
                + SvgPathEncoder.format(matrix.getB()) + ' '
                + SvgPathEncoder.format(matrix.getC()) + ' '
                + SvgPathEncoder.format(matrix.getD()) + ' '
                + SvgPathEncoder.format(matrix.getE()) + ' '
                + SvgPathEncoder.format(matrix.getF()) + ')';
    }

    private static String paintAttrs(PaintStyle paint, boolean fill) {
        String attr = fill ? "fill" : "stroke";
        if (paint.getKind() == PaintStyle.Kind.NONE) {
            return " " + attr + "=\"none\"";
        }
        if (paint.getKind() == PaintStyle.Kind.GRAY) {
            int pct = (int) Math.round(paint.getV0() * 100);
            return " " + attr + "=\"rgb(" + pct + ',' + pct + ',' + pct + ")\"";
        }
        if (paint.getKind() == PaintStyle.Kind.RGB) {
            return " " + attr + "=\"rgb("
                    + toRgbChannel(paint.getV0()) + ','
                    + toRgbChannel(paint.getV1()) + ','
                    + toRgbChannel(paint.getV2()) + ")\"";
        }
        if (paint.getKind() == PaintStyle.Kind.CMYK) {
            double[] rgb = cmykToRgb(paint.getV0(), paint.getV1(), paint.getV2(), paint.getV3());
            return " " + attr + "=\"rgb("
                    + toRgbChannel(rgb[0]) + ','
                    + toRgbChannel(rgb[1]) + ','
                    + toRgbChannel(rgb[2]) + ")\"";
        }
        return " " + attr + "=\"black\"";
    }

    private static String strokeAttrs(StrokeStyle style) {
        StringBuilder attrs = new StringBuilder();
        attrs.append(" fill=\"none\"");
        attrs.append(" stroke-width=\"").append(SvgPathEncoder.format(style.getLineWidth())).append('"');
        attrs.append(" stroke-linecap=\"").append(lineCap(style.getLineCap())).append('"');
        attrs.append(" stroke-linejoin=\"").append(lineJoin(style.getLineJoin())).append('"');
        attrs.append(" stroke-miterlimit=\"").append(SvgPathEncoder.format(style.getMiterLimit())).append('"');
        if (style.getDashPattern().length > 0) {
            attrs.append(" stroke-dasharray=\"");
            double[] dash = style.getDashPattern();
            for (int i = 0; i < dash.length; i++) {
                if (i > 0) {
                    attrs.append(' ');
                }
                attrs.append(SvgPathEncoder.format(dash[i]));
            }
            attrs.append('"');
            attrs.append(" stroke-dashoffset=\"").append(SvgPathEncoder.format(style.getDashOffset())).append('"');
        }
        return attrs.toString();
    }

    private static String windingRuleAttr(WindingRule rule) {
        return rule == WindingRule.EVEN_ODD ? "evenodd" : "nonzero";
    }

    private static String lineCap(int cap) {
        switch (cap) {
            case 1:
                return "round";
            case 2:
                return "square";
            default:
                return "butt";
        }
    }

    private static String lineJoin(int join) {
        switch (join) {
            case 1:
                return "round";
            case 2:
                return "bevel";
            default:
                return "miter";
        }
    }

    private static int toRgbChannel(double unit) {
        return Math.max(0, Math.min(255, (int) Math.round(unit * 255)));
    }

    private static double[] cmykToRgb(double c, double m, double y, double k) {
        return new double[] {
                (1 - c) * (1 - k),
                (1 - m) * (1 - k),
                (1 - y) * (1 - k)
        };
    }

    private static String escapeAttr(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
