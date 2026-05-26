package com.convert2web.image;

import com.convert2web.model.EpsDocument;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.model.Matrix;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts Adobe AGM raster tiles from a raw Illustrator page body: {@code %%BeginBinary}
 * payloads and inline {@code /DS [ <~ ... ~> ]} samples inside image dictionaries.
 */
public final class IllustratorAgmImageExtractor {
    private static final Logger logger = Logger.getLogger(IllustratorAgmImageExtractor.class.getName());

    private static final Pattern IMAGE_DICT = Pattern.compile(
            "/T\\s+1\\s*/W\\s+(\\d+)\\s*/H\\s+(\\d+)",
            Pattern.MULTILINE);
    private static final Pattern CT_MATRIX = Pattern.compile(
            "\\[\\s*([-\\d.eE+]+)\\s+([-\\d.eE+]+)\\s+([-\\d.eE+]+)\\s+"
                    + "([-\\d.eE+]+)\\s+([-\\d.eE+]+)\\s+([-\\d.eE+]+)\\s*\\]\\s*ct");
    private static final Pattern BINARY_BLOCK = Pattern.compile(
            "%%BeginBinary:\\s*\\d+\\s*\\r?\\n(?:img|sepimg|idximg)\\s*\\r?\\n",
            Pattern.MULTILINE);
    private static final Pattern EMPTY_SEPIMG = Pattern.compile(
            "%%BeginBinary:\\s*\\d+\\s*\\r?\\nsepimg\\s*\\r?\\n\\s*%%EndBinary");
    private IllustratorAgmImageExtractor() {
    }

    public static List<AgmEmbeddedImage> extract(String rawPageBody) {
        if (rawPageBody == null || rawPageBody.isEmpty()) {
            return List.of();
        }
        return extractTilesInPaintOrder(rawPageBody);
    }

    private static List<AgmEmbeddedImage> extractTilesInPaintOrder(String rawPageBody) {
        List<AgmEmbeddedImage> images = new ArrayList<>();
        int search = 0;
        while (true) {
            int snap = rawPageBody.indexOf("snap_to_device", search);
            if (snap < 0) {
                break;
            }
            search = snap + 1;
            if (isSnapToDeviceDefinition(rawPageBody, snap)) {
                continue;
            }
            int dictStart = findImageDictStart(rawPageBody, snap);
            if (dictStart < 0) {
                continue;
            }
            int dictEnd = findDictionaryEnd(rawPageBody, dictStart);
            if (dictEnd < 0) {
                continue;
            }
            String dict = rawPageBody.substring(dictStart, dictEnd);
            Matcher dictMatcher = IMAGE_DICT.matcher(dict);
            if (!dictMatcher.find()) {
                continue;
            }
            int width = Integer.parseInt(dictMatcher.group(1));
            int height = Integer.parseInt(dictMatcher.group(2));
            AgmEmbeddedImage image = tryExtractTile(
                    rawPageBody, snap, dictStart, dictEnd, dict, width, height);
            if (image != null) {
                images.add(image);
            }
        }
        return images;
    }

    private static boolean isSnapToDeviceDefinition(String rawPageBody, int snap) {
        int scan = snap + "snap_to_device".length();
        while (scan < rawPageBody.length() && Character.isWhitespace(rawPageBody.charAt(scan))) {
            scan++;
        }
        return scan < rawPageBody.length() && rawPageBody.charAt(scan) == '{';
    }

    private static int findImageDictStart(String rawPageBody, int snap) {
        int dictStart = rawPageBody.indexOf("<<", snap);
        if (dictStart >= 0 && dictStart - snap <= 120) {
            return dictStart;
        }
        return -1;
    }

    private static AgmEmbeddedImage tryExtractTile(
            String rawPageBody,
            int snap,
            int dictStart,
            int dictEnd,
            String dict,
            int width,
            int height) {
        Matrix displayMatrix = displayMatrixForTile(parseRecentConcat(rawPageBody, dictStart));
        if (hasNonemptyBeginBinary(rawPageBody, dictEnd)) {
            return extractBeginBinaryTile(rawPageBody, dictEnd, width, height, displayMatrix);
        }
        return AgmTileDecoder.decodeInlineDs(dict, width, height, displayMatrix);
    }

    private static AgmEmbeddedImage extractBeginBinaryTile(
            String rawPageBody,
            int dictEnd,
            int width,
            int height,
            Matrix displayMatrix) {
        Matcher binaryMatcher = BINARY_BLOCK.matcher(rawPageBody);
        if (!binaryMatcher.find(dictEnd) || binaryMatcher.start() - dictEnd > 40) {
            return null;
        }
        int dataStart = binaryMatcher.end();
        int dataEnd = rawPageBody.indexOf("%%EndBinary", dataStart);
        if (dataEnd < 0) {
            return null;
        }
        String payload = rawPageBody.substring(dataStart, dataEnd).trim();
        String operatorLine = rawPageBody.substring(binaryMatcher.start(), dataStart);
        String operator = operatorLine.contains("sepimg")
                ? "sepimg"
                : operatorLine.contains("idximg") ? "idximg" : "img";
        return AgmTileDecoder.decodeBeginBinaryPayload(
                operator, width, height, payload, displayMatrix, rawPageBody);
    }

