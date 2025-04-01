package com.convert2web;

import java.io.*;
import java.util.*;
import org.apache.xmlgraphics.ps.dsc.DSCException;
import org.apache.xmlgraphics.ps.dsc.DSCParser;
import org.apache.xmlgraphics.ps.dsc.DSCParserConstants;
import org.apache.xmlgraphics.ps.dsc.events.DSCCommentBoundingBox;
import org.apache.xmlgraphics.ps.dsc.events.DSCEvent;
import org.apache.xmlgraphics.ps.dsc.events.DSCHeaderComment;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.Locale; // Required for String.format locale independence
import java.awt.geom.Point2D; // Import Point2D
import java.awt.geom.Rectangle2D;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.logging.ConsoleHandler; // Added import

public class EpsToSvgConverter {
    private static int clipIdCounter = 0;
    private static Map<String, String> clipPathMap = new HashMap<>();

    // NEW: Inner class to hold path data and style
    private static class SvgPath {
        String pathData;
        String style;
        double[] ctm; // CTM active when this path was created

        SvgPath(String pathData, String style, double[] ctm) {
            this.pathData = pathData;
            this.style = style;
            this.ctm = ctm.clone();
        }
    }

    private static class GraphicsState implements Cloneable {
        double[] ctm; // Current Transformation Matrix [a b c d tx ty]
        double lineWidth;
        String strokeColor;
        String fillColor;
        // NEW Graphics State properties
        double lineCap;    // 0=butt, 1=round, 2=square
        double lineJoin;   // 0=miter, 1=round, 2=bevel
        double miterLimit;
        double[] dashArray; // null if solid
        double dashOffset;
        // End NEW
        double[] bbox; // Bounding box [llx lly urx ury]
        public String clipId = null; // The ID of the current clip path, if any
        public boolean useEvenOddFill = false; // By default use non-zero winding rule
        
        GraphicsState(double[] bbox) {
            this.ctm = new double[]{1, 0, 0, 1, 0, 0}; // Initialize CTM to identity
            this.lineWidth = 1.0;
            this.strokeColor = "rgb(0,0,0)"; // Default black stroke
            this.fillColor = "rgb(0,0,0)";   // Default black fill
            // Initialize NEW properties
            this.lineCap = 0;
            this.lineJoin = 0;
            this.miterLimit = 10.0; // Default miter limit in PostScript/PDF/SVG
            this.dashArray = null;
            this.dashOffset = 0;
            // End Initialize NEW
            this.bbox = (bbox != null) ? bbox.clone() : null;
        }
        
        @Override
        public GraphicsState clone() {
            try {
                GraphicsState clone = (GraphicsState) super.clone();
                // Deep copy the arrays
                clone.ctm = this.ctm.clone();
                // Clone dash array if it exists
                clone.dashArray = (this.dashArray != null) ? this.dashArray.clone() : null;
                // bbox is shared since it doesn't change after initialization
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("Cloning failed", e);
            }
        }
        
        // NEW: Helper to format RGB
        private String rgbColor(double r, double g, double b) {
            r = Math.max(0, Math.min(1, r));
            g = Math.max(0, Math.min(1, g));
            b = Math.max(0, Math.min(1, b));
            return String.format(Locale.ROOT, "rgb(%d,%d,%d)",
                (int)(r * 255), (int)(g * 255), (int)(b * 255));
        }
        
        // NEW: Converts 0-1 range CMYK to "rgb(r,g,b)" string
        public String cmykColor(double c, double m, double y, double k) {
            double r = (1.0 - c) * (1.0 - k);
            double g = (1.0 - m) * (1.0 - k);
            double b = (1.0 - y) * (1.0 - k);
            return rgbColor(r, g, b); // Reuse the rgbColor method for formatting
        }
        
        // NEW: Converts 0-1 range Gray to "rgb(r,g,b)" string
        public String grayColor(double gray) {
            return rgbColor(gray, gray, gray);
        }
        
        public Point2D.Double transformPoint(double x, double y) {
            double[] transformed = transform(x, y);
            return new Point2D.Double(transformed[0], transformed[1]);
        }

        public double[] transform(double x, double y) {
            // Apply CTM transformation
            double[] transformed = new double[2];
            transformed[0] = ctm[0] * x + ctm[2] * y + ctm[4];
            transformed[1] = ctm[1] * x + ctm[3] * y + ctm[5];

            // It should only reflect the PostScript CTM.
            // The global SVG transform will handle Y-flip.
            // double scale = 10.0; // Example scale - REMOVE
            // transformed[0] = (transformed[0] - bbox[0]) * scale; // REMOVE
            // transformed[1] = (bbox[3] - transformed[1]) * scale; // REMOVE

            return transformed;
        }

