package com.convert2web;

import com.convert2web.ps.PostScriptLexer;
import com.convert2web.ps.PostScriptParser;
import com.convert2web.ps.PostScriptParseException;
import com.convert2web.ps.PostScriptToken;
import com.convert2web.ps.PostScriptTokenType;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Parse probe for failing EPS. */
public final class ParseProbe {
    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "--dump".equals(args[0])) {
            Path eps = Path.of(args[1]);
            var data = BinaryEpsReader.read(eps.toString());
            String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
            String body = AdobeIllustratorPageRunner.extractPageBody(ps);
            java.nio.file.Files.writeString(Path.of("/tmp/sanitized-body.ps"), body);
            System.out.println("wrote /tmp/sanitized-body.ps len=" + body.length());
            return;
        }
        Path eps = Path.of(args[0]);
        var data = BinaryEpsReader.read(eps.toString());
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        String body = AdobeIllustratorPageRunner.extractPageBody(ps);
        String preamble = AdobeIllustratorShorthand.buildPreamble(ps);
        probe("preamble", preamble);
        probe("body", body);
        probe("preamble+body", preamble + body);
        int pageStart = AdobeIllustratorPageRunner.findPageBodyStart(ps);
        if (pageStart > 0) {
            String prolog = AdobeIllustratorPageRunner.sanitizeIllustratorPageText(
                    ps.substring(0, pageStart));
            probe("sanitized-prolog", prolog);
        }
    }

    private static void probe(String label, String text) throws java.io.IOException {
        try (PostScriptLexer lex = new PostScriptLexer(new StringReader(text))) {
            new PostScriptParser().parseAll(lex);
            System.out.println(label + ": parse ok");
        } catch (Exception e) {
            System.out.println(label + ": parse fail: " + e.getMessage());
            if (label.equals("body")) {
                tokenContext(text, 8);
            }
        }
    }

    private static void tokenContext(String text, int tail) throws java.io.IOException {
        try (PostScriptLexer lex = new PostScriptLexer(new StringReader(text))) {
            java.util.ArrayList<PostScriptToken> recent = new java.util.ArrayList<>();
            while (true) {
                PostScriptToken t = lex.nextToken();
                recent.add(t);
                if (recent.size() > tail) {
                    recent.remove(0);
                }
                if (t.getType() == PostScriptTokenType.EOF) {
                    break;
                }
            }
        } catch (PostScriptParseException e) {
            // lexer may not throw
        }
        try (PostScriptLexer lex = new PostScriptLexer(new StringReader(text))) {
            java.util.ArrayList<String> recent = new java.util.ArrayList<>();
            while (true) {
                PostScriptToken t;
                try {
                    t = lex.nextToken();
                } catch (PostScriptParseException ex) {
                    System.out.println("  lexer error: " + ex.getMessage());
                    break;
                }
                recent.add(t.getType() + ":" + abbrev(t.getText()));
                if (recent.size() > tail) {
                    recent.remove(0);
                }
                if (t.getType() == PostScriptTokenType.EOF) {
                    break;
                }
            }
            System.out.println("  last tokens: " + recent);
        }
        try (PostScriptLexer lex = new PostScriptLexer(new StringReader(text))) {
            new PostScriptParser().parseAll(lex);
        } catch (Exception ex) {
            System.out.println("  parser: " + ex.getMessage());
        }
    }

    private static String abbrev(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 20 ? s.substring(0, 20) + "..." : s;
    }
}
