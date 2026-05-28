package com.convert2web.ps;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocumentBuilder;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.Path;
import com.convert2web.model.PathBounds;
import com.convert2web.model.SourceSpan;
import com.convert2web.model.WindingRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Appends {@link com.convert2web.model.GraphicsCommand}s from VM graphics state.
 */
public final class EpsDocumentRecorder {
    private static final int COVER_TILE_TEXT_LOOKBACK = 80;

    private final PostScriptVm vm;
    private final EpsDocumentBuilder builder;
    private final List<GraphicsCommand.Clip> clipStack = new ArrayList<>();

    public EpsDocumentRecorder(PostScriptVm vm, EpsDocumentBuilder builder) {
        this.vm = vm;
        this.builder = builder;
    }

    public EpsDocumentBuilder getBuilder() {
        return builder;
    }

    public void recordFill(VmGraphicsState state, WindingRule windingRule) {
        Path path = state.snapshotPath();
        if (path.isEmpty()) {
            state.clearPath();
            return;
        }
        Matrix ctm = state.getCtm();
        if (state.isInCompoundPath()) {
            return;
        }
        recordFillPath(path, state.getFillColor(), windingRule, ctm);
        state.clearPath();
    }

    /** Illustrator {@code *U}: fill accumulated compound subpaths with even-odd rule. */
    public void finishCompoundPath(VmGraphicsState state) {
        state.endCompoundPath();
        Path path = state.snapshotPath();
        if (path.isEmpty()) {
            return;
        }
        Matrix ctm = state.getCtm();
        recordFillPath(path, state.getFillColor(), WindingRule.EVEN_ODD, ctm);
        state.clearPath();
    }

    private void recordFillPath(Path path, PaintStyle fill,
            WindingRule windingRule, Matrix ctm) {
        SourceSpan sourceSpan = vm.getCurrentSourceSpan().orElse(null);
        if (isLargeBackgroundFill(path, ctm, builder.getBoundingBox())) {
            builder.addFillAtFront(path, fill, windingRule, ctm, sourceSpan);
        } else {
            builder.addFill(path, fill, windingRule, ctm, sourceSpan);
        }
    }

    public void recordText(
            String text,
            double x,
            double y,
            String fontName,
            double fontSize,
            PaintStyle fill,
            Matrix ctm) {
        recordText(text, x, y, fontName, fontSize, fill, ctm, null);
    }

    public void recordText(
            String text,
            double x,
            double y,
            String fontName,
            double fontSize,
            PaintStyle fill,
            Matrix ctm,
            double[] glyphAdvances) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GraphicsCommand.Text command = new GraphicsCommand.Text(
                text, x, y, fontName, fontSize, fill, ctm, glyphAdvances,
                getActiveClips(), vm.getCurrentSourceSpan().orElse(null));
        appendText(command);
    }

    /**
     * Illustrator EPS often paints border/background fills after glyphs; queue them at the
     * back so letterforms and dividers stay visible in SVG.
     */
    private static boolean isLargeBackgroundFill(Path path, Matrix ctm, BoundingBox page) {
        if (page == null) {
            return false;
        }
        double[] b = PathBounds.transformedBounds(path, ctm);
        double pathW = b[2] - b[0];
        double pathH = b[3] - b[1];
        if (pathW <= 0 || pathH <= 0) {
            return false;
        }
        double pageW = page.getWidth();
        double pageH = page.getHeight();
        if (pageW <= 0 || pageH <= 0) {
            return false;
        }
        double areaRatio = (pathW * pathH) / (pageW * pageH);
        return areaRatio >= 0.7
                && pathW >= pageW * 0.8
                && pathH >= pageH * 0.75;
    }

    public void recordStroke(VmGraphicsState state) {
        Path path = state.snapshotPath();
        if (path.isEmpty()) {
            state.clearPath();
            return;
        }
        builder.addStroke(
                path,
                state.getStrokeColor(),
                state.getStrokeStyle(),
                state.getCtm(),
                vm.getCurrentSourceSpan().orElse(null));
        state.clearPath();
    }

    public void recordClip(VmGraphicsState state, WindingRule windingRule) {
        Path path = state.snapshotPath();
        if (path.isEmpty()) {
            state.clearPath();
            return;
        }
        Matrix ctm = state.getCtm();
        SourceSpan sourceSpan = vm.getCurrentSourceSpan().orElse(null);
        clipStack.add(new GraphicsCommand.Clip(path, windingRule, ctm, sourceSpan));
        builder.addClip(path, windingRule, ctm, sourceSpan);
        state.incrementClipDepth();
        state.clearPath();
    }

    /** Clips active at the current paint point (for AGM tiles). */
    public List<GraphicsCommand.Clip> getActiveClips() {
        return List.copyOf(clipStack);
    }

    public void recordEmbeddedImage(int width, int height, Matrix ctm, byte[] pngBytes) {
        recordEmbeddedImage(width, height, ctm, pngBytes, false);
    }

    public void recordEmbeddedImage(
            int width,
            int height,
            Matrix ctm,
            byte[] pngBytes,
            boolean paintPendingTextAfterImage) {
        builder.addEmbeddedImage(
                width,
                height,
                ctm,
                pngBytes,
                getActiveClips(),
                vm.getCurrentSourceSpan().orElse(null));
        if (paintPendingTextAfterImage) {
            double[] bounds = imageBounds(ctm, width, height);
            builder.moveRecentTextAfterLastCommand(
                    text -> textBaselineOverlapsImage(text, bounds),
                    COVER_TILE_TEXT_LOOKBACK);
        }
    }

  /** Emits {@link com.convert2web.model.GraphicsCommand.PopClip} for each restored clip level. */
    public void popClipsToDepth(int targetDepth, int previousDepth) {
        SourceSpan sourceSpan = vm.getCurrentSourceSpan().orElse(null);
        for (int i = targetDepth; i < previousDepth; i++) {
            builder.addPopClip(sourceSpan);
            if (!clipStack.isEmpty()) {
                clipStack.remove(clipStack.size() - 1);
            }
        }
    }

    public void flushPendingText() {
    }

    private void appendText(GraphicsCommand.Text text) {
        builder.addText(
                text.getText(),
                text.getX(),
                text.getY(),
                text.getFontName(),
                text.getFontSize(),
                text.getFill(),
                text.getCtm(),
                text.getGlyphAdvances(),
                text.getClipStack(),
                text.getSourceSpan().orElse(null));
    }

    private static boolean textBaselineOverlapsImage(GraphicsCommand.Text text, double[] imageBounds) {
        Matrix ctm = text.getCtm();
        double x = ctm.transformX(text.getX(), text.getY());
        double y = ctm.transformY(text.getX(), text.getY());
        double width = textWidth(text);
        double pad = Math.max(1.0, text.getFontSize() * 0.15);
        double top = Math.min(y, y - text.getFontSize());
        double bottom = Math.max(y, y - text.getFontSize());
        return x - pad <= imageBounds[2]
                && x + width + pad >= imageBounds[0]
                && top <= imageBounds[3] + pad
                && bottom >= imageBounds[1] - pad;
    }

    private static double textWidth(GraphicsCommand.Text text) {
        double[] advances = text.getGlyphAdvances();
        if (advances != null && advances.length > 0) {
            double width = 0;
            int count = Math.min(text.getText().length(), advances.length);
            for (int i = 0; i < count; i++) {
                width += advances[i];
            }
            return width;
        }
        return Math.max(text.getFontSize() * 0.55 * text.getText().length(), text.getFontSize() * 0.5);
    }

    private static double[] imageBounds(Matrix display, int width, int height) {
        double[][] corners = {{0, 0}, {width, 0}, {0, height}, {width, height}};
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (double[] corner : corners) {
            double u = corner[0] / width;
            double v = 1.0 - corner[1] / height;
            double x = display.transformX(u, v);
            double y = display.transformY(u, v);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return new double[] {minX, minY, maxX, maxY};
    }
}
