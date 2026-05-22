package com.convert2web;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Batch tier/error triage for still-failing EPS paths (one path per line in args file). */
public final class FailureTriage {
    private static final Logger PAGE_LOG = Logger.getLogger(AdobeIllustratorPageRunner.class.getName());

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: FailureTriage <paths-file> [max] [--detail]");
            System.exit(2);
        }
        List<String> paths = Files.readAllLines(Path.of(args[0]));
        int max = paths.size();
        boolean detail = false;
        for (String arg : args) {
            if ("--detail".equals(arg)) {
                detail = true;
            } else if (!arg.startsWith("-")) {
                try {
                    max = Integer.parseInt(arg);
                } catch (NumberFormatException ignored) {
                    // paths-file already args[0]
                }
            }
        }
        Map<String, Integer> reasons = new LinkedHashMap<>();
        int n = 0;
        for (String path : paths) {
            if (n >= max) {
                break;
            }
            if (path.isBlank()) {
                continue;
            }
            String reason = triage(path.trim());
            reasons.merge(reason, 1, Integer::sum);
            if (detail) {
                System.out.println(reason + "\t" + path.trim());
            }
            n++;
        }
        System.out.println("Triaged " + n + " files:");
        reasons.forEach((k, v) -> System.out.println("  " + v + "\t" + k));
    }

    private static String triage(String path) throws Exception {
        Path p = Path.of(path);
        if (!Files.exists(p)) {
            return "missing-file";
        }
        try {
            EpsFormatDetector.validateEpsFile(path);
        } catch (Exception e) {
            return "not-eps:" + shorten(e.getMessage());
        }
        String ps = readPostScript(p);
        if (!AdobeIllustratorPageRunner.isAdobeIllustratorEps(ps)) {
            return "not-illustrator";
        }
        BoundingBox bbox = bboxFromHeader(ps);
        String body = AdobeIllustratorPageRunner.extractPageBody(ps);
        if (body == null || body.isEmpty()) {
            return "no-page-body";
        }
        String stripped = stripBeginBinary(body);
        if (stripped.contains("%%BeginBinary")) {
            return "begin-binary-remains-in-body";
        }

        CapturingHandler handler = new CapturingHandler();
        Logger.getLogger("").addHandler(handler);
        PAGE_LOG.setLevel(Level.FINE);
        try {
            EpsDocument full = AdobeIllustratorPageRunner.runFullPostScript(ps, bbox);
            if (hasPaths(full)) {
                return "would-succeed-full";
            }
            EpsDocument page = AdobeIllustratorPageRunner.runPageBody(body, bbox, ps);
            if (hasPaths(page)) {
                return "would-succeed-page-only";
            }
        } finally {
            Logger.getLogger("").removeHandler(handler);
        }
        String msg = handler.lastFine();
        if (msg == null) {
            return "empty-no-log";
        }
        if (msg.contains("PROCEDURE_END")) {
            return "prolog-procedure-end";
        }
        if (msg.contains("hex string")) {
            return "hex-string";
        }
        if (msg.contains("Unexpected token")) {
            return "unexpected-token:" + extractTail(msg);
        }
        if (msg.contains("undefined")) {
            return "undefined:" + extractTail(msg);
        }
        if (msg.contains("stackunderflow")) {
            return "stackunderflow";
        }
        return "other:" + shorten(msg);
    }

    private static String stripBeginBinary(String body) {
        return body.replaceAll("(?s)%%BeginBinary:.*?%%EndBinary\\s*", "");
    }

    private static String extractTail(String msg) {
        int i = msg.lastIndexOf(':');
        return i >= 0 ? msg.substring(i + 1).trim() : msg;
    }

    private static String shorten(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.length() > 60 ? msg.substring(0, 60) : msg;
    }

    private static boolean hasPaths(EpsDocument d) {
        return d != null && !d.getCommands().isEmpty();
    }

    private static String readPostScript(Path path) throws Exception {
        byte[] data = Files.readAllBytes(path);
        if (data.length >= 4 && data[0] == (byte) 0xC5) {
            int off = ((data[7] & 0xFF) << 24) | ((data[6] & 0xFF) << 16)
                    | ((data[5] & 0xFF) << 8) | (data[4] & 0xFF);
            int ln = ((data[11] & 0xFF) << 24) | ((data[10] & 0xFF) << 16)
                    | ((data[9] & 0xFF) << 8) | (data[8] & 0xFF);
            return new String(data, off, Math.min(ln, data.length - off), StandardCharsets.ISO_8859_1);
        }
        return Files.readString(path, StandardCharsets.ISO_8859_1);
    }

    private static BoundingBox bboxFromHeader(String ps) {
        for (String line : ps.split("\\r\\n|\\r|\\n")) {
            if (line.startsWith("%%BoundingBox:")) {
                String[] p = line.substring(14).trim().split("\\s+");
                if (p.length == 4) {
                    return new BoundingBox(
                            Double.parseDouble(p[0]),
                            Double.parseDouble(p[1]),
                            Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]));
                }
            }
        }
        return null;
    }

    private static final class CapturingHandler extends java.util.logging.Handler {
        private String last;

        @Override
        public void publish(java.util.logging.LogRecord record) {
            if (record.getLevel().intValue() <= Level.FINE.intValue()) {
                last = record.getMessage();
                if (record.getParameters() != null && record.getMessage() != null) {
                    last = java.text.MessageFormat.format(record.getMessage(), record.getParameters());
                }
            }
        }

        String lastFine() {
            return last;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