    private static boolean hasNonemptyBeginBinary(String rawPageBody, int fromIndex) {
        Matcher matcher = BINARY_BLOCK.matcher(rawPageBody);
        if (!matcher.find(fromIndex)) {
            return false;
        }
        if (matcher.start() - fromIndex > 40) {
            return false;
        }
        int dataStart = matcher.end();
        int dataEnd = rawPageBody.indexOf("%%EndBinary", dataStart);
        if (dataEnd < 0) {
            return false;
        }
        return rawPageBody.substring(dataStart, dataEnd).trim().length() >= 20;
    }

    private static int findDictionaryEnd(String text, int start) {
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

    public static EpsDocument mergeIntoDocument(EpsDocument document, String rawPageBody) {
        return mergeIntoDocument(document, rawPageBody, null);
    }

    public static EpsDocument mergeIntoDocument(
            EpsDocument document, String rawPageBody, Path illustratorReferenceSvg) {
        List<AgmEmbeddedImage> images = extract(rawPageBody);
        if (images.isEmpty()) {
            return document;
        }
        Optional<IllustratorSvgLayerReference> reference = illustratorReferenceSvg == null
                ? Optional.empty()
                : IllustratorSvgLayerReference.parseFile(illustratorReferenceSvg);
        List<GraphicsCommand> merged = new ArrayList<>(images.size() + document.getCommands().size());
        if (reference.isPresent()) {
            appendLayerGroupedRasters(merged, images, reference.get());
        } else {
            for (AgmEmbeddedImage image : images) {
                merged.add(image.toCommand());
            }
        }
        merged.addAll(document.getCommands());
        return new EpsDocument(
                document.getBoundingBox(),
                document.getHiResBoundingBox(),
                merged,
                document.metadata());
    }

    private static void appendLayerGroupedRasters(
            List<GraphicsCommand> merged,
            List<AgmEmbeddedImage> images,
            IllustratorSvgLayerReference reference) {
        Map<String, List<AgmEmbeddedImage>> grouped = reference.groupTiles(images);
        for (IllustratorSvgLayerReference.LayerRegion layer : reference.layers()) {
            List<AgmEmbeddedImage> tiles = grouped.get(layer.id());
            if (tiles == null || tiles.isEmpty()) {
                continue;
            }
            merged.add(new GraphicsCommand.BeginLayerGroup(layer.id()));
            dominantTile(tiles).ifPresent(image -> merged.add(image.toCommand()));
            merged.add(new GraphicsCommand.EndLayerGroup());
            grouped.remove(layer.id());
        }
        List<AgmEmbeddedImage> unassigned = grouped.get("unassigned_raster");
        if (unassigned != null && !unassigned.isEmpty()) {
            merged.add(new GraphicsCommand.BeginLayerGroup("unassigned_raster"));
            for (AgmEmbeddedImage image : unassigned) {
                merged.add(image.toCommand());
            }
            merged.add(new GraphicsCommand.EndLayerGroup());
            grouped.remove("unassigned_raster");
        }
        for (Map.Entry<String, List<AgmEmbeddedImage>> entry : grouped.entrySet()) {
            merged.add(new GraphicsCommand.BeginLayerGroup(entry.getKey()));
            dominantTile(entry.getValue()).ifPresent(image -> merged.add(image.toCommand()));
            merged.add(new GraphicsCommand.EndLayerGroup());
        }
    }

    private static Optional<AgmEmbeddedImage> dominantTile(List<AgmEmbeddedImage> tiles) {
        if (tiles.isEmpty()) {
            return Optional.empty();
        }
        AgmEmbeddedImage best = tiles.get(0);
        double bestArea = AgmImageBounds.displayedArea(best);
        for (int i = 1; i < tiles.size(); i++) {
            AgmEmbeddedImage candidate = tiles.get(i);
            double area = AgmImageBounds.displayedArea(candidate);
            if (area > bestArea) {
                best = candidate;
                bestArea = area;
            }
        }
        return Optional.of(best);
    }

    /**
     * CTM from {@code ct} operators before {@code snap_to_device} only.
     * {@code /M[...]} is already reflected in {@code /W} and {@code /H}; baking {@code /M}
     * into this matrix makes the SVG renderer apply width/height twice (e.g. 25x38 sepimg
     * blown up to 150x241pt instead of ~6x6pt).
     */
    private static Matrix displayMatrixForTile(Matrix ctm) {
        return ctm;
    }

    private static Matrix parseRecentConcat(String text, int marker) {
        int snap = text.lastIndexOf("snap_to_device", marker);
        int windowEnd = snap >= 0 ? snap : marker;
        String window = text.substring(Math.max(0, windowEnd - 400), windowEnd);
        Matcher matcher = CT_MATRIX.matcher(window);
        Matrix composed = Matrix.identity();
        while (matcher.find()) {
            Matrix piece = parseMatrix(
                    matcher.group(1), matcher.group(2), matcher.group(3),
                    matcher.group(4), matcher.group(5), matcher.group(6));
            composed = composed.postConcat(piece);
        }
        return composed;
    }

    private static Matrix parseMatrix(
            String a, String b, String c, String d, String e, String f) {
        return new Matrix(
                Double.parseDouble(a),
                Double.parseDouble(b),
                Double.parseDouble(c),
                Double.parseDouble(d),
                Double.parseDouble(e),
                Double.parseDouble(f));
    }
}
