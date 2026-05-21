package com.convert2web;

import java.io.*;
import java.util.logging.*;

/**
 * Main application class for EPS to SVG conversion
 */
public class App {
    private static final Logger logger = Logger.getLogger(App.class.getName());

    static {
        configureLogger();
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java -jar eps2svg.jar <input_eps_file> <output_svg_file>");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

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

        try {
            new EpsToSvgConverter().convert(inputPath, outputPath);
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

        for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(Level.INFO);
        rootLogger.addHandler(handler);
    }
}