        // Helper for matrix multiplication: result = a * b
        // Assumes a = [a,b,c,d,e,f] -> [[a,c,e],[b,d,f],[0,0,1]]
        // Assumes b = [a',b',c',d',e',f'] -> [[a',c',e'],[b',d',f'],[0,0,1]]
        // C = A * B
        private double[] multiplyMatrix(double[] a, double[] b) {
            double a_a=a[0], a_b=a[1], a_c=a[2], a_d=a[3], a_e=a[4], a_f=a[5];
            double b_a=b[0], b_b=b[1], b_c=b[2], b_d=b[3], b_e=b[4], b_f=b[5];
            double[] result = new double[6];

            // Rotational/Scaling part (Corrected for C = A * B)
            result[0] = a_a * b_a + a_c * b_b; // c0 = a*a' + c*b'
            result[1] = a_b * b_a + a_d * b_b; // c1 = b*a' + d*b'
            result[2] = a_a * b_c + a_c * b_d; // c2 = a*c' + c*d'
            result[3] = a_b * b_c + a_d * b_d; // c3 = b*c' + d*d'

            // Translational part (Corrected for C = A * B)
            result[4] = a_a * b_e + a_c * b_f + a_e; // c4 = a*e' + c*f' + e
            result[5] = a_b * b_e + a_d * b_f + a_f; // c5 = b*e' + d*f' + f

            return result;
        }

        // Concatenate a matrix with the CTM: CTM = matrix * CTM
        public void concat(double[] matrix) {
            this.ctm = multiplyMatrix(matrix, this.ctm);
        }

        public void translate(double tx, double ty) {
            double[] translationMatrix = {1, 0, 0, 1, tx, ty};
            // CTM = translationMatrix * CTM
            this.ctm = multiplyMatrix(translationMatrix, this.ctm);
        }

        public void scale(double sx, double sy) {
            double[] scaleMatrix = {sx, 0, 0, sy, 0, 0};
            // CTM = scaleMatrix * CTM
             this.ctm = multiplyMatrix(scaleMatrix, this.ctm);
       }

        public void rotate(double angle) {
            double radians = Math.toRadians(angle);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            double[] rotationMatrix = {cos, sin, -sin, cos, 0, 0};
            // CTM = rotationMatrix * CTM
            this.ctm = multiplyMatrix(rotationMatrix, this.ctm);
        }
        
        // Get a copy of the current transformation matrix
        public double[] getCtm() {
             return this.ctm.clone();
        }

        public double[] getBoundingBox() {
            return bbox;
        }
    }
    
    private class PathBuilder {
        private final StringBuilder path = new StringBuilder();
        private boolean empty = true;

        PathBuilder() {
            // REMOVED: this.stateProvider = stateProvider;
        }

        public void moveTo(double x, double y) {
            // Use raw coordinates
            // Point2D.Double transformed = stateProvider.transformPoint(x, y);
            path.append(String.format(Locale.ROOT, "M %.3f,%.3f", x, y));
            empty = false;
        }

        public void lineTo(double x, double y) {
            // Use raw coordinates
            // Point2D.Double transformed = stateProvider.transformPoint(x, y);
            path.append(String.format(Locale.ROOT, " L %.3f,%.3f", x, y));
        }

        public void curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
            // Use raw coordinates
            // Point2D.Double t1 = stateProvider.transformPoint(x1, y1);
            // Point2D.Double t2 = stateProvider.transformPoint(x2, y2);
            // Point2D.Double t3 = stateProvider.transformPoint(x3, y3);
            path.append(String.format(Locale.ROOT, " C %.3f,%.3f %.3f,%.3f %.3f,%.3f",
                x1, y1, x2, y2, x3, y3)); // Use raw x, y
        }
        
        public void closePath() {
            path.append(" Z");
        }
        
        public boolean isEmpty() {
            return empty;
        }

