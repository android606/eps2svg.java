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
        Eps2SvgCli.ParsedCommand command;
        try {
            command = Eps2SvgCli.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            Eps2SvgCli.printHelp(System.err);
            System.exit(1);
            return;
        }


        if (command.help()) {
            Eps2SvgCli.printHelp(System.out);
            System.exit(0);
        }

        if (command.inputPath() == null || command.outputPath() == null) {
            Eps2SvgCli.printHelp(System.err);
            System.exit(1);
        }

        String inputPath = command.inputPath();
        String outputPath = command.outputPath();

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
            new EpsToSvgConverter(command.renderOptions()).convert(inputPath, outputPath);
            System.out.println("Conversion completed successfully.");
        } catch (IOException e) {
            System.err.println("Conversion failed: " + e.getMessage());
            if (!e.getMessage().startsWith("Not an EPS file:")) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private static void configureLogger() {
        if (System.getProperty("java.util.logging.config.file") != null) {
            return;
        }
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
