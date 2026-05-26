package com.convert2web;

import com.convert2web.render.SvgRenderOptions;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for DOS binary EPS files (magic C5D0D3C6).
 * Extracts embedded PostScript and writes SVG without Ghostscript.
 */
public class BinaryEpsInterpreter {
    private static final Logger logger = Logger.getLogger(BinaryEpsInterpreter.class.getName());

    private final BinaryEpsConverter converter;

    public BinaryEpsInterpreter() {
        this(SvgRenderOptions.none());
    }

    public BinaryEpsInterpreter(SvgRenderOptions renderOptions) {
        this.converter = new BinaryEpsConverter(renderOptions);
    }

    /**
     * CLI helper: exit 0 if the file is DOS binary EPS, 1 otherwise.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: BinaryEpsInterpreter <eps-file>");
            System.exit(2);
        }
        try {
            System.exit(isBinaryEps(args[0]) ? 0 : 1);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * True when the file starts with the DOS binary EPS header magic.
     */
    public static boolean isBinaryEps(String inputEpsPath) throws IOException {
        return BinaryEpsReader.isDosBinaryEps(inputEpsPath);
    }

    /**
     * Convert a DOS binary EPS file to SVG.
     *
     * @throws IOException if the file is not binary EPS or conversion fails
     */
    public void convertToSvg(String inputEpsPath, String outputSvgPath) throws IOException {
        if (!isBinaryEps(inputEpsPath)) {
            throw new IOException("Not a DOS binary EPS file: " + inputEpsPath);
        }
        logger.info("Converting binary EPS: " + inputEpsPath);
        if (converter.tryConvert(inputEpsPath, outputSvgPath)) {
            logger.info("Binary EPS conversion succeeded: " + outputSvgPath);
            return;
        }
        throw new IOException(
                "Binary EPS conversion failed for " + inputEpsPath
                        + ": could not interpret embedded PostScript.");
    }
}
