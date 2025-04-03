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
    void initialize(double llx, double lly, double urx, double ury);
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
    
    // --- Additional Transformation Methods ---
    void scale(double sx, double sy); // Scale transformation
    void translate(double tx, double ty); // Translate transformation
    void saveGraphicsState(); // Save current graphics state
    void restoreGraphicsState(); // Restore previously saved graphics state

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
    
    // --- Text Support ---
    void beginText(); // Handle BT operator
    void endText();   // Handle ET operator
    void setFont(String fontName, double fontSize);  // Handle /Font selection and size
    void showText(String text); // Basic text showing operation
    void moveText(double x, double y); // Move text position
    void setTextMatrix(double[] matrix); // Set text transformation matrix

    java.awt.geom.Point2D getCurrentPoint();

    // ---> Add method to check if the current path is empty/degenerate <--- 
    boolean isCurrentPathEffectivelyEmpty();

    void setRGBColor(double r, double g, double b);
    void clip(boolean useEvenOddRule);
} 