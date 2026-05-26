package com.convert2web;

import com.convert2web.image.IllustratorAgmImageExtractor;
import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.ps.PostScriptLexer;
import com.convert2web.ps.PostScriptParseException;
import com.convert2web.ps.PostScriptParser;
import com.convert2web.ps.PostScriptVm;
import com.convert2web.ps.PsValue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs Adobe Illustrator EPS via full prolog execution when possible, otherwise page body
 * with shorthand aliases from the file prolog or static fallbacks.
 */
final class AdobeIllustratorPageRunner {
    /** {@link com.convert2web.render.SvgRenderer} metadata: skip EPS Y-flip when {@code false}. */
    static final String METADATA_SVG_PAGE_Y_FLIP = "svg.pageYFlip";

    private static final Logger logger = Logger.getLogger(AdobeIllustratorPageRunner.class.getName());

    /** Static-only preamble; prefer {@link #buildShorthandPreamble(String)} per file. */
    static final String SHORTHAND_PREAMBLE = AdobeIllustratorShorthand.buildPreamble("");

    private AdobeIllustratorPageRunner() {
    }

    static boolean isAdobeIllustratorEps(String postScript) {
        return postScript.contains("%%Creator: (Adobe Illustrator")
                || postScript.contains("%%Creator: Adobe Illustrator")
                || postScript.contains("%AI8_CreatorVersion:")
                || postScript.contains("%%AI8_CreatorVersion:");
    }

    static String buildShorthandPreamble(String postScript) {
        return AdobeIllustratorShorthand.buildPreamble(postScript);
    }

    /**
     * Tier 1: execute prolog (lenient), reinforce file shorthands, then page body (lenient).
     * Trailer/terminate sections are skipped so AGM cleanup does not erase recorded paths.
     * Files without a page marker run the entire program leniently.
     */
    static EpsDocument runFullPostScript(String postScript, BoundingBox boundingBox) {
        return runFullPostScript(postScript, boundingBox, true);
    }

    /**
     * @param illustratorFile when false (plain ASCII EPS), always apply the SVG Y-flip for
     *        zero-origin bounding boxes; Illustrator zero-origin pages use Y-down device space.
     */
    static EpsDocument runFullPostScript(
            String postScript, BoundingBox boundingBox, boolean illustratorFile) {
        boolean illustratorYDown = illustratorFile && isIllustratorYDownPage(boundingBox);
        int pageStart = findPageBodyStart(postScript);
        if (pageStart < 0) {
            return runProgramLenient(
                    sanitizeIllustratorPageText(postScript), boundingBox, illustratorYDown, "full PostScript");
        }

        String pageBody = extractPageBody(postScript);
        if (pageBody == null || pageBody.isEmpty()) {
            return null;
        }

        PostScriptVm vm = newPostScriptVm(boundingBox, illustratorYDown);
        String prolog = sanitizeIllustratorPageText(postScript.substring(0, pageStart));
        try {
            executeProgram(vm, prolog, true);
        } catch (Exception e) {
            logger.log(Level.FINE, "Prolog execution error (continuing): {0}", e.getMessage());
        }
        vm.resetForPageBody();
        String rawPage = extractRawPageBody(postScript);
        if (rawPage != null) {
            vm.setIndexedPaletteContext(rawPage);
        }
        try {
            executeProgram(vm, buildShorthandPreamble(postScript) + "end\n", true);
            executeProgram(vm, pageBody + "\nend\n", false);
        } catch (Exception e) {
            logger.log(Level.FINE, "Page execution strict failed, retrying lenient: {0}", e.getMessage());
            try {
                executeProgram(vm, pageBody + "\nend\n", true);
            } catch (Exception e2) {
                logger.log(Level.FINE, "Page execution lenient failed: {0}", e2.getMessage());
                return null;
            }
        }
        return documentOrNull(vm, "full PostScript", postScript);
    }

    /**
     * Tier 2: execute prolog through page setup, reinforce shorthands, then page body.
     */
    static EpsDocument runPrologThenPageBody(String postScript, BoundingBox boundingBox) {
        return runFullPostScript(postScript, boundingBox);
    }

    /**
     * Tier 3: page body only with shorthand preamble (file {@code ldf} aliases + static fallbacks).
     */
    static EpsDocument runPageBody(String pageBody, BoundingBox boundingBox) {
        return runPageBody(pageBody, boundingBox, null);
    }

    static EpsDocument runPageBody(String pageBody, BoundingBox boundingBox, String postScriptForAliases) {
        return runPageBody(pageBody, boundingBox, isIllustratorYDownPage(boundingBox), postScriptForAliases);
    }

    static EpsDocument runPageBody(String pageBody, BoundingBox boundingBox, boolean illustratorYDown) {
        return runPageBody(pageBody, boundingBox, illustratorYDown, null);
    }

    static EpsDocument runPageBody(
            String pageBody, BoundingBox boundingBox, boolean illustratorYDown, String postScriptForAliases) {
        String preamble = postScriptForAliases != null
                ? buildShorthandPreamble(postScriptForAliases)
                : SHORTHAND_PREAMBLE;
        String program = preamble + pageBody + "\nend\n";
        PostScriptVm vm = newPostScriptVm(boundingBox, illustratorYDown);
        if (postScriptForAliases != null) {
            String rawPage = extractRawPageBody(postScriptForAliases);
            if (rawPage != null) {
                vm.setIndexedPaletteContext(rawPage);
            }
        }
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(program))) {
            List<PsValue> tokens = new PostScriptParser().parseAll(lexer);
            try {
                vm.executeAll(tokens);
            } catch (Exception strict) {
                logger.log(Level.FINE, "Page body strict failed, retrying lenient: {0}", strict.getMessage());
                vm.executeAllLenient(tokens);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Adobe page body execution failed: {0}", e.getMessage());
            return null;
        }
        return documentOrNull(vm, "page body+shorthand", postScriptForAliases);
    }

    /**
     * Tries tier 1, then 2, then 3 in order.
     */
    static EpsDocument convertIllustratorPostScript(String postScript, BoundingBox boundingBox) {
        return convertIllustratorPostScript(postScript, boundingBox, null);
    }

    static EpsDocument convertIllustratorPostScript(
            String postScript, BoundingBox boundingBox, java.nio.file.Path illustratorReferenceSvg) {
        EpsDocument document = convertIllustratorPostScriptInternal(postScript, boundingBox);
        if (document == null || illustratorReferenceSvg == null) {
            return document;
        }
        List<GraphicsCommand> vectorsOnly = new ArrayList<>();
        for (GraphicsCommand command : document.getCommands()) {
            if (!(command instanceof GraphicsCommand.EmbeddedImage)) {
                vectorsOnly.add(command);
            }
        }
        EpsDocument vectors = new EpsDocument(
                document.getBoundingBox(),
                document.getHiResBoundingBox(),
                vectorsOnly,
                document.metadata());
        String rawPage = extractRawPageBody(postScript);
        return IllustratorAgmImageExtractor.mergeIntoDocument(vectors, rawPage, illustratorReferenceSvg);
    }

    private static EpsDocument convertIllustratorPostScriptInternal(String postScript, BoundingBox boundingBox) {
        EpsDocument document = runFullPostScript(postScript, boundingBox);
        if (document != null) {
            logger.info("Illustrator path: full PostScript");
            return document;
        }
        document = runPrologThenPageBody(postScript, boundingBox);
        if (document != null) {
            logger.info("Illustrator path: prolog+page");
            return document;
        }
        String pageBody = extractPageBody(postScript);
        if (pageBody == null || pageBody.isEmpty()) {
            return null;
        }
        document = runPageBody(pageBody, boundingBox, postScript);
        if (document != null) {
            logger.info("Illustrator path: page body+shorthand");
        }
        return document;
    }

    static int findPageBodyStart(String postScript) {
        int start = findLastDscLine(postScript, "%%EndPageSetup");
        if (start < 0) {
            start = findLastDscLine(postScript, "%%EndSetup");
        }
        if (start < 0) {
            return -1;
        }
        return skipLineTerminator(postScript, start);
    }

    static String extractPageBody(String postScript) {
        int start = findPageBodyStart(postScript);
        if (start < 0) {
            return null;
        }

        int end = findDscLineAfter(postScript, "%%PageTrailer", start);
        if (end < 0) {
            end = findDscLineAfter(postScript, "%%Trailer", start);
        }
        if (end < 0) {
            return null;
        }
        String body = postScript.substring(start, end).trim();
        if (body.isEmpty()) {
            return null;
        }
        return sanitizeIllustratorPageBody(body);
    }

    /** Page body text before sanitization (for raster placeholder discovery). */
    static String extractRawPageBody(String postScript) {
        int start = findPageBodyStart(postScript);
        if (start < 0) {
            return null;
        }
        int end = findDscLineAfter(postScript, "%%PageTrailer", start);
        if (end < 0) {
            end = findDscLineAfter(postScript, "%%Trailer", start);
        }
        if (end < 0) {
            return null;
        }
        String body = postScript.substring(start, end).trim();
        return body.isEmpty() ? null : body;
    }

    /**
     * Sanitizes Illustrator page body for VM execution: keeps image dictionaries and
     * {@code %%BeginBinary} blocks (parsed by {@link com.convert2web.ps.PostScriptLexer}).
     */
    static String sanitizeIllustratorPageBody(String text) {
        if (text == null) {
            return null;
        }
        String body = stripLevelWrapperBlocks(text);
        body = stripIllustratorColorServerDicts(body);
        body = body.replaceAll("(?m)^false sop\\s*\\r?\\n?", "");
        body = body.replaceAll("(?m)^true sop\\s*\\r?\\n?", "");
        body = body.replaceAll("(?m)^\\d+ /0 /CSD get_res sepcs\\s*\\r?\\n?", "");
        body = stripMarkedBlocks(body, "/BCKTCI");
        body = stripShadingBlocks(body);
        body = stripAdobeFontBlocks(body, "%ADOBeginSubsetFont:");
        body = stripAdobeFontBlocks(body, "%ADOt1write:");
        body = body.replaceAll("(?m)^%ADO.*$\\r?\\n?", "");
        body = body.replaceAll("(?m)^userdict /annotatepage.*ifelse\\s*\\r?\\n?", "");
        body = body.replaceAll(
                "(?m)^1\\s+-1\\s+scale\\s+0\\s+-?[\\d.]+(?:[eE][+-]?\\d+)?\\s+translate\\s*\\r?\\n?",
                "");
        return body;
    }

    /**
     * Removes Illustrator binary blobs and setup blocks that break the lexer or are not needed
     * for vector path extraction in prolog/setup sections.
     */
    static String sanitizeIllustratorPageText(String text) {
        if (text == null) {
            return null;
        }
        String body = stripLevelWrapperBlocks(text);
        body = stripMarkedBlocks(body, "%%BeginBinary:", "%%EndBinary");
        body = stripMarkedBlocks(body, "/BCKTCI");
        body = stripShadingBlocks(body);
        body = stripDoubleAngleDicts(body);
        body = stripDictionaryRemnants(body);
        body = stripAdobeFontBlocks(body, "%ADOBeginSubsetFont:");
        body = stripAdobeFontBlocks(body, "%ADOt1write:");
        body = body.replaceAll("(?s)<\\s*/[^>]{1,2000}>\\s*", "");
        body = body.replaceAll("(?s)<~[^~]{0,50000}~>\\s*", "");
        body = body.replaceAll("(?s)<[^~]{0,50000}~>\\s*", "");
        body = body.replaceAll("(?m)^[^\\r\\n]{0,500}~>\\s*\\r?\\n?", "");
        body = body.replaceAll("(?m)^%ADO.*$\\r?\\n?", "");
        body = body.replaceAll("(?m)^userdict /annotatepage.*ifelse\\s*\\r?\\n?", "");
        body = body.replaceAll(
                "(?m)^1\\s+-1\\s+scale\\s+0\\s+-?[\\d.]+(?:[eE][+-]?\\d+)?\\s+translate\\s*\\r?\\n?",
                "");
        return body;
    }

    /**
     * Removes lines left when {@link #stripDoubleAngleDicts} closes on an inner {@code >>}
     * (e.g. {@code ]}, {@code } >>}, {@code /O 3 >>}).
     */
    static String stripDictionaryRemnants(String text) {
        String s = text;
        s = s.replaceAll("(?m)^\\s*\\}\\s*>>\\s*\\r?\\n?", "");
        s = s.replaceAll("(?m)^\\s*/O\\s+\\d+\\s*>>\\s*\\r?\\n?", "");
        s = s.replaceAll("(?s)\\}\\s*>>\\s*", "");
        return s;
    }

    /** Removes all PostScript {@code << ... >>} dictionary literals. */
    static String stripDoubleAngleDicts(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf("<<", i);
            if (start < 0) {
                out.append(text.substring(i));
                break;
            }
            out.append(text, i, start);
            int dictDepth = 1;
            int arrayDepth = 0;
            int j = start + 2;
            while (j < text.length() - 1 && dictDepth > 0) {
                if (text.startsWith("<<", j)) {
                    dictDepth++;
                    j += 2;
                } else if (text.charAt(j) == '[') {
                    arrayDepth++;
                    j++;
                } else if (text.charAt(j) == ']' && arrayDepth > 0) {
                    arrayDepth--;
                    j++;
                } else if (text.startsWith(">>", j) && arrayDepth == 0) {
                    dictDepth--;
                    j += 2;
                } else {
                    j++;
                }
            }
            i = j;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
        }
        return out.toString();
    }

    private static String stripShadingBlocks(String text) {
        String marker = "/0 <<\n/ShadingType";
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int start = text.indexOf(marker, i);
            if (start < 0) {
                out.append(text.substring(i));
                break;
            }
            out.append(text, i, start);
            int shfill = text.indexOf("shfill", start);
            if (shfill < 0) {
                out.append(text.substring(start));
                break;
            }
            i = shfill + 6;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
        }
        return out.toString();
    }

    private static String stripAdobeFontBlocks(String text, String startMarker) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int start = text.indexOf(startMarker, i);
            if (start < 0) {
                out.append(text.substring(i));
                break;
            }
            out.append(text, i, start);
            int end = findNextPaintLineIndex(text, start);
            i = end < 0 ? text.length() : end;
        }
        return out.toString();
    }

    private static int findNextPaintLineIndex(String text, int from) {
        int search = from;
        while (search < text.length()) {
            int lineStart = search;
            if (search > 0 && !isLineBreak(text.charAt(search - 1))) {
                int nl = nextLineBreak(text, search);
                if (nl < 0) {
                    return -1;
                }
                lineStart = skipLineBreak(text, nl);
                search = lineStart;
            }
            int lineEnd = nextLineBreak(text, lineStart);
            String line = text.substring(lineStart, lineEnd < 0 ? text.length() : lineEnd).trim();
            if (isIllustratorPaintLine(line)) {
                return lineStart;
            }
            search = lineEnd < 0 ? text.length() : skipLineBreak(text, lineEnd);
        }
        return -1;
    }

    private static boolean isIllustratorPaintLine(String line) {
        return line.matches("\\d+(\\.\\d+)?(\\s+\\d+(\\.\\d+)?)+\\s+\\S+");
    }

    private static boolean isLineBreak(char ch) {
        return ch == '\n' || ch == '\r';
    }

    private static int nextLineBreak(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            if (isLineBreak(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int skipLineBreak(String text, int index) {
        int i = index;
        while (i < text.length() && isLineBreak(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String stripMarkedBlocks(String text, String startMarker) {
        return stripMarkedBlocks(text, startMarker, null);
    }

    private static String stripMarkedBlocks(String text, String startMarker, String endMarker) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int start = text.indexOf(startMarker, i);
            if (start < 0) {
                out.append(text.substring(i));
                break;
            }
            out.append(text, i, start);
            int end;
            if (endMarker != null) {
                end = text.indexOf(endMarker, start);
                if (end < 0) {
                    out.append(text.substring(start));
                    break;
                }
                i = end + endMarker.length();
            } else {
                int line = text.indexOf('\n', start);
                if (line < 0) {
                    break;
                }
                int next = text.indexOf('\n', line + 1);
                while (next > 0 && next < text.length() - 1
                        && Character.isDigit(text.charAt(next + 1))) {
                    line = next;
                    next = text.indexOf('\n', line + 1);
                }
                i = line >= 0 ? line : text.length();
            }
            while (i < text.length() && (text.charAt(i) == '\r' || text.charAt(i) == '\n' || text.charAt(i) == ' ')) {
                i++;
            }
        }
        return out.toString();
    }

    /**
     * Removes Illustrator color-server {@code << /Name (…) … >>} blocks (executable entries, not
     * static dict literals). Image paint dictionaries use {@code /W} and {@code /H}, not {@code /Name}.
     */
    static String stripIllustratorColorServerDicts(String text) {
        String marker = "<<\n/Name";
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int nameIdx = text.indexOf(marker, i);
            if (nameIdx < 0) {
                out.append(text.substring(i));
                break;
            }
            int dictStart = text.lastIndexOf("<<", nameIdx);
            if (dictStart < 0 || nameIdx - dictStart > 24) {
                out.append(text, i, nameIdx + 1);
                i = nameIdx + 1;
                continue;
            }
            out.append(text, i, dictStart);
            int dictEnd = findDoubleAngleDictionaryEnd(text, dictStart);
            if (dictEnd < 0) {
                out.append(text.substring(dictStart));
                break;
            }
            i = dictEnd;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (text.startsWith("/CSD add_res", i)) {
                int lineEnd = text.indexOf('\n', i);
                i = lineEnd < 0 ? text.length() : lineEnd + 1;
            }
        }
        return out.toString();
    }

    private static int findDoubleAngleDictionaryEnd(String text, int start) {
        if (!text.startsWith("<<", start)) {
            return -1;
        }
        int dictDepth = 1;
        int arrayDepth = 0;
        int j = start + 2;
        while (j < text.length() - 1 && dictDepth > 0) {
            if (text.startsWith("<<", j)) {
                dictDepth++;
                j += 2;
            } else if (text.charAt(j) == '[') {
                arrayDepth++;
                j++;
            } else if (text.charAt(j) == ']' && arrayDepth > 0) {
                arrayDepth--;
                j++;
            } else if (text.startsWith(">>", j) && arrayDepth == 0) {
                dictDepth--;
                j += 2;
            } else {
                j++;
            }
        }
        return dictDepth == 0 ? j : -1;
    }

    /**
     * Removes Illustrator {@code levelN{ ... }if} wrapper procedures that confuse the parser.
     */
    static String stripLevelWrapperBlocks(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int level = text.indexOf("level", i);
            if (level < 0) {
                out.append(text.substring(i));
                break;
            }
            out.append(text, i, level);
            int brace = text.indexOf('{', level);
            if (brace < 0 || brace - level > 12) {
                i = level + 5;
                continue;
            }
            int depth = 0;
            int j = brace;
            for (; j < text.length(); j++) {
                char c = text.charAt(j);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
            }
            i = j + 1;
            if (i < text.length() && text.startsWith("if", i)) {
                i += 2;
            }
            while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t'
                    || text.charAt(i) == '\r' || text.charAt(i) == '\n')) {
                i++;
            }
        }
        return out.toString();
    }

    private static EpsDocument runProgramLenient(
            String program, BoundingBox boundingBox, boolean illustratorYDown, String tierLabel) {
        PostScriptVm vm = newPostScriptVm(boundingBox, illustratorYDown);
        try {
            executeProgram(vm, program, true);
        } catch (Exception e) {
            logger.log(Level.FINE, "{0} failed: {1}", new Object[] {tierLabel, e.getMessage()});
            return null;
        }
        return documentOrNull(vm, tierLabel, null);
    }

    private static void executeProgram(PostScriptVm vm, String program, boolean lenient) throws Exception {
        List<PsValue> tokens;
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(program))) {
            tokens = new PostScriptParser().parseAll(lexer);
        } catch (PostScriptParseException e) {
            if (!lenient) {
                throw e;
            }
            logger.log(Level.FINE, "Parse skipped (lenient): {0}", e.getMessage());
            return;
        }
        if (lenient) {
            vm.executeAllLenient(tokens);
        } else {
            vm.executeAll(tokens);
        }
    }

    private static PostScriptVm newPostScriptVm(BoundingBox boundingBox, boolean illustratorYDown) {
        PostScriptVm vm = new PostScriptVm();
        if (illustratorYDown) {
            vm.getDocumentBuilder().putMetadata(METADATA_SVG_PAGE_Y_FLIP, "false");
        }
        if (boundingBox != null) {
            vm.getDocumentBuilder().setBoundingBox(boundingBox);
        }
        return vm;
    }

    private static boolean hasEmbeddedImages(EpsDocument document) {
        for (GraphicsCommand command : document.getCommands()) {
            if (command instanceof GraphicsCommand.EmbeddedImage) {
                return true;
            }
        }
        return false;
    }

    private static EpsDocument documentOrNull(PostScriptVm vm, String tier, String postScript) {
        EpsDocument document = vm.getDocument();
        if (document.getCommands().isEmpty()) {
            logger.log(Level.FINE, "Illustrator {0}: no graphics commands recorded", tier);
            return null;
        }
        String rawPage = postScript == null ? null : extractRawPageBody(postScript);
        if (!hasEmbeddedImages(document) && rawPage != null) {
            document = IllustratorAgmImageExtractor.mergeIntoDocument(document, rawPage);
        }
        if (!hasVisibleArt(document)) {
            List<BoundingBox> rasterRegions = rawPage == null
                    ? List.of()
                    : IllustratorPatternRasterPlaceholder.scan(rawPage);
            if (rasterRegions.isEmpty()) {
                logger.log(Level.FINE,
                        "Illustrator {0}: only background clips/fills (no raster placeholder regions)",
                        tier);
                return null;
            }
            document = withRasterPlaceholders(document, rasterRegions);
            logger.log(Level.INFO, "Illustrator path: {0} + raster placeholder ({1} region(s))",
                    new Object[] {tier, rasterRegions.size()});
        } else {
            logger.log(Level.INFO, "Illustrator path: {0} ({1} command(s))",
                    new Object[] {tier, document.getCommands().size()});
        }
        return document;
    }

    private static EpsDocument withRasterPlaceholders(EpsDocument document, List<BoundingBox> regions) {
        List<GraphicsCommand> commands = new ArrayList<>(document.getCommands());
        for (BoundingBox region : regions) {
            commands.add(new GraphicsCommand.RasterPlaceholder(region, Matrix.identity()));
        }
        return new EpsDocument(
                document.getBoundingBox(),
                document.getHiResBoundingBox(),
                commands,
                document.metadata());
    }

    /**
     * True when the document has non-background vector art (not only white fills and clips).
     */
    static boolean hasVisibleArt(EpsDocument document) {
        boolean nonWhiteFill = false;
        boolean hasStroke = false;
        for (GraphicsCommand command : document.getCommands()) {
            if (command instanceof GraphicsCommand.RasterPlaceholder) {
                return true;
            }
            if (command instanceof GraphicsCommand.EmbeddedImage) {
                return true;
            }
            if (command instanceof GraphicsCommand.Text) {
                GraphicsCommand.Text text = (GraphicsCommand.Text) command;
                if (!isWhiteOrNone(text.getFill())) {
                    return true;
                }
            }
            if (command instanceof GraphicsCommand.Stroke) {
                hasStroke = true;
            } else if (command instanceof GraphicsCommand.Fill) {
                GraphicsCommand.Fill fill = (GraphicsCommand.Fill) command;
                if (!isWhiteOrNone(fill.getFill())) {
                    nonWhiteFill = true;
                }
            }
        }
        return nonWhiteFill || hasStroke;
    }

    private static boolean isWhiteOrNone(PaintStyle paint) {
        switch (paint.getKind()) {
            case NONE:
                return true;
            case GRAY:
                return paint.getV0() >= 0.99;
            case RGB:
                return paint.getV0() >= 0.99 && paint.getV1() >= 0.99 && paint.getV2() >= 0.99;
            case CMYK:
                return paint.getV0() <= 0.01 && paint.getV1() <= 0.01
                        && paint.getV2() <= 0.01 && paint.getV3() <= 0.01;
            default:
                return false;
        }
    }

    private static int findLastDscLine(String text, String marker) {
        int search = 0;
        int lastAtLineStart = -1;
        while (search < text.length()) {
            int idx = text.indexOf(marker, search);
            if (idx < 0) {
                break;
            }
            if (isDscLineStart(text, idx)) {
                lastAtLineStart = idx;
            }
            search = idx + marker.length();
        }
        return lastAtLineStart;
    }

    private static int findDscLineAfter(String text, String marker, int fromIndex) {
        int search = Math.max(0, fromIndex);
        while (search < text.length()) {
            int idx = text.indexOf(marker, search);
            if (idx < 0) {
                return -1;
            }
            if (isDscLineStart(text, idx)) {
                return idx;
            }
            search = idx + marker.length();
        }
        return -1;
    }

    private static boolean isDscLineStart(String text, int markerIndex) {
        return markerIndex == 0
                || text.charAt(markerIndex - 1) == '\n'
                || text.charAt(markerIndex - 1) == '\r';
    }

    private static int skipLineTerminator(String text, int markerStart) {
        int lineEnd = text.indexOf('\n', markerStart);
        int cr = text.indexOf('\r', markerStart);
        if (lineEnd < 0 || (cr >= 0 && cr < lineEnd)) {
            lineEnd = cr;
        }
        if (lineEnd < 0) {
            return markerStart;
        }
        int next = lineEnd + 1;
        if (next < text.length() && text.charAt(lineEnd) == '\r' && text.charAt(next) == '\n') {
            next++;
        }
        return next;
    }

    /**
     * Illustrator pages with a non-zero {@code %%BoundingBox} origin use classic EPS Y-up
     * coordinates and need the normal SVG Y-flip. Zero-origin pages match Illustrator's
     * top-left device space and skip the flip.
     */
    static boolean isIllustratorYDownPage(BoundingBox boundingBox) {
        return boundingBox == null || (boundingBox.getLlx() == 0.0 && boundingBox.getLly() == 0.0);
    }
}
