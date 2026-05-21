package com.convert2web;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import com.convert2web.ps.PostScriptLexer;
import com.convert2web.ps.PostScriptParser;
import com.convert2web.ps.PostScriptVm;
import com.convert2web.ps.PsValue;

import java.io.StringReader;
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
        int pageStart = findPageBodyStart(postScript);
        if (pageStart < 0) {
            return runProgramLenient(postScript, boundingBox, "full PostScript");
        }

        String pageBody = extractPageBody(postScript);
        if (pageBody == null || pageBody.isEmpty()) {
            return null;
        }

        PostScriptVm vm = newPostScriptVm(boundingBox, isIllustratorYDownPage(boundingBox));
        String prolog = postScript.substring(0, pageStart);
        try {
            executeProgram(vm, prolog, true);
        } catch (Exception e) {
            logger.log(Level.FINE, "Prolog execution error (continuing): {0}", e.getMessage());
        }
        vm.resetForPageBody();
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
        return documentOrNull(vm, "full PostScript");
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
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(program))) {
            List<PsValue> tokens = new PostScriptParser().parseAll(lexer);
            vm.executeAll(tokens);
        } catch (Exception e) {
            logger.log(Level.FINE, "Adobe page body execution failed: {0}", e.getMessage());
            return null;
        }
        return documentOrNull(vm, "page body+shorthand");
    }

    /**
     * Tries tier 1, then 2, then 3 in order.
     */
    static EpsDocument convertIllustratorPostScript(String postScript, BoundingBox boundingBox) {
        EpsDocument document = runFullPostScript(postScript, boundingBox);
        if (document != null) {
            return document;
        }
        document = runPrologThenPageBody(postScript, boundingBox);
        if (document != null) {
            return document;
        }
        String pageBody = extractPageBody(postScript);
        if (pageBody == null || pageBody.isEmpty()) {
            return null;
        }
        return runPageBody(pageBody, boundingBox, postScript);
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
        body = body.replaceAll("(?s)%ADOBeginSubsetFont:.*?(?=(?:\\r|\\n)\\d+\\.)", "");
        body = body.replaceAll("(?s)/BCKTCI.*?(?=(?:\\r|\\n)\\d+\\.)", "");
        body = body.replaceAll("(?s)\\d+\\.\\d+ \\d+\\.\\d+ mo\\s*\\([^)]*\\)sh\\s*", "");
        body = body.replaceAll("(?m)^%ADO.*$\\r?\\n?", "");
        body = body.replaceAll("(?m)^userdict /annotatepage.*ifelse\\s*\\r?\\n?", "");
        body = body.replaceAll(
                "(?m)^1\\s+-1\\s+scale\\s+0\\s+-?[\\d.]+(?:[eE][+-]?\\d+)?\\s+translate\\s*\\r?\\n?",
                "");
        return body;
    }

    private static EpsDocument runProgramLenient(
            String program, BoundingBox boundingBox, String tierLabel) {
        PostScriptVm vm = newPostScriptVm(boundingBox, isIllustratorYDownPage(boundingBox));
        try {
            executeProgram(vm, program, true);
        } catch (Exception e) {
            logger.log(Level.FINE, "{0} failed: {1}", new Object[] {tierLabel, e.getMessage()});
            return null;
        }
        return documentOrNull(vm, tierLabel);
    }

    private static void executeProgram(PostScriptVm vm, String program, boolean lenient) throws Exception {
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(program))) {
            List<PsValue> tokens = new PostScriptParser().parseAll(lexer);
            if (lenient) {
                vm.executeAllLenient(tokens);
            } else {
                vm.executeAll(tokens);
            }
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

    private static EpsDocument documentOrNull(PostScriptVm vm, String tier) {
        EpsDocument document = vm.getDocument();
        if (document.getCommands().isEmpty()) {
            logger.log(Level.FINE, "Illustrator {0}: no graphics commands recorded", tier);
            return null;
        }
        logger.log(Level.FINE, "Illustrator {0}: {1} command(s)",
                new Object[] {tier, document.getCommands().size()});
        return document;
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
