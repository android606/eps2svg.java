package com.convert2web;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.IOException;

/**
 * Interface defining the graphical operations required by the EpsInterpreter.
 * Implementations will handle the actual rendering (e.g., to SVG).
 */
public interface GraphicsHandler {

    // --- Document/Output ---
    void initialize(double[] bbox); // Initialize with bounding box (if available)
    void writeToFile(String outputPath) throws Exception; // Finalize and write output

    // --- Path Construction ---
    void newPath();
    void moveTo(double x, double y);
    void lineTo(double x, double y);
    void curveTo(double x1, double y1, double x2, double y2, double x3, double y3);
    void closePath();

    // --- Painting ---
    void fill(); // Non-zero winding rule
    void eoFill(); // Even-odd winding rule
    void stroke();

    // --- Graphics State ---
    void gsave(); // Save graphics state
    void grestore(); // Restore graphics state
    void concatMatrix(double[] matrix); // Concatenate CTM

    // --- State Attributes ---
    void setGrayFill(double gray); // 0.0 - 1.0
    void setGrayStroke(double gray); // 0.0 - 1.0
    void setRGBColorFill(double r, double g, double b); // 0.0 - 1.0
    void setRGBColorStroke(double r, double g, double b); // 0.0 - 1.0
    void setCMYKColorFill(double c, double m, double y, double k); // 0.0 - 1.0
    void setCMYKColorStroke(double c, double m, double y, double k); // 0.0 - 1.0

    void setLineWidth(double width);
    void setLineCap(int cap); // 0=butt, 1=round, 2=square
    void setLineJoin(int join); // 0=miter, 1=round, 2=bevel
    void setMiterLimit(double limit);
    void setDash(double[] pattern, double offset);
    
    // TODO: Add methods for clip, eoclip, fonts, text, etc. as needed

    java.awt.geom.Point2D getCurrentPoint();

    // ---> Add method to check if the current path is empty/degenerate <--- 
    boolean isCurrentPathEffectivelyEmpty();

} 