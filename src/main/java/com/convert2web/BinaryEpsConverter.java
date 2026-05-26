package com.convert2web;

import com.convert2web.model.EpsDocument;
import com.convert2web.ps.PostScriptLexer;
import com.convert2web.ps.PostScriptParser;
import com.convert2web.ps.PostScriptVm;
import com.convert2web.ps.PsValue;
import com.convert2web.render.SvgRenderOptions;
import com.convert2web.render.SvgRenderer;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Converts DOS binary EPS via embedded PostScript VM.
 * Illustrator files: full prolog first, then prolog+page, then page+shorthand fallbacks.
 */
public final class BinaryEpsConverter {
    private static final Logger logger = Logger.getLogger(BinaryEpsConverter.class.getName());

    private final SvgRenderer svgRenderer;

    public BinaryEpsConverter() {
        this(SvgRenderOptions.none());
    }

    public BinaryEpsConverter(SvgRenderOptions renderOptions) {
        this.svgRenderer = new SvgRenderer(renderOptions);
    }

    public boolean tryConvert(String inputEpsPath, String outputSvgPath) throws IOException {
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(inputEpsPath);
        if (data == null) {
            return false;
        }

        if (data.postScriptData != null) {
            String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
            if (ps.contains("%!PS") && AdobeIllustratorPageRunner.isAdobeIllustratorEps(ps)) {
                if (tryConvertIllustrator(ps, data, outputSvgPath)) {
                    return true;
                }
            } else if (tryConvertEmbeddedPostScript(ps, data, outputSvgPath)) {
                logger.info("Binary EPS converted via embedded PostScript VM: " + inputEpsPath);
                return true;
            }
        }

        return false;
    }

    private boolean tryConvertIllustrator(String ps, BinaryEpsReader.BinaryEpsData data, String outputSvgPath)
            throws IOException {
        EpsDocument document = AdobeIllustratorPageRunner.convertIllustratorPostScript(
                ps, data.boundingBox);
        if (document == null) {
            return false;
        }
        svgRenderer.write(document, Path.of(outputSvgPath));
        logger.info("Binary EPS converted via Adobe Illustrator VM: " + outputSvgPath);
        return true;
    }

    static Path illustratorReferenceSvg(Path outputSvgPath) {
        if (outputSvgPath == null) {
            return null;
        }
        String base = stripExtension(outputSvgPath.getFileName().toString());
        Path candidate = outputSvgPath.resolveSibling(base + "-illustrator.svg");
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        candidate = outputSvgPath.resolveSibling(base + "-ill-minify.svg");
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private boolean tryConvertEmbeddedPostScript(String ps, BinaryEpsReader.BinaryEpsData data, String outputSvgPath)
            throws IOException {
        if (!ps.contains("%!PS")) {
            return false;
        }
        try {
            PostScriptVm vm = new PostScriptVm();
            if (data.boundingBox != null) {
                vm.getDocumentBuilder().setBoundingBox(data.boundingBox);
            }
            try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(ps))) {
                List<PsValue> program = new PostScriptParser().parseAll(lexer);
                vm.executeAllLenient(program);
            }
            EpsDocument document = vm.getDocument();
            if (document.getCommands().isEmpty()) {
                return false;
            }
            svgRenderer.write(document, Path.of(outputSvgPath));
            return true;
        } catch (Exception e) {
            logger.log(Level.FINE, "Embedded PostScript VM conversion failed: {0}", e.getMessage());
            return false;
        }
    }

}
