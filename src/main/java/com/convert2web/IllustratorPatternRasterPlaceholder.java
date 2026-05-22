package com.convert2web;

import com.convert2web.model.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds Illustrator pattern-paint dictionaries that embed ASCII85 raster tiles
 * (stripped during sanitization) and records their bounds for SVG placeholders.
 */
final class IllustratorPatternRasterPlaceholder {
    private static final Pattern BBOX = Pattern.compile(
            "/BBox\\s*\\[\\s*([-\\d.]+(?:[eE][+-]?\\d+)?)\\s+"
                    + "([-\\d.]+(?:[eE][+-]?\\d+)?)\\s+"
                    + "([-\\d.]+(?:[eE][+-]?\\d+)?)\\s+"
                    + "([-\\d.]+(?:[eE][+-]?\\d+)?)\\s*\\]");

    private IllustratorPatternRasterPlaceholder() {
    }

    static List<BoundingBox> scan(String pageBody) {
        if (pageBody == null || pageBody.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<BoundingBox> regions = new ArrayList<>();
        int index = 0;
        while (index < pageBody.length()) {
            int start = pageBody.indexOf("<<", index);
            if (start < 0) {
                break;
            }
            int end = findDictionaryEnd(pageBody, start);
            String dict = pageBody.substring(start, end);
            if (isRasterPatternDictionary(dict)) {
                BoundingBox bbox = parseBBox(dict);
                if (bbox != null && seen.add(bboxKey(bbox))) {
                    regions.add(bbox);
                }
            }
            index = end;
        }
        return regions;
    }

    private static boolean isRasterPatternDictionary(String dict) {
        return dict.contains("/PaintProc")
                && (dict.contains("<~") || dict.contains("/T 1") || dict.contains("/T\n1"));
    }

    private static BoundingBox parseBBox(String dict) {
        Matcher matcher = BBOX.matcher(dict);
        if (!matcher.find()) {
            return null;
        }
        double x0 = Double.parseDouble(matcher.group(1));
        double y0 = Double.parseDouble(matcher.group(2));
        double x1 = Double.parseDouble(matcher.group(3));
        double y1 = Double.parseDouble(matcher.group(4));
        double llx = Math.min(x0, x1);
        double lly = Math.min(y0, y1);
        double urx = Math.max(x0, x1);
        double ury = Math.max(y0, y1);
        if (urx <= llx || ury <= lly) {
            return null;
        }
        return new BoundingBox(llx, lly, urx, ury);
    }

    private static String bboxKey(BoundingBox bbox) {
        return bbox.getLlx() + "," + bbox.getLly() + "," + bbox.getUrx() + "," + bbox.getUry();
    }

    private static int findDictionaryEnd(String text, int start) {
        int depth = 1;
        int j = start + 2;
        while (j < text.length() - 1 && depth > 0) {
            if (text.startsWith("<<", j)) {
                depth++;
                j += 2;
            } else if (text.startsWith(">>", j)) {
                depth--;
                j += 2;
            } else {
                j++;
            }
        }
        return j;
    }
}
