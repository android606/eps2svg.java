package com.convert2web;

import com.convert2web.render.SvgRenderOptions;

import java.io.*;
import java.util.logging.Logger;

/**
 * Main class for converting EPS files to SVG
 */
public class EpsToSvgConverter {
    private static final Logger logger = Logger.getLogger(EpsToSvgConverter.class.getName());
    private final SvgRenderOptions renderOptions;

    public EpsToSvgConverter() {
        this(SvgRenderOptions.none());
    }

    public EpsToSvgConverter(SvgRenderOptions renderOptions) {
        this.renderOptions = renderOptions == null ? SvgRenderOptions.none() : renderOptions;
        logger.info("EPS to SVG Converter initialized");
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

        try {
            new EpsToSvgConverter(command.renderOptions()).convert(command.inputPath(), command.outputPath());
            System.out.println("Conversion successful: " + command.outputPath());
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
            new BinaryEpsInterpreter(renderOptions).convertToSvg(inputPath, outputPath);
            logger.info("Conversion completed via binary EPS interpreter.");
            return;
        }

        try {
            new AsciiEpsConverter(renderOptions).convert(inputPath, outputPath);
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
