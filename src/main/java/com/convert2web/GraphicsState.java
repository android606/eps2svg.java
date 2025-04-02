package com.convert2web;

import java.awt.geom.Point2D;

public class GraphicsState implements Cloneable {
    private double[] ctm = {1, 0, 0, 1, 0, 0}; // Current Transformation Matrix
    private double[] bbox;
    public double lineWidth = 1.0;
    public String strokeColor = "black"; // Default black
    public String fillColor = "none";    // Default none
    public String clipId = null; // The ID of the current clip path, if any
    public boolean useEvenOddFill = false; // By default use non-zero winding rule

    public GraphicsState(double[] bbox) {
        this.bbox = bbox;
    }

    public Point2D.Double transformPoint(double x, double y) {
        // Apply CTM transformation
        double[] transformed = new double[2];
        transformed[0] = ctm[0] * x + ctm[2] * y + ctm[4];
        transformed[1] = ctm[1] * x + ctm[3] * y + ctm[5];
        return new Point2D.Double(transformed[0], transformed[1]);
    }

    public void scale(double sx, double sy) {
        double[] newCTM = {
            ctm[0] * sx, ctm[1] * sx,
            ctm[2] * sy, ctm[3] * sy,
            ctm[4], ctm[5]
        };
        ctm = newCTM;
    }

    public void translate(double tx, double ty) {
        double[] newCTM = {
            ctm[0], ctm[1],
            ctm[2], ctm[3],
            ctm[4] + tx, ctm[5] + ty
        };
        ctm = newCTM;
    }

    public void rotate(double angle) {
        double cos = Math.cos(Math.toRadians(angle));
        double sin = Math.sin(Math.toRadians(angle));
        double[] newCTM = {
            ctm[0] * cos - ctm[2] * sin,
            ctm[1] * cos - ctm[3] * sin,
            ctm[0] * sin + ctm[2] * cos,
            ctm[1] * sin + ctm[3] * cos,
            ctm[4], ctm[5]
        };
        ctm = newCTM;
    }

    public double[] getBoundingBox() {
        return bbox;
    }

    @Override
    public GraphicsState clone() {
        try {
            GraphicsState clone = (GraphicsState) super.clone();
            clone.ctm = this.ctm.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Cloning failed", e);
        }
    }
} 