package com.convert2web;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.LogManager;

// W3C DOM imports
import org.w3c.dom.Document;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

// Batik imports
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.anim.dom.SVGDOMImplementation;
// Required for SVGGraphics2DIOException if used in catch block
import org.apache.batik.svggen.SVGGraphics2DIOException; 

/**
 * Main class for converting EPS files to SVG
 */
public class EpsToSvgConverter {
    private static final Logger logger = Logger.getLogger(EpsToSvgConverter.class.getName());
    private boolean forceFallback = false; // Traditional force fallback option (affects binary EPS only)
    private boolean forceGhostscript = false; // Use GhostScript specifically
    private boolean forceTiff = false; // Use TIFF preview specifically
    
    /**
     * Constructor initializes logger
     */
    public EpsToSvgConverter() {
        logger.info("EPS to SVG Converter initialized");
    }
    
    /**
     * Main method that handles command line arguments - input and output file paths
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar converter.jar <inputFile.eps> <outputFile.svg>");
            return;
        }
        
        String inputFile = args[0];
        String outputFile = args[1];
        
        try {
            EpsToSvgConverter converter = new EpsToSvgConverter();
            converter.convert(inputFile, outputFile);
            System.out.println("Conversion successful: " + outputFile);
        } catch (IOException e) {
            System.err.println("Conversion failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Set force fallback flag for binary EPS files
     * @param force If true, direct parsing is skipped to test fallbacks
     */
    public void setForceFallback(boolean force) {
        this.forceFallback = force;
        logger.info("Force fallback set to: " + force);
    }
    
    /**
     * Set force GhostScript flag
     * @param force If true, will specifically use GhostScript for conversion
     */
    public void setForceGhostscript(boolean force) {
        this.forceGhostscript = force;
        logger.info("Force GhostScript set to: " + force);
    }
    
    /**
     * Set force TIFF preview flag
     * @param force If true, will specifically use TIFF preview for conversion
     */
    public void setForceTiff(boolean force) {
        this.forceTiff = force;
        logger.info("Force TIFF preview set to: " + force);
    }
    
    /**
     * Convert an EPS file to SVG
     * @param inputPath Path to the input EPS file
     * @param outputPath Path to the output SVG file
     * @throws IOException If file operations fail
     */
    public void convert(String inputPath, String outputPath) throws IOException {
        logger.info("Starting conversion of " + inputPath + " to " + outputPath);
        
        // Validate input file
        File inputFile = new File(inputPath);
        if (!inputFile.exists() || !inputFile.isFile()) {
            throw new IOException("Input file does not exist: " + inputPath);
        }
        
        // Determine EPS file type
        boolean isBinaryEps = isBinaryEpsFile(inputPath);
        logger.info("EPS file detected as: " + (isBinaryEps ? "binary" : "standard"));
        
        // Create graphics handler for SVG output
        BatikGraphicsHandler graphicsHandler = new BatikGraphicsHandler();
        
        try {
            if (isBinaryEps) {
                // Process Binary EPS
                BinaryEpsInterpreter interpreter = new BinaryEpsInterpreter(graphicsHandler);
                
                // Pass through any force flags
                if (forceFallback) {
                    interpreter.setForceFallback(forceFallback);
                }
                
                if (forceGhostscript) {
                    interpreter.setForceGhostscript(forceGhostscript);
                }
                
                if (forceTiff) {
                    interpreter.setForceTiff(forceTiff);
                }
                
                // Process the file
                interpreter.processEps(inputPath);
            } else {
                // Process Standard EPS
                EpsInterpreter interpreter = new EpsInterpreter(graphicsHandler);
                interpreter.processEps(inputPath);
            }
            
            // Write the SVG to the output file
            graphicsHandler.writeToFile(outputPath);
            logger.info("Conversion completed successfully. SVG written to: " + outputPath);
        } catch (Exception e) {
            logger.severe("Conversion failed: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to convert EPS file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Determine if an EPS file is in binary format
     * @param filePath Path to the EPS file
     * @return true if the file is binary EPS, false if standard EPS
     * @throws IOException If file operations fail
     */
    private boolean isBinaryEpsFile(String filePath) throws IOException {
        try (InputStream is = new BufferedInputStream(new FileInputStream(filePath))) {
            // Read the first 4 bytes to check for binary EPS magic number
            byte[] header = new byte[4];
            int bytesRead = is.read(header, 0, 4);
            
            if (bytesRead < 4) {
                return false;
            }
            
            // Check for binary EPS header magic number (0xC5D0D3C6)
            return header[0] == (byte)0xC5 && 
                   header[1] == (byte)0xD0 && 
                   header[2] == (byte)0xD3 && 
                   header[3] == (byte)0xC6;
        }
    }
} 