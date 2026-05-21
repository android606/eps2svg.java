package com.convert2web.ps;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocumentBuilder;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.Path;
import com.convert2web.model.PathBounds;
import com.convert2web.model.WindingRule;

/**
 * Appends {@link com.convert2web.model.GraphicsCommand}s from VM graphics state.
 */
public final class EpsDocumentRecorder {
    private final EpsDocumentBuilder builder;

    public EpsDocumentRecorder(EpsDocumentBuilder builder) {
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
        if (isLargeBackgroundFill(path, ctm, builder.getBoundingBox())) {
            builder.addFillAtFront(path, fill, windingRule, ctm);
        } else {
            builder.addFill(path, fill, windingRule, ctm);
        }
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
        builder.addStroke(path, state.getStrokeColor(), state.getStrokeStyle(), state.getCtm());
        state.clearPath();
    }

    public void recordClip(VmGraphicsState state, WindingRule windingRule) {
        Path path = state.snapshotPath();
        if (path.isEmpty()) {
            state.clearPath();
            return;
        }
        builder.addClip(path, windingRule, state.getCtm());
        state.clearPath();
    }
}
