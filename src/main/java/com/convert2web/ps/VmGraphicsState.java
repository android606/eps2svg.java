package com.convert2web.ps;

import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.StrokeStyle;

/**
 * Graphics state snapshot for {@code gsave}/{@code grestore}.
 */
public final class VmGraphicsState {
    private Matrix ctm = Matrix.identity();
    private PaintStyle fillColor = PaintStyle.gray(0);
    private PaintStyle strokeColor = PaintStyle.gray(0);
    private StrokeStyle strokeStyle = StrokeStyle.defaults();
    private double lineWidth = 1.0;

    public VmGraphicsState copy() {
        VmGraphicsState copy = new VmGraphicsState();
        copy.ctm = ctm;
        copy.fillColor = fillColor;
        copy.strokeColor = strokeColor;
        copy.strokeStyle = strokeStyle;
        copy.lineWidth = lineWidth;
        return copy;
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
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(double lineWidth) {
        this.lineWidth = lineWidth;
    }
}
