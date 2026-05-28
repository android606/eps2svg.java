package com.convert2web.render;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.Path;
import com.convert2web.model.PathBounds;
import com.convert2web.model.SourceSpan;
import com.convert2web.model.StrokeStyle;
import com.convert2web.model.WindingRule;
import com.convert2web.ps.IllustratorTextPaint;

import java.util.Optional;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders {@link EpsDocument} to SVG XML without depending on Batik.
 */
public final class SvgRenderer {
    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String RASTER_PLACEHOLDER_PATTERN_ID = "raster-placeholder-hatch";

    private static final class ClipFrame {
        private final String id;
        private final SourceSpan sourceSpan;
        private boolean opened;

        private ClipFrame(String id, SourceSpan sourceSpan) {
            this.id = id;
            this.sourceSpan = sourceSpan;
        }
    }

    private static final class ElementIds {
        private static final String ID_PREFIX = "e";
        private int next = 1;

        private String next() {
            return ID_PREFIX + (next++);
        }
    }

    private final SvgRenderOptions options;

    public SvgRenderer() {
        this(SvgRenderOptions.none());
    }

    public SvgRenderer(SvgRenderOptions options) {
        this.options = options == null ? SvgRenderOptions.none() : options;
    }

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
        boolean deviceYDown = "false".equals(document.metadata().get("svg.pageYFlip"));
        double pageUry = document.getBoundingBox().getUry();

        StringBuilder defs = new StringBuilder();
        StringBuilder body = new StringBuilder();
        Map<String, SvgFontMetrics.FontChoice> fontReport = collectFontReport(document);
        Deque<ClipFrame> clipFrames = new ArrayDeque<>();
        Deque<String> layerGroupIds = new ArrayDeque<>();
        ElementIds elementIds = new ElementIds();
        String rootId = elementIds.next();
        boolean rasterPlaceholderPattern = false;

        for (GraphicsCommand command : document.getCommands()) {
            if (command instanceof GraphicsCommand.RasterPlaceholder) {
                rasterPlaceholderPattern = true;
            }
            if (command instanceof GraphicsCommand.BeginLayerGroup) {
                GraphicsCommand.BeginLayerGroup group = (GraphicsCommand.BeginLayerGroup) command;
                String layerId = sanitizeLayerId(group.getLayerId());
                layerGroupIds.push(layerId);
                appendSourceTracePrologue(body, group, "layer-begin", options);
                body.append("<g id=\"").append(escapeAttr(elementIds.next())).append("\"");
                body.append(sourceTraceAttrs(group, options));
                body.append(" data-layer-id=\"").append(escapeAttr(layerId)).append("\">\n");
                continue;
            }
            if (command instanceof GraphicsCommand.EndLayerGroup) {
                if (!layerGroupIds.isEmpty()) {
                    appendSourceTracePrologue(body, command, "layer-end", options);
                    body.append("</g>\n");
                    layerGroupIds.pop();
                }
                continue;
            }
            if (command instanceof GraphicsCommand.Clip) {
                GraphicsCommand.Clip clip = (GraphicsCommand.Clip) command;
                String clipId = elementIds.next();
                appendClipPath(defs, clipId, clip, elementIds, options);
                clipFrames.push(new ClipFrame(clipId, clip.getSourceSpan().orElse(null)));
                continue;
            }
            if (command instanceof GraphicsCommand.PopClip) {
                if (!clipFrames.isEmpty()) {
                    ClipFrame frame = clipFrames.pop();
                    if (frame.opened) {
                        appendSourceTracePrologue(body, command, "pop-clip", options);
                        body.append("</g>\n");
                    }
                }
                continue;
            }
            if (command instanceof GraphicsCommand.EmbeddedImage) {
                GraphicsCommand.EmbeddedImage embeddedImage =
                        (GraphicsCommand.EmbeddedImage) command;
                if (!embeddedImage.getClipStack().isEmpty()) {
                    String paint = embeddedImageElement(
                            defs, embeddedImage, deviceYDown, pageUry, elementIds, options);
                    if (!paint.isEmpty()) {
                        body.append(paint);
                    }
                    continue;
                }
            }
            if (command instanceof GraphicsCommand.Text) {
                GraphicsCommand.Text text = (GraphicsCommand.Text) command;
                if (!text.getClipStack().isEmpty()) {
                    String paint = clippedTextElement(defs, text, elementIds, options);
                    if (!paint.isEmpty()) {
                        body.append(paint);
                    }
                    continue;
                }
            }
            String paint = renderPaintCommand(command, deviceYDown, pageUry, elementIds, options);
            if (!paint.isEmpty()) {
                openPendingClips(body, clipFrames, elementIds, options);
                body.append(paint);
            }
        }