        @Override
        public String toString() {
            return path.toString();
        }
    }
    
    private double viewBoxMinX, viewBoxMinY;
    private double viewBoxWidth, viewBoxHeight;
    private double documentHeight; // Store document height for Y-coordinate flipping
    private Stack<GraphicsState> gsStack;
    private Stack<Double> stack;
    private GraphicsState currentState;
    private PathBuilder currentPath;
    private List<SvgPath> paths; // List type is SvgPath
    private boolean pathStarted;
    private double lastX, lastY;
    private double[] ctmAtPathStart; // NEW: Store CTM at the start of a path
    private boolean justProcessedArrayEndMarker = false; // Flag for setdash '[] 0 d' detection
    private boolean processBounds = false; // NEW: Flag to control bounds calculation

    // Fields for calculated bounding box
    private double overallMinX, overallMinY, overallMaxX, overallMaxY;
    private double overallMaxStrokeWidth;
    private boolean boundsInitialized;

    public EpsToSvgConverter() {
        // Removed initialization here, will be done in convert
    }

    // Helper method to check for identity matrix (within tolerance)
    private boolean isIdentityMatrix(double[] m) {
        if (m == null || m.length != 6) return false;
        double tolerance = 1e-6;
        return Math.abs(m[0] - 1.0) < tolerance && Math.abs(m[1]) < tolerance &&
               Math.abs(m[2]) < tolerance && Math.abs(m[3] - 1.0) < tolerance &&
               Math.abs(m[4]) < tolerance && Math.abs(m[5]) < tolerance;
    }

    public void convert(String inputFile, String outputFile) throws IOException {
        // --- Initialization ---
        gsStack = new Stack<>();
        stack = new Stack<>();
        paths = new ArrayList<>();
        currentPath = new PathBuilder();
        pathStarted = false;
        lastX = lastY = 0;
        justProcessedArrayEndMarker = false; // Reset flag
        currentState = new GraphicsState(null); // Initialize ONCE with null bbox and identity CTM
        // Explicitly ensure CTM is identity BEFORE parsing starts
        currentState.ctm = new double[]{1, 0, 0, 1, 0, 0}; 
        debug("Initial CTM reset to identity before parsing."); 
        double[] foundBbox = null; // Temporary storage for BBox
        // Initialize calculated bounds tracking
        overallMinX = Double.MAX_VALUE;
        overallMinY = Double.MAX_VALUE;
        overallMaxX = Double.MIN_VALUE;
        overallMaxY = Double.MIN_VALUE;
        overallMaxStrokeWidth = 1.0; // Default stroke width
        boundsInitialized = false;
        debug("Initial CTM: " + java.util.Arrays.toString(currentState.ctm));

        // --- Single Pass Processing ---
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmedLine = line.trim();

                // Check for BoundingBox
                if (trimmedLine.startsWith("%%BoundingBox:")) {
                    try {
                        String[] parts = trimmedLine.substring("%%BoundingBox:".length()).trim().split("\\s+");
                        if (parts.length == 4) {
                            foundBbox = new double[] {
                                Double.parseDouble(parts[0]),
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3])
                            };
                             debug("Found/Updated %%BoundingBox: " + java.util.Arrays.toString(foundBbox)); // Log the found values
                        } else {
                             debug("Invalid %%BoundingBox format at line " + lineNumber + ": " + trimmedLine);
                        }
                    } catch (NumberFormatException e) {
                        debug("Error parsing %%BoundingBox at line " + lineNumber + ": " + trimmedLine);
                        foundBbox = null; // Reset if error occurs
                    }
                } else if (trimmedLine.equals("%%EndSetup")) {
                    // Reset CTM to identity after setup section, before main content
                    currentState.ctm = new double[]{1, 0, 0, 1, 0, 0};
                    debug("CTM reset to identity after %%EndSetup.");
                    processBounds = true; // NEW: Start processing bounds now
                }

                // Ignore comment lines entirely for token processing
                if (trimmedLine.startsWith("%")) {
                    continue;
                }
                
                // Process all tokens on the line (modifies currentState including CTM)
                // Split carefully to handle potential operators adjacent to numbers/delimiters
                 String[] tokens = trimmedLine.split("\\s+|(?<=[()\\[\\]])|(?=[()\\[\\]])"); // Split by whitespace, keep delimiters (), []
                 for (String token : tokens) {
                     if (!token.isEmpty()) { // Process non-empty tokens
                         processToken(token);
                         // Reset the ']' flag after processing the token *after* ']'
                         if (!token.equals("]")) {
                            justProcessedArrayEndMarker = false;
                         }
                     }
                 }
            }
        }

        // --- Finalization ---
        // Ensure BoundingBox was found
        if (foundBbox == null) {
            throw new IOException("Could not find valid %%BoundingBox in EPS file: " + inputFile);
        }

        // Log the final BBox values right before using them
        debug("Final BBox values used for SVG: " + java.util.Arrays.toString(foundBbox));
        
        // --- Calculate final bounds from content ---
        double[] calculatedBbox;
        if (boundsInitialized) {
            double padding = overallMaxStrokeWidth / 2.0;
            calculatedBbox = new double[] {
                overallMinX - padding,
                overallMinY - padding,
                overallMaxX + padding,
                overallMaxY + padding
            };
            debug("Calculated BBox from content (incl. padding): " + java.util.Arrays.toString(calculatedBbox));
        } else {
            debug("Warning: No drawing commands found to calculate bounds, falling back to %%BoundingBox.");
            // Fallback to parsed BBox if no drawing happened
            if (foundBbox == null) {
                 throw new IOException("Could not determine bounding box from either %%BoundingBox or drawing content.");
            }
            calculatedBbox = foundBbox.clone(); 
        }

        // Set the final bounding box on the state AFTER all processing
        currentState.bbox = calculatedBbox; // Use calculated (or fallback) bounds
        debug("Final BBox set. CTM before writing SVG: " + java.util.Arrays.toString(currentState.ctm));

        // --- Write Output ---
        writeSvgFile(outputFile, calculatedBbox); // Pass calculated (or fallback) bounds to writeSvgFile
    }

    private void writeSvgFile(String outputFile, double[] bbox) throws IOException {
        // Log the bbox received by this function
        debug("writeSvgFile received bbox: " + java.util.Arrays.toString(bbox)); 

        // Use the passed bbox for viewbox calculations
        viewBoxMinX = bbox[0];
        viewBoxMinY = bbox[1];
        double urx = bbox[2];
        double ury = bbox[3];
        viewBoxWidth = urx - viewBoxMinX;
        viewBoxHeight = ury - viewBoxMinY;
        // documentHeight might be needed for y-flip logic, but SVG handles coordinate system via viewBox
        // Ensure width and height are positive
        if (viewBoxWidth <= 0 || viewBoxHeight <= 0) {
            System.err.printf("Warning: Calculated SVG dimensions non-positive (w=%.2f, h=%.2f). Adjusting viewBox.%n", viewBoxWidth, viewBoxHeight);
            viewBoxWidth = (viewBoxWidth <= 0) ? 1.0 : viewBoxWidth;
            viewBoxHeight = (viewBoxHeight <= 0) ? 1.0 : viewBoxHeight;
             // Keep original viewBoxMinX/Y for coordinate mapping
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            // Write SVG header
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\r\n");
            // Write SVG tag with attributes on a single line
             writer.write(String.format(Locale.ROOT,
                 "<svg id=\"svg1\" version=\"1.1\" width=\"%.6f\" height=\"%.6f\" viewBox=\"%.3f %.3f %.3f %.3f\" xmlns=\"http://www.w3.org/2000/svg\">\r\n",
                 viewBoxWidth, viewBoxHeight, // Use calculated dimensions for width/height
                  viewBoxMinX, viewBoxMinY, viewBoxWidth, viewBoxHeight)); // Use calculated viewbox
            // Write the <g> tag WITHOUT the global transform
            writer.write(" <g id=\"g1\">\r\n");

            // Define SVG transform matrices
            double[] flipMatrix = {1, 0, 0, -1, 0, 0}; // Y-flip
            double translateY = viewBoxMinY + ury; // Y-translation (minY + maxY)
            double[] translateMatrix = {1, 0, 0, 1, 0, translateY};

            // Write paths
             for (int i = 0; i < paths.size(); i++) {
                 SvgPath svgPath = paths.get(i);
                 writer.write(String.format(Locale.ROOT, "   <path id=\"path%d\"\r\n", i + 1));
                 writer.write(String.format(Locale.ROOT, "     d=\"%s\"\r\n", svgPath.pathData));
                if (svgPath.style != null && !svgPath.style.isEmpty()) {
                    writer.write(String.format(Locale.ROOT, "     style=\"%s\"\r\n", svgPath.style));
                }
                // Calculate the final transform matrix: Translate * Flip * StoredCTM
                double[] storedCtm = svgPath.ctm; // Get CTM stored with the path
                
                // --- Correct Order: (Translate * Flip) * CTM ---
                // 1. Calculate Translate * Flip (svgBaseTransform = T * F)
                // T = [1, 0, 0, 1, 0, ty], F = [1, 0, 0, -1, 0, 0]
                // svgBaseTransform = [1*1+0*0, 1*0+0*(-1), 0*1+1*0, 0*0+1*(-1), 1*0+0*0+0, 1*0+0*0+ty]
                //                  = [1, 0, 0, -1, 0, ty]
                double[] svgBaseTransform = {1, 0, 0, -1, 0, translateY};

                // 2. Multiply svgBaseTransform * storedCtm (finalMatrix = svgBaseTransform * CTM)
                // Let svgBaseTransform = [a,b,c,d,e,f] = [1, 0, 0, -1, 0, ty]
                // Let storedCtm        = [a',b',c',d',e',f']
                double ctm_a = storedCtm[0], ctm_b = storedCtm[1], ctm_c = storedCtm[2];
                double ctm_d = storedCtm[3], ctm_e = storedCtm[4], ctm_f = storedCtm[5];

                double[] finalMatrix = new double[6];
                // C = A * B --> finalMatrix = svgBaseTransform * storedCtm
                // a=1, b=0, c=0, d=-1, e=0, f=translateY
                finalMatrix[0] = 1 * ctm_a + 0 * ctm_b;        // a*a' + c*b'
                finalMatrix[1] = 0 * ctm_a + (-1) * ctm_b;     // b*a' + d*b'
                finalMatrix[2] = 1 * ctm_c + 0 * ctm_d;        // a*c' + c*d'
                finalMatrix[3] = 0 * ctm_c + (-1) * ctm_d;     // b*c' + d*d'
                finalMatrix[4] = 1 * ctm_e + 0 * ctm_f + 0;  // a*e' + c*f' + e
                finalMatrix[5] = 0 * ctm_e + (-1) * ctm_f + translateY; // b*e' + d*f' + f

                // Format and write transform matrix if it's not identity
                boolean isIdentity = isIdentityMatrix(finalMatrix); // Use the helper method
                debug(String.format(Locale.ROOT,
                    "Path %d: Final Matrix = %s, isIdentity = %b",
                    i + 1, java.util.Arrays.toString(finalMatrix), isIdentity)); // Added logging

                if (!isIdentity) { 
                    writer.write(String.format(Locale.ROOT,
                        "     transform=\"matrix(%.6f %.6f %.6f %.6f %.6f %.6f)\"\r\n",
                        finalMatrix[0], finalMatrix[1], finalMatrix[2], finalMatrix[3], finalMatrix[4], finalMatrix[5]));
                 }
                writer.write("   />\r\n");
            }

            writer.write(" </g>\r\n");
            writer.write("</svg>\r\n");
        }
         debug("SVG file written to: " + outputFile);
    }

    // Helper to update overall bounds based on transformed point
    private void updateBounds(double x, double y) {
        // NEW: Only process bounds after %%EndSetup
        if (!processBounds) {
            return; // Do not update bounds before main content starts
        }

        double[] currentTransform = currentState.getCtm(); // Get current CTM
        if (currentTransform == null) {
             debug("Warning: updateBounds called with null currentState CTM. Using identity.");
             currentTransform = new double[]{1, 0, 0, 1, 0, 0}; // Use identity as fallback
        }
        // Apply the current transformation matrix
        double tx = currentTransform[0] * x + currentTransform[2] * y + currentTransform[4];
        double ty = currentTransform[1] * x + currentTransform[3] * y + currentTransform[5];
        
        // Log coordinates before and after transformation
        debug(String.format(Locale.ROOT, "updateBounds: Input(%.3f, %.3f) -> Transformed(%.3f, %.3f) with CTM %s", 
                              x, y, tx, ty, java.util.Arrays.toString(currentTransform)));

        if (!boundsInitialized) {
            overallMinX = tx;
            overallMinY = ty;
            overallMaxX = tx;
            overallMaxY = ty;
            boundsInitialized = true;
        } else {
            overallMinX = Math.min(overallMinX, tx);
            overallMinY = Math.min(overallMinY, ty);
            overallMaxX = Math.max(overallMaxX, tx);
            overallMaxY = Math.max(overallMaxY, ty);
        }
         // Log bounds update for debugging
         // debug(String.format(Locale.ROOT, "Updated bounds with point (%.2f, %.2f) -> (%.2f, %.2f): min(%.2f, %.2f) max(%.2f, %.2f)", 
         //       x, y, tx, ty, overallMinX, overallMinY, overallMaxX, overallMaxY));
    }

    private void processToken(String token) {
        if (token.equals("]")) { // Array end marker
            debug("Token: ] (Array End Marker)");
            justProcessedArrayEndMarker = true; // Set flag for potential '[] 0 d'
            return; // Don't process ']' further
        }

        // --- Drawing Operators ---
        if (token.equals("moveto") || token.equals("m")) {
             if (stack.size() >= 2) {
                 double y = stack.pop();
                 double x = stack.pop();

                 // DEBUG: Check CTM right before potential storage
                 debug("CTM before moveto (m): " + java.util.Arrays.toString(currentState.ctm));

                 // Start a new path if needed
                 if (!pathStarted || currentPath == null) {
                     currentPath = new PathBuilder();
                     pathStarted = true;
                     this.ctmAtPathStart = currentState.getCtm(); // Capture CTM at moveto
                 }
                 currentPath.moveTo(x, y);
                 updateBounds(x, y); // Update bounds using current CTM
                 lastX = x;
                 lastY = y;
                 debug("moveto: " + x + ", " + y);
             } else {
                 debug("moveto: insufficient parameters");
             }
        } else if (token.equals("lineto") || token.equals("l")) {
             if (stack.size() >= 2) {
                if (pathStarted && currentPath != null) {
                    double y = stack.pop();
                    double x = stack.pop();
                    updateBounds(x, y); // Update bounds using current CTM
                    currentPath.lineTo(x, y);
                    lastX = x;
                    lastY = y;
                    debug("lineto: (" + x + "," + y + ")");
                } else {
                     debug("lineto: path not started");
                    // Pop operands anyway to keep stack clean? Or error?
                    if (stack.size() >= 2) { stack.pop(); stack.pop(); }
                 }
             } else {
                debug("lineto: insufficient parameters");
            }
        } else if (token.equals("curveto") || token.equals("c")) {
            if (stack.size() >= 6) {
                if (pathStarted && currentPath != null) {
                    double y3 = stack.pop();
                    double x3 = stack.pop();
                    double y2 = stack.pop();
                    double x2 = stack.pop();
                    double y1 = stack.pop();
                    double x1 = stack.pop();
                    currentPath.curveTo(x1, y1, x2, y2, x3, y3);
                    updateBounds(x1, y1);
                    updateBounds(x2, y2);
                    updateBounds(x3, y3);
                    lastX = x3;
                    lastY = y3;
                    debug("curveto: (" + x1 + "," + y1 + "), (" + x2 + "," + y2 + "), (" + x3 + "," + y3 + ")");
                } else {
                     debug("curveto: path not started");
                      // Pop operands anyway?
                     if (stack.size() >= 6) { for(int i=0;i<6;i++) stack.pop(); }
                }
            } else {
                debug("curveto: insufficient parameters");
            }
        } else if (token.equals("closepath") || token.equals("h") || token.equals("H")) {
             if (pathStarted && currentPath != null) {
                    currentPath.closePath();
                debug("closepath");
            } else {
                debug("closepath: path not started");
            }
        } else if (token.equals("fill") || token.equals("f") || token.equals("*f")) {
            if (pathStarted && currentPath != null && !currentPath.isEmpty()) {
                String pathData = currentPath.toString();
                if (!processBounds) { // Check if we are before %%EndSetup
                    logger.warning("Ignoring path generated before main content (fill): " + pathData);
                } else if (!pathData.matches("M [\\d\\.-]+,[\\d\\.-]+(?:\\s+Z)?$") && !pathData.isEmpty()) {
                    // Create style string for fill
                     String fillRule = "nonzero"; // Assuming default fill rule for now
                     String style = String.format(Locale.ROOT, "fill:%s; stroke:none; fill-rule:%s;",
                                                  currentState.fillColor, fillRule);
                     // Use the CTM captured at the start of the path (moveto)
                     paths.add(new SvgPath(pathData, style, this.ctmAtPathStart != null ? this.ctmAtPathStart : currentState.getCtm())); // Use ctmAtPathStart
                     debug("fill: added path with data: " + pathData + " Style: " + style);
                 } else {
                      debug("fill: skipped empty or moveto-only path: " + pathData);
                 }
                currentPath = null; // Path consumed, needs new moveto
                pathStarted = false;
            } else {
                debug("fill: path not started or empty");
            }
         } else if (token.equals("stroke") || token.equals("S") || token.equals("s")) {
             if (pathStarted && currentPath != null && !currentPath.isEmpty()) {
                 String pathData = currentPath.toString();
                  if (!processBounds) { // Check if we are before %%EndSetup
                      logger.warning("Ignoring path generated before main content (stroke): " + pathData);
                  } else if (!pathData.matches("M [\\d\\.-]+,[\\d\\.-]+(?:\\s+Z)?$") && !pathData.isEmpty()) {
                     // Create style string for stroke
                     // TODO: Handle linecap, linejoin, miterlimit, dasharray from currentState
                     String style = String.format(Locale.ROOT, "fill:none; stroke:%s; stroke-width:%.3f;",
                                                  currentState.strokeColor, currentState.lineWidth);
                     // Use the CTM captured at the start of the path (moveto)
                      paths.add(new SvgPath(pathData, style, this.ctmAtPathStart != null ? this.ctmAtPathStart : currentState.getCtm())); // Use ctmAtPathStart
                      debug("stroke: added path with data: " + pathData + " Style: " + style);
                 } else {
                     debug("stroke: skipped empty or moveto-only path: " + pathData);
                 }
                 currentPath = null; // Path consumed, needs new moveto
             } else {
                 debug("stroke: path not started or empty");
             }
         } else if (token.matches("-?\\d*\\.?\\d+(E-?\\d+)?")) { // Regex includes scientific notation
            try {
                stack.push(Double.parseDouble(token));
                 debug("pushed number to stack: " + token);
            } catch (NumberFormatException e) {
                 debug("Error parsing number token: " + token + " - " + e.getMessage());
            }
        } else if (token.equals("concat") || token.equals("cm")) {
            if (stack.size() >= 6) {
                try {
                    // Order: a b c d e f from stack (PostScript)
                    double f = stack.pop();
                    double e = stack.pop();
                    double d = stack.pop();
                    double c = stack.pop();
                    double b = stack.pop();
                    double a = stack.pop();
                    double[] matrix = {a, b, c, d, e, f};
                    currentState.concat(matrix);
                    debug(String.format(Locale.ROOT, "concat/cm: matrix=[" + a + "," + b + "," + c + "," + d + "," + e + "," + f + "]"));
                    debug(String.format(Locale.ROOT, "CTM after concat/cm: %s", java.util.Arrays.toString(currentState.ctm)));
                } catch (Exception e) { // Catch potential stack errors if types mismatch
                    debug("Error processing concat/cm: " + e.getMessage());
                    // Attempt to recover stack? Or maybe just log and continue?
                }
            } else {
                debug("concat/cm: insufficient parameters on stack (" + stack.size() + ")");
            }
        // ... Other token handlers (gsave, grestore, translate, scale, rotate, colors, etc.) ...
        // --- Make sure these handlers correctly modify 'currentState' ---
         } else if (token.equals("gsave") || token.equals("q")) {
            debug("CTM before gsave: " + java.util.Arrays.toString(currentState.ctm));
            gsStack.push(currentState.clone());
            debug("gsave: saved graphics state. Stack depth: " + gsStack.size());
            debug("CTM after gsave (should be same): " + java.util.Arrays.toString(currentState.ctm));
        } else if (token.equals("grestore") || token.equals("Q")) {
            debug("CTM before grestore: " + java.util.Arrays.toString(currentState.ctm));
            if (!gsStack.isEmpty()) {
                currentState = gsStack.pop();
                debug("grestore: restored graphics state. Stack depth: " + gsStack.size());
                debug("CTM after grestore: " + java.util.Arrays.toString(currentState.ctm));
            } else {
                debug("grestore: stack empty");
            }
        } else if (token.equals("translate")) {
            if (stack.size() >= 2) {
                double ty = stack.pop();
                double tx = stack.pop();
                debug(String.format(Locale.ROOT, "CTM before translate(%.3f, %.3f): %s", tx, ty, java.util.Arrays.toString(currentState.ctm)));
                currentState.translate(tx, ty);
                debug(String.format(Locale.ROOT, "CTM after translate: %s", java.util.Arrays.toString(currentState.ctm)));
            } else {
                debug("translate: insufficient parameters");
            }
        } else if (token.equals("scale")) {
            if (stack.size() >= 2) {
                double sy = stack.pop();
                double sx = stack.pop();
                debug(String.format(Locale.ROOT, "CTM before scale(%.3f, %.3f): %s", sx, sy, java.util.Arrays.toString(currentState.ctm)));
                currentState.scale(sx, sy);
                 debug(String.format(Locale.ROOT, "CTM after scale: %s", java.util.Arrays.toString(currentState.ctm)));
           } else {
                debug("scale: insufficient parameters");
            }
        } else if (token.equals("rotate")) {
            if (stack.size() >= 1) {
                double angle = stack.pop();
                debug(String.format(Locale.ROOT, "CTM before rotate(%.3f): %s", angle, java.util.Arrays.toString(currentState.ctm)));
                currentState.rotate(angle);
                 debug(String.format(Locale.ROOT, "CTM after rotate: %s", java.util.Arrays.toString(currentState.ctm)));
           } else {
                debug("rotate: insufficient parameter");
            }
         } else if (token.equals("setlinewidth") || token.equals("w")) {
            if (stack.size() >= 1) {
                currentState.lineWidth = stack.pop();
                overallMaxStrokeWidth = Math.max(overallMaxStrokeWidth, currentState.lineWidth);
                debug("setlinewidth: " + currentState.lineWidth);
            } else {
                 debug("setlinewidth: insufficient parameters");
            }
        } else if (token.equals("setlinecap") || token.equals("J")) {
             if (stack.size() >= 1) {
                 currentState.lineCap = stack.pop();
                 debug("setlinecap: " + currentState.lineCap);
             } else {
                 debug("setlinecap: insufficient parameters");
             }
        } else if (token.equals("setlinejoin") || token.equals("j")) {
            if (stack.size() >= 1) {
                currentState.lineJoin = stack.pop();
                 debug("setlinejoin: " + currentState.lineJoin);
            } else {
                 debug("setlinejoin: insufficient parameters");
            }
         } else if (token.equals("setmiterlimit") || token.equals("M")) {
            if (stack.size() >= 1) {
                currentState.miterLimit = stack.pop();
                 debug("setmiterlimit: " + currentState.miterLimit);
            } else {
                 debug("setmiterlimit: insufficient parameters");
            }
        } else if (token.equals("setdash") || token.equals("d")) {
              // Simplified 'd' handler for '[] 0 d' case primarily
              if (token.equals("d") && stack.size() >= 1) { // Need at least offset for 'd'
                   double offset = stack.peek(); // Peek, don't pop yet
                   // Check the flag set by the ']' token handler
                   if (justProcessedArrayEndMarker && offset == 0.0) {
                        // Assume '[] 0 d' sequence detected
                        stack.pop(); // Pop the offset 0
                        currentState.dashArray = null; // Solid line
                        currentState.dashOffset = 0.0;
                        debug("setdash: Detected [] 0 d sequence, setting solid line.");
                        justProcessedArrayEndMarker = false; // Consume the flag
                   } else {
                        // Actual array + offset handling not implemented
                        // Pop offset, assume array was consumed/ignored earlier
                        offset = stack.pop(); // Pop offset confirmed needed
                        currentState.dashArray = null; // Force solid line as fallback
                        currentState.dashOffset = offset;
                        debug("setdash: Array pattern handling not fully implemented. Offset=" + offset + ". Setting solid line.");
                        justProcessedArrayEndMarker = false; // Consume flag if it was somehow set
                   }
              } else if (token.equals("setdash") && stack.size() >= 2) {
                   // Handle full 'setdash' keyword - more complex stack state needed
                   debug("setdash: Full keyword handling not implemented.");
                   // Simplistic fallback: pop offset and assume solid line
                   double offset = stack.pop();
                   currentState.dashArray = null;
                   currentState.dashOffset = offset;
                   // Need to pop array representation as well - tricky!
                   // stack.pop(); // Attempt to pop assumed array marker/object?
                   justProcessedArrayEndMarker = false; // Reset flag
              } else {
                  debug("setdash: insufficient parameters or unrecognized state.");
                  justProcessedArrayEndMarker = false; // Reset flag
              }

         } else if (token.equals("setcmykcolor") && stack.size() >= 4) {
             // Check if the *next* token implies fill or stroke context, or handle ambiguity
             // For now, assume fill based on 'k' command usually being fill
             double kVal = stack.pop();
             double yVal = stack.pop();
             double mVal = stack.pop();
             double cVal = stack.pop();
             currentState.fillColor = currentState.cmykColor(cVal, mVal, yVal, kVal);
             debug("setcmykcolor (assumed fill): " + currentState.fillColor);
         } else if (token.equals("setgray") && stack.size() >= 1) {
             // Assume fill based on 'g' usually being fill
             double grayVal = stack.pop();
             currentState.fillColor = currentState.grayColor(grayVal);
             debug("setgray (assumed fill): " + currentState.fillColor);

        } else if (token.equals("k")) { // setcmykcolor (fill) - Explicit
           if (stack.size() >= 4) {
                double kVal = stack.pop();
                double yVal = stack.pop();
                double mVal = stack.pop();
                double cVal = stack.pop();
                currentState.fillColor = currentState.cmykColor(cVal, mVal, yVal, kVal);
                debug("setcmykcolor fill (k): " + currentState.fillColor);
            } else {
                debug("setcmykcolor fill (k): insufficient parameters");
            }
        } else if (token.equals("K")) { // setcmykcolor (stroke) - Explicit
           if (stack.size() >= 4) {
                double kVal = stack.pop();
                double yVal = stack.pop();
                double mVal = stack.pop();
                double cVal = stack.pop();
                currentState.strokeColor = currentState.cmykColor(cVal, mVal, yVal, kVal);
                debug("setcmykcolor stroke (K): " + currentState.strokeColor);
            } else {
                debug("setcmykcolor stroke (K): insufficient parameters");
            }
        } else if (token.equals("g")) { // setgray (fill) - Explicit
            if (stack.size() >= 1) {
                double grayVal = stack.pop();
                currentState.fillColor = currentState.grayColor(grayVal);
                debug("setgray fill (g): " + currentState.fillColor);
            } else {
                debug("setgray fill (g): insufficient parameters");
            }
        } else if (token.equals("G")) { // setgray (stroke) - Explicit
            if (stack.size() >= 1) {
                double grayVal = stack.pop();
                currentState.strokeColor = currentState.grayColor(grayVal);
                debug("setgray stroke (G): " + currentState.strokeColor);
            } else {
                debug("setgray stroke (G): insufficient parameters");
            }
        // --- Illustrator specific / potentially problematic ---
        } else if (token.equals("u") || token.equals("U") || token.equals("*u") || token.equals("*U")) {
            // Custom Illustrator command - often wraps paths. Might involve gsave/grestore.
             debug("Unhandled token: " + token + " (Illustrator path grouping?)");
             // Should we gsave/grestore here? Depends on specific definition.
        } else if (token.equals("A")) { // Seen in logs, just pops one operand
             if (!stack.isEmpty()) {
                 stack.pop();
                  debug("Handled token: " + token + " (pop)");
             } else {
                 debug("Unhandled token: " + token + " (pop - stack empty)");
             }
         } else if (token.equals("O")) { // Seen in logs, maybe Illustrator setup
             // Don't clear stack - let operands be consumed by subsequent commands if needed
             debug("Unhandled token: O (Likely Illustrator setup)");
         } else if (token.equals("D") || token.equals("d1") || token.equals("dX")) {
              // Seems related to path drawing or state setting in logs
              debug("Unhandled token: " + token + " (Potentially drawing related)");
              // Often preceded by a number (e.g., 1 D) - maybe pop it?
              if (!stack.isEmpty() && (token.equals("D") || token.equals("d1"))) {
                   // Maybe pop one operand based on logs?
                   // stack.pop();
                   // debug("Popped one operand based on D/d1 token heuristic");
              }

        // --- Default / Unhandled ---
        } else {
            // Try parsing as number LAST
            try {
                stack.push(Double.parseDouble(token));
                debug("pushed number to stack: " + token);
            } catch (NumberFormatException e) {
                // If it's not a common keyword, log as unhandled
                if (!isCommonPsKeyword(token)) {
                    debug("Unhandled token: " + token);
                }
                 // Otherwise, assume it's a handled keyword (like 'def', 'dict') and ignore
            }
            justProcessedArrayEndMarker = false; // Reset flag if token wasn't ']'
        }
    }

    // Helper to avoid logging every known PS keyword as "unhandled"
    private boolean isCommonPsKeyword(String token) {
        switch (token) {
            case "def": case "dict": case "begin": case "end": case "if": case "ifelse":
            case "pop": case "exch": case "dup": case "copy": case "index": case "get":
            case "put": case "true": case "false": case "null": case "type": case "cvx":
            case "cvi": case "string": case "length": case "array": case "astore": case "aload":
            case "readonly": case "executeonly": case "noaccess": case "currentdict":
            case "currentfile": case "setpacking": case "packedarray": case "where":
            // Add more common keywords as needed
                return true;
            default:
                return false;
        }
    }

    // --- Logging ---
    private static final Logger logger = Logger.getLogger(EpsToSvgConverter.class.getName());
    private void debug(String message) {
        logger.log(Level.FINE, message); // Use FINE level for debug messages
    }

    public static void main(String[] args) {
         // Setup logging level (e.g., FINE for debug, INFO for standard output)
         Logger rootLogger = Logger.getLogger("");
         rootLogger.setLevel(Level.FINE); // Set desired level
         Handler handler = new ConsoleHandler();
         handler.setLevel(Level.FINE); // Ensure handler also allows the level
         // Use a simple formatter that includes the level name
         handler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%4$-7s] %5$s %n";
            @Override
            public synchronized String format(LogRecord lr) {
                return String.format(format,
                        new java.util.Date(lr.getMillis()),
                        lr.getSourceClassName(), // Optional: class name
                        lr.getLoggerName(),      // Optional: logger name
                        lr.getLevel().getLocalizedName(),
                        formatMessage(lr)
                );
            }
         });
        // Remove existing handlers to avoid duplicate output
        for (Handler h : rootLogger.getHandlers()) {
            rootLogger.removeHandler(h);
        }
        rootLogger.addHandler(handler);

        if (args.length != 2) {
            System.err.println("Usage: java EpsToSvgConverter <input.eps> <output.svg>");
            return;
        }
        EpsToSvgConverter converter = new EpsToSvgConverter();
        try {
            converter.convert(args[0], args[1]);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Conversion failed: " + e.getMessage(), e);
        }
    }
} 