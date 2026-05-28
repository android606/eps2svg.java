package com.convert2web.ps;

import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.Path;
import com.convert2web.model.PathSegment;
import com.convert2web.model.StrokeStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Graphics state snapshot for {@code gsave}/{@code grestore}.
 */
public final class VmGraphicsState {
    private Matrix ctm = Matrix.identity();
    private PaintStyle fillColor = PaintStyle.gray(0);
    private PaintStyle strokeColor = PaintStyle.gray(0);
    private StrokeStyle strokeStyle = StrokeStyle.defaults();
    private double lineWidth = 1.0;
    private String fontName = "Helvetica";
    private double fontSize = 12.0;
    private Matrix textMatrix = Matrix.identity();
    private final List<PathSegment> pathSegments = new ArrayList<>();
    private double currentX;
    private double currentY;
    private boolean hasCurrentPoint;
    private int compoundPathDepth;
    private int clipDepth;

    public VmGraphicsState copy() {
        VmGraphicsState copy = new VmGraphicsState();
        copy.ctm = ctm;
        copy.fillColor = fillColor;
        copy.strokeColor = strokeColor;
        copy.strokeStyle = strokeStyle;
        copy.lineWidth = lineWidth;
        copy.fontName = fontName;
        copy.fontSize = fontSize;
        copy.textMatrix = textMatrix;
        copy.pathSegments.addAll(pathSegments);
        copy.currentX = currentX;
        copy.currentY = currentY;
        copy.hasCurrentPoint = hasCurrentPoint;
        copy.compoundPathDepth = compoundPathDepth;
        copy.clipDepth = clipDepth;
        return copy;
    }

    public void beginCompoundPath() {
        compoundPathDepth++;
    }

    public void endCompoundPath() {
        if (compoundPathDepth > 0) {
            compoundPathDepth--;
        }
    }

    public boolean isInCompoundPath() {
        return compoundPathDepth > 0;
    }

    public int getClipDepth() {
        return clipDepth;
    }

    public void incrementClipDepth() {
        clipDepth++;
    }

    public Matrix getCtm() {
        return ctm;
    }

    public void setCtm(Matrix ctm) {
        this.ctm = ctm;
    }

    public PaintStyle getFillColor() {
        return fillColor;
    }

    public void setFillColor(PaintStyle fillColor) {
        this.fillColor = fillColor;
    }

    public PaintStyle getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(PaintStyle strokeColor) {
        this.strokeColor = strokeColor;
    }

    public StrokeStyle getStrokeStyle() {
        return strokeStyle;
    }

    public void setStrokeStyle(StrokeStyle strokeStyle) {
        this.strokeStyle = strokeStyle;
        this.lineWidth = strokeStyle.getLineWidth();
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = fontSize;
    }

    public Matrix getTextMatrix() {
        return textMatrix;
    }

    public void setTextMatrix(Matrix textMatrix) {
        this.textMatrix = textMatrix == null ? Matrix.identity() : textMatrix;
    }

    public void setLineWidth(double lineWidth) {
        this.lineWidth = lineWidth;
        this.strokeStyle = new StrokeStyle(
                lineWidth,
                strokeStyle.getLineCap(),
                strokeStyle.getLineJoin(),
                strokeStyle.getMiterLimit(),
                strokeStyle.getDashPattern(),
                strokeStyle.getDashOffset());
    }

    public void clearPath() {
        pathSegments.clear();
        hasCurrentPoint = false;
    }

    /** Drops a lone {@code moveto} left for text positioning so it is not painted later. */
    public void clearIfOnlyMoveToPath() {
        if (pathSegments.size() == 1 && pathSegments.get(0) instanceof PathSegment.MoveTo) {
            clearPath();
        }
    }

    public Path snapshotPath() {
        return new Path(new ArrayList<>(pathSegments));
    }

    public void appendRectangle(double x, double y, double width, double height) {
        clearPath();
        moveTo(x, y);
        lineTo(x + width, y);
        lineTo(x + width, y + height);
        lineTo(x, y + height);
        closePath();
    }

    public void moveTo(double x, double y) {
        currentX = x;
        currentY = y;
        hasCurrentPoint = true;
        pathSegments.add(new PathSegment.MoveTo(x, y));
    }

    public void lineTo(double x, double y) {
        requireCurrentPoint();
        pathSegments.add(new PathSegment.LineTo(x, y));
        currentX = x;
        currentY = y;
    }

    public void curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        requireCurrentPoint();
        pathSegments.add(new PathSegment.CurveTo(x1, y1, x2, y2, x3, y3));
        currentX = x3;
        currentY = y3;
    }

    public void closePath() {
        pathSegments.add(new PathSegment.Close());
    }

    public double getCurrentX() {
        requireCurrentPoint();
        return currentX;
    }

    public double getCurrentY() {
        requireCurrentPoint();
        return currentY;
    }

    public void currentPoint(double[] out) {
        requireCurrentPoint();
        out[0] = ctm.transformX(currentX, currentY);
        out[1] = ctm.transformY(currentX, currentY);
    }

    public void appendArc(double x, double y, double radius, double ang1, double ang2, boolean clockwise) {
        double startX = x + radius * Math.cos(Math.toRadians(ang1));
        double startY = y + radius * Math.sin(Math.toRadians(ang1));
        if (!hasCurrentPoint) {
            moveTo(startX, startY);
        }
        int steps = Math.max(8, (int) Math.ceil(Math.abs(ang2 - ang1) / 15.0));
        double start = Math.toRadians(ang1);
        double end = Math.toRadians(ang2);
        double sweep = end - start;
        if (clockwise && sweep > 0) {
            sweep -= 2 * Math.PI;
        } else if (!clockwise && sweep < 0) {
            sweep += 2 * Math.PI;
        }
        for (int i = 1; i <= steps; i++) {
            double t = start + sweep * i / steps;
            lineTo(x + radius * Math.cos(t), y + radius * Math.sin(t));
        }
    }

    private void requireCurrentPoint() {
        if (!hasCurrentPoint) {
            throw new PostScriptVmException("nocurrentpoint");
        }
    }
}