        if (rasterPlaceholderPattern) {
            appendRasterPlaceholderPatternDef(defs);
        }

        while (!clipFrames.isEmpty()) {
            ClipFrame frame = clipFrames.pop();
            if (frame.opened) {
                body.append("</g>\n");
            }
        }
        while (!layerGroupIds.isEmpty()) {
            body.append("</g>\n");
            layerGroupIds.pop();
        }

        SvgDisplaySize displaySize = SvgDisplaySize.fromViewBox(width, height, options);

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg id=\"").append(escapeAttr(rootId)).append("\"");
        svg.append(" xmlns=\"").append(SVG_NS).append("\"");
        svg.append(" width=\"").append(escapeAttr(displaySize.widthAttribute())).append("\"");
        svg.append(" height=\"").append(escapeAttr(displaySize.heightAttribute())).append("\"");
        // viewBox is 0..size; page <g> maps EPS coords (llx,lly..urx,ury) into that space.
        svg.append(" viewBox=\"0 0 ")
                .append(SvgPathEncoder.format(width)).append(' ')
                .append(SvgPathEncoder.format(height)).append("\">\n");
        if (defs.length() > 0) {
            svg.append("<defs>\n").append(defs).append("</defs>\n");
        }
        appendFontReportComment(svg, fontReport);
        svg.append("<style type=\"text/css\">image{image-rendering:pixelated;}</style>\n");
        if (pageTransform.isEmpty()) {
            svg.append(body);
        } else {
            svg.append("<g id=\"").append(escapeAttr(elementIds.next())).append("\"");
            svg.append(" transform=\"").append(escapeAttr(pageTransform)).append("\">\n");
            svg.append(body);
            svg.append("</g>\n");
        }
        svg.append("</svg>\n");
        return svg.toString();
    }

    private Map<String, SvgFontMetrics.FontChoice> collectFontReport(EpsDocument document) {
        Map<String, SvgFontMetrics.FontChoice> fonts = new LinkedHashMap<>();
        for (GraphicsCommand command : document.getCommands()) {
            if (command instanceof GraphicsCommand.Text) {
                GraphicsCommand.Text text = (GraphicsCommand.Text) command;
                SvgFontMetrics.FontChoice choice = SvgFontMetrics.chooseFont(
                        text.getFontName(), text.getFontSize(), shouldResolveFont(options));
                fonts.putIfAbsent(choice.sourceFamily(), choice);
            }
        }
        return fonts;
    }

    private static void appendFontReportComment(
            StringBuilder svg,
            Map<String, SvgFontMetrics.FontChoice> fontReport) {
        if (fontReport.isEmpty()) {
            return;
        }
        svg.append("<!-- EPS font report:\n");
        for (SvgFontMetrics.FontChoice choice : fontReport.values()) {
            svg.append("  ")
                    .append(xmlCommentSafe(choice.sourceFamily()))
                    .append(" -> ");
            if (choice.resolvedFamily() == null || !choice.substituted()) {
                svg.append("not substituted");
            } else {
                svg.append(xmlCommentSafe(choice.resolvedFamily()));
            }
            svg.append('\n');
        }
        svg.append("-->\n");
    }

    private static String xmlCommentSafe(String value) {
        return (value == null ? "" : value).replace("--", "- -");
    }

    private static void openPendingClips(
            StringBuilder body,
            Deque<ClipFrame> clipFrames,
            ElementIds elementIds,
            SvgRenderOptions options) {
        for (java.util.Iterator<ClipFrame> iterator = clipFrames.descendingIterator(); iterator.hasNext();) {
            ClipFrame frame = iterator.next();
            if (!frame.opened) {
                appendSourceTracePrologue(body, frame.sourceSpan, "clip-apply", options);
                body.append("<g id=\"").append(escapeAttr(elementIds.next())).append("\"");
                body.append(sourceTraceAttrs(frame.sourceSpan, options));
                body.append(" clip-path=\"url(#").append(frame.id).append(")\">\n");
                frame.opened = true;
            }
        }
    }

    public void write(EpsDocument document, java.nio.file.Path outputFile) throws IOException {
        Files.writeString(outputFile, render(document), StandardCharsets.UTF_8);
    }

    public void write(EpsDocument document, Writer writer) throws IOException {
        writer.write(render(document));
    }

    private static void appendClipPath(
            StringBuilder defs,
            String id,
            GraphicsCommand.Clip clip,
            ElementIds elementIds,
            SvgRenderOptions options) {
        Matrix ctm = clip.getCtm();
        String d = ctm.equals(Matrix.identity())
                ? SvgPathEncoder.toPathData(clip.getPath())
                : SvgPathEncoder.toPathData(clip.getPath(), ctm);
        if (d.isEmpty()) {
            return;
        }
        appendSourceTracePrologue(defs, clip, "clip", options);
        defs.append("<clipPath id=\"").append(id).append('"');
        defs.append(sourceTraceAttrs(clip, options));
        defs.append(">\n");
        defs.append("  <path id=\"").append(escapeAttr(elementIds.next())).append('"');
        defs.append(sourceTraceAttrs(clip, options));
        defs.append(" d=\"").append(escapeAttr(d)).append('"');
        defs.append(" clip-rule=\"").append(windingRuleAttr(clip.getWindingRule())).append("\"/>\n");
        defs.append("</clipPath>\n");
    }

    private static String renderPaintCommand(
            GraphicsCommand command,
            boolean deviceYDown,
            double pageUry,
            ElementIds elementIds,
            SvgRenderOptions options) {
        Matrix ctm = command.getCtm();
        String transform = matrixTransform(ctm);
        if (command instanceof GraphicsCommand.Fill) {
            GraphicsCommand.Fill fill = (GraphicsCommand.Fill) command;
            return pathElement(fill, "fill", fill.getPath(), transform, elementIds,
                    paintAttrs(fill.getFill(), true)
                            + " fill-rule=\"" + windingRuleAttr(fill.getWindingRule()) + "\"",
                    options);
        }
        if (command instanceof GraphicsCommand.Stroke) {
            GraphicsCommand.Stroke stroke = (GraphicsCommand.Stroke) command;
            return pathElement(stroke, "stroke", stroke.getPath(), transform, elementIds,
                    paintAttrs(stroke.getStrokeColor(), false) + strokeAttrs(stroke.getStrokeStyle()),
                    options);
        }
        if (command instanceof GraphicsCommand.RasterPlaceholder) {
            GraphicsCommand.RasterPlaceholder placeholder = (GraphicsCommand.RasterPlaceholder) command;
            return rasterPlaceholderElement(placeholder, transform, elementIds, options);
        }
        if (command instanceof GraphicsCommand.EmbeddedImage) {
            return embeddedImageElement(
                    null, (GraphicsCommand.EmbeddedImage) command, deviceYDown, pageUry, elementIds, options);
        }
        if (command instanceof GraphicsCommand.Text) {
            return textElement((GraphicsCommand.Text) command, elementIds, options);
        }
        return "";
    }

    private static String textElement(
            GraphicsCommand.Text text,
            ElementIds elementIds,
            SvgRenderOptions options) {
        if (text.getText().isEmpty()) {
            return "";
        }
        double fontSize = text.getFontSize();
        double x = text.getX();
        double y = text.getY();
        Matrix ctm = text.getCtm();
        StringBuilder element = new StringBuilder();
        appendSourceTracePrologue(element, text, "text", options);
        element.append("<text");
        element.append(" id=\"").append(escapeAttr(elementIds.next())).append('"');
        element.append(sourceTraceAttrs(text, options));
        String positionTransform = textPositionTransform(ctm, x, y);
        if (!positionTransform.isEmpty()) {
            element.append(" transform=\"").append(escapeAttr(positionTransform)).append('"');
            element.append(" x=\"0\" y=\"0\"");
        } else {
            element.append(" x=\"").append(SvgPathEncoder.format(x)).append('"');
            element.append(" y=\"").append(SvgPathEncoder.format(y)).append('"');
        }
        element.append(" font-size=\"").append(SvgPathEncoder.format(fontSize)).append('"');
        SvgFontMetrics.FontChoice fontChoice = SvgFontMetrics.chooseFont(
                text.getFontName(), fontSize, shouldResolveFont(options));
        element.append(" font-family=\"")
                .append(escapeAttr(svgFontFamily(text.getFontName(), fontChoice, options)))
                .append('"');
        String fontWeight = svgFontWeight(fontChoice);
        if (!fontWeight.isEmpty()) {
            element.append(" font-weight=\"").append(fontWeight).append('"');
        }
        String fontStretch = svgFontStretch(fontChoice);
        if (!fontStretch.isEmpty()) {
            element.append(" font-stretch=\"").append(fontStretch).append('"');
        }
        if (IllustratorTextPaint.isItalicFont(text.getFontName())) {
            element.append(" font-style=\"italic\"");
        }
        element.append(paintAttrs(text.getFill(), true));
        element.append('>');
        appendTextContent(element, text, fontChoice, options);
        element.append("</text>\n");
        return element.toString();
    }

    private static String clippedTextElement(
            StringBuilder defs,
            GraphicsCommand.Text text,
            ElementIds elementIds,
            SvgRenderOptions options) {
        if (defs == null || text.getClipStack().isEmpty()) {
            return textElement(text, elementIds, options);
        }
        StringBuilder element = new StringBuilder();
        for (GraphicsCommand.Clip clip : text.getClipStack()) {
            String clipId = elementIds.next();
            appendClipPath(defs, clipId, clip, elementIds, options);
            appendSourceTracePrologue(element, clip, "clip-apply", options);
            element.append("<g id=\"").append(escapeAttr(elementIds.next())).append('"');
            element.append(sourceTraceAttrs(clip, options));
            element.append(" clip-path=\"url(#").append(clipId).append(")\">\n");
        }
        element.append(textElement(text, elementIds, options));
        for (int i = 0; i < text.getClipStack().size(); i++) {
            element.append("</g>\n");
        }
        return element.toString();
    }

    /**
     * Illustrator-style text positioning: {@code translate(x,y)} plus linear CTM when needed.
     * Operand-space anchor must not be multiplied by msf/text-matrix scale (that is font-size).
     */
    private static String textPositionTransform(Matrix ctm, double x, double y) {
        if (ctm == null || ctm.equals(Matrix.identity())) {
            return "translate(" + SvgPathEncoder.format(x) + ' ' + SvgPathEncoder.format(y) + ')';
        }
        return "matrix("
                + SvgPathEncoder.format(ctm.getA()) + ' '
                + SvgPathEncoder.format(ctm.getB()) + ' '
                + SvgPathEncoder.format(ctm.getC()) + ' '
                + SvgPathEncoder.format(ctm.getD()) + ' '
                + SvgPathEncoder.format(ctm.transformX(x, y)) + ' '
                + SvgPathEncoder.format(ctm.transformY(x, y)) + ')';
    }

    private static void appendTextContent(
            StringBuilder element,
            GraphicsCommand.Text text,
            SvgFontMetrics.FontChoice fontChoice,
            SvgRenderOptions options) {
        String value = text.getText();
        double[] advances = text.getGlyphAdvances();
        if (advances == null
                || advances.length == 0
                || value.length() <= 1
                || options.fontMetricsMode() == SvgRenderOptions.FontMetricsMode.AUTO) {
            element.append(escapeText(value));
            return;
        }
        if (options.fontMetricsMode() == SvgRenderOptions.FontMetricsMode.RELATIVE) {
            String dx = relativeDxPositions(text, fontChoice, advances);
            if (!dx.isEmpty()) {
                element.append("<tspan dx=\"").append(dx).append("\">");
                element.append(escapeText(value));
                element.append("</tspan>");
                return;
            }
        }
        StringBuilder xPositions = new StringBuilder("0");
        double cumulative = 0;
        for (int i = 0; i < value.length() - 1; i++) {
            if (i < advances.length) {
                cumulative += advances[i];
            }
            xPositions.append(' ').append(SvgPathEncoder.format(cumulative));
        }
        element.append("<tspan x=\"").append(xPositions).append("\">");
        element.append(escapeText(value));
        element.append("</tspan>");
    }

    private static String relativeDxPositions(
            GraphicsCommand.Text text,
            SvgFontMetrics.FontChoice fontChoice,
            double[] epsAdvances) {
        String family = fontChoice.resolvedFamily();
        if (family == null || family.isEmpty()) {
            return "";
        }
        double[] measured = SvgFontMetrics.glyphAdvances(
                family, text.getFontName(), text.getFontSize(), text.getText());
        if (measured.length == 0) {
            return "";
        }
        StringBuilder dx = new StringBuilder("0");
        int count = Math.min(Math.min(text.getText().length() - 1, epsAdvances.length), measured.length);
        for (int i = 0; i < count; i++) {
            dx.append(' ').append(SvgPathEncoder.format(epsAdvances[i] - measured[i]));
        }
        return dx.toString();
    }

    private static double effectiveFontSize(double fontSize, Matrix ctm) {
        double sx = Math.hypot(ctm.getA(), ctm.getB());
        double sy = Math.hypot(ctm.getC(), ctm.getD());
        return fontSize * Math.max(sx, sy);
    }

    private static boolean shouldResolveFont(SvgRenderOptions options) {
        return options.substituteFonts()
                || options.fontMetricsMode() == SvgRenderOptions.FontMetricsMode.RELATIVE;
    }

    private static String svgFontFamily(
            String fontName,
            SvgFontMetrics.FontChoice fontChoice,
            SvgRenderOptions options) {
        String name = fontName == null || fontName.isEmpty() ? "Helvetica" : fontName;
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (!options.substituteFonts() || !fontChoice.substituted()) {
            return cssFontFamily(name) + ", " + genericFallback(name);
        }
        String source = SvgFontMetrics.sourceFamily(name);
        if (fontChoice.resolvedFamily() != null && fontChoice.substituted()) {
            return cssFontFamily(fontChoice.resolvedFamily())
                    + ", "
                    + cssFontFamily(source)
                    + ", "
                    + genericFallback(source);
        }
        if (name.contains("Raleway")) {
            return cssFontFamily(name) + ", sans-serif";
        }
        if (name.contains("Gotham")) {
            return cssFontFamily(name) + ", Arial, sans-serif";
        }
        if (name.contains("Helvetica") || name.contains("Arial")) {
            return cssFontFamily(name) + ", Arial, sans-serif";
        }
        if (name.contains("Times")) {
            return cssFontFamily(name) + ", Times New Roman, Times, serif";
        }
        if (name.contains("Courier")) {
            return cssFontFamily(name) + ", Courier New, Courier, monospace";
        }
        return cssFontFamily(name) + ", sans-serif";
    }

    private static String cssFontFamily(String family) {
        if (family == null || family.isEmpty()) {
            return "sans-serif";
        }
        if (family.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
            return family;
        }
        return "'" + family.replace("'", "\\'") + "'";
    }

    private static String genericFallback(String family) {
        String name = family == null ? "" : family.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("times") || name.contains("serif")) {
            return "serif";
        }
        if (name.contains("courier") || name.contains("mono")) {
            return "monospace";
        }
        return "sans-serif";
    }

    private static String svgFontWeight(SvgFontMetrics.FontChoice fontChoice) {
        int weight = fontChoice.traits().weight();
        if (weight != 400) {
            return Integer.toString(weight);
        }
        return "";
    }

    private static String svgFontStretch(SvgFontMetrics.FontChoice fontChoice) {
        int width = fontChoice.traits().width();
        if (width <= 65) {
            return "extra-condensed";
        }
        if (width <= 80) {
            return "condensed";
        }
        if (width <= 90) {
            return "semi-condensed";
        }
        if (width >= 120) {
            return "expanded";
        }
        return "";
    }

    /**
     * Renders an AGM tile like Illustrator: intrinsic pixel size plus an affine transform.
     * When {@code defs} is non-null and the image has a paint-time clip stack, clip paths are
     * emitted in defs and referenced from the {@code <image>} element.
     */
    private static String embeddedImageElement(
            StringBuilder defs,
            GraphicsCommand.EmbeddedImage image,
            boolean deviceYDown,
            double pageUry,
            ElementIds elementIds,
            SvgRenderOptions options) {
        if (image.getPngBytes().length == 0) {
            return "";
        }
        StringBuilder element = new StringBuilder();
        appendSourceTracePrologue(element, image, "image", options);
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return "";
        }
        Matrix displayMatrix = embeddedImageTransformMatrix(
                image.getCtm(), width, height, deviceYDown, pageUry);
        if (!displayMatrix.equals(Matrix.identity()) && deviceYDown && height <= 1) {
            double[] bounds = transformedImageBounds(image);
            double svgY = bounds[1] > pageUry * 0.5 && bounds[3] < pageUry * 0.88
                    ? pageUry - bounds[3]
                    : bounds[1];
            displayMatrix = new Matrix(
                    displayMatrix.getA(),
                    displayMatrix.getB(),
                    displayMatrix.getC(),
                    displayMatrix.getD(),
                    displayMatrix.getE(),
                    svgY);
        }
        String transform = matrixTransform(displayMatrix);
        String base64 = Base64.getEncoder().encodeToString(image.getPngBytes());
        List<GraphicsCommand.Clip> clips = image.getClipStack();
        if (defs != null && !clips.isEmpty()) {
            for (int i = 0; i < clips.size() - 1; i++) {
                String clipId = elementIds.next();
                GraphicsCommand.Clip stackClip = clips.get(i);
                appendClipPath(defs, clipId, stackClip, elementIds, options);
                appendSourceTracePrologue(element, stackClip, "clip-apply", options);
                element.append("<g id=\"").append(escapeAttr(elementIds.next())).append('"');
                element.append(sourceTraceAttrs(stackClip, options));
                element.append(" clip-path=\"url(#").append(clipId).append(")\">\n");
            }
            String imageClipId = elementIds.next();
            GraphicsCommand.Clip localClip =
                    imageLocalClip(clips.get(clips.size() - 1), displayMatrix);
            appendClipPath(defs, imageClipId, localClip, elementIds, options);
            element.append("<image");
            element.append(" id=\"").append(escapeAttr(elementIds.next())).append('"');
            element.append(sourceTraceAttrs(image, options));
            element.append(" clip-path=\"url(#").append(imageClipId).append(")\"");
        } else {
            element.append("<image");
            element.append(" id=\"").append(escapeAttr(elementIds.next())).append('"');
            element.append(sourceTraceAttrs(image, options));
        }
        if (!transform.isEmpty()) {
            element.append(" width=\"").append(width).append('"');
            element.append(" height=\"").append(height).append('"');
            element.append(" transform=\"").append(escapeAttr(transform)).append('"');
        } else {
            double[] bounds = transformedImageBounds(image);
            double x = bounds[0];
            double w = bounds[2] - bounds[0];
            double h = bounds[3] - bounds[1];
            double y = deviceYDown
                    ? (bounds[1] > pageUry * 0.5 && bounds[3] < pageUry * 0.88
                            ? pageUry - bounds[3]
                            : bounds[1])
                    : bounds[1];
            if (w <= 0 || h <= 0) {
                return "";
            }
            element.append(" x=\"").append(SvgPathEncoder.format(x)).append('"');
            element.append(" y=\"").append(SvgPathEncoder.format(y)).append('"');
            element.append(" width=\"").append(SvgPathEncoder.format(w)).append('"');
            element.append(" height=\"").append(SvgPathEncoder.format(h)).append('"');
            element.append(" preserveAspectRatio=\"none\"");
        }
        element.append(" href=\"data:image/png;base64,").append(base64).append("\"/>\n");
        for (int i = 0; i < clips.size() - 1; i++) {
            element.append("</g>\n");
        }
        return element.toString();
    }

    private static GraphicsCommand.Clip imageLocalClip(GraphicsCommand.Clip clip, Matrix displayMatrix) {
        if (displayMatrix.equals(Matrix.identity())) {
            return clip;
        }
        try {
            Matrix localClipCtm = displayMatrix.invert().postConcat(clip.getCtm());
            return new GraphicsCommand.Clip(
                    clip.getPath(),
                    clip.getWindingRule(),
                    localClipCtm,
                    clip.getSourceSpan().orElse(null));
        } catch (IllegalArgumentException ex) {
            return clip;
        }
    }

    /**
     * Maps raster pixels to SVG user space (Illustrator-style), using the tile CTM from EPS.
     */
    private static Matrix embeddedImageTransformMatrix(
            Matrix ctm, int width, int height, boolean deviceYDown, double pageUry) {
        double[][] pixelCorners = {{0, 0}, {width, 0}, {0, height}, {width, height}};
        double[][] corners = new double[4][2];
        // Composed AGM CTMs already include [1 0 0 -1 0 pageHeight] when d < 0.
        boolean ctmMapsToDeviceYDown = ctm.getD() < 0;
        for (int i = 0; i < pixelCorners.length; i++) {
            corners[i] = imagePixelToEps(ctm, width, height, pixelCorners[i][0], pixelCorners[i][1]);
            if (deviceYDown && !ctmMapsToDeviceYDown) {
                corners[i][1] = pageUry - corners[i][1];
            }
        }
        int origin = 0;
        for (int i = 1; i < corners.length; i++) {
            if (corners[i][1] < corners[origin][1]
                    || (corners[i][1] == corners[origin][1] && corners[i][0] < corners[origin][0])) {
                origin = i;
            }
        }
        int right = -1;
        int down = -1;
        for (int i = 0; i < corners.length; i++) {
            if (i == origin) {
                continue;
            }
            if (right < 0
                    || Math.abs(corners[i][1] - corners[origin][1])
                            < Math.abs(corners[right][1] - corners[origin][1])
                    || (Math.abs(corners[i][1] - corners[origin][1])
                                    == Math.abs(corners[right][1] - corners[origin][1])
                            && corners[i][0] > corners[right][0])) {
                right = i;
            }
            if (down < 0
                    || Math.abs(corners[i][0] - corners[origin][0])
                            < Math.abs(corners[down][0] - corners[origin][0])
                    || (Math.abs(corners[i][0] - corners[origin][0])
                                    == Math.abs(corners[down][0] - corners[origin][0])
                            && corners[i][1] > corners[down][1])) {
                down = i;
            }
        }
        if (right < 0 || down < 0) {
            return Matrix.identity();
        }
        double a = (corners[right][0] - corners[origin][0]) / width;
        double b = (corners[right][1] - corners[origin][1]) / width;
        double c = (corners[down][0] - corners[origin][0]) / height;
        double d = (corners[down][1] - corners[origin][1]) / height;
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c) || !Double.isFinite(d)) {
            return Matrix.identity();
        }
        return new Matrix(a, b, c, d, corners[origin][0], corners[origin][1]);
    }

    private static double[] imagePixelToEps(Matrix display, int width, int height, double px, double py) {
        double u = px / width;
        double v = 1.0 - py / height;
        return new double[] {display.transformX(u, v), display.transformY(u, v)};
    }

    private static void appendRasterPlaceholderPatternDef(StringBuilder defs) {
        defs.append("<pattern id=\"").append(RASTER_PLACEHOLDER_PATTERN_ID).append("\"");
        defs.append(" patternUnits=\"userSpaceOnUse\" width=\"8\" height=\"8\"");
        defs.append(" patternTransform=\"rotate(45)\">\n");
        defs.append("  <line x1=\"0\" y1=\"0\" x2=\"0\" y2=\"8\" stroke=\"#999\" stroke-width=\"1\"/>\n");
        defs.append("</pattern>\n");
    }

    private static String rasterPlaceholderElement(
            GraphicsCommand.RasterPlaceholder placeholder,
            String transform,
            ElementIds elementIds,
            SvgRenderOptions options) {
        BoundingBox region = placeholder.getRegion();
        double x = region.getLlx();
        double y = region.getLly();
        double w = region.getWidth();
        double h = region.getHeight();
        StringBuilder element = new StringBuilder();
        appendSourceTracePrologue(element, placeholder, "raster-placeholder", options);
        if (!transform.isEmpty()) {
            element.append("<g");
            element.append(" id=\"").append(escapeAttr(elementIds.next())).append('"');
            element.append(" transform=\"").append(escapeAttr(transform)).append('"');
            element.append(sourceTraceAttrs(placeholder, options));
            element.append(">\n  ");
        }
        element.append("<rect");
        element.append(" id=\"").append(escapeAttr(elementIds.next())).append('"');
        element.append(sourceTraceAttrs(placeholder, options));
        element.append(" x=\"").append(SvgPathEncoder.format(x)).append('"');
        element.append(" y=\"").append(SvgPathEncoder.format(y)).append('"');
        element.append(" width=\"").append(SvgPathEncoder.format(w)).append('"');
        element.append(" height=\"").append(SvgPathEncoder.format(h)).append('"');
        element.append(" fill=\"url(#").append(RASTER_PLACEHOLDER_PATTERN_ID).append(")\"");
        element.append(" fill-opacity=\"0.35\"");
        element.append(" stroke=\"#666\" stroke-width=\"0.75\" stroke-dasharray=\"4 3\"");
        element.append(">\n");
        element.append(transform.isEmpty() ? "  " : "    ");
        element.append("<title id=\"").append(escapeAttr(elementIds.next())).append("\">");
        element.append("Raster region (placeholder)</title>\n");
        element.append(transform.isEmpty() ? "" : "  ");
        element.append("</rect>");
        if (!transform.isEmpty()) {
            element.append("\n</g>");
        }
        element.append('\n');
        return element.toString();
    }

    private static final double VIEWPORT_PAD = 1.0;

    private static BoundingBox visibleViewport(EpsDocument document) {
        Bounds preferred = new Bounds();
        Bounds fallbackPaint = new Bounds();
        Bounds page = Bounds.from(document.getBoundingBox());
        for (GraphicsCommand command : document.getCommands()) {
            if (command instanceof GraphicsCommand.Clip) {
                continue;
            }
            if (command instanceof GraphicsCommand.Fill) {
                GraphicsCommand.Fill fill = (GraphicsCommand.Fill) command;
                double[] bounds = PathBounds.transformedBounds(fill.getPath(), command.getCtm());
                bounds = page.clamp(bounds);
                fallbackPaint.include(bounds);
                if (!isWhiteOrNone(fill.getFill())) {
                    preferred.include(bounds);
                }
            } else if (command instanceof GraphicsCommand.Stroke) {
                GraphicsCommand.Stroke stroke = (GraphicsCommand.Stroke) command;
                double[] bounds = PathBounds.transformedBounds(stroke.getPath(), command.getCtm());
                double pad = Math.max(VIEWPORT_PAD, stroke.getStrokeStyle().getLineWidth() / 2.0);
                bounds = page.clamp(bounds, pad);
                fallbackPaint.include(bounds);
                preferred.include(bounds);
            } else if (command instanceof GraphicsCommand.RasterPlaceholder) {
                GraphicsCommand.RasterPlaceholder placeholder = (GraphicsCommand.RasterPlaceholder) command;
                BoundingBox region = placeholder.getRegion();
                double[] bounds = new double[] {
                        region.getLlx(), region.getLly(), region.getUrx(), region.getUry()
                };
                bounds = page.clamp(bounds);
                fallbackPaint.include(bounds);
                preferred.include(bounds);
            } else if (command instanceof GraphicsCommand.EmbeddedImage) {
                GraphicsCommand.EmbeddedImage image = (GraphicsCommand.EmbeddedImage) command;
                double[] bounds = transformedImageBounds(image);
                bounds = page.clamp(bounds);
                fallbackPaint.include(bounds);
                preferred.include(bounds);
            } else if (command instanceof GraphicsCommand.Text) {
                GraphicsCommand.Text text = (GraphicsCommand.Text) command;
                double[] bounds = transformedTextBounds(text);
                bounds = page.clamp(bounds);
                fallbackPaint.include(bounds);
                if (!isWhiteOrNone(text.getFill())) {
                    preferred.include(bounds);
                }
            }
        }
        if (!preferred.isEmpty()) {
            return padViewport(page, preferred.toBoundingBox());
        }
        if (!fallbackPaint.isEmpty()) {
            return padViewport(page, fallbackPaint.toBoundingBox());
        }
        return document.getBoundingBox();
    }

    private static double[] transformedImageBounds(GraphicsCommand.EmbeddedImage image) {
        Matrix ctm = image.getCtm();
        int width = image.getWidth();
        int height = image.getHeight();
        double[][] pixelCorners = {{0, 0}, {width, 0}, {0, height}, {width, height}};
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (double[] corner : pixelCorners) {
            double[] point = imagePixelToEps(ctm, width, height, corner[0], corner[1]);
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
            minY = Math.min(minY, point[1]);
            maxY = Math.max(maxY, point[1]);
        }
        return new double[] {minX, minY, maxX, maxY};
    }

    private static double[] transformedTextBounds(GraphicsCommand.Text text) {
        Matrix ctm = text.getCtm();
        double fontSize = effectiveFontSize(text.getFontSize(), ctm);
        double x0 = ctm.transformX(text.getX(), text.getY());
        double y0 = ctm.transformY(text.getX(), text.getY());
        double width = Math.max(fontSize * 0.55 * text.getText().length(), fontSize * 0.5);
        return new double[] {x0, y0 - fontSize, x0 + width, y0 + fontSize * 0.25};
    }

    private static BoundingBox padViewport(Bounds page, BoundingBox viewport) {
        BoundingBox padded = new BoundingBox(
                viewport.getLlx() - VIEWPORT_PAD,
                viewport.getLly() - VIEWPORT_PAD,
                viewport.getUrx() + VIEWPORT_PAD,
                viewport.getUry() + VIEWPORT_PAD);
        double[] clamped = page.clamp(new double[] {
                padded.getLlx(), padded.getLly(), padded.getUrx(), padded.getUry()
        });
        return new BoundingBox(clamped[0], clamped[1], clamped[2], clamped[3]);
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

    private static String pathElement(
            GraphicsCommand command,
            String kind,
            Path path,
            String transform,
            ElementIds elementIds,
            String paintAttributes,
            SvgRenderOptions options) {
        String d = SvgPathEncoder.toPathData(path);
        if (d.isEmpty()) {
            return "";
        }
        StringBuilder element = new StringBuilder();
        appendSourceTracePrologue(element, command, kind, options);
        if (!transform.isEmpty()) {
            element.append("<g");
            element.append(" id=\"").append(escapeAttr(elementIds.next())).append('"');
            element.append(" transform=\"").append(escapeAttr(transform)).append('"');
            element.append(sourceTraceAttrs(command, options));
            element.append(">\n  ");
        }
        element.append("<path");
        element.append(" id=\"").append(escapeAttr(elementIds.next())).append('"');
        element.append(sourceTraceAttrs(command, options));
        element.append(" d=\"").append(escapeAttr(d)).append('"');
        element.append(paintAttributes);
        element.append("/>");
        if (!transform.isEmpty()) {
            element.append("\n</g>");
        }
        element.append('\n');
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

    private static void appendSourceTracePrologue(
            StringBuilder target,
            GraphicsCommand command,
            String kind,
            SvgRenderOptions options) {
        if (options == null || !options.emitSourceTrace()) {
            return;
        }
        command.getSourceSpan().ifPresent(span -> target.append(span.svgSourceComment(kind)));
    }

    private static void appendSourceTracePrologue(
            StringBuilder target, SourceSpan span, String kind, SvgRenderOptions options) {
        if (options == null || !options.emitSourceTrace() || span == null) {
            return;
        }
        target.append(span.svgSourceComment(kind));
    }

    private static String sourceTraceAttrs(GraphicsCommand command, SvgRenderOptions options) {
        if (options == null || !options.emitSourceTrace()) {
            return "";
        }
        return sourceTraceAttrs(command.getSourceSpan(), options);
    }

    private static String sourceTraceAttrs(SourceSpan span, SvgRenderOptions options) {
        if (options == null || !options.emitSourceTrace() || span == null) {
            return "";
        }
        return span.svgDataAttributes();
    }

    private static String sourceTraceAttrs(Optional<SourceSpan> span, SvgRenderOptions options) {
        if (options == null || !options.emitSourceTrace()) {
            return "";
        }
        return span.map(SourceSpan::svgDataAttributes).orElse("");
    }

    private static String sanitizeLayerId(String layerId) {
        if (layerId == null || layerId.isBlank()) {
            return "layer";
        }
        return layerId.replaceAll("[^A-Za-z0-9_\\-:.]", "_");
    }

    private static String escapeAttr(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private static String escapeText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
