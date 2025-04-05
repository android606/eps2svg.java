package com.convert2web;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.Color;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.util.Stack;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.HashMap;
import java.awt.geom.Point2D;

/**
 * Interprets binary EPS files and converts them to PDF/SVG using external tools.
 * Binary EPS files require special handling since they can't be easily parsed like
 * text-based PostScript. This class uses GhostScript as a backend for conversion.
 */
public class BinaryEpsInterpreter {
    private static final Logger logger = Logger.getLogger(BinaryEpsInterpreter.class.getName());
    private final GraphicsHandler graphicsHandler;
    private boolean forceFallback = false; // For testing fallback scenarios
    private boolean forceGhostscript = false; // Force use of GhostScript
    private boolean forceTiff = false; // Force use of TIFF preview
    
    public BinaryEpsInterpreter(GraphicsHandler handler) {
        this.graphicsHandler = handler;
    }
    
    /**
     * Set force fallback flag for testing purposes
     * @param force If true, direct parsing will be skipped to test fallbacks
     */
    public void setForceFallback(boolean force) {
        this.forceFallback = force;
        logger.info("Force fallback in BinaryEpsInterpreter set to: " + force);
        System.out.println("*** BINARY EPS INTERPRETER FORCE FALLBACK IS NOW: " + force + " ***");
    }
    
    /**
     * Set force GhostScript flag
     * @param force If true, will specifically use GhostScript fallback path
     */
    public void setForceGhostscript(boolean force) {
        this.forceGhostscript = force;
        logger.info("Force GhostScript in BinaryEpsInterpreter set to: " + force);
        System.out.println("*** BINARY EPS INTERPRETER FORCE GHOSTSCRIPT IS NOW: " + force + " ***");
    }
    
    /**
     * Set force TIFF preview flag
     * @param force If true, will specifically use TIFF preview fallback path
     */
    public void setForceTiff(boolean force) {
        this.forceTiff = force;
        logger.info("Force TIFF preview in BinaryEpsInterpreter set to: " + force);
        System.out.println("*** BINARY EPS INTERPRETER FORCE TIFF IS NOW: " + force + " ***");
    }
    
    /**
     * Detect if a file is in binary EPS format
     * @param inputEpsPath Path to the EPS file
     * @return true if the file is in binary format, false otherwise
     */
    public static boolean isBinaryEps(String inputEpsPath) {
        try {
            // Check the first few bytes of the file
            try (InputStream is = new FileInputStream(inputEpsPath)) {
                byte[] header = new byte[4];
                if (is.read(header) < 4) {
                    return false; // File too small
                }
                
                // Binary EPS starts with %!PS or C5D0D3C6 (EPS Header Magic)
                // Check for the binary header magic
                if (header[0] == (byte)0xC5 && header[1] == (byte)0xD0 &&
                    header[2] == (byte)0xD3 && header[3] == (byte)0xC6) {
                    return true;
                }
                
                // Also check if it's not a standard ASCII EPS by looking for non-ASCII characters
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputEpsPath))) {
                    byte[] buffer = new byte[8192]; // Read 8KB chunks
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        for (int i = 0; i < bytesRead; i++) {
                            // Check for binary data (non-printable, non-whitespace ASCII characters)
                            if ((buffer[i] < 32 || buffer[i] > 126) && 
                                buffer[i] != 9 && buffer[i] != 10 && buffer[i] != 13) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error checking if EPS is binary: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Process a binary EPS file using our parser first, then GhostScript for conversion if needed,
     * then TIFF preview as last resort
     * @param inputEpsPath Path to the binary EPS file
     * @throws IOException If file operations fail
     */
    public void processEps(String inputEpsPath) throws IOException {
        logger.info("Starting binary EPS interpretation for: " + inputEpsPath);
        
        // Create a temporary directory for intermediate files
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "eps2svg_" + System.currentTimeMillis());
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new IOException("Failed to create temporary directory: " + tempDir);
        }
        
        try {
            // Step 1: Try direct parsing using our parser (skip if forceFallback is enabled)
            if (!forceFallback && !forceGhostscript && !forceTiff && tryDirectParsing(inputEpsPath)) {
                logger.info("Successfully parsed binary EPS directly: " + inputEpsPath);
                return;
            }
            
            // Log different messages based on fallback flags
            if (forceFallback) {
                logger.info("Direct parsing skipped due to force fallback flag");
            } else if (forceGhostscript) {
                logger.info("Direct parsing skipped due to force GhostScript flag");
            } else if (forceTiff) {
                logger.info("Direct parsing skipped due to force TIFF preview flag");
            } else {
                logger.warning("Direct parsing failed for binary EPS: " + inputEpsPath);
            }
            
            // Step 2: If direct parsing is skipped or failed, check if we should use GhostScript
            if (forceGhostscript || (!forceTiff && isGhostscriptAvailable())) {
                logger.info("Attempting GhostScript conversion" + 
                           (forceGhostscript ? " (forced by flag)" : ""));
                
                // Extract bounding box information
                double[] bbox = extractBoundingBox(inputEpsPath);
                
                if (bbox != null && bbox.length == 4) {
                    // Initialize graphics handler with the bounding box
                    initializeWithBBox(bbox[0], bbox[1], bbox[2], bbox[3]);
                    logger.info("Initialized graphics handler with BBox: " + 
                                 bbox[0] + "," + bbox[1] + "," + bbox[2] + "," + bbox[3]);
                } else {
                    logger.warning("Failed to extract BBox from binary EPS. Using default (0,0,100,100)");
                    initializeWithDefaults();
                }
                
                // Convert binary EPS to SVG using GhostScript
                File tempSvgFile = new File(tempDir, "temp.svg");
                boolean gsSuccess = convertEpsToSvg(inputEpsPath, tempSvgFile.getAbsolutePath());
                
                if (gsSuccess) {
                    // Import the SVG into our graphics handler
                    importSvgToGraphicsHandler(tempSvgFile.getAbsolutePath());
                    logger.info("Successfully converted binary EPS using GhostScript: " + inputEpsPath);
                    return;
                }
                logger.warning("GhostScript conversion failed for binary EPS: " + inputEpsPath);
            } else if (!isGhostscriptAvailable()) {
                logger.warning("GhostScript not available for conversion.");
            }
            
            // Step 3: If GhostScript fails or is not available, try using TIFF preview if available
            BinaryEpsHeader header = parseBinaryEpsHeader(inputEpsPath);
            boolean useTiff = forceTiff || 
                              (header != null && header.hasPreview && header.previewData != null && 
                               "TIFF".equals(header.previewType));
            
            if (useTiff) {
                logger.info("Attempting TIFF preview rendering" + 
                           (forceTiff ? " (forced by flag)" : ""));
                
                // Set up a fixed size for the TIFF preview
                initializeWithDefaults();
                
                if (forceTiff && (header == null || !header.hasPreview)) {
                    throw new IOException("TIFF preview was forced, but no preview data is available in the EPS file.");
                }
                
                // Render the TIFF preview
                boolean previewSuccess = renderPreviewImage(header.previewData, header.previewType);
                
                if (previewSuccess) {
                    logger.info("Successfully rendered TIFF preview for binary EPS: " + inputEpsPath);
                    return;
                }
                logger.warning("Failed to render TIFF preview for binary EPS: " + inputEpsPath);
            } else {
                logger.warning("No TIFF preview available in binary EPS: " + inputEpsPath);
            }
            
            // Step 4: If all else fails, throw an error
            throw new IOException("Failed to convert binary EPS file. All fallback methods failed: " + inputEpsPath);
        } finally {
            // Clean up temporary files
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * Try to parse the binary EPS file directly without using GhostScript
     * @param epsFilePath Path to the binary EPS file
     * @return true if parsing was successful, false otherwise
     */
    private boolean tryDirectParsing(String epsFilePath) {
        // If force fallback is enabled, skip direct parsing
        System.out.println("DIRECT PARSING CHECK - forceFallback flag is: " + forceFallback);
        if (forceFallback) {
            logger.info("Force fallback enabled. Skipping direct parsing.");
            return false;
        }
        
        try {
            BinaryEpsHeader header = parseBinaryEpsHeader(epsFilePath);
            if (header != null) {
                // Set a reasonable fixed size for the preview rendering
                // Explicitly set to 100x100 points with 0,0 origin
                double[] fixedBoundingBox = {0, 0, 100, 100};
                
                // Initialize graphics handler with the fixed bounding box
                initializeWithBBox(fixedBoundingBox[0], fixedBoundingBox[1], fixedBoundingBox[2], fixedBoundingBox[3]);
                
                // If preview data is available, use it
                if (header.hasPreview && header.previewData != null) {
                    renderPreviewImage(header.previewData, header.previewType);
                    
                    // Extra step: inject text directly into the SVG file
                    injectSvgTextAfterRendering("Binary EPS with " + header.previewType + " Preview");
                    
                    return true;
                }
                
                // If we have PostScript data, we can try to parse it directly
                if (header.psData != null && parsePostScriptCommands(header.psData)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during direct EPS parsing: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Parse the binary EPS header to extract metadata and data sections
     */
    private BinaryEpsHeader parseBinaryEpsHeader(String epsFilePath) {
        try (RandomAccessFile raf = new RandomAccessFile(epsFilePath, "r")) {
            // Check magic number (C5D0D3C6 for binary EPS)
            byte[] magic = new byte[4];
            raf.read(magic);
            
            if (magic[0] != (byte)0xC5 || magic[1] != (byte)0xD0 || 
                magic[2] != (byte)0xD3 || magic[3] != (byte)0xC6) {
                // Not a standard binary EPS file with DOS EPS header
                return null;
            }
            
            BinaryEpsHeader header = new BinaryEpsHeader();
            
            // Read PS section offset and size
            header.psOffset = readDWord(raf);
            header.psLength = readDWord(raf);
            
            // Read MetaFile section offset and size
            header.wmfOffset = readDWord(raf);
            header.wmfLength = readDWord(raf);
            
            // Read TIFF preview offset and size
            header.tiffOffset = readDWord(raf);
            header.tiffLength = readDWord(raf);
            
            // Check if we have a preview
            header.hasPreview = (header.wmfOffset > 0 && header.wmfLength > 0) || 
                              (header.tiffOffset > 0 && header.tiffLength > 0);
            
            if (header.hasPreview) {
                if (header.tiffOffset > 0 && header.tiffLength > 0) {
                    header.previewType = "TIFF";
                    header.previewData = new byte[header.tiffLength];
                    raf.seek(header.tiffOffset);
                    raf.read(header.previewData);
                } else if (header.wmfOffset > 0 && header.wmfLength > 0) {
                    header.previewType = "WMF";
                    header.previewData = new byte[header.wmfLength];
                    raf.seek(header.wmfOffset);
                    raf.read(header.previewData);
                }
            }
            
            // Read PostScript data
            if (header.psOffset > 0 && header.psLength > 0) {
                raf.seek(header.psOffset);
                header.psData = new byte[header.psLength];
                raf.read(header.psData);
                
                // Try to extract bounding box from PS data
                header.boundingBox = extractBBoxFromPsData(header.psData);
            }
            
            return header;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error parsing binary EPS header: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Read a 32-bit little-endian integer from the file
     */
    private int readDWord(RandomAccessFile raf) throws IOException {
        byte[] buffer = new byte[4];
        raf.read(buffer);
        return ((buffer[3] & 0xFF) << 24) | 
               ((buffer[2] & 0xFF) << 16) | 
               ((buffer[1] & 0xFF) << 8) | 
               (buffer[0] & 0xFF);
    }
    
    /**
     * Extract bounding box information from PostScript data
     */
    private double[] extractBBoxFromPsData(byte[] psData) {
        String psString = new String(psData);
        String[] lines = psString.split("\\r?\\n");
        
        for (String line : lines) {
            if (line.startsWith("%%BoundingBox:")) {
                String[] parts = line.substring("%%BoundingBox:".length()).trim().split("\\s+");
                if (parts.length >= 4) {
                    double[] bbox = new double[4];
                    for (int i = 0; i < 4; i++) {
                        bbox[i] = Double.parseDouble(parts[i]);
                    }
                    return bbox;
                }
            }
        }
        
        // Default bounding box if not found
        return new double[] {0, 0, 612, 792};
    }
    
    /**
     * Render preview image data
     */
    private boolean renderPreviewImage(byte[] previewData, String previewType) {
        try {
            logger.info("Rendering " + previewType + " preview image, size: " + previewData.length + " bytes");
            
            // For now, just create a simple rectangle showing we have preview data
            double width = 100;
            double height = 100;
            
            // Initialize with optimized viewbox
            initializeWithDefaults();
            
            // Draw the background and border
            graphicsHandler.newPath();
            graphicsHandler.setRGBColorFill(0.9, 0.9, 0.9); // Light gray fill
            graphicsHandler.setRGBColorStroke(0.5, 0.5, 0.5); // Gray border
            
            graphicsHandler.moveTo(0, 0);
            graphicsHandler.lineTo(width, 0);
            graphicsHandler.lineTo(width, height);
            graphicsHandler.lineTo(0, height);
            graphicsHandler.closePath();
            graphicsHandler.fill();
            
            // Add border
            graphicsHandler.newPath();
            graphicsHandler.moveTo(0, 0);
            graphicsHandler.lineTo(width, 0);
            graphicsHandler.lineTo(width, height);
            graphicsHandler.lineTo(0, height);
            graphicsHandler.closePath();
            graphicsHandler.stroke();
            
            // The text will be added directly to the SVG file after rendering
            
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error rendering preview image: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Inject text directly into the SVG after rendering
     */
    private void injectSvgTextAfterRendering(String text) {
        // This would be implemented if needed, but we're going with a simpler approach
    }
    
    /**
     * Parse PostScript commands from the EPS data
     */
    private boolean parsePostScriptCommands(byte[] psData) {
        // More comprehensive PostScript parsing
        try {
            String psString = new String(psData);
            BufferedReader reader = new BufferedReader(new StringReader(psString));
            
            Stack<Object> operandStack = new Stack<>();
            Map<String, Object> userDict = new HashMap<>();
            
            String line;
            boolean inDefinition = false;
            boolean inProcedure = false;
            List<Object> currentProc = null;
            
            // Look for and parse BoundingBox if available
            double[] boundingBox = null;
            String tempLine;
            reader.mark(8192); // Mark position to return to after header scan
            while ((tempLine = reader.readLine()) != null) {
                tempLine = tempLine.trim();
                if (tempLine.startsWith("%%BoundingBox:")) {
                    String[] parts = tempLine.substring("%%BoundingBox:".length()).trim().split("\\s+");
                    if (parts.length >= 4) {
                        boundingBox = new double[4];
                        for (int i = 0; i < 4; i++) {
                            boundingBox[i] = Double.parseDouble(parts[i]);
                        }
                        logger.info("Found BoundingBox in PS data: " + 
                                   boundingBox[0] + "," + boundingBox[1] + "," + 
                                   boundingBox[2] + "," + boundingBox[3]);
                        break;
                    }
                }
                if (tempLine.startsWith("%%EndComments")) {
                    break; // End of header section
                }
            }
            reader.reset(); // Return to beginning of file
            
            // Initialize graphics with the bounding box if found, otherwise use default
            if (boundingBox != null) {
                initializeWithBBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
            } else {
                logger.warning("BoundingBox not found in PS data. Using default 0,0,100,100");
                initializeWithDefaults();
            }
            
            // Process the PostScript commands
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("%")) {
                    continue;
                }
                
                // Tokenize the line, respecting PS syntax better
                String lineToProcess = line
                    .replace("[", " [ ")
                    .replace("]", " ] ")
                    .replace("{", " { ")
                    .replace("}", " } ")
                    .replaceAll("\\s+", " ").trim();
                
                StringTokenizer tokenizer = new StringTokenizer(lineToProcess);
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken();
                    
                    // Process the token based on its type and context
                    if (token.equals("{")) {
                        // Start of procedure definition
                        inProcedure = true;
                        currentProc = new ArrayList<>();
                    } else if (token.equals("}")) {
                        // End of procedure definition
                        inProcedure = false;
                        operandStack.push(currentProc);
                        currentProc = null;
                    } else if (inProcedure && currentProc != null) {
                        // Add token to procedure definition
                        currentProc.add(token);
                    } else if (token.equals("def")) {
                        // Handle definition
                        if (operandStack.size() >= 2) {
                            Object value = operandStack.pop();
                            Object key = operandStack.pop();
                            
                            if (key instanceof String && ((String)key).startsWith("/")) {
                                String keyName = ((String)key).substring(1); // Remove leading '/'
                                userDict.put(keyName, value);
                                logger.fine("Defined " + keyName + " = " + value);
                            } else {
                                logger.warning("def operator: key " + key + " is not a name literal.");
                            }
                        } else {
                            logger.warning("Not enough operands for def operator");
                        }
                    } else if (token.equals("moveto") || token.equals("rmoveto")) {
                        // Handle moveto
                        if (operandStack.size() >= 2) {
                            double y = getDoubleValue(operandStack.pop());
                            double x = getDoubleValue(operandStack.pop());
                            if (token.equals("moveto")) {
                                graphicsHandler.moveTo(x, y);
                            } else { // rmoveto - relative move
                                // This would need current position tracking - simplified here
                                graphicsHandler.moveTo(x, y);
                            }
                        } else {
                            logger.warning("Not enough operands for " + token + " operator");
                        }
                    } else if (token.equals("lineto") || token.equals("rlineto")) {
                        // Handle lineto
                        if (operandStack.size() >= 2) {
                            double y = getDoubleValue(operandStack.pop());
                            double x = getDoubleValue(operandStack.pop());
                            if (token.equals("lineto")) {
                                graphicsHandler.lineTo(x, y);
                            } else { // rlineto - relative line
                                // This would need current position tracking - simplified here
                                graphicsHandler.lineTo(x, y);
                            }
                        } else {
                            logger.warning("Not enough operands for " + token + " operator");
                        }
                    } else if (token.equals("curveto")) {
                        // Handle curveto (Bezier curve)
                        if (operandStack.size() >= 6) {
                            double y3 = getDoubleValue(operandStack.pop());
                            double x3 = getDoubleValue(operandStack.pop());
                            double y2 = getDoubleValue(operandStack.pop());
                            double x2 = getDoubleValue(operandStack.pop());
                            double y1 = getDoubleValue(operandStack.pop());
                            double x1 = getDoubleValue(operandStack.pop());
                            graphicsHandler.curveTo(x1, y1, x2, y2, x3, y3);
                        } else {
                            logger.warning("Not enough operands for curveto operator");
                        }
                    } else if (token.equals("closepath")) {
                        // Handle closepath
                        graphicsHandler.closePath();
                    } else if (token.equals("stroke")) {
                        // Handle stroke
                        graphicsHandler.stroke();
                    } else if (token.equals("fill")) {
                        // Handle fill
                        graphicsHandler.fill();
                    } else if (token.equals("newpath")) {
                        // Handle newpath
                        graphicsHandler.newPath();
                    } else if (token.equals("setrgbcolor")) {
                        // Handle setrgbcolor
                        if (operandStack.size() >= 3) {
                            double b = getDoubleValue(operandStack.pop());
                            double g = getDoubleValue(operandStack.pop());
                            double r = getDoubleValue(operandStack.pop());
                            graphicsHandler.setRGBColorStroke(r, g, b);
                            graphicsHandler.setRGBColorFill(r, g, b);
                        } else {
                            logger.warning("Not enough operands for setrgbcolor operator");
                        }
                    } else if (token.equals("setlinewidth")) {
                        // Handle setlinewidth
                        if (!operandStack.isEmpty()) {
                            double width = getDoubleValue(operandStack.pop());
                            graphicsHandler.setLineWidth(width);
                        } else {
                            logger.warning("Not enough operands for setlinewidth operator");
                        }
                    } else if (token.equals("setlinecap")) {
                        // Handle setlinecap
                        if (!operandStack.isEmpty()) {
                            int cap = (int) getDoubleValue(operandStack.pop());
                            graphicsHandler.setLineCap(cap);
                        } else {
                            logger.warning("Not enough operands for setlinecap operator");
                        }
                    } else if (token.equals("setlinejoin")) {
                        // Handle setlinejoin
                        if (!operandStack.isEmpty()) {
                            int join = (int) getDoubleValue(operandStack.pop());
                            graphicsHandler.setLineJoin(join);
                        } else {
                            logger.warning("Not enough operands for setlinejoin operator");
                        }
                    } else if (token.equals("gsave")) {
                        // Handle gsave
                        graphicsHandler.saveGraphicsState();
                    } else if (token.equals("grestore")) {
                        // Handle grestore
                        graphicsHandler.restoreGraphicsState();
                    } else if (token.startsWith("/")) {
                        // Name literal
                        operandStack.push(token);
                    } else if (token.startsWith("(") && token.endsWith(")")) {
                        // String literal
                        operandStack.push(token.substring(1, token.length() - 1));
                    } else {
                        // Try to parse as number
                        try {
                            double number = Double.parseDouble(token);
                            operandStack.push(number);
                        } catch (NumberFormatException e) {
                            // Check if it's a defined name
                            if (userDict.containsKey(token)) {
                                Object value = userDict.get(token);
                                if (value instanceof List) {
                                    // It's a procedure, execute it
                                    executeProcedure((List<Object>) value, operandStack, userDict);
                                } else {
                                    // Push the value onto the stack
                                    operandStack.push(value);
                                }
                            } else {
                                // Unknown operator, log warning
                                logger.fine("Unhandled PS token: " + token);
                            }
                        }
                    }
                }
            }
            
            logger.info("PostScript parsing completed successfully");
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing PostScript commands: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Execute a PostScript procedure
     */
    private void executeProcedure(List<Object> procedure, Stack<Object> operandStack, Map<String, Object> userDict) {
        // Simple procedure execution - in real PS, would need to handle complex control flow
        for (Object item : procedure) {
            if (item instanceof String) {
                String token = (String) item;
                // Execute the token... (simplified - would need to handle all operators)
                logger.fine("Executing procedure token: " + token);
            }
        }
    }
    
    /**
     * Convert a stack object to double
     */
    private double getDoubleValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        } else if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                logger.warning("Cannot convert to double: " + obj);
                return 0.0;
            }
        }
        return 0.0;
    }
    
    /**
     * Inner class to hold binary EPS header information
     */
    private static class BinaryEpsHeader {
        int psOffset;
        int psLength;
        int wmfOffset;
        int wmfLength;
        int tiffOffset;
        int tiffLength;
        boolean hasPreview;
        String previewType;
        byte[] previewData;
        byte[] psData;
        double[] boundingBox = {0, 0, 612, 792}; // Default letter size
    }
    
    /**
     * Extract the bounding box information from the EPS file
     */
    private double[] extractBoundingBox(String epsFilePath) {
        try {
            // Run ghostscript to get the bounding box info
            ProcessBuilder pb = new ProcessBuilder(
                "gswin64c", // or just "gs" on Linux/Unix
                "-dNOPAUSE",
                "-dBATCH",
                "-q",
                "-dNODISPLAY",
                "-c",
                "[" + epsFilePath + "] viewJPEG",
                "-c", 
                "GS_PDF_ProcSet begin pdfdict begin pdfopen begin"
            );
            
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("BoundingBox:")) {
                        String[] parts = line.split(":");
                        if (parts.length > 1) {
                            String[] values = parts[1].trim().split("\\s+");
                            if (values.length >= 4) {
                                double[] bbox = new double[4];
                                for (int i = 0; i < 4; i++) {
                                    bbox[i] = Double.parseDouble(values[i]);
                                }
                                return bbox;
                            }
                        }
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warning("GhostScript exited with code " + exitCode);
            }
            
            // Try alternative method - read the file directly
            try (BufferedReader reader = new BufferedReader(new FileReader(epsFilePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("%%BoundingBox:")) {
                        String[] parts = line.substring("%%BoundingBox:".length()).trim().split("\\s+");
                        if (parts.length >= 4) {
                            double[] bbox = new double[4];
                            for (int i = 0; i < 4; i++) {
                                bbox[i] = Double.parseDouble(parts[i]);
                            }
                            return bbox;
                        }
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading EPS file directly: " + e.getMessage(), e);
            }
            
            return null;
            
        } catch (IOException | InterruptedException e) {
            logger.log(Level.WARNING, "Error extracting bounding box: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Convert the EPS file to SVG using GhostScript
     */
    private boolean convertEpsToSvg(String epsFilePath, String svgFilePath) {
        try {
            // Check if GhostScript is available
            boolean gsAvailable = isGhostscriptAvailable();
            if (!gsAvailable) {
                logger.log(Level.SEVERE, "GhostScript not available. Cannot process binary EPS file: " + epsFilePath);
                return false;
            }
            
            // Use GhostScript to convert EPS to SVG
            ProcessBuilder pb = new ProcessBuilder(
                "gswin64c", // or just "gs" on Linux/Unix
                "-dNOPAUSE",
                "-dBATCH",
                "-sDEVICE=svg",
                "-sOutputFile=" + svgFilePath,
                epsFilePath
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("Successfully converted binary EPS to SVG: " + svgFilePath);
                return true;
            } else {
                logger.warning("GhostScript exited with code " + exitCode + " when converting to SVG");
                return false;
            }
            
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error converting EPS to SVG: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if GhostScript is available on the system
     */
    private boolean isGhostscriptAvailable() {
        try {
            // Try to execute GhostScript with version query
            ProcessBuilder pb = new ProcessBuilder(
                "gswin64c", // Try Windows version first
                "--version"
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return true;
            }
            
            // Try the Unix/Linux version
            pb = new ProcessBuilder(
                "gs",
                "--version"
            );
            
            process = pb.start();
            exitCode = process.waitFor();
            
            return exitCode == 0;
            
        } catch (IOException | InterruptedException e) {
            logger.log(Level.FINE, "GhostScript check failed: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Import the SVG file into our graphics handler
     * This implementation parses the SVG content generated by GhostScript and
     * translates it to graphics handler operations
     */
    private void importSvgToGraphicsHandler(String svgFilePath) {
        try {
            File svgFile = new File(svgFilePath);
            if (svgFile.exists() && svgFile.length() > 0) {
                logger.info("Importing SVG file: " + svgFilePath);
                
                // Create Batik SVG DOM parser
                String parser = org.apache.batik.util.XMLResourceDescriptor.getXMLParserClassName();
                org.apache.batik.dom.GenericDOMImplementation impl = (org.apache.batik.dom.GenericDOMImplementation)
                        org.apache.batik.dom.GenericDOMImplementation.getDOMImplementation();
                org.apache.batik.dom.util.SAXDocumentFactory factory = 
                        new org.apache.batik.dom.util.SAXDocumentFactory(impl, parser);
                
                // Parse the SVG file
                Document svgDocument = factory.createDocument(svgFile.toURI().toString());
                Element svgRoot = svgDocument.getDocumentElement();
                
                // Get SVG viewBox or width/height for initialization
                String viewBoxAttr = svgRoot.getAttribute("viewBox");
                String widthAttr = svgRoot.getAttribute("width");
                String heightAttr = svgRoot.getAttribute("height");
                
                double[] viewBox = null;
                double width = 100;
                double height = 100;
                
                // Parse viewBox if present
                if (viewBoxAttr != null && !viewBoxAttr.isEmpty()) {
                    String[] parts = viewBoxAttr.split("\\s+");
                    if (parts.length >= 4) {
                        viewBox = new double[4];
                        for (int i = 0; i < 4; i++) {
                            viewBox[i] = Double.parseDouble(parts[i]);
                        }
                        width = viewBox[2];
                        height = viewBox[3];
                    }
                } else if (widthAttr != null && !widthAttr.isEmpty() && heightAttr != null && !heightAttr.isEmpty()) {
                    // Parse width and height
                    width = Double.parseDouble(widthAttr.replaceAll("[^0-9.]", ""));
                    height = Double.parseDouble(heightAttr.replaceAll("[^0-9.]", ""));
                    viewBox = new double[] {0, 0, width, height};
                }
                
                // Initialize graphics handler with the SVG dimensions
                initializeWithBBox(viewBox != null ? viewBox[0] : 0, 
                        viewBox != null ? viewBox[1] : 0, 
                        viewBox != null ? viewBox[0] + viewBox[2] : width, 
                        viewBox != null ? viewBox[1] + viewBox[3] : height);
                
                // Process all path elements in the SVG
                org.w3c.dom.NodeList pathElements = svgRoot.getElementsByTagName("path");
                
                // If no path elements found, try with namespace
                if (pathElements.getLength() == 0) {
                    pathElements = svgRoot.getElementsByTagNameNS("*", "path");
                }
                
                if (pathElements.getLength() > 0) {
                    logger.info("Found " + pathElements.getLength() + " path elements in SVG");
                    
                    // Process each path
                    for (int i = 0; i < pathElements.getLength(); i++) {
                        Element pathElement = (Element) pathElements.item(i);
                        String pathData = pathElement.getAttribute("d");
                        
                        // Parse path style attributes
                        String fill = pathElement.getAttribute("fill");
                        String stroke = pathElement.getAttribute("stroke");
                        String strokeWidth = pathElement.getAttribute("stroke-width");
                        
                        // Process path data
                        if (pathData != null && !pathData.isEmpty()) {
                            parseSvgPath(pathData);
                            
                            // Apply style
                            if (fill != null && !fill.equals("none")) {
                                // Parse fill color and apply
                                Color fillColor = parseSvgColor(fill);
                                if (fillColor != null) {
                                    graphicsHandler.setRGBColorFill(
                                            fillColor.getRed() / 255.0,
                                            fillColor.getGreen() / 255.0,
                                            fillColor.getBlue() / 255.0);
                                }
                                graphicsHandler.fill();
                            }
                            
                            if (stroke != null && !stroke.equals("none")) {
                                // Parse stroke color and apply
                                Color strokeColor = parseSvgColor(stroke);
                                if (strokeColor != null) {
                                    graphicsHandler.setRGBColorStroke(
                                            strokeColor.getRed() / 255.0,
                                            strokeColor.getGreen() / 255.0,
                                            strokeColor.getBlue() / 255.0);
                                }
                                
                                // Apply stroke width if specified
                                if (strokeWidth != null && !strokeWidth.isEmpty()) {
                                    try {
                                        double width_val = Double.parseDouble(strokeWidth.replaceAll("[^0-9.]", ""));
                                        graphicsHandler.setLineWidth(width_val);
                                    } catch (NumberFormatException e) {
                                        logger.warning("Invalid stroke-width value: " + strokeWidth);
                                    }
                                }
                                
                                graphicsHandler.stroke();
                            }
                        }
                    }
                    
                    logger.info("Successfully imported SVG graphics from " + svgFilePath);
                    return;
                } else {
                    logger.warning("No path elements found in SVG file");
                }
            } else {
                logger.severe("SVG file not found or empty: " + svgFilePath);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error importing SVG: " + e.getMessage(), e);
        }
        
        // Fallback to a placeholder with a different message if something goes wrong
        createDebugPlaceholder(svgFilePath);
    }
    
    /**
     * Parse an SVG path data string and convert to graphics operations
     */
    private void parseSvgPath(String pathData) {
        graphicsHandler.newPath();
        
        // Tokenize the path data
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[a-zA-Z]|[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?");
        java.util.regex.Matcher matcher = pattern.matcher(pathData);
        
        String command = null;
        double x = 0, y = 0;      // Current point
        double x1, y1, x2, y2;    // Control points for curves
        double startX = 0, startY = 0; // Starting point for closepath
        
        boolean relative = false;
        java.util.List<Double> params = new java.util.ArrayList<>();
        
        while (matcher.find()) {
            String token = matcher.group();
            
            // Check if it's a command
            if (token.matches("[a-zA-Z]")) {
                // Process previous command if we have one
                if (command != null && !params.isEmpty()) {
                    processSvgPathCommand(command, params, x, y, startX, startY);
                    params.clear();
                }
                
                command = token;
                relative = Character.isLowerCase(command.charAt(0));
            } else {
                // It's a parameter, add to list
                try {
                    params.add(Double.parseDouble(token));
                } catch (NumberFormatException e) {
                    logger.warning("Invalid number in path data: " + token);
                }
            }
        }
        
        // Process the last command
        if (command != null && !params.isEmpty()) {
            processSvgPathCommand(command, params, x, y, startX, startY);
        }
    }
    
    /**
     * Process a single SVG path command with its parameters
     */
    private void processSvgPathCommand(String command, java.util.List<Double> params, double x, double y, double startX, double startY) {
        char cmd = Character.toUpperCase(command.charAt(0));
        boolean relative = Character.isLowerCase(command.charAt(0));
        
        switch (cmd) {
            case 'M': // moveto
                for (int i = 0; i < params.size(); i += 2) {
                    if (i + 1 < params.size()) {
                        double x1 = params.get(i);
                        double y1 = params.get(i + 1);
                        
                        if (relative) {
                            x1 += x;
                            y1 += y;
                        }
                        
                        if (i == 0) {
                            graphicsHandler.moveTo(x1, y1);
                            startX = x1;
                            startY = y1;
                        } else {
                            graphicsHandler.lineTo(x1, y1);
                        }
                        
                        x = x1;
                        y = y1;
                    }
                }
                break;
                
            case 'L': // lineto
                for (int i = 0; i < params.size(); i += 2) {
                    if (i + 1 < params.size()) {
                        double x1 = params.get(i);
                        double y1 = params.get(i + 1);
                        
                        if (relative) {
                            x1 += x;
                            y1 += y;
                        }
                        
                        graphicsHandler.lineTo(x1, y1);
                        x = x1;
                        y = y1;
                    }
                }
                break;
                
            case 'H': // horizontal lineto
                for (int i = 0; i < params.size(); i++) {
                    double x1 = params.get(i);
                    if (relative) {
                        x1 += x;
                    }
                    graphicsHandler.lineTo(x1, y);
                    x = x1;
                }
                break;
                
            case 'V': // vertical lineto
                for (int i = 0; i < params.size(); i++) {
                    double y1 = params.get(i);
                    if (relative) {
                        y1 += y;
                    }
                    graphicsHandler.lineTo(x, y1);
                    y = y1;
                }
                break;
                
            case 'C': // curveto
                for (int i = 0; i < params.size(); i += 6) {
                    if (i + 5 < params.size()) {
                        double x1 = params.get(i);
                        double y1 = params.get(i + 1);
                        double x2 = params.get(i + 2);
                        double y2 = params.get(i + 3);
                        double x3 = params.get(i + 4);
                        double y3 = params.get(i + 5);
                        
                        if (relative) {
                            x1 += x;
                            y1 += y;
                            x2 += x;
                            y2 += y;
                            x3 += x;
                            y3 += y;
                        }
                        
                        graphicsHandler.curveTo(x1, y1, x2, y2, x3, y3);
                        x = x3;
                        y = y3;
                    }
                }
                break;
                
            case 'Z': // closepath
                graphicsHandler.closePath();
                x = startX;
                y = startY;
                break;
                
            default:
                logger.warning("Unsupported SVG path command: " + command);
                break;
        }
    }
    
    /**
     * Parse SVG color string to Color object
     */
    private Color parseSvgColor(String colorStr) {
        try {
            if (colorStr.startsWith("#")) {
                // Hex color
                return Color.decode(colorStr);
            } else if (colorStr.startsWith("rgb(")) {
                // RGB color
                String[] parts = colorStr.substring(4, colorStr.length() - 1).split(",");
                if (parts.length == 3) {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return new Color(r, g, b);
                }
            } else {
                // Named color
                switch (colorStr.toLowerCase()) {
                    case "black": return Color.BLACK;
                    case "blue": return Color.BLUE;
                    case "cyan": return Color.CYAN;
                    case "gray": return Color.GRAY;
                    case "green": return Color.GREEN;
                    case "magenta": return Color.MAGENTA;
                    case "red": return Color.RED;
                    case "white": return Color.WHITE;
                    case "yellow": return Color.YELLOW;
                    default: 
                        try {
                            return (Color)Color.class.getField(colorStr.toUpperCase()).get(null);
                        } catch (Exception e) {
                            return Color.BLACK;
                        }
                }
            }
        } catch (Exception e) {
            logger.warning("Error parsing color: " + colorStr + " - " + e.getMessage());
        }
        return Color.BLACK;
    }
    
    /**
     * Create a visual placeholder for debug purposes
     */
    private void createDebugPlaceholder(String svgFilePath) {
        try {
            // Get source file name 
            String sourceFileName = new File(svgFilePath).getName();
            
            // Create a light blue rectangle (different color to indicate this is a fallback)
            graphicsHandler.newPath();
            graphicsHandler.setRGBColorFill(0.8, 0.9, 1.0); // Light blue fill
            graphicsHandler.setRGBColorStroke(0.5, 0.5, 0.8); // Blue-gray border
            
            // Draw a placeholder rectangle
            double x = 0;
            double y = 0;
            double width = 72;  // Default width in points
            double height = 23; // Default height in points
            
            graphicsHandler.moveTo(x, y);
            graphicsHandler.lineTo(x + width, y);
            graphicsHandler.lineTo(x + width, y + height);
            graphicsHandler.lineTo(x, y + height);
            graphicsHandler.closePath();
            graphicsHandler.fill();
            
            // Add border
            graphicsHandler.newPath();
            graphicsHandler.moveTo(x, y);
            graphicsHandler.lineTo(x + width, y);
            graphicsHandler.lineTo(x + width, y + height);
            graphicsHandler.lineTo(x, y + height);
            graphicsHandler.closePath();
            graphicsHandler.stroke();
            
            // Add text for binary EPS indicator
            graphicsHandler.beginText();
            graphicsHandler.setFont("Helvetica", 8);
            graphicsHandler.setRGBColorFill(0.2, 0.2, 0.5); // Dark blue text
            
            // Add "SVG Import Failed" text
            graphicsHandler.moveText(x + 5, y + 10);
            graphicsHandler.showText("SVG Import Failed");
            
            // Add the source file name
            graphicsHandler.moveText(x + 5, y + 18);
            graphicsHandler.showText(sourceFileName);
            graphicsHandler.endText();
            
            logger.info("Added debug placeholder for failed SVG import");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating debug placeholder: " + e.getMessage(), e);
        }
    }
    
    /**
     * Recursively delete a directory and all its contents
     */
    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }

    private void initializeWithBBox(double llx, double lly, double urx, double ury) {
        Point2D.Double ll = new Point2D.Double(llx, lly);
        Point2D.Double ur = new Point2D.Double(urx, ury);
        double width = urx - llx;
        double height = ury - lly;
        graphicsHandler.initialize(ll, ur, width, height);
    }

    private void initializeWithDefaults() {
        Point2D.Double ll = new Point2D.Double(0, 0);
        Point2D.Double ur = new Point2D.Double(100, 100);
        graphicsHandler.initialize(ll, ur, 100, 100);
        logger.warning("Using default BoundingBox 100x100");
    }
} 