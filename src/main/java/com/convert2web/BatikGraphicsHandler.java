package com.convert2web;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;

// W3C DOM imports
import org.w3c.dom.Document;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

// Batik imports
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2DIOException;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.DefaultExtensionHandler;
import org.apache.batik.ext.awt.g2d.GraphicContext;
import org.apache.batik.dom.util.DOMUtilities;

// JAXP Transformer imports
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.OutputKeys;
import java.io.StringWriter; // Added for DOMUtilities logging

/**
 * Implementation of GraphicsHandler using Apache Batik to generate SVG output.
 */
public class BatikGraphicsHandler implements GraphicsHandler {
    private static final Logger logger = Logger.getLogger(BatikGraphicsHandler.class.getName());

    // Helper class to store graphics state
    private static class GraphicsState {
        final AffineTransform transform;
        final Paint paint;
        final Stroke stroke;
        final Shape clip;

        GraphicsState(AffineTransform transform, Paint paint, Stroke stroke, Shape clip) {
            this.transform = transform;
            this.paint = paint;
            this.stroke = stroke;
            this.clip = clip;
        }
    }

    private SVGGraphics2D svgGenerator;
    private Document document;
    private Element root;
    private Path2D.Double currentPath;
    private Stack<GraphicsState> stateStack = new Stack<>();
    private Stack<Path2D.Double> pathStack = new Stack<>();
    private double[] bbox = null; // Store BBox for setting SVG dimensions
    private final double PADDING = 3.0; // Padding

    public BatikGraphicsHandler() {
        // Initialization happens in the initialize() method now
        // This ensures DOM/SVG setup happens after BBox might be known
    }

    @Override
    public void initialize(double[] bbox) {
        this.bbox = bbox;
        // Use Batik's SVG-specific DOM Implementation
        DOMImplementation domImpl = SVGDOMImplementation.getDOMImplementation(); 
        String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI; // Use Batik's constant
        this.document = domImpl.createDocument(svgNS, "svg", null);

        // Use SVGGeneratorContext with the correct factory method
        SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(document);
        ctx.setExtensionHandler(new DefaultExtensionHandler()); 
        this.svgGenerator = new SVGGraphics2D(ctx, false); // false = textAsShapes

        this.root = document.getDocumentElement();

        // Set SVG attributes based on BBox
        if (bbox != null && bbox.length == 4) {
            double width = bbox[2] - bbox[0];
            double height = bbox[3] - bbox[1];
            root.setAttributeNS(null, "width", String.valueOf(width));
            root.setAttributeNS(null, "height", String.valueOf(height));
            root.setAttributeNS(null, "viewBox", String.format(Locale.US, "%.2f %.2f %.2f %.2f", 
                                                              bbox[0], bbox[1], width, height));
            
            svgGenerator.setSVGCanvasSize(new Dimension((int)Math.ceil(width), (int)Math.ceil(height)));

            // Apply initial transform (Y-flip and translate)
            svgGenerator.setTransform(AffineTransform.getTranslateInstance(0, height));
            svgGenerator.transform(AffineTransform.getScaleInstance(1, -1));
            // Optionally add offset for bbox origin if needed later:
            // svgGenerator.transform(AffineTransform.getTranslateInstance(-bbox[0], -bbox[1]));

        } else {
            root.setAttributeNS(null, "width", "612"); // Default width
            root.setAttributeNS(null, "height", "792"); // Default height
            logger.warning("initialize: BoundingBox not found or invalid, using default SVG size.");
        }

        // Initialize graphics state
        stateStack.clear();
        pathStack.clear();
        newPath(); // Initialize currentPath
        // Set default graphics properties
        svgGenerator.setPaint(Color.BLACK); 
        svgGenerator.setStroke(new BasicStroke(1.0f)); 

        // Push the initial state onto the stacks
        GraphicsState initialState = new GraphicsState(
            new AffineTransform(svgGenerator.getTransform()),
            svgGenerator.getPaint(),
            svgGenerator.getStroke(),
            svgGenerator.getClip()
        );
        stateStack.push(initialState);
        pathStack.push((Path2D.Double) currentPath.clone());

        logger.fine("BatikGraphicsHandler initialized using SVGGeneratorContext.");
    }

    @Override
    public void writeToFile(String outputPath) throws IOException {
        try {
            if (svgGenerator == null) {
                throw new IllegalStateException("svgGenerator is not initialized.");
            }
            logger.log(Level.FINE, "Writing SVG to file: {0}", outputPath);

            // Get Root BEFORE dispose()
            Element effectiveRoot = svgGenerator.getRoot(); 
            logger.log(Level.FINE, "Obtained effectiveRoot ({0}) for JAXP Transform.", 
                       (effectiveRoot != null ? effectiveRoot.getNodeName() : "null"));
            inspectBatikDom("[In writeToFile, after getRoot(), before JAXP]");

            // dispose() remains commented out as it wasn't needed for the working solution
            // logger.log(Level.INFO, "Calling svgGenerator.dispose()...");
            // svgGenerator.dispose();

            // stream() block remains commented out
            /* ... stream() block ... */

            // Use JAXP Transformer 
             if (effectiveRoot == null) { 
                throw new IOException("Cannot write SVG: effectiveRoot obtained from svgGenerator was null.");
             }
             try (Writer out = new FileWriter(outputPath, StandardCharsets.UTF_8)) {
                 logger.log(Level.FINE, "Serializing effectiveRoot DOM using JAXP Transformer...");
                 TransformerFactory tFactory = TransformerFactory.newInstance();

                 Transformer transformer = tFactory.newTransformer();
                 transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                 transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

                 DOMSource source = new DOMSource(effectiveRoot);
                 StreamResult result = new StreamResult(out);
                 transformer.transform(source, result);

                 logger.log(Level.FINE, "SVG file written via JAXP Transformer to: {0}", outputPath);
             } catch (TransformerException e) {
                 logger.log(Level.SEVERE, "Error during JAXP DOM transformation", e);
                 throw new IOException("Failed to transform DOM to SVG file", e);
             } catch (IOException e) {
                 logger.log(Level.SEVERE, "Error writing SVG file via Transformer", e);
                 throw e;
             }
        
        } catch (IOException e) { // Catch general IOExceptions
             logger.log(Level.SEVERE, "Error writing SVG content to file", e);
             throw e; 
        }
    }

    @Override
    public void newPath() {
        if (currentPath == null) {
            currentPath = new Path2D.Double();
        } else {
            currentPath.reset();
        }
        logger.fine("newpath");
    }

    @Override
    public void moveTo(double x, double y) {
        // EPS coords might be relative to its own origin. 
        // Apply the SVG generator's CTM to transform to SVG space.
        Point2D pt = svgGenerator.getTransform().transform(new Point2D.Double(x, y), null);
        if (currentPath == null) currentPath = new Path2D.Double();
        currentPath.moveTo(pt.getX(), pt.getY());
        logger.fine(String.format(Locale.ROOT, "moveto: (%.2f, %.2f) -> SVG(%.2f, %.2f)", x, y, pt.getX(), pt.getY()));
    }

    @Override
    public void lineTo(double x, double y) {
        Point2D pt = svgGenerator.getTransform().transform(new Point2D.Double(x, y), null);
        if (currentPath == null || currentPath.getCurrentPoint() == null) { 
            logger.warning("lineto: called before moveto?"); 
            if (currentPath == null) currentPath = new Path2D.Double();
            currentPath.moveTo(pt.getX(), pt.getY()); // Start path at current point
        } else {
            currentPath.lineTo(pt.getX(), pt.getY());
        }
        logger.fine(String.format(Locale.ROOT, "lineto: (%.2f, %.2f) -> SVG(%.2f, %.2f)", x, y, pt.getX(), pt.getY()));
    }

    @Override
    public void curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        Point2D pt1 = svgGenerator.getTransform().transform(new Point2D.Double(x1, y1), null);
        Point2D pt2 = svgGenerator.getTransform().transform(new Point2D.Double(x2, y2), null);
        Point2D pt3 = svgGenerator.getTransform().transform(new Point2D.Double(x3, y3), null);
        if (currentPath == null || currentPath.getCurrentPoint() == null) {
             logger.warning("curveto: called before moveto?");
             if (currentPath == null) currentPath = new Path2D.Double();
             currentPath.moveTo(pt1.getX(), pt1.getY()); // Start path at first control point?
        }
        currentPath.curveTo(pt1.getX(), pt1.getY(), pt2.getX(), pt2.getY(), pt3.getX(), pt3.getY());
        logger.fine(String.format(Locale.ROOT, "curveto: (%.2f,%.2f; %.2f,%.2f; %.2f,%.2f)", x1,y1,x2,y2,x3,y3));
    }

    @Override
    public void closePath() {
        if (currentPath != null && currentPath.getCurrentPoint() != null) {
            currentPath.closePath();
            logger.fine("closepath");
        } else {
            logger.fine("closepath: path not started or empty.");
        }
    }

    @Override
    public void fill() {
        // --- Remove DIAGNOSTIC rectangle drawing ---
        /*
        try {
            java.awt.geom.Rectangle2D.Double testRect = new java.awt.geom.Rectangle2D.Double(10, 10, 50, 50);
            Paint originalPaint = svgGenerator.getPaint();
            svgGenerator.setPaint(Color.RED);
            svgGenerator.fill(testRect);
            svgGenerator.setPaint(originalPaint);
            logger.log(Level.FINE, "fill: Drew diagnostic red rectangle at (10,10) w=50, h=50");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during diagnostic fill operation", e);
        }
        */
        // --- Original fill logic ---
        try {
            if (currentPath != null && !currentPath.getPathIterator(null).isDone()) {
                logger.log(Level.FINE, "fill: path bounds={0}, CTM={1}", new Object[]{currentPath.getBounds2D(), svgGenerator.getTransform()});
                svgGenerator.fill(currentPath);
            } else {
                 logger.log(Level.WARNING, "fill: currentPath was empty or null when attempting to fill it.");
            }
            
            inspectBatikDom("[Immediately after filling CURRENT PATH]"); // Update context
            newPath();
            logger.log(Level.FINE, "fill: path reset");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during fill operation", e);
        }
    }

    @Override
    public void eoFill() {
         if (currentPath != null && currentPath.getCurrentPoint() != null) {
            logger.fine("eofill: path bounds=" + currentPath.getBounds2D());
            Path2D.Double eoPath = new Path2D.Double(currentPath);
            eoPath.setWindingRule(Path2D.WIND_EVEN_ODD);
            svgGenerator.fill(eoPath);
            currentPath = new Path2D.Double(); // Reset path after painting
            logger.fine("eofill: path reset");
         } else {
             logger.warning("eofill: called with no path or empty path.");
         }
    }

    @Override
    public void stroke() {
        if (currentPath == null || currentPath.getPathIterator(null).isDone()) {
            logger.log(Level.WARNING, "stroke: Attempted to stroke an empty or null path.");
            return;
        }
        try {
             // Rely on the CTM set on svgGenerator
            logger.log(Level.FINE, "stroke: path bounds={0}, CTM={1}", new Object[]{currentPath.getBounds2D(), svgGenerator.getTransform()});
            svgGenerator.draw(currentPath); // Pass original path
            inspectBatikDom("[Immediately after stroking CURRENT PATH]");
            newPath(); // Correct casing
            logger.log(Level.FINE, "stroke: path reset");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during stroke operation", e);
        }
    }

    @Override
    public void gsave() {
        if (svgGenerator == null) return;

        // Save current state
        AffineTransform currentTransform = svgGenerator.getTransform();
        Paint currentPaint = svgGenerator.getPaint();
        Stroke currentStroke = svgGenerator.getStroke();
        Shape currentClip = svgGenerator.getClip();

        // Create state object (making copies where necessary, e.g., AffineTransform)
        GraphicsState currentState = new GraphicsState(
            new AffineTransform(currentTransform),
            currentPaint,
            currentStroke,
            currentClip
        );

        stateStack.push(currentState);
        pathStack.push((Path2D.Double) currentPath.clone());
        logger.fine("gsave: State stack depth " + stateStack.size() + ", Path stack depth " + pathStack.size());
    }

    @Override
    public void grestore() {
        if (svgGenerator == null) return;

        if (!stateStack.isEmpty() && stateStack.size() > 1) { // Ensure we don't pop the initial base state
            GraphicsState restoredState = stateStack.pop();

            // Restore state onto the single svgGenerator instance
            svgGenerator.setTransform(restoredState.transform);
            svgGenerator.setPaint(restoredState.paint);
            svgGenerator.setStroke(restoredState.stroke);
            svgGenerator.setClip(restoredState.clip);

             if (!pathStack.isEmpty()) {
                 currentPath = pathStack.pop(); // Restore previous path
             } else {
                 logger.warning("grestore: Path stack was empty when trying to restore path.");
                 currentPath = new Path2D.Double(); // Reset to be safe
             }

            logger.fine("grestore: State stack depth " + stateStack.size() + ", Path stack depth " + pathStack.size());
        } else {
            logger.warning("grestore: Attempted to restore beyond initial state or stack was empty.");
        }
        // DO NOT dispose or replace svgGenerator instance here
    }

    @Override
    public void concatMatrix(double[] matrix) {
        if (matrix == null || matrix.length != 6) {
             logger.warning("concatMatrix: Invalid matrix provided.");
            return;
        }
        AffineTransform at = new AffineTransform(matrix);
        svgGenerator.transform(at); // Concatenate in Batik
        logger.fine("concatMatrix: applied " + Arrays.toString(matrix) + ", New CTM: " + formatAffineTransform(svgGenerator.getTransform()));
    }

    // --- State Attribute Setters ---
    // These methods directly set Batik's state.

    private Color parseColor(double[] components) {
        // Reuse logic from old GraphicState or refine here
         if (components == null || components.length == 0) return Color.BLACK;
         try {
            if (components.length == 1) { // Grayscale
                float g = Math.max(0.0f, Math.min(1.0f, (float) components[0]));
                return new Color(g, g, g);
            } else if (components.length == 3) { // RGB
                 float r = Math.max(0.0f, Math.min(1.0f, (float) components[0]));
                 float g = Math.max(0.0f, Math.min(1.0f, (float) components[1]));
                 float b = Math.max(0.0f, Math.min(1.0f, (float) components[2]));
                return new Color(r, g, b);
            } else if (components.length == 4) { // CMYK -> RGB (simplistic)
                float c = Math.max(0.0f, Math.min(1.0f, (float) components[0]));
                float m = Math.max(0.0f, Math.min(1.0f, (float) components[1]));
                float y = Math.max(0.0f, Math.min(1.0f, (float) components[2]));
                float k = Math.max(0.0f, Math.min(1.0f, (float) components[3]));
                float r = (1.0f - c) * (1.0f - k);
                float g = (1.0f - m) * (1.0f - k);
                float b = (1.0f - y) * (1.0f - k);
                return new Color(Math.max(0.0f, Math.min(1.0f, r)), 
                                 Math.max(0.0f, Math.min(1.0f, g)), 
                                 Math.max(0.0f, Math.min(1.0f, b)));
            } else {
                logger.warning("Unsupported color component count: " + components.length);
                return Color.MAGENTA; // Error color
            }
        } catch (IllegalArgumentException e) {
             logger.warning("Invalid color value: " + Arrays.toString(components) + " -> " + e.getMessage());
             return Color.RED; // Error color
        }
    }

    @Override
    public void setGrayFill(double gray) {
        Color color = parseColor(new double[]{gray});
        svgGenerator.setPaint(color);
        logger.fine("setGrayFill: " + gray + " -> " + color);
    }

    @Override
    public void setGrayStroke(double gray) {
        // Batik uses Paint for both fill and stroke color
        // Need separate state tracking if PS allows different fill/stroke
        // For now, assuming setting stroke color also sets Paint
        Color color = parseColor(new double[]{gray});
        svgGenerator.setPaint(color);
         logger.fine("setGrayStroke: " + gray + " -> " + color + " (sets current Paint)");
    }

    @Override
    public void setRGBColorFill(double r, double g, double b) {
        Color color = parseColor(new double[]{r, g, b});
        svgGenerator.setPaint(color);
        logger.fine("setRGBColorFill: " + r +","+ g +","+ b + " -> " + color);
    }

    @Override
    public void setRGBColorStroke(double r, double g, double b) {
         Color color = parseColor(new double[]{r, g, b});
         svgGenerator.setPaint(color);
         logger.fine("setRGBColorStroke: " + r +","+ g +","+ b + " -> " + color + " (sets current Paint)");
    }

    @Override
    public void setCMYKColorFill(double c, double m, double y, double k) {
         Color color = parseColor(new double[]{c, m, y, k});
         svgGenerator.setPaint(color);
         logger.fine("setCMYKColorFill: " + c +","+ m +","+ y +","+ k + " -> " + color);
    }

    @Override
    public void setCMYKColorStroke(double c, double m, double y, double k) {
         Color color = parseColor(new double[]{c, m, y, k});
         svgGenerator.setPaint(color);
         logger.fine("setCMYKColorStroke: " + c +","+ m +","+ y +","+ k + " -> " + color + " (sets current Paint)");
    }

    @Override
    public void setLineWidth(double width) {
        Stroke currentStroke = svgGenerator.getStroke();
        float newWidth = Math.max(0.0f, (float) width); // Ensure non-negative
        if (currentStroke instanceof BasicStroke) {
            BasicStroke bs = (BasicStroke) currentStroke;
            // Create new stroke with updated width, preserving other attributes
            svgGenerator.setStroke(new BasicStroke(newWidth, bs.getEndCap(), bs.getLineJoin(), bs.getMiterLimit(), bs.getDashArray(), bs.getDashPhase()));
        } else {
            // If not a BasicStroke, just create a simple one
            svgGenerator.setStroke(new BasicStroke(newWidth));
        }
        logger.fine("setLineWidth: " + width);
    }

    @Override
    public void setLineCap(int cap) {
        Stroke currentStroke = svgGenerator.getStroke();
        int awtCap = (cap == 0) ? BasicStroke.CAP_BUTT : (cap == 1) ? BasicStroke.CAP_ROUND : BasicStroke.CAP_SQUARE;
        if (currentStroke instanceof BasicStroke) {
            BasicStroke bs = (BasicStroke) currentStroke;
            svgGenerator.setStroke(new BasicStroke(bs.getLineWidth(), awtCap, bs.getLineJoin(), bs.getMiterLimit(), bs.getDashArray(), bs.getDashPhase()));
        } else {
            svgGenerator.setStroke(new BasicStroke(1.0f, awtCap, BasicStroke.JOIN_MITER)); // Default width/join
        }
        logger.fine("setLineCap: " + cap);
    }

    @Override
    public void setLineJoin(int join) {
        Stroke currentStroke = svgGenerator.getStroke();
        int awtJoin = (join == 0) ? BasicStroke.JOIN_MITER : (join == 1) ? BasicStroke.JOIN_ROUND : BasicStroke.JOIN_BEVEL;
         if (currentStroke instanceof BasicStroke) {
            BasicStroke bs = (BasicStroke) currentStroke;
            svgGenerator.setStroke(new BasicStroke(bs.getLineWidth(), bs.getEndCap(), awtJoin, bs.getMiterLimit(), bs.getDashArray(), bs.getDashPhase()));
        } else {
            svgGenerator.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, awtJoin)); // Default width/cap
        }
        logger.fine("setLineJoin: " + join);
    }

    @Override
    public void setMiterLimit(double limit) {
        Stroke currentStroke = svgGenerator.getStroke();
        float newLimit = Math.max(1.0f, (float) limit); // Miter limit must be >= 1.0
         if (currentStroke instanceof BasicStroke) {
            BasicStroke bs = (BasicStroke) currentStroke;
            svgGenerator.setStroke(new BasicStroke(bs.getLineWidth(), bs.getEndCap(), bs.getLineJoin(), newLimit, bs.getDashArray(), bs.getDashPhase()));
        } else {
             // Setting miter limit implies BasicStroke
             svgGenerator.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, newLimit));
        }
        logger.fine("setMiterLimit: " + limit);
    }

    @Override
    public void setDash(double[] pattern, double offset) {
        Stroke currentStroke = svgGenerator.getStroke();
        float[] floatPattern = null;
        if (pattern != null && pattern.length > 0) {
            floatPattern = new float[pattern.length];
            for (int i = 0; i < pattern.length; i++) {
                floatPattern[i] = Math.max(0.0f, (float) pattern[i]); // Dashes must be non-negative
            }
        } else {
             floatPattern = null; // null or empty array means solid line
        }
        float floatOffset = Math.max(0.0f, (float) offset);

         if (currentStroke instanceof BasicStroke) {
            BasicStroke bs = (BasicStroke) currentStroke;
            svgGenerator.setStroke(new BasicStroke(bs.getLineWidth(), bs.getEndCap(), bs.getLineJoin(), bs.getMiterLimit(), floatPattern, floatOffset));
        } else {
             svgGenerator.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, floatPattern, floatOffset));
        }
        logger.fine("setDash: pattern=" + Arrays.toString(pattern) + " offset=" + offset);
    }
    
    // --- Helper Methods --- 
    private void inspectBatikDom() {
        inspectBatikDom("Default call");
    }

    /**
     * Inspects the Batik DOM to count specific elements, for debugging.
     * Now logs all immediate children of the root element and counts <path> and <rect>.
     * @param context A string describing when/where the inspection is happening.
     */
    private void inspectBatikDom(String context) {
        if (svgGenerator == null) {
             logger.log(Level.WARNING, "inspectBatikDom ({0}): svgGenerator is null, cannot inspect.", context);
             return;
        }
        // Use the DOM document associated with the generator
        Document doc = svgGenerator.getDOMFactory(); 
        if (doc == null) {
             logger.log(Level.WARNING, "inspectBatikDom ({0}): svgGenerator.getDOMFactory() returned null, cannot inspect.", context);
             return;
        }
        Element currentEffectiveRoot = doc.getDocumentElement();
        if (currentEffectiveRoot == null) {
            logger.log(Level.WARNING, "inspectBatikDom ({0}): Document root is null, cannot inspect.", context);
             return;
        }

        NodeList children = currentEffectiveRoot.getChildNodes();
        int elementCount = 0;
        List<String> childTags = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elementCount++;
                childTags.add("<" + node.getNodeName() + ">");
            }
        }
        logger.log(Level.FINE, "inspectBatikDom ({0}): Root <{1}> has {2} child element(s): {3}", 
                     new Object[]{context, currentEffectiveRoot.getTagName(), elementCount, childTags});

        // Deeper inspection for <path> elements
        NodeList pathElements = currentEffectiveRoot.getElementsByTagNameNS(SVGDOMImplementation.SVG_NAMESPACE_URI, "path");
         logger.log(Level.FINE, "inspectBatikDom ({0}): Found {1} <path> element(s) under root.", new Object[]{context, pathElements.getLength()});
         for (int i = 0; i < Math.min(pathElements.getLength(), 5); i++) { // Log first few
            Node pathNode = pathElements.item(i);
            if (pathNode instanceof Element) {
                Element pathElement = (Element) pathNode;
                String dAttribute = pathElement.getAttributeNS(null, "d");
                String dSnippet = (dAttribute.length() > 80) ? dAttribute.substring(0, 80) + "..." : dAttribute;
                logger.log(Level.FINE, "inspectBatikDom ({0}): Path[{1}] d='{2}'", new Object[]{context, i, dSnippet});
            }
         }
         
        // Deeper inspection for <rect> elements
        NodeList rectElements = currentEffectiveRoot.getElementsByTagNameNS(SVGDOMImplementation.SVG_NAMESPACE_URI, "rect");
         logger.log(Level.FINE, "inspectBatikDom ({0}): Found {1} <rect> element(s) under root.", new Object[]{context, rectElements.getLength()});
         for (int i = 0; i < Math.min(rectElements.getLength(), 5); i++) { // Log first few
            Node rectNode = rectElements.item(i);
            if (rectNode instanceof Element) {
                Element rectElement = (Element) rectNode;
                logger.log(Level.FINE, "inspectBatikDom ({0}): Rect[{1}] x='{2}' y='{3}' w='{4}' h='{5}' fill='{6}'", 
                    new Object[]{context, i, 
                                 rectElement.getAttributeNS(null, "x"), 
                                 rectElement.getAttributeNS(null, "y"), 
                                 rectElement.getAttributeNS(null, "width"), 
                                 rectElement.getAttributeNS(null, "height"),
                                 rectElement.getAttributeNS(null, "fill")});
            }
         }
    }

     private String formatAffineTransform(java.awt.geom.AffineTransform transform) {
         // (Same implementation as before)
         double[] matrix = new double[6];
         transform.getMatrix(matrix);
         return String.format(Locale.ROOT, "[%.3f %.3f %.3f %.3f %.3f %.3f]",
             matrix[0], matrix[1], matrix[2], matrix[3], matrix[4], matrix[5]);
     }
} 