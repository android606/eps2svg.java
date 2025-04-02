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
 * Main class to convert EPS files to SVG using a separated interpreter and handler.
 */
public class EpsToSvgConverter {
    private static final Logger logger = Logger.getLogger(EpsToSvgConverter.class.getName());

    // --- Constructor ---
    public EpsToSvgConverter() {
        setupLogger();
    }
    
    // --- Logger Setup ---
    private void setupLogger() {
        // Basic console logging setup, consider moving to a static block or config file
        Logger rootLogger = Logger.getLogger(""); // Get root logger
        rootLogger.setLevel(Level.FINE); // Set desired logging level
        // Remove existing handlers to avoid duplicate logging if called multiple times
        for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
             rootLogger.removeHandler(handler);
        }
        // Add a console handler
        java.util.logging.ConsoleHandler consoleHandler = new java.util.logging.ConsoleHandler();
        consoleHandler.setLevel(Level.FINE); // Or Level.INFO for less verbosity
        consoleHandler.setFormatter(new SimpleFormatter() {
             private static final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";
            @Override
            public synchronized String format(java.util.logging.LogRecord lr) {
                 return String.format(format,
                         new java.util.Date(lr.getMillis()),
                         lr.getLevel().getLocalizedName(),
                         lr.getMessage()
                 );
             }
         });
         rootLogger.addHandler(consoleHandler);

        // Optional: Add a file handler
         try {
             FileHandler fileHandler = new FileHandler("converter.log", false); // Overwrite log on each run
             fileHandler.setLevel(Level.FINE);
             fileHandler.setFormatter(new SimpleFormatter()); // Or XMLFormatter
             rootLogger.addHandler(fileHandler);
         } catch (IOException e) {
             logger.log(Level.SEVERE, "Could not initialize log file handler", e);
         }

        logger.fine("Logger initialized."); // Initial log message
    }

    // --- Main method - Entry point ---
    public static void main(String[] args) {
        // Configure logging (Keep this section)
        try {
            InputStream stream = EpsToSvgConverter.class.getClassLoader().getResourceAsStream("logging.properties");
            if (stream == null) {
                System.err.println("Failed to find logging.properties. Using default logging settings.");
            } else {
                LogManager.getLogManager().readConfiguration(stream);
            }
        } catch (IOException e) {
            System.err.println("Error loading logging configuration: " + e.getMessage());
        }

        // --- Restore argument parsing --- 
        if (args.length < 2) { // Use args again
            System.err.println("Usage: java EpsToSvgConverter <inputFile.eps> <outputFile.svg>");
            System.exit(1);
        }
        String inputFile = args[0];
        String outputFile = args[1];

        EpsToSvgConverter converter = new EpsToSvgConverter();
        try {
            converter.convert(inputFile, outputFile);
            logger.info("Conversion process finished for: " + inputFile);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Conversion failed: " + e.getMessage(), e);
            System.exit(1);
        }
    }

    // --- Refactored Conversion Method ---
    public void convert(String inputEpsPath, String outputSvgPath) throws Exception {
        logger.info("Starting conversion: " + inputEpsPath + " -> " + outputSvgPath);
        GraphicsHandler handler = null;
        try {
            // 1. Instantiate the Batik Handler
            handler = new BatikGraphicsHandler();
            
            // 2. Instantiate the EPS Interpreter with the handler
            EpsInterpreter interpreter = new EpsInterpreter(handler);
            
            // 3. Process the EPS file (interpreter initializes handler with BBox)
            interpreter.processEps(inputEpsPath);
            
            // 4. Write the output SVG file (Handler takes care of finalizing)
            handler.writeToFile(outputSvgPath);

            logger.info("Conversion completed successfully: " + outputSvgPath);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "IOException during conversion: " + e.getMessage(), e);
            throw e; // Re-throw IOExceptions
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during conversion process: " + e.getMessage(), e);
            // Wrap other exceptions
            throw new RuntimeException("Conversion failed unexpectedly: " + e.getMessage(), e);
        }
    }
} 