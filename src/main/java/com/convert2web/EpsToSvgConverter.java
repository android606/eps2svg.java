package com.convert2web;

import java.io.*;
import java.util.logging.Logger;

/**
 * Main class for converting EPS files to SVG
 */
public class EpsToSvgConverter {
    private static final Logger logger = Logger.getLogger(EpsToSvgConverter.class.getName());

    public EpsToSvgConverter() {
        logger.info("EPS to SVG Converter initialized");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar converter.jar <inputFile.eps> <outputFile.svg>");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];

        try {
            new EpsToSvgConverter().convert(inputFile, outputFile);
            System.out.println("Conversion successful: " + outputFile);
        } catch (IOException e) {
            System.err.println("Conversion failed: " + e.getMessage());
            if (!e.getMessage().startsWith("Not an EPS file:")) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    public void convert(String inputPath, String outputPath) throws IOException {
        logger.info("Starting conversion of " + inputPath + " to " + outputPath);

        File inputFile = new File(inputPath);
        if (!inputFile.exists() || !inputFile.isFile()) {
            throw new IOException("Input file does not exist: " + inputPath);
        }

        try {
            EpsFormatDetector.validateEpsFile(inputPath);
        } catch (IOException e) {
            logger.info("Conversion path: rejected (" + e.getMessage() + ")");
            throw e;
        }

        boolean isBinaryEps = BinaryEpsInterpreter.isBinaryEps(inputPath);
        logger.info("EPS file detected as: " + (isBinaryEps ? "binary" : "standard"));

        if (isBinaryEps) {
            new BinaryEpsInterpreter().convertToSvg(inputPath, outputPath);
            logger.info("Conversion completed via binary EPS interpreter.");
            return;
        }

        try {
            new AsciiEpsConverter().convert(inputPath, outputPath);
            logger.info("Conversion completed via ASCII VM pipeline.");
            return;
        } catch (Exception asciiFailure) {
            logger.warning("ASCII VM pipeline failed, using legacy interpreter: "
                    + asciiFailure.getMessage());
        }

        BatikGraphicsHandler graphicsHandler = new BatikGraphicsHandler();
        try {
            EpsInterpreter interpreter = new EpsInterpreter(graphicsHandler);
            interpreter.processEps(inputPath);
            graphicsHandler.writeToFile(outputPath);
            logger.info("Conversion completed successfully. SVG written to: " + outputPath);
        } catch (Exception e) {
            logger.severe("Conversion failed: " + e.getMessage());
            throw new IOException("Failed to convert EPS file: " + e.getMessage(), e);
        }
    }
}
