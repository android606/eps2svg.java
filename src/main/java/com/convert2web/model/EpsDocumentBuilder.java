package com.convert2web.model;

import com.convert2web.model.GraphicsCommand.Clip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Builds an {@link EpsDocument} as the interpreter records drawing operations.
 */
public final class EpsDocumentBuilder {
    private BoundingBox boundingBox;
    private BoundingBox hiResBoundingBox;
    private final List<GraphicsCommand> commands = new ArrayList<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();

    public EpsDocumentBuilder setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
        return this;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public EpsDocumentBuilder setHiResBoundingBox(BoundingBox hiResBoundingBox) {
        this.hiResBoundingBox = hiResBoundingBox;
        return this;
    }

    public EpsDocumentBuilder putMetadata(String key, String value) {
        metadata.put(key, value);
        return this;
    }

    public EpsDocumentBuilder addFill(Path path, PaintStyle fill, WindingRule windingRule, Matrix ctm) {
        return addFill(path, fill, windingRule, ctm, null);
    }

    public EpsDocumentBuilder addFill(
            Path path, PaintStyle fill, WindingRule windingRule, Matrix ctm, SourceSpan sourceSpan) {
        commands.add(new GraphicsCommand.Fill(path, fill, windingRule, ctm, sourceSpan));
        return this;
    }

    /** Inserts a fill so it paints behind subsequently recorded artwork. */
    public EpsDocumentBuilder addFillAtFront(Path path, PaintStyle fill, WindingRule windingRule, Matrix ctm) {
        return addFillAtFront(path, fill, windingRule, ctm, null);
    }

    public EpsDocumentBuilder addFillAtFront(
            Path path, PaintStyle fill, WindingRule windingRule, Matrix ctm, SourceSpan sourceSpan) {
        commands.add(0, new GraphicsCommand.Fill(path, fill, windingRule, ctm, sourceSpan));
        return this;
    }

    public EpsDocumentBuilder addStroke(Path path, PaintStyle strokeColor, StrokeStyle strokeStyle, Matrix ctm) {
        return addStroke(path, strokeColor, strokeStyle, ctm, null);
    }

    public EpsDocumentBuilder addStroke(
            Path path,
            PaintStyle strokeColor,
            StrokeStyle strokeStyle,
            Matrix ctm,
            SourceSpan sourceSpan) {
        commands.add(new GraphicsCommand.Stroke(path, strokeColor, strokeStyle, ctm, sourceSpan));
        return this;
    }

    public EpsDocumentBuilder addClip(Path path, WindingRule windingRule, Matrix ctm) {
        return addClip(path, windingRule, ctm, null);
    }

    public EpsDocumentBuilder addClip(Path path, WindingRule windingRule, Matrix ctm, SourceSpan sourceSpan) {
        commands.add(new GraphicsCommand.Clip(path, windingRule, ctm, sourceSpan));
        return this;
    }

    public EpsDocumentBuilder addPopClip() {
        return addPopClip(null);
    }

    public EpsDocumentBuilder addPopClip(SourceSpan sourceSpan) {
        commands.add(new GraphicsCommand.PopClip(sourceSpan));
        return this;
    }

    public EpsDocumentBuilder addRasterPlaceholder(BoundingBox region, Matrix ctm) {
        return addRasterPlaceholder(region, ctm, null);
    }

    public EpsDocumentBuilder addRasterPlaceholder(BoundingBox region, Matrix ctm, SourceSpan sourceSpan) {
        commands.add(new GraphicsCommand.RasterPlaceholder(region, ctm, sourceSpan));
        return this;
    }

    public EpsDocumentBuilder addEmbeddedImage(int width, int height, Matrix ctm, byte[] pngBytes) {
        return addEmbeddedImage(width, height, ctm, pngBytes, List.of());
    }

    public EpsDocumentBuilder addEmbeddedImage(
            int width,
            int height,
            Matrix ctm,
            byte[] pngBytes,
            List<Clip> clipStack) {
        return addEmbeddedImage(width, height, ctm, pngBytes, clipStack, null);
    }

    public EpsDocumentBuilder addEmbeddedImage(
            int width,
            int height,
            Matrix ctm,
            byte[] pngBytes,
            List<Clip> clipStack,
            SourceSpan sourceSpan) {
        commands.add(new GraphicsCommand.EmbeddedImage(width, height, ctm, pngBytes, clipStack, sourceSpan));
        return this;
    }

    public EpsDocumentBuilder moveRecentTextAfterLastCommand(
            Predicate<GraphicsCommand.Text> predicate,
            int maxLookback) {
        if (commands.isEmpty() || predicate == null || maxLookback <= 0) {
            return this;
        }
        int last = commands.size() - 1;
        int first = Math.max(0, last - maxLookback);
        List<GraphicsCommand> moved = new ArrayList<>();
        for (int i = last - 1; i >= first; i--) {
            GraphicsCommand command = commands.get(i);
            if (command instanceof GraphicsCommand.Text
                    && predicate.test((GraphicsCommand.Text) command)) {
                moved.add(0, command);
                commands.remove(i);
                last--;
            }
        }
        commands.addAll(last + 1, moved);
        return this;
    }

    public EpsDocumentBuilder addText(
            String text,
            double x,
            double y,
            String fontName,
            double fontSize,
            PaintStyle fill,
            Matrix ctm) {
        return addText(text, x, y, fontName, fontSize, fill, ctm, null);
    }

    public EpsDocumentBuilder addText(
            String text,
            double x,
            double y,
            String fontName,
            double fontSize,
            PaintStyle fill,
            Matrix ctm,
            double[] glyphAdvances) {
        return addText(text, x, y, fontName, fontSize, fill, ctm, glyphAdvances, null);
    }

    public EpsDocumentBuilder addText(
            String text,
            double x,
            double y,
            String fontName,
            double fontSize,
            PaintStyle fill,
            Matrix ctm,
            double[] glyphAdvances,
            SourceSpan sourceSpan) {
        return addText(text, x, y, fontName, fontSize, fill, ctm, glyphAdvances, List.of(), sourceSpan);
    }

    public EpsDocumentBuilder addText(
            String text,
            double x,
            double y,
            String fontName,
            double fontSize,
            PaintStyle fill,
            Matrix ctm,
            double[] glyphAdvances,
            List<Clip> clipStack,
            SourceSpan sourceSpan) {
        commands.add(new GraphicsCommand.Text(
                text, x, y, fontName, fontSize, fill, ctm, glyphAdvances, clipStack, sourceSpan));
        return this;
    }

    /** Records text in paint order; renderer preserves later clips/images/fills. */
    public EpsDocumentBuilder addTextOnTop(
            String text,
            double x,
            double y,
            String fontName,
            double fontSize,
            PaintStyle fill,
            Matrix ctm,
            double[] glyphAdvances) {
        return addTextOnTop(text, x, y, fontName, fontSize, fill, ctm, glyphAdvances, null);
    }

    public EpsDocumentBuilder addTextOnTop(
            String text,
            double x,
            double y,
            String fontName,
            double fontSize,
            PaintStyle fill,
            Matrix ctm,
            double[] glyphAdvances,
            SourceSpan sourceSpan) {
        commands.add(new GraphicsCommand.Text(
                text, x, y, fontName, fontSize, fill, ctm, glyphAdvances, sourceSpan));
        return this;
    }

    public EpsDocument build() {
        if (boundingBox == null) {
            boundingBox = new BoundingBox(0, 0, 100, 100);
        }
        return new EpsDocument(boundingBox, hiResBoundingBox, commands, metadata);
    }
}
