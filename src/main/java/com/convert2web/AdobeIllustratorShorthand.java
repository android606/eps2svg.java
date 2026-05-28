package com.convert2web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds Illustrator page-body shorthand preambles from embedded prolog {@code ldf} lines,
 * with static fallbacks when prolog execution is not used.
 */
final class AdobeIllustratorShorthand {
    private static final Pattern LDF_ALIAS = Pattern.compile(
            "/([A-Za-z@*][A-Za-z0-9]*)\\s+/([A-Za-z][A-Za-z0-9_]*)\\s+ldf");

    /** AGM procedure names mapped to PostScript our VM implements. */
    private static final Map<String, String> AGM_TARGET_TO_PS = Map.ofEntries(
            Map.entry("clp_npth", "clip"),
            Map.entry("eoclp_npth", "eoclip"),
            Map.entry("npth_clp", "clip"),
            Map.entry("gry", "setgray"),
            Map.entry("rgb", "setrgbcolor"),
            Map.entry("cmyk", "setcmykcolor"),
            Map.entry("colr", "setrgbcolor"),
            Map.entry("sep", "setcmykcolor"),
            Map.entry("devn", "setcmykcolor"),
            Map.entry("idx", "setrgbcolor"),
            Map.entry("graphic_setup", "gsave"),
            Map.entry("graphic_cleanup", "grestore"));

    private static final Set<String> VM_OPERATORS = Set.of(
            "moveto", "lineto", "curveto", "closepath", "fill", "eofill", "stroke",
            "clip", "eoclip", "newpath", "concat", "gsave", "grestore",
            "setlinewidth", "setlinecap", "setlinejoin", "setmiterlimit", "setdash",
            "setgray", "setrgbcolor", "setcmykcolor", "setoverprint", "translate",
            "scale", "rotate", "show", "setfont", "findfont", "scalefont", "selectfont", "msf");

    /** Static fallbacks when prolog is not executed (order preserved for readability). */
    private static final Map<String, String> STATIC_BODY = Map.ofEntries(
            Map.entry("mo", "{ moveto }"),
            Map.entry("li", "{ lineto }"),
            Map.entry("cv", "{ curveto }"),
            Map.entry("m", "{ moveto }"),
            Map.entry("l", "{ lineto }"),
            Map.entry("L", "{ lineto }"),
            Map.entry("c", "{ curveto }"),
            Map.entry("C", "{ curveto }"),
            Map.entry("v", "{ curveto }"),
            Map.entry("@", "{ stroke }"),
            Map.entry("cp", "{ closepath }"),
            Map.entry("h", "{ closepath }"),
            Map.entry("clp", "{ clip }"),
            Map.entry("f", "{ fill }"),
            Map.entry("ef", "{ eofill }"),
            Map.entry("np", "{ newpath }"),
            Map.entry("ct", "{ concat }"),
            Map.entry("cmyk", "{ setcmykcolor }"),
            Map.entry("k", "{ setcmykcolor }"),
            Map.entry("K", "{ setcmykcolor }"),
            Map.entry("nzopmsc", "{ setcmykcolor }"),
            Map.entry("rg", "{ setrgbcolor }"),
            Map.entry("g", "{ setgray }"),
            Map.entry("gry", "{ setgray }"),
            Map.entry("w", "{ setlinewidth }"),
            Map.entry("lw", "{ setlinewidth }"),
            Map.entry("j", "{ setlinejoin }"),
            Map.entry("lj", "{ setlinejoin }"),
            Map.entry("J", "{ setlinecap }"),
            Map.entry("lc", "{ setlinecap }"),
            Map.entry("M", "{ setmiterlimit }"),
            Map.entry("ml", "{ setmiterlimit }"),
            Map.entry("d", "{ setdash }"),
            Map.entry("dsh", "{ setdash }"),
            Map.entry("sh", "{ show }"),
            Map.entry("sf", "{ setfont }"),
            Map.entry("se", "{ selectfont }"),
            Map.entry("msf", "{ msf }"),
            Map.entry("Fo", "{ findfont }"),
            Map.entry("sadj", "{ pop }"),
            Map.entry("sop", "{ pop }"),
            Map.entry("XR", "{ pop }"),
            Map.entry("Lb", "{ }"),
            Map.entry("LB", "{ }"),
            Map.entry("Ln", "{ pop }"),
            Map.entry("q", "{ gsave }"),
            Map.entry("Q", "{ grestore }"),
            Map.entry("gs", "{ gsave }"),
            Map.entry("gr", "{ grestore }"),
            Map.entry("A", "{ pop }"),
            Map.entry("O", "{ pop }"),
            Map.entry("pgsv", "{ gsave }"),
            Map.entry("pgrs", "{ grestore }"),
            Map.entry("add_res", "{ pop pop pop }"),
            Map.entry("CSA", "{ pop pop }"),
            Map.entry("annotatepage", "{ pop pop }"));

    private AdobeIllustratorShorthand() {
    }

    static String buildPreamble(String postScript) {
        int prologEnd = AdobeIllustratorPageRunner.findPageBodyStart(postScript);
        String prolog = prologEnd > 0 ? postScript.substring(0, prologEnd) : postScript;

        Map<String, String> aliases = new LinkedHashMap<>();
        Matcher matcher = LDF_ALIAS.matcher(prolog);
        while (matcher.find()) {
            String shorthand = matcher.group(1);
            String target = matcher.group(2);
            String psOp = resolveToPostScript(target);
            if (psOp != null) {
                aliases.put(shorthand, psOp);
            }
        }

        Map<String, String> bodies = new LinkedHashMap<>(STATIC_BODY);
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            bodies.put(entry.getKey(), "{ " + entry.getValue() + " }");
        }

        StringBuilder preamble = new StringBuilder("userdict begin\n");
        for (Map.Entry<String, String> entry : bodies.entrySet()) {
            preamble.append('/').append(entry.getKey()).append(' ')
                    .append(entry.getValue()).append(" bind def\n");
        }
        return preamble.toString();
    }

    private static String resolveToPostScript(String target) {
        if (VM_OPERATORS.contains(target)) {
            return target;
        }
        return AGM_TARGET_TO_PS.get(target);
    }
}
