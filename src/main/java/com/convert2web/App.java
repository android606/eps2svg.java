package com.convert2web;

import java.io.*;
import java.util.Date;
import java.util.logging.*;
import java.util.Arrays;

/**
 * Main application class for EPS to SVG conversion
 */
public class App {
    private static final Logger logger = Logger.getLogger(App.class.getName());

    static {
        configureLogger();
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 4) {
            System.out.println("Usage: java -jar eps2svg.jar <input_eps_file> <output_svg_file> [--force-ghostscript|--force-tiff]");
            System.out.println("\nOptions:");
            System.out.println("  --force-ghostscript   Force use of GhostScript for conversion");
            System.out.println("  --force-tiff          Force use of TIFF preview for conversion");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];
        
        // Check for optional flags
        boolean forceGhostscript = false;
        boolean forceTiff = false;
        
        for (int i = 2; i < args.length; i++) {
            if ("--force-ghostscript".equals(args[i])) {
                forceGhostscript = true;
                System.out.println("*** FORCE GHOSTSCRIPT FLAG IS SET ***");
            } else if ("--force-tiff".equals(args[i])) {
                forceTiff = true;
                System.out.println("*** FORCE TIFF FLAG IS SET ***");
            } else {
                System.out.println("Unknown argument: " + args[i]);
                System.out.println("Usage: java -jar eps2svg.jar <input_eps_file> <output_svg_file> [--force-ghostscript|--force-tiff]");
                System.exit(1);
            }
        }
        
        // Quick validation of input and output paths
        File inputFile = new File(inputPath);
        if (!inputFile.exists() || !inputFile.isFile()) {
            System.err.println("Input file does not exist: " + inputPath);
            System.exit(1);
        }
        
        File outputFile = new File(outputPath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                System.err.println("Failed to create parent directories for output file: " + outputPath);
                System.exit(1);
            }
        }
        
        // Perform the conversion
        try {
            EpsToSvgConverter converter = new EpsToSvgConverter();
            
            // Set the force flags if specified
            if (forceGhostscript) {
                converter.setForceGhostscript(true);
            }
            
            if (forceTiff) {
                converter.setForceTiff(true);
            }
            
            converter.convert(inputPath, outputPath);
            System.out.println("Conversion completed successfully.");
        } catch (IOException e) {
            System.err.println("Conversion failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void configureLogger() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);
        
        // Remove default handlers
        for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
        
        // Add console handler
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(Level.INFO);
        rootLogger.addHandler(handler);
    }
} 