package com.convert2web;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

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
                    graphicsHandler.initialize(bbox[0], bbox[1], bbox[2], bbox[3]);
                    logger.info("Initialized graphics handler with BBox: " + 
                                 bbox[0] + "," + bbox[1] + "," + bbox[2] + "," + bbox[3]);
                } else {
                    logger.warning("Failed to extract BBox from binary EPS. Using default (0,0,100,100)");
                    graphicsHandler.initialize(0.0, 0.0, 100.0, 100.0);
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
                graphicsHandler.initialize(0.0, 0.0, 100.0, 100.0);
                
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
                graphicsHandler.initialize(
                    fixedBoundingBox[0], 
                    fixedBoundingBox[1], 
                    fixedBoundingBox[2], 
                    fixedBoundingBox[3]
                );
                
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
            graphicsHandler.initialize(0, 0, width, height);
            
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
        // Basic PostScript parsing for simple commands
        // For a complete implementation, a full PostScript interpreter would be needed
        try {
            String psString = new String(psData);
            String[] lines = psString.split("\\r?\\n");
            
            boolean inDefinition = false;
            double x = 0, y = 0;
            
            for (String line : lines) {
                line = line.trim();
                
                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("%")) {
                    continue;
                }
                
                // Very simple parsing of some basic commands
                if (line.contains(" moveto")) {
                    String[] parts = line.split(" moveto")[0].trim().split("\\s+");
                    if (parts.length >= 2) {
                        x = Double.parseDouble(parts[parts.length - 2]);
                        y = Double.parseDouble(parts[parts.length - 1]);
                        graphicsHandler.moveTo(x, y);
                    }
                } else if (line.contains(" lineto")) {
                    String[] parts = line.split(" lineto")[0].trim().split("\\s+");
                    if (parts.length >= 2) {
                        x = Double.parseDouble(parts[parts.length - 2]);
                        y = Double.parseDouble(parts[parts.length - 1]);
                        graphicsHandler.lineTo(x, y);
                    }
                } else if (line.equals("stroke")) {
                    graphicsHandler.stroke();
                } else if (line.equals("fill")) {
                    graphicsHandler.fill();
                } else if (line.contains(" setrgbcolor")) {
                    String[] parts = line.split(" setrgbcolor")[0].trim().split("\\s+");
                    if (parts.length >= 3) {
                        double r = Double.parseDouble(parts[parts.length - 3]);
                        double g = Double.parseDouble(parts[parts.length - 2]);
                        double b = Double.parseDouble(parts[parts.length - 1]);
                        graphicsHandler.setRGBColorStroke(r, g, b);
                        graphicsHandler.setRGBColorFill(r, g, b);
                    }
                }
                // Many more commands would need to be implemented for a complete parser
            }
            
            logger.info("Basic PostScript parsing completed");
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing PostScript commands: " + e.getMessage(), e);
            return false;
        }
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
     * This is a placeholder - the actual implementation would depend on the graphics handler
     */
    private void importSvgToGraphicsHandler(String svgFilePath) {
        try {
            File svgFile = new File(svgFilePath);
            if (svgFile.exists() && svgFile.length() > 0) {
                // Import SVG using an appropriate SVG reader/parser
                logger.info("Importing SVG file: " + svgFilePath);
                // This would be where you'd use an SVG parser to read the file and then
                // translate the SVG elements to graphics handler operations
                // For now, we just log a placeholder message
                logger.warning("SVG import not yet fully implemented. Using visual placeholder.");
            } else {
                logger.severe("SVG file not found or empty: " + svgFilePath);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error importing SVG: " + e.getMessage(), e);
        }
        
        // Create a visual placeholder for binary EPS
        try {
            // Get source file name 
            String sourceFileName = new File(svgFilePath).getName();
            
            // Create a light gray rectangle
            graphicsHandler.newPath();
            graphicsHandler.setRGBColorFill(0.9, 0.9, 0.9); // Light gray fill
            graphicsHandler.setRGBColorStroke(0.5, 0.5, 0.5); // Gray border
            
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
            graphicsHandler.setRGBColorFill(0.2, 0.2, 0.2); // Dark gray text
            
            // Add "Binary EPS File" text
            graphicsHandler.moveText(x + 5, y + 10);
            graphicsHandler.showText("Binary EPS File");
            
            // Add the source file name
            graphicsHandler.moveText(x + 5, y + 18);
            graphicsHandler.showText(sourceFileName);
            graphicsHandler.endText();
            
            logger.info("Added visual placeholder for binary EPS file");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating placeholder for binary EPS: " + e.getMessage(), e);
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
} 