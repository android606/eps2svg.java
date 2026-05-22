package com.convert2web;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import com.convert2web.ps.PostScriptLexer;
import com.convert2web.ps.PostScriptParser;
import com.convert2web.ps.PostScriptVm;
import com.convert2web.ps.PostScriptVmException;
import com.convert2web.ps.PsValue;
import com.convert2web.render.SvgRenderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Converts ASCII EPS via lexer/parser/VM and {@link SvgRenderer}.
 */
public final class AsciiEpsConverter {
    private static final Logger logger = Logger.getLogger(AsciiEpsConverter.class.getName());

    private final SvgRenderer svgRenderer = new SvgRenderer();

    public EpsDocument convertToDocument(String inputEpsPath) throws IOException {
        EpsHeader header = readHeader(inputEpsPath);
        String fullText = Files.readString(Path.of(inputEpsPath), StandardCharsets.ISO_8859_1);
        if (GhostscriptEpsPageRunner.isGhostscriptEps(fullText)) {
            EpsDocument ghostscript = tryGhostscriptPage(inputEpsPath, header, fullText);
            if (ghostscript != null) {
                logger.info("ASCII path: Ghostscript EPS page");
                return ghostscript;
            }
            throw new IOException("Ghostscript EPS page stream could not be converted to vector graphics");
        }

        if (AdobeIllustratorPageRunner.isAdobeIllustratorEps(fullText)) {
            EpsDocument adobe = AdobeIllustratorPageRunner.convertIllustratorPostScript(
                    fullText, header.boundingBox);
            if (adobe != null) {
                logger.info("ASCII path: Adobe Illustrator (see Illustrator path log)");
                return applyHiResBoundingBox(adobe, header);
            }
        }

        EpsDocument fullVm = AdobeIllustratorPageRunner.runFullPostScript(
                fullText, header.boundingBox, false);
        if (fullVm != null && !fullVm.getCommands().isEmpty()) {
            logger.info("ASCII path: full PostScript (non-Illustrator)");
            return applyHiResBoundingBox(fullVm, header);
        }

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(inputEpsPath), StandardCharsets.ISO_8859_1))) {
            String line;
            boolean headerDone = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!headerDone) {
                    if (trimmed.startsWith("%%EndComments")) {
                        headerDone = true;
                    } else if (!trimmed.startsWith("%")) {
                        headerDone = true;
                        appendBodyLine(body, line);
                    }
                    continue;
                }
                if (trimmed.startsWith("%%EOF") || "showpage".equals(trimmed)) {
                    break;
                }
                if (trimmed.startsWith("%%BeginData:")) {
                    skipHexDataSection(reader);
                    continue;
                }
                if (trimmed.startsWith("%%")) {
                    continue;
                }
                if ("endstream".equals(trimmed)) {
                    break;
                }
                appendBodyLine(body, line);
            }
        }

        PostScriptVm vm = new PostScriptVm();
        if (header.boundingBox != null) {
            vm.getDocumentBuilder().setBoundingBox(header.boundingBox);
        }
        if (header.hiResBoundingBox != null) {
            vm.getDocumentBuilder().setHiResBoundingBox(header.hiResBoundingBox);
        }
        for (Map.Entry<String, String> entry : header.metadata.entrySet()) {
            vm.getDocumentBuilder().putMetadata(entry.getKey(), entry.getValue());
        }
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(body.toString()))) {
            List<PsValue> program = new PostScriptParser().parseAll(lexer);
            try {
                vm.executeAll(program);
            } catch (PostScriptVmException e) {
                if (vm.getDocument().getCommands().isEmpty()) {
                    throw e;
                }
            }
        }
        logger.info("ASCII path: body-only VM");
        return vm.getDocument();
    }

    public void convert(String inputEpsPath, String outputSvgPath) throws IOException {
        EpsDocument document = convertToDocument(inputEpsPath);
        svgRenderer.write(document, Path.of(outputSvgPath));
    }

    private static void appendBodyLine(StringBuilder body, String line) {
        int comment = line.indexOf('%');
        if (comment >= 0) {
            line = line.substring(0, comment);
        }
        if (!line.trim().isEmpty()) {
            body.append(line).append('\n');
        }
    }

    private static EpsHeader readHeader(String inputEpsPath) throws IOException {
        EpsHeader header = new EpsHeader();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(inputEpsPath), StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("%%BoundingBox:")) {
                    header.boundingBox = parseBoundingBox(trimmed.substring("%%BoundingBox:".length()));
                } else if (trimmed.startsWith("%%HiResBoundingBox:")) {
                    header.hiResBoundingBox = parseBoundingBox(trimmed.substring("%%HiResBoundingBox:".length()));
                } else if (trimmed.startsWith("%%") && trimmed.contains(":")) {
                    int colon = trimmed.indexOf(':');
                    String key = trimmed.substring(2, colon).trim();
                    String value = trimmed.substring(colon + 1).trim();
                    if (!key.isEmpty()) {
                        header.metadata.put(key, value);
                    }
                }
                if (trimmed.startsWith("%%EndComments")) {
                    break;
                }
                if (!trimmed.startsWith("%")) {
                    break;
                }
            }
        }
        return header;
    }

    private static EpsDocument applyHiResBoundingBox(EpsDocument document, EpsHeader header) {
        if (header.hiResBoundingBox == null) {
            return document;
        }
        return new EpsDocument(
                document.getBoundingBox(),
                header.hiResBoundingBox,
                document.getCommands(),
                document.metadata());
    }

    private static void skipHexDataSection(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().startsWith("%%EndData")) {
                return;
            }
        }
    }

    private static EpsDocument tryGhostscriptPage(
            String inputEpsPath, EpsHeader header, String postScript) throws IOException {
        String stream = GhostscriptEpsPageRunner.extractPageStream(postScript);
        if (stream == null) {
            return null;
        }
        EpsDocument document = AdobeIllustratorPageRunner.runPageBody(
                stream, header.boundingBox, false);
        if (document == null) {
            return null;
        }
        if (header.hiResBoundingBox != null) {
            document = new EpsDocument(
                    document.getBoundingBox(),
                    header.hiResBoundingBox,
                    document.getCommands(),
                    document.metadata());
        }
        return document;
    }

    private static BoundingBox parseBoundingBox(String values) {
        String[] parts = values.trim().split("\\s+");
        if (parts.length != 4) {
            return null;
        }
        return new BoundingBox(
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]));
    }

    private static final class EpsHeader {
        private BoundingBox boundingBox;
        private BoundingBox hiResBoundingBox;
        private final Map<String, String> metadata = new LinkedHashMap<>();
    }
}
