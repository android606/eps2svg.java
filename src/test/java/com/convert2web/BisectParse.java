package com.convert2web;

import com.convert2web.ps.PostScriptLexer;
import com.convert2web.ps.PostScriptParser;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Find approximate parse failure offset in a PostScript fragment. */
public final class BisectParse {
    public static void main(String[] args) throws Exception {
        String body = Files.readString(Path.of(args[0]));
        int lo = 0;
        int hi = body.length();
        while (lo < hi - 1) {
            int mid = (lo + hi) / 2;
            if (parses(body.substring(0, mid))) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        System.out.println("fail near " + hi + " of " + body.length());
        int from = Math.max(0, hi - 150);
        int to = Math.min(body.length(), hi + 150);
        System.out.println(body.substring(from, to).replace("\n", "\\n"));
    }

    private static boolean parses(String text) {
        try (PostScriptLexer lex = new PostScriptLexer(new StringReader(text))) {
            new PostScriptParser().parseAll(lex);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
