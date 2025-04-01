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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EpsToSvgConverter {
    private static int clipIdCounter = 0;
    private static Map<String, String> clipPathMap = new HashMap<>();

    private static class GraphicsState implements Cloneable {
        // Current Transformation Matrix [a b c d tx ty] equivalent to:
        // [a c tx]
        // [b d ty]
        // [0 0 1 ]
        public double[] ctm = {1.0, 0.0, 0.0, 1.0, 0.0, 0.0}; // Identity matrix
        public double lineWidth = 1.0;
        public String strokeColor = "black"; // Default black
        public String fillColor = "none";    // Default none
        private double[] bbox; // BoundingBox (llx, lly, urx, ury)
        public String clipId = null; // The ID of the current clip path, if any
        public boolean useEvenOddFill = false; // By default use non-zero winding rule
        
        public GraphicsState(double[] bbox) {
            this.bbox = (bbox != null && bbox.length == 4) ? bbox : new double[]{0, 0, 1, 1};
        }
        
        @Override
        public GraphicsState clone() {
            try {
                GraphicsState clone = (GraphicsState) super.clone();
                // Deep copy the arrays
                clone.ctm = this.ctm.clone();
                // bbox is shared since it doesn't change after initialization
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("Cloning failed", e);
            }
        }
        
        private String rgbColor(double r, double g, double b) {
            r = Math.max(0, Math.min(1, r));
            g = Math.max(0, Math.min(1, g));
            b = Math.max(0, Math.min(1, b));
            return String.format(Locale.ROOT, "rgb(%d,%d,%d)",
                (int)(r * 255), (int)(g * 255), (int)(b * 255));
        }
        
        // Converts 0-1 range CMYK to "rgb(r,g,b)" string
        public String cmykColor(double c, double m, double y, double k) {
            double r = (1.0 - c) * (1.0 - k);
            double g = (1.0 - m) * (1.0 - k);
            double b = (1.0 - y) * (1.0 - k);
            return rgbColor(r, g, b); // Reuse the rgbColor method for formatting
        }
        
        // Transform from current local PS coordinates to global PS coordinates using the CTM
        double toGlobalX(double localX) {
            // x' = a*x + c*y + tx
            // Note: We assume y=0 for X transformation based on PostScript model for individual coords?
            // Let's refine this: CTM applies to the point (x,y)
            // For simplicity in path building, let's apply the CTM directly there.
            // This method might not be needed, or needs x AND y.
            // Let's transform the point (localX, localY) instead.
            return ctm[0] * localX + ctm[2] * 0 + ctm[4]; // Incorrect if y is non-zero
        }
        double toGlobalY(double localY) {
            // y' = b*x + d*y + ty
            return ctm[1] * 0 + ctm[3] * localY + ctm[5]; // Incorrect if x is non-zero
        }

        // Apply current transformation matrix to a point
        public Point2D.Double transformPoint(double x, double y) {
            double newX = ctm[0] * x + ctm[2] * y + ctm[4];
            double newY = ctm[1] * x + ctm[3] * y + ctm[5];
            return new Point2D.Double(newX, newY);
        }

        // Helper function for matrix multiplication: NewCTM = M * OldCTM
        public void concatMatrix(double[] m) { // m = [a b c d tx ty]
            double a1 = ctm[0], b1 = ctm[1], c1 = ctm[2], d1 = ctm[3], tx1 = ctm[4], ty1 = ctm[5];
            double a2 = m[0], b2 = m[1], c2 = m[2], d2 = m[3], tx2 = m[4], ty2 = m[5];

            // Perform matrix multiplication according to PostScript spec
            // For PostScript, the CTM is applied to the coordinates as [x y 1] * CTM
            // So new matrix is concatenated as new_CTM = old_CTM * matrix
            double new_a = a1 * a2 + c1 * b2;
            double new_b = b1 * a2 + d1 * b2;
            double new_c = a1 * c2 + c1 * d2;
            double new_d = b1 * c2 + d1 * d2;
            double new_tx = a1 * tx2 + c1 * ty2 + tx1;
            double new_ty = b1 * tx2 + d1 * ty2 + ty1;
            
            ctm[0] = new_a;
            ctm[1] = new_b;
            ctm[2] = new_c;
            ctm[3] = new_d;
            ctm[4] = new_tx;
            ctm[5] = new_ty;
        }

        // Transform from global PS coordinates to SVG coordinates within the viewBox
        double mapToSvgX(double globalX) {
            // Global X directly maps to SVG X because viewBox's llx handles the origin
            return globalX;
        }
        double mapToSvgY(double globalY) {
            // Flip Y relative to viewBox origin (lly) and height (ury - lly)
            // y_svg = lly + (ury - y_ps_global)
            return bbox[1] + (bbox[3] - globalY);
        }
    }
    
    private static class PathBuilder {
        private GraphicsState currentGS;
        // Store the *last written* SVG coordinates
        private double lastSvgX = 0, lastSvgY = 0;
        private boolean pathStarted = false;
        private StringBuilder pathData = new StringBuilder();
        
        // Save the last point for relative operations
        private double lastX, lastY;
        private boolean hasLastPoint = false;

        public PathBuilder(GraphicsState gs) {
            this.currentGS = gs;
            this.pathData = new StringBuilder();
            this.pathStarted = false;
        }

        public void updateGraphicsState(GraphicsState newGS) {
            this.currentGS = newGS;
        }

        // Pass bbox for mapping calculation
        public void moveTo(double x, double y, GraphicsState gs, double[] bbox) {
            // 1. Apply internal CTM
            System.out.println("DEBUG moveTo: Input (" + x + "," + y + ")");
            System.out.println("DEBUG CTM: [" + 
                gs.ctm[0] + " " + gs.ctm[1] + " " + 
                gs.ctm[2] + " " + gs.ctm[3] + " " + 
                gs.ctm[4] + " " + gs.ctm[5] + "]");
            
            Point2D.Double transformedPoint = gs.transformPoint(x, y);
            double psX = transformedPoint.getX();
            double psY = transformedPoint.getY();
            System.out.println("DEBUG transformed point: (" + psX + "," + psY + ")");

            // 2. Convert to SVG coordinates with proper bounding box adjustment
            double svgX = psX;
            double svgY = psY;
            
            // Apply proper coordinate transformation for SVG
            // Adjust X coordinate by subtracting the left edge of the bounding box
            svgX = svgX - bbox[0];
            
            // Adjust Y coordinate by inverting it relative to the bounding box height
            // This is because in PostScript Y grows upward, in SVG Y grows downward
            svgY = bbox[3] - svgY;
            
            System.out.println("DEBUG final SVG point: (" + svgX + "," + svgY + ")");

            lastSvgX = svgX;
            lastSvgY = svgY;
            
            // Start a new path - clear previous data
            pathData = new StringBuilder();
            pathData.append(String.format(Locale.ROOT, "M %.6f %.6f", svgX, svgY));
            pathStarted = true;
            
            // Save the last point for relative operations
            lastX = x;
            lastY = y;
            hasLastPoint = true;
        }

        // Pass bbox for mapping calculation
        public void lineTo(double x, double y, GraphicsState gs, double[] bbox) {
            if (!pathStarted) {
                 System.err.println("Warning: lineTo called without preceding moveto. Using last point as start.");
                 // Implicitly move to the last SVG point if path wasn't started?
                 // This might still be problematic. Ideally, EPS should always have moveto.
                 pathData.append(String.format(Locale.ROOT, "M %.6f %.6f ", lastSvgX, lastSvgY));
                 pathStarted = true;
            }
            // 1. Apply internal CTM
            System.out.println("DEBUG lineTo: Input (" + x + "," + y + ")");
            System.out.println("DEBUG CTM: [" + 
                gs.ctm[0] + " " + gs.ctm[1] + " " + 
                gs.ctm[2] + " " + gs.ctm[3] + " " + 
                gs.ctm[4] + " " + gs.ctm[5] + "]");
            
            Point2D.Double transformedPoint = gs.transformPoint(x, y);
            double psX = transformedPoint.getX();
            double psY = transformedPoint.getY();
            System.out.println("DEBUG transformed point: (" + psX + "," + psY + ")");

            // 2. Convert to SVG coordinates with proper bounding box adjustment
            double svgX = psX;
            double svgY = psY;
            
            // Apply proper coordinate transformation for SVG
            // Adjust X coordinate by subtracting the left edge of the bounding box
            svgX = svgX - bbox[0];
            
            // Adjust Y coordinate by inverting it relative to the bounding box height
            // This is because in PostScript Y grows upward, in SVG Y grows downward
            svgY = bbox[3] - svgY;
            
            System.out.println("DEBUG final SVG point: (" + svgX + "," + svgY + ")");

            lastSvgX = svgX;
            lastSvgY = svgY;
            pathData.append(String.format(Locale.ROOT, " L %.6f %.6f", svgX, svgY));
            
            // Save the last point for relative operations
            lastX = x;
            lastY = y;
            hasLastPoint = true;
        }

        public void closePath() {
            if (pathStarted) {
                pathData.append(" Z");
            }
        }

        public String toString() {
            return pathData.length() > 0 ? pathData.toString().trim() : "";
        }

        public boolean isEmpty() {
            boolean empty = pathData.length() == 0 || !pathStarted;
            System.out.println("DEBUG PathBuilder.isEmpty(): pathData.length=" + pathData.length() + ", pathStarted=" + pathStarted + ", returning " + empty);
            return empty;
        }
        
        public boolean hasPathStarted() {
            return pathStarted;
        }
        
        public void clear() {
            pathData = new StringBuilder();
            pathStarted = false;
        }
        
        // buildPath needs to format the output correctly for SVG <path>
        // It should now ONLY return the raw path data string (M ..., L ..., Z)
        // The fill/stroke attributes will be applied to the <path> element itself.
        public String buildPathDataString() {
            return pathData.length() > 0 ? pathData.toString().trim() : "";
        }

        // Get the last point coordinates
        public double[] getLastPoint() {
            if (hasLastPoint) {
                return new double[] { lastX, lastY };
            }
            return null;
        }

        public void curveTo(double x1, double y1, double x2, double y2, double x3, double y3, 
                            GraphicsState gs, double[] bbox) {
            if (!pathStarted) {
                System.err.println("Warning: curveTo called without preceding moveto. Using last point as start.");
                pathData.append(String.format(Locale.ROOT, "M %.6f %.6f ", lastSvgX, lastSvgY));
                pathStarted = true;
            }
            
            // 1. Apply CTM transformation to all control points
            System.out.println("DEBUG curveTo: Control 1 (" + x1 + "," + y1 + ")");
            System.out.println("DEBUG curveTo: Control 2 (" + x2 + "," + y2 + ")");
            System.out.println("DEBUG curveTo: End point (" + x3 + "," + y3 + ")");
            
            Point2D.Double cp1 = gs.transformPoint(x1, y1);
            Point2D.Double cp2 = gs.transformPoint(x2, y2);
            Point2D.Double ep = gs.transformPoint(x3, y3);
            
            // 2. Convert to SVG coordinates
            double cp1x = cp1.getX() - bbox[0];
            double cp1y = bbox[3] - cp1.getY();
            
            double cp2x = cp2.getX() - bbox[0];
            double cp2y = bbox[3] - cp2.getY();
            
            double epx = ep.getX() - bbox[0];
            double epy = bbox[3] - ep.getY();
            
            // 3. Append to path data
            pathData.append(String.format(Locale.ROOT, " C %.6f %.6f %.6f %.6f %.6f %.6f", 
                    cp1x, cp1y, cp2x, cp2y, epx, epy));
            
            // Save the last point for relative operations
            lastX = x3;
            lastY = y3;
            hasLastPoint = true;
            
            // Update the last SVG coordinates
            lastSvgX = epx;
            lastSvgY = epy;
        }
    }
    
    private File epsFile;
    private File svgFile;
    private List<String> paths;
    private PathBuilder currentPath;
    private double viewBoxMinX, viewBoxMinY, viewBoxWidth, viewBoxHeight;
    private double documentHeight; // Store document height for Y-coordinate flipping
    private Stack<GraphicsState> gsStack;
    private Stack<String> stack;
    private GraphicsState currentState;

    /**
     * Constructor that takes EPS input and SVG output files.
     */
    public EpsToSvgConverter(File epsFile, File svgFile) {
        this.epsFile = epsFile;
        this.svgFile = svgFile;
        this.paths = new ArrayList<>();
        this.gsStack = new Stack<>();
        this.stack = new Stack<>();
        this.currentState = new GraphicsState(null);
        this.currentPath = new PathBuilder(currentState);
        viewBoxMinX = viewBoxMinY = 0;
        viewBoxWidth = viewBoxHeight = 612;
        documentHeight = 792; // Default - will be updated when BoundingBox is parsed
    }

    public void convert() throws IOException {
        clipIdCounter = 0; // Reset counter for each conversion

        try (BufferedReader reader = new BufferedReader(new FileReader(epsFile))) {
            // Parse EPS header to extract bounding box
            double[] bbox = parseHeader(reader);
            
            if (bbox == null) {
                throw new IOException("Could not find bounding box in EPS file");
            }
            
            // Process all drawing commands and generate SVG paths
            List<String> svgPaths = processCommands(reader, bbox);
            
            // Write SVG output file
            writeSvgFile(svgPaths, bbox, svgFile.getAbsolutePath());
            
            System.out.println("Conversion completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing EPS file: " + e.getMessage());
            throw e;
        }
    }

    private static List<String> processCommands(BufferedReader reader, double[] bbox) throws IOException {
        Stack<GraphicsState> gsStack = new Stack<>();
        Stack<String> stack = new Stack<>();
        GraphicsState currentState = new GraphicsState(bbox);
        PathBuilder currentPath = new PathBuilder(currentState);
        
        List<String> paths = new ArrayList<>();
        
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            
            // Skip comments and empty lines
            if (line.trim().startsWith("%") || line.trim().isEmpty()) {
                System.out.println("DEBUG line " + lineNumber + ": Skipping comment or empty line: '" + line + "'");
                continue;
            }
            
            System.out.println("DEBUG line " + lineNumber + ": Processing: '" + line + "'");
            
            // Handle arrays like [1 0 0 1 0 0]
            if (line.contains("[") && line.contains("]")) {
                int startBracket = line.indexOf("[");
                int endBracket = line.indexOf("]", startBracket);
                
                if (startBracket >= 0 && endBracket > startBracket) {
                    String array = line.substring(startBracket + 1, endBracket).trim();
                    String[] elements = array.split("\\s+");
                    
                    System.out.println("DEBUG line " + lineNumber + ": Processing array: '" + line.substring(startBracket, endBracket + 1) + "'");
                    
                    // Push array elements onto the stack
                    for (String element : elements) {
                        if (!element.isEmpty()) {
                            System.out.println("DEBUG line " + lineNumber + ": Pushed array element to stack: '" + element + "'");
                            stack.push(element);
                        }
                    }
                    
                    // Process the command after the array
                    String remainder = line.substring(endBracket + 1).trim();
                    if (!remainder.isEmpty()) {
                        processToken(remainder, stack, currentState, currentPath, gsStack, paths, bbox);
                    }
                    
                    continue;
                }
            }
            
            // Handle procedure definitions
            if (line.startsWith("/") && line.contains("def")) {
                System.out.println("DEBUG line " + lineNumber + ": Skipping procedure definition: '" + line + "'");
                continue;
            }
            
            // Split line into tokens
            StringTokenizer tokenizer = new StringTokenizer(line, " \t\n\r\f", true);
            StringBuilder tokenBuilder = new StringBuilder();
            
            while (tokenizer.hasMoreTokens()) {
                String part = tokenizer.nextToken().trim();
                if (part.isEmpty()) continue;
                
                // Accumulate parts of token
                tokenBuilder.append(part);
                
                // Process complete token
                String token = tokenBuilder.toString();
                if (!token.isEmpty() && !Character.isWhitespace(token.charAt(0))) {
                    System.out.println("DEBUG line " + lineNumber + ": Processing token: '" + token + "'");
                    
                    processToken(token, stack, currentState, currentPath, gsStack, paths, bbox);
                    
                    // Reset for next token
                    tokenBuilder = new StringBuilder();
                }
            }
        }
        
        // Add any remaining path
        if (!currentPath.isEmpty()) {
            String pathData = currentPath.buildPathDataString();
            System.out.println("DEBUG PathBuilder.isEmpty(): pathData.length=" + pathData.length() + 
                               ", pathStarted=" + currentPath.hasPathStarted() + 
                               ", returning false");
            
            // Handle any remaining fill/stroke attributes
            String clipAttr = (currentState.clipId != null)
                ? String.format(Locale.ROOT, " clip-path=\"url(#%s)\"", currentState.clipId)
                : "";
            
            if (currentState.fillColor != null && !currentState.fillColor.equals("none")) {
                paths.add(String.format(Locale.ROOT,
                    "<path d=\"%s\" fill=\"%s\" stroke=\"none\"%s/>",
                    pathData, currentState.fillColor, clipAttr));
            } else {
                paths.add(String.format(Locale.ROOT,
                    "<path d=\"%s\" fill=\"none\" stroke=\"none\" %s/>",
                    pathData, clipAttr));
            }
        }
        
        System.out.println("DEBUG processCommands: Processed " + lineNumber + " lines, generated " + paths.size() + " paths:");
        for (int i = 0; i < paths.size(); i++) {
            System.out.println("DEBUG Path " + i + ": " + paths.get(i));
        }
        
        return paths;
    }

    // Process a single token
    private static void processToken(String token, Stack<String> stack, 
                                   GraphicsState currentState, PathBuilder currentPath,
                                   Stack<GraphicsState> gsStack, List<String> paths,
                                   double[] bbox) {
        // Check if it's a numeric value
        if (token.matches("-?\\d+(\\.\\d+)?")) {
            stack.push(token);
            System.out.println("DEBUG pushed number to stack: " + token);
            return;
        }
        
        // Process operation
        try {
            System.out.println("DEBUG processing token: " + token);
            switch (token) {
                case "gsave":
                case "gs": // Cairo abbreviation
                case "q":  // PDF syntax
                    System.out.println("DEBUG gsave: CTM BEFORE = [" + 
                        currentState.ctm[0] + " " + currentState.ctm[1] + " " + 
                        currentState.ctm[2] + " " + currentState.ctm[3] + " " + 
                        currentState.ctm[4] + " " + currentState.ctm[5] + "]");
                    gsStack.push(currentState.clone());
                    System.out.println("DEBUG gsave: Pushed state to stack, stack size = " + gsStack.size());
                    break;
                case "grestore":
                case "gr": // Cairo abbreviation
                case "Q":  // PDF syntax
                    if (!gsStack.isEmpty()) {
                        System.out.println("DEBUG grestore: CTM BEFORE = [" + 
                            currentState.ctm[0] + " " + currentState.ctm[1] + " " + 
                            currentState.ctm[2] + " " + currentState.ctm[3] + " " + 
                            currentState.ctm[4] + " " + currentState.ctm[5] + "]");
                        
                        // Save current path data before restoring state
                        String pathData = currentPath.buildPathDataString();
                        
                        // If there's a valid path, save it with current state attributes
                        if (!currentPath.isEmpty()) {
                            String clipAttr = (currentState.clipId != null)
                                ? String.format(Locale.ROOT, " clip-path=\"url(#%s)\"", currentState.clipId)
                                : "";
                            
                            if (currentState.fillColor != null && !currentState.fillColor.equals("none")) {
                                paths.add(String.format(Locale.ROOT,
                                    "<path d=\"%s\" fill=\"%s\" stroke=\"none\"%s/>",
                                    pathData, currentState.fillColor, clipAttr));
                                System.out.println("DEBUG added fill path: " + pathData);
                            }
                        }
                        
                        // Now restore the graphics state
                        currentState = gsStack.pop();
                        // Create new path with restored state
                        currentPath = new PathBuilder(currentState);
                        System.out.println("DEBUG grestore: CTM AFTER = [" + 
                            currentState.ctm[0] + " " + currentState.ctm[1] + " " + 
                            currentState.ctm[2] + " " + currentState.ctm[3] + " " + 
                            currentState.ctm[4] + " " + currentState.ctm[5] + "]");
                        System.out.println("DEBUG grestore: Popped state from stack, stack size = " + gsStack.size());
                    } else { System.err.println("Warning: Unmatched grestore."); }
                    break;
                case "moveto":
                case "m": // Scribus/PDF synonym for moveto
                    if (stack.size() >= 2) {
                        double y = Double.parseDouble(stack.pop());
                        double x = Double.parseDouble(stack.pop());
                        currentPath.moveTo(x, y, currentState, bbox);
                        System.out.println("DEBUG moveto: " + x + ", " + y);
                    } else { System.err.println("Warning: Stack underflow for moveto."); }
                    break;
                case "rmoveto": // Relative move-to
                    if (stack.size() >= 2) {
                        double dy = Double.parseDouble(stack.pop());
                        double dx = Double.parseDouble(stack.pop());
                        
                        // Get last point or default to (0,0)
                        double[] lastPoint = currentPath.getLastPoint();
                        double lastX = 0, lastY = 0;
                        if (lastPoint != null) {
                            lastX = lastPoint[0];
                            lastY = lastPoint[1];
                        }
                        
                        // Clear any existing path data
                        currentPath.clear();
                        
                        currentPath.moveTo(lastX + dx, lastY + dy, currentState, bbox);
                        System.out.println("DEBUG rmoveto: relative " + dx + ", " + dy + " from " + lastX + ", " + lastY);
                    } else { System.err.println("Warning: Stack underflow for rmoveto."); }
                    break;
                case "l":  // Abbreviation for lineto
                    if (stack.size() >= 2) {
                        double y = Double.parseDouble(stack.pop());
                        double x = Double.parseDouble(stack.pop());
                        currentPath.lineTo(x, y, currentState, bbox);
                        System.out.println("DEBUG lineto: (" + x + "," + y + ")");
                    } else { System.err.println("Warning: Stack underflow for lineto."); }
                    break;
                case "li":  // Scribus synonym for lineto
                    if (stack.size() >= 2) {
                        double y = Double.parseDouble(stack.pop());
                        double x = Double.parseDouble(stack.pop());
                        currentPath.lineTo(x, y, currentState, bbox);
                        System.out.println("DEBUG lineto (li): (" + x + "," + y + ")");
                    } else { System.err.println("Warning: Stack underflow for li."); }
                    break;
                case "rlineto": // Relative line-to
                    if (stack.size() >= 2) {
                        double dy = Double.parseDouble(stack.pop());
                        double dx = Double.parseDouble(stack.pop());
                        
                        // Get last point or default to (0,0)
                        double[] lastPoint = currentPath.getLastPoint();
                        double lastX = 0, lastY = 0;
                        if (lastPoint != null) {
                            lastX = lastPoint[0];
                            lastY = lastPoint[1];
                        } else {
                            System.err.println("Warning: rlineto called without preceding moveto or lineto. Using (0,0) as start.");
                        }
                        
                        currentPath.lineTo(lastX + dx, lastY + dy, currentState, bbox);
                        System.out.println("DEBUG rlineto: relative " + dx + ", " + dy + " from " + lastX + ", " + lastY);
                    } else { System.err.println("Warning: Stack underflow for rlineto."); }
                    break;
                case "curveto":
                case "c": // Bezier curve
                    if (stack.size() >= 6) {
                        double y3 = Double.parseDouble(stack.pop()); // End point y
                        double x3 = Double.parseDouble(stack.pop()); // End point x
                        double y2 = Double.parseDouble(stack.pop()); // Control point 2 y
                        double x2 = Double.parseDouble(stack.pop()); // Control point 2 x
                        double y1 = Double.parseDouble(stack.pop()); // Control point 1 y
                        double x1 = Double.parseDouble(stack.pop()); // Control point 1 x
                        
                        currentPath.curveTo(x1, y1, x2, y2, x3, y3, currentState, bbox);
                        System.out.println("DEBUG curveto: (" + x1 + "," + y1 + "), (" + x2 + "," + y2 + "), (" + x3 + "," + y3 + ")");
                    } else { System.err.println("Warning: Stack underflow for curveto."); }
                    break;
                case "closepath":
                case "h": // PDF syntax
                case "cl": // Scribus syntax
                    currentPath.closePath();
                    System.out.println("DEBUG closepath");
                    break;
                case "concat":
                case "cm": // Abbreviation
                    if (stack.size() >= 6) {
                        double f = Double.parseDouble(stack.pop()); // ty
                        double e = Double.parseDouble(stack.pop()); // tx
                        double d = Double.parseDouble(stack.pop()); // d
                        double c = Double.parseDouble(stack.pop()); // c
                        double b = Double.parseDouble(stack.pop()); // b
                        double a = Double.parseDouble(stack.pop()); // a
                        
                        System.out.println("DEBUG concat: Input matrix [" + a + " " + b + " " + c + " " + d + " " + e + " " + f + "]");
                        
                        // Apply concatenation of CTM (multiply matrices)
                        double[] newCTM = new double[6];
                        newCTM[0] = a * currentState.ctm[0] + b * currentState.ctm[2];
                        newCTM[1] = a * currentState.ctm[1] + b * currentState.ctm[3];
                        newCTM[2] = c * currentState.ctm[0] + d * currentState.ctm[2];
                        newCTM[3] = c * currentState.ctm[1] + d * currentState.ctm[3];
                        newCTM[4] = e * currentState.ctm[0] + f * currentState.ctm[2] + currentState.ctm[4];
                        newCTM[5] = e * currentState.ctm[1] + f * currentState.ctm[3] + currentState.ctm[5];
                        
                        currentState.ctm = newCTM;
                        System.out.println("DEBUG concat: New CTM = [" + 
                            currentState.ctm[0] + " " + currentState.ctm[1] + " " + 
                            currentState.ctm[2] + " " + currentState.ctm[3] + " " + 
                            currentState.ctm[4] + " " + currentState.ctm[5] + "]");
                        
                        // Create a new path with the updated transformation matrix
                        currentPath = new PathBuilder(currentState);
                    } else { System.err.println("Warning: Stack underflow for concat/cm. Stack size: " + stack.size()); }
                    break;
                case "fill":
                case "f": // Abbreviation
                    if (!currentPath.isEmpty()) {
                        String fillPathData = currentPath.buildPathDataString();
                        String fillColor = currentState.fillColor != null ? currentState.fillColor : "black";
                        String fillRuleAttr = currentState.useEvenOddFill ? "fill-rule=\"evenodd\"" : "";
                        String clipAttr = currentState.clipId != null ? "clip-path=\"url(#" + currentState.clipId + ")\"" : "";
                        
                        paths.add(String.format("<path d=\"%s\" fill=\"%s\" %s stroke=\"none\" %s />", 
                            fillPathData, fillColor, fillRuleAttr, clipAttr));
                        System.out.println("DEBUG fill: added path with data: " + fillPathData);
                    } else {
                        System.out.println("DEBUG fill: Path is empty, not adding");
                    }
                    currentPath = new PathBuilder(currentState); // Create a new empty path
                    break;
                case "setlinewidth":
                case "w": // Abbreviation
                    if (stack.size() >= 1) {
                        double width = Double.parseDouble(stack.pop());
                        currentState.lineWidth = width;
                        System.out.println("DEBUG setlinewidth: " + width);
                    } else { 
                        System.err.println("Warning: Stack underflow for setlinewidth."); 
                    }
                    break;
                case "stroke":
                case "st": // Abbreviation
                case "S":  // PDF syntax
                    if (!currentPath.isEmpty()) {
                        String strokePathData = currentPath.buildPathDataString();
                        String strokeColor = currentState.strokeColor != null ? currentState.strokeColor : "black";
                        String strokeWidth = String.format(Locale.ROOT, "%.1f", currentState.lineWidth);
                        String clipAttr = currentState.clipId != null ? "clip-path=\"url(#" + currentState.clipId + ")\"" : "";
                        
                        paths.add(String.format("<path d=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"%s\" %s />", 
                            strokePathData, strokeColor, strokeWidth, clipAttr));
                        System.out.println("DEBUG stroke: added path with data: " + strokePathData);
                    } else {
                        System.out.println("DEBUG stroke: Path is empty, not adding");
                    }
                    currentPath = new PathBuilder(currentState); // Create a new empty path
                    break;
                case "setrgbcolor":
                case "rg": // Abbreviation
                    if (stack.size() >= 3) {
                        double b = Double.parseDouble(stack.pop());
                        double g = Double.parseDouble(stack.pop());
                        double r = Double.parseDouble(stack.pop());
                        
                        currentState.fillColor = String.format(Locale.ROOT, "rgb(%d,%d,%d)", 
                            (int)(r*255), (int)(g*255), (int)(b*255));
                        currentState.strokeColor = currentState.fillColor;
                        System.out.println("DEBUG setrgbcolor: " + currentState.fillColor);
                    } else { System.err.println("Warning: Stack underflow for setrgbcolor."); }
                    break;
                case "setgray":
                case "g": // PDF syntax
                    if (stack.size() >= 1) {
                        double gray = Double.parseDouble(stack.pop());
                        int grayValue = (int)(gray * 255);
                        currentState.fillColor = String.format(Locale.ROOT, "rgb(%d,%d,%d)", 
                            grayValue, grayValue, grayValue);
                        currentState.strokeColor = currentState.fillColor;
                        System.out.println("DEBUG setgray: " + currentState.fillColor);
                    } else { System.err.println("Warning: Stack underflow for setgray."); }
                    break;
                case "clip":
                case "W": // PDF syntax
                    if (!currentPath.isEmpty()) {
                        String clipPathData = currentPath.buildPathDataString();
                        String clipId = "clip" + (++clipIdCounter);
                        clipPathMap.put(clipId, clipPathData);
                        currentState.clipId = clipId;
                        System.out.println("DEBUG clip: Created clip path with ID " + clipId);
                    }
                    break;
                case "eoclip":
                case "W*": // PDF syntax
                    if (!currentPath.isEmpty()) {
                        String clipPathData = currentPath.buildPathDataString();
                        String clipId = "clip" + (++clipIdCounter);
                        clipPathMap.put(clipId, clipPathData);
                        currentState.clipId = clipId;
                        currentState.useEvenOddFill = true;
                        System.out.println("DEBUG eoclip: Created clip path with ID " + clipId + " (even-odd)");
                    }
                    break;
                case "re": // rectangle
                    if (stack.size() >= 4) {
                        double height = Double.parseDouble(stack.pop());
                        double width = Double.parseDouble(stack.pop());
                        double y = Double.parseDouble(stack.pop());
                        double x = Double.parseDouble(stack.pop());
                        
                        currentPath.moveTo(x, y, currentState, bbox);
                        currentPath.lineTo(x + width, y, currentState, bbox);
                        currentPath.lineTo(x + width, y + height, currentState, bbox);
                        currentPath.lineTo(x, y + height, currentState, bbox);
                        currentPath.closePath();
                        System.out.println("DEBUG re: Created rectangle at (" + x + "," + y + ") with dimensions " + width + "x" + height);
                    } else { System.err.println("Warning: Stack underflow for re."); }
                    break;
                case "showpage":
                    // No action needed for SVG output
                    break;
                case "newpath":
                case "n": // Abbreviation
                    currentPath = new PathBuilder(currentState); // Start a new path
                    System.out.println("DEBUG newpath: Created new path");
                    break;
                case "exch": // Exchange top 2 elements on stack
                    if (stack.size() >= 2) {
                        String a = stack.pop();
                        String b = stack.pop();
                        stack.push(a);
                        stack.push(b);
                        System.out.println("DEBUG exch: Exchanged stack elements");
                    } else { System.err.println("Warning: Stack underflow for exch."); }
                    break;
                // Add support for more operators
                case "rectclip": // Rectangle clipping - similar to clip but with a rectangle
                    if (stack.size() >= 4) {
                        double height = Double.parseDouble(stack.pop());
                        double width = Double.parseDouble(stack.pop());
                        double y = Double.parseDouble(stack.pop());
                        double x = Double.parseDouble(stack.pop());
                        
                        // Create a temporary path for the clip
                        PathBuilder clipPath = new PathBuilder(currentState);
                        clipPath.moveTo(x, y, currentState, bbox);
                        clipPath.lineTo(x + width, y, currentState, bbox);
                        clipPath.lineTo(x + width, y + height, currentState, bbox);
                        clipPath.lineTo(x, y + height, currentState, bbox);
                        clipPath.closePath();
                        
                        String clipPathData = clipPath.buildPathDataString();
                        String clipId = "clip" + (++clipIdCounter);
                        clipPathMap.put(clipId, clipPathData);
                        currentState.clipId = clipId;
                        System.out.println("DEBUG rectclip: Created rectangle clip at (" + x + "," + y + ") with dimensions " + width + "x" + height);
                    } else { System.err.println("Warning: Stack underflow for rectclip."); }
                    break;
                default:
                    // Handle procedures/definitions - for simplicity we ignore them
                    if (token.startsWith("/") || token.equals("def")) {
                        System.out.println("DEBUG: Skipping PostScript definition: " + token);
                    } else {
                        System.out.println("DEBUG: Unhandled token: " + token);
                    }
                    break;
            }
        } catch (Exception e) {
            System.err.println("Warning: Error processing token '" + token + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void convert(String epsFilePath, String svgFilePath) {
        try {
            // Process output directory
            File outputFile = new File(svgFilePath);
            File outputDir = outputFile.getParentFile();
            if (outputDir != null && !outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            // Reset clip ID counter for this conversion
            clipIdCounter = 0;
            clipPathMap.clear(); // Clear any previous clip paths
            
            // 1. Read the EPS file and parse the header for BoundingBox
            File epsFile = new File(epsFilePath);
            BufferedReader reader = new BufferedReader(new FileReader(epsFile));
            reader.mark(8192); // Mark the beginning of the file (with a generous buffer)
            
            double[] bbox = parseHeader(reader);
            if (bbox == null) {
                System.err.println("Error: No BoundingBox found in EPS file. Defaulting to [0 0 612 792]");
                bbox = new double[] {0, 0, 612, 792};
            }
            
            // If parseHeader() reset the reader, we don't need to reset it here.
            // Otherwise, reset the reader to start from the beginning again
            try {
                reader.reset();
                System.out.println("DEBUG: Reader reset to beginning of file for command processing");
            } catch (IOException e) {
                System.err.println("Warning: Could not reset reader. Reinitializing reader.");
                reader.close();
                reader = new BufferedReader(new FileReader(epsFile));
            }
            
            // 2. Read commands after header
            List<String> paths = processCommands(reader, bbox);
            reader.close();
            
            // 3. Write SVG file
            writeSvgFile(paths, bbox, svgFilePath);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static double[] parseHeader(BufferedReader reader) throws IOException {
        double[] bbox = null;
        String line;
        
        System.out.println("DEBUG: Parsing header...");
        
        // Look for %%BoundingBox or %%HiResBoundingBox in the first 50 lines only
        // This is to avoid consuming the entire file when parsing header
        int lineCount = 0;
        boolean endOfHeaderFound = false;
        
        while ((line = reader.readLine()) != null && lineCount < 50 && !endOfHeaderFound) {
            lineCount++;
            line = line.trim();
            System.out.println("DEBUG header line " + lineCount + ": '" + line + "'");
            
            // Check for end of header
            if (line.equals("%%EndComments")) {
                System.out.println("DEBUG: End of header comments found");
                endOfHeaderFound = true;
                break;
            }
            
            // Prefer HiResBoundingBox if available for higher precision
            if (line.startsWith("%%HiResBoundingBox:")) {
                String[] parts = line.substring("%%HiResBoundingBox:".length()).trim().split("\\s+");
                if (parts.length == 4) {
                    try {
                        bbox = new double[4];
                        for (int i = 0; i < 4; i++) {
                            bbox[i] = Double.parseDouble(parts[i]);
                        }
                        System.out.println("Found HiResBoundingBox: [" + bbox[0] + " " + bbox[1] + " " + 
                                           bbox[2] + " " + bbox[3] + "]");
                        // Continue to see if we find a %%BoundingBox for completeness,
                        // but we'll prefer the HiResBoundingBox
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing HiResBoundingBox: " + line);
                    }
                }
            } else if (line.startsWith("%%BoundingBox:")) {
                String[] parts = line.substring("%%BoundingBox:".length()).trim().split("\\s+");
                if (parts.length == 4) {
                    try {
                        bbox = new double[4];
                        for (int i = 0; i < 4; i++) {
                            bbox[i] = Double.parseDouble(parts[i]);
                        }
                        System.out.println("Found BoundingBox: [" + bbox[0] + " " + bbox[1] + " " + 
                                           bbox[2] + " " + bbox[3] + "]");
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing BoundingBox: " + line);
                    }
                }
            }
        }
        
        // If header comments end wasn't found, reset reader to start
        if (!endOfHeaderFound) {
            try {
                reader.reset();
                System.out.println("DEBUG: Reset reader to beginning of file");
            } catch (IOException e) {
                System.err.println("Warning: Could not reset reader. Some content may be skipped.");
            }
        }
        
        return bbox;
    }
    
    private static void writeSvgFile(List<String> paths, double[] bbox, String svgFilePath) {
        try {
            // Create output directory if it doesn't exist
            File outputDir = new File(svgFilePath).getParentFile();
            if (outputDir != null && !outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            FileOutputStream fos = new FileOutputStream(svgFilePath);
            PrintWriter writer = new PrintWriter(fos);
            
            // Calculate viewBox based on bounding box
            double viewBoxMinX = bbox[0];
            double viewBoxMinY = bbox[1];
            double viewBoxWidth = bbox[2] - bbox[0];
            double viewBoxHeight = bbox[3] - bbox[1];
            
            // Write SVG header
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                        "xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
                        String.format(Locale.ROOT, "viewBox=\"%.6f %.6f %.6f %.6f\" ", 
                                     viewBoxMinX, viewBoxMinY, viewBoxWidth, viewBoxHeight) +
                        "width=\"" + viewBoxWidth + "pt\" " +
                        "height=\"" + viewBoxHeight + "pt\">");
            
            // Add clip path definitions if needed
            if (!clipPathMap.isEmpty()) {
                writer.println("  <defs>");
                for (Map.Entry<String, String> entry : clipPathMap.entrySet()) {
                    writer.println("    <clipPath id=\"" + entry.getKey() + "\">");
                    writer.println("      <path d=\"" + entry.getValue() + "\" />");
                    writer.println("    </clipPath>");
                }
                writer.println("  </defs>");
            }

            // Track unique paths to avoid duplicates
            Set<String> uniquePaths = new HashSet<>();
            int totalPaths = paths.size();
            int skippedPaths = 0;
            int writtenPaths = 0;
            
            // Print detailed path information for debugging
            for (int i = 0; i < paths.size(); i++) {
                String path = paths.get(i);
                System.out.println("DEBUG: Path " + i + " (length=" + path.length() + "): " + path);
                
                // Extract the path data only (d="...") for comparison
                int dStart = path.indexOf("d=\"") + 3;
                int dEnd = path.indexOf("\"", dStart);
                String pathData = dStart > 2 && dEnd > dStart ? path.substring(dStart, dEnd) : "";
                
                // Extract the fill color
                int fillStart = path.indexOf("fill=\"") + 6;
                int fillEnd = path.indexOf("\"", fillStart);
                String fillColor = fillStart > 5 && fillEnd > fillStart ? path.substring(fillStart, fillEnd) : "";
                
                // Create a normalized path for deduplication
                String normalizedPath = pathData + "|" + fillColor;
                System.out.println("DEBUG: Normalized path " + i + ": " + normalizedPath);
            }

            // Output paths - fix to explicitly check for valid path data and avoid duplicates
            System.out.println("DEBUG writeSvgFile: Received " + paths.size() + " paths");
            
            for (String path : paths) {
                if (path != null && !path.trim().isEmpty()) {
                    // Skip empty paths
                    if (path.contains("d=\"\"") || !path.contains("d=")) {
                        skippedPaths++;
                        System.out.println("DEBUG: Skipping empty path: " + path);
                        continue;
                    }
                    
                    // Extract the path data and fill color for normalized comparison
                    int dStart = path.indexOf("d=\"") + 3;
                    int dEnd = path.indexOf("\"", dStart);
                    String pathData = dStart > 2 && dEnd > dStart ? path.substring(dStart, dEnd) : "";
                    
                    int fillStart = path.indexOf("fill=\"") + 6;
                    int fillEnd = path.indexOf("\"", fillStart);
                    String fillColor = fillStart > 5 && fillEnd > fillStart ? path.substring(fillStart, fillEnd) : "";
                    
                    // Create a normalized path for deduplication
                    String normalizedPath = pathData + "|" + fillColor;
                    
                    // Skip duplicate paths
                    if (!uniquePaths.add(normalizedPath)) {
                        skippedPaths++;
                        System.out.println("DEBUG: Skipping duplicate path: " + normalizedPath);
                        continue;
                    }
                    
                    writer.println("  " + path);
                    writtenPaths++;
                } else {
                    System.out.println("DEBUG writeSvgFile: Skipping null or empty path");
                    skippedPaths++;
                }
            }
            
            System.out.println("DEBUG: Total paths: " + totalPaths + ", Skipped: " + skippedPaths + ", Written: " + writtenPaths);
            
            writer.println("</svg>");
            writer.flush();
            writer.close();
            
            System.out.println("SVG file created: " + svgFilePath);
            
        } catch (IOException e) {
            System.err.println("Error writing SVG file: " + e.getMessage());
        }
    }

    // Normalize path for comparison (combine d attribute and fill/stroke color)
    private static String normalizePath(String pathElement) {
        // Extract d attribute
        Pattern dPattern = Pattern.compile("d=\"([^\"]*)\"");
        Matcher dMatcher = dPattern.matcher(pathElement);
        
        if (!dMatcher.find()) {
            return "";
        }
        
        String d = dMatcher.group(1).trim();
        
        // Extract fill color 
        String fill = "none";
        Pattern fillPattern = Pattern.compile("fill=\"([^\"]*)\"");
        Matcher fillMatcher = fillPattern.matcher(pathElement);
        if (fillMatcher.find()) {
            fill = fillMatcher.group(1);
        }
        
        // Return d + fill color to ensure identical paths with different colors are preserved
        return d + "|" + fill;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java EpsToSvgConverter <input.eps> <output.svg>");
            return;
        }
        
        convert(args[0], args[1]);
    }
} 