package com.convert2web;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extracts vector page content from Ghostscript {@code eps2write} EPS files.
 */
final class GhostscriptEpsPageRunner {
    private GhostscriptEpsPageRunner() {
    }

    static boolean isGhostscriptEps(String postScript) {
        return postScript.contains("eps2write")
                || postScript.contains("GPL Ghostscript");
    }

    /**
     * Content between {@code stream} after {@code %%EndPageSetup} and {@code endstream}.
     */
    static String extractPageStream(String postScript) {
        int setup = postScript.indexOf("%%EndPageSetup");
        if (setup < 0) {
            return null;
        }
        int stream = postScript.indexOf("stream", setup);
        if (stream < 0) {
            return null;
        }
        int lineEnd = postScript.indexOf('\n', stream);
        if (lineEnd < 0) {
            return null;
        }
        int start = lineEnd + 1;
        int end = postScript.indexOf("endstream", start);
        if (end < 0) {
            return null;
        }
        String body = postScript.substring(start, end).trim();
        return body.isEmpty() ? null : body;
    }

    static EpsDocument convertFile(Path inputPath, BoundingBox boundingBox) throws IOException {
        String ps = Files.readString(inputPath, StandardCharsets.ISO_8859_1);
        if (!isGhostscriptEps(ps)) {
            return null;
        }
        String stream = extractPageStream(ps);
        if (stream == null) {
            return null;
        }
        return AdobeIllustratorPageRunner.runPageBody(stream, boundingBox, false);
    }
}
