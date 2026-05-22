package com.convert2web;

import com.convert2web.model.EpsDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * One-line path summary per EPS (no SVG write). Usage: PathBatchSummary &lt;paths-file&gt;
 */
public final class PathBatchSummary {
    private static final Logger ROOT = Logger.getLogger("com.convert2web");

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: PathBatchSummary <paths-file>");
            System.exit(2);
        }
        List<String> paths = Files.readAllLines(Path.of(args[0]));
        for (String raw : paths) {
            if (raw.isBlank()) {
                continue;
            }
            String path = raw.trim();
            System.out.println(summarize(path));
        }
    }

    private static String summarize(String inputPath) {
        LogCapture capture = new LogCapture();
        capture.attach();
        String outcome;
        try {
            EpsFormatDetector.validateEpsFile(inputPath);
            boolean binary = BinaryEpsInterpreter.isBinaryEps(inputPath);
            capture.note("format:" + (binary ? "binary" : "standard"));
            if (binary) {
                BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(inputPath);
                if (data == null || data.postScriptData == null) {
                    outcome = "FAIL";
                } else {
                    String ps = new String(data.postScriptData, java.nio.charset.StandardCharsets.ISO_8859_1);
                    if (ps.contains("%!PS") && AdobeIllustratorPageRunner.isAdobeIllustratorEps(ps)) {
                        EpsDocument doc = AdobeIllustratorPageRunner.convertIllustratorPostScript(
                                ps, data.boundingBox);
                        outcome = doc != null ? "OK" : "FAIL";
                        if (doc != null) {
                            capture.note("pipeline:binary-illustrator");
                        }
                    } else if (ps.contains("%!PS")) {
                        outcome = tryEmbeddedVm(ps, data) ? "OK" : "FAIL";
                        if (outcome.equals("OK")) {
                            capture.note("pipeline:binary-embedded-ps");
                        }
                    } else {
                        outcome = "FAIL";
                    }
                }
            } else {
                try {
                    new AsciiEpsConverter().convertToDocument(inputPath);
                    outcome = "OK";
                    capture.note("pipeline:ascii-vm");
                } catch (Exception e) {
                    outcome = "FAIL";
                    capture.note("ascii-error:" + shorten(e.getMessage()));
                }
            }
        } catch (IOException e) {
            outcome = "FAIL";
            capture.note("rejected:" + shorten(e.getMessage()));
        } catch (Exception e) {
            outcome = "FAIL";
            capture.note("error:" + shorten(e.getMessage()));
        } finally {
            capture.detach();
        }
        return outcome + "\t" + capture.primaryPath() + "\t" + inputPath;
    }

    private static boolean tryEmbeddedVm(String ps, BinaryEpsReader.BinaryEpsData data) {
        try {
            com.convert2web.ps.PostScriptVm vm = new com.convert2web.ps.PostScriptVm();
            if (data.boundingBox != null) {
                vm.getDocumentBuilder().setBoundingBox(data.boundingBox);
            }
            try (com.convert2web.ps.PostScriptLexer lexer =
                    new com.convert2web.ps.PostScriptLexer(new java.io.StringReader(ps))) {
                List<com.convert2web.ps.PsValue> program =
                        new com.convert2web.ps.PostScriptParser().parseAll(lexer);
                vm.executeAllLenient(program);
            }
            return !vm.getDocument().getCommands().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static String shorten(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.length() > 80 ? msg.substring(0, 80) : msg;
    }

    private static final class LogCapture {
        private final List<String> pathLines = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private Handler handler;

        void attach() {
            handler = new Handler() {
                @Override
                public void publish(LogRecord record) {
                    if (record.getLevel().intValue() < Level.INFO.intValue()) {
                        return;
                    }
                    String msg = record.getMessage();
                    if (record.getParameters() != null && msg != null) {
                        msg = MessageFormat.format(msg, record.getParameters());
                    }
                    if (msg == null) {
                        return;
                    }
                    if (msg.contains("path:") || msg.contains("Illustrator path:")
                            || msg.contains("detected as:") || msg.contains("rejected")
                            || msg.contains("Binary EPS converted")) {
                        pathLines.add(msg);
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            };
            handler.setLevel(Level.INFO);
            ROOT.addHandler(handler);
            ROOT.setLevel(Level.INFO);
        }

        void detach() {
            if (handler != null) {
                ROOT.removeHandler(handler);
            }
        }

        void note(String note) {
            notes.add(note);
        }

        String primaryPath() {
            String illustrator = null;
            for (String line : pathLines) {
                if (line.contains("Illustrator path:")) {
                    illustrator = line.substring(line.indexOf("Illustrator path:"));
                }
            }
            if (illustrator != null) {
                return illustrator;
            }
            for (String line : pathLines) {
                if (line.contains("ASCII path:")) {
                    return line.substring(line.indexOf("ASCII path:"));
                }
            }
            for (String line : pathLines) {
                if (line.contains("rejected")) {
                    return line;
                }
            }
            if (!notes.isEmpty()) {
                return String.join("; ", notes);
            }
            if (!pathLines.isEmpty()) {
                return pathLines.get(pathLines.size() - 1);
            }
            return "unknown";
        }
    }
}
