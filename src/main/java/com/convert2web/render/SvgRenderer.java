package com.convert2web.render;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.Path;
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

    public String render(EpsDocument document) {
        BoundingBox bbox = document.getBoundingBox();
        double llx = bbox.getLlx();
        double lly = bbox.getLly();
        double width = bbox.getWidth();
        double height = bbox.getHeight();
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

        for (GraphicsCommand command : document.getCommands()) {
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
        return "";
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
