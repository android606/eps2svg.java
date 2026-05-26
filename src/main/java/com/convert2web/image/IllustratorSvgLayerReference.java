package com.convert2web.image;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.Matrix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an Adobe Illustrator SVG export to recover layer {@code id} values and
 * each layer's on-page bounds. Used to group AGM tiles under the same {@code <g id>}
 * names for incremental debugging against a known-good reference.
 */
public final class IllustratorSvgLayerReference {
    private static final Pattern GROUP_ID = Pattern.compile("<g\\s+id=\"([^\"]+)\"");
    private static final Pattern IMAGE_TAG = Pattern.compile(
            "<image\\s+[^>]*?width=\"([^\"]+)\"\\s+height=\"([^\"]+)\"\\s+transform=\"([^\"]+)\"",
            Pattern.DOTALL);
    private static final Pattern IMAGE_TAG_ALT = Pattern.compile(
            "<image\\s+[^>]*?transform=\"([^\"]+)\"[^>]*?width=\"([^\"]+)\"\\s+height=\"([^\"]+)\"",
            Pattern.DOTALL);

    private final List<LayerRegion> layers;

    private IllustratorSvgLayerReference(List<LayerRegion> layers) {
        this.layers = List.copyOf(layers);
    }

    public List<LayerRegion> layers() {
        return layers;
    }

    public static Optional<IllustratorSvgLayerReference> parseFile(Path illustratorSvg) {
        if (illustratorSvg == null || !Files.isRegularFile(illustratorSvg)) {
            return Optional.empty();
        }
        try {
            String svg = Files.readString(illustratorSvg, StandardCharsets.UTF_8);
            List<LayerRegion> layers = parse(svg);
            return layers.isEmpty() ? Optional.empty() : Optional.of(new IllustratorSvgLayerReference(layers));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static List<LayerRegion> parse(String svg) {
        if (svg == null || svg.isEmpty()) {
            return List.of();
        }
        double pageHeight = parsePageHeight(svg);
        int bodyStart = svg.indexOf("</defs>");
        String body = bodyStart >= 0 ? svg.substring(bodyStart) : svg;
        List<LayerRegion> regions = new ArrayList<>();
        Matcher groupMatcher = GROUP_ID.matcher(body);
        while (groupMatcher.find()) {
            String id = groupMatcher.group(1);
            if (!isLayerId(id)) {
                continue;
            }
            int groupStart = groupMatcher.start();
            int groupEnd = findClosingGroup(body, groupStart);
            if (groupEnd < 0) {
                continue;
            }
            String chunk = body.substring(groupStart, groupEnd);
            parseImageBounds(id, chunk, pageHeight).ifPresent(regions::add);
        }
        return regions;
    }

    private static double parsePageHeight(String svg) {
        Matcher matcher = Pattern.compile("viewBox=\"0 0 [^ ]+ ([^\"]+)\"").matcher(svg);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return 191.0;
    }

    /**
     * Assigns a layer id using the tile center in EPS user space.
     */
    public String layerForTile(AgmEmbeddedImage image) {
        double[] bounds = AgmImageBounds.epsBounds(image);
        double cx = (bounds[0] + bounds[2]) / 2.0;
        double cy = (bounds[1] + bounds[3]) / 2.0;
        String bestId = null;
        double bestArea = Double.POSITIVE_INFINITY;
        for (LayerRegion layer : layers) {
            if (layer.contains(cx, cy)) {
                double area = layer.bbox().getWidth() * layer.bbox().getHeight();
                if (area < bestArea) {
                    bestArea = area;
                    bestId = layer.id();
                }
            }
        }
        return bestId != null ? bestId : "unassigned_raster";
    }

    /**
     * Groups tiles by layer id, preserving reference layer order.
     */
    public Map<String, List<AgmEmbeddedImage>> groupTiles(List<AgmEmbeddedImage> tiles) {
        Map<String, List<AgmEmbeddedImage>> grouped = new LinkedHashMap<>();
        for (LayerRegion layer : layers) {
            grouped.put(layer.id(), new ArrayList<>());
        }
        grouped.put("unassigned_raster", new ArrayList<>());
        for (AgmEmbeddedImage tile : tiles) {
            String layerId = layerForTile(tile);
            grouped.computeIfAbsent(layerId, key -> new ArrayList<>()).add(tile);
        }
        grouped.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return grouped;
    }

    private static boolean isLayerId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        if (id.startsWith("clippath") || id.equals("pattern")) {
            return false;
        }
        return !id.startsWith("Unnamed_Pattern");
    }

    private static Optional<LayerRegion> parseImageBounds(String layerId, String groupChunk, double pageHeight) {
        Matcher matcher = IMAGE_TAG.matcher(groupChunk);
        if (!matcher.find()) {
            matcher = IMAGE_TAG_ALT.matcher(groupChunk);
            if (!matcher.find()) {
                return Optional.empty();
            }
            double width = Double.parseDouble(matcher.group(2));
            double height = Double.parseDouble(matcher.group(3));
            Matrix matrix = parseTransform(matcher.group(1));
            return Optional.of(new LayerRegion(
                    layerId, svgImageBoundsToEps(matrix, width, height, pageHeight)));
        }
        double width = Double.parseDouble(matcher.group(1));
        double height = Double.parseDouble(matcher.group(2));
        Matrix matrix = parseTransform(matcher.group(3));
        return Optional.of(new LayerRegion(
                layerId, svgImageBoundsToEps(matrix, width, height, pageHeight)));
    }

    /**
     * Illustrator SVG image transforms use top-left SVG coordinates; AGM tiles use EPS (Y up).
     */
    private static BoundingBox svgImageBoundsToEps(
            Matrix matrix, double width, double height, double pageHeight) {
        double displayW = Math.abs(matrix.getA()) * width;
        double displayH = Math.abs(matrix.getD()) * height;
        double tx = matrix.getE();
        double ty = matrix.getF();
        double llx = tx;
        double urx = tx + displayW;
        double ury = pageHeight - ty;
        double lly = pageHeight - ty - displayH;
        return new BoundingBox(llx, lly, urx, ury);
    }

    private static Matrix parseTransform(String transform) {
        Matrix matrix = Matrix.identity();
        if (transform == null || transform.isBlank()) {
            return matrix;
        }
        Matcher tokenMatcher = Pattern.compile("(translate\\([^)]*\\)|scale\\([^)]*\\))").matcher(transform);
        while (tokenMatcher.find()) {
            String token = tokenMatcher.group(1);
            if (token.startsWith("translate")) {
                Matcher translate = Pattern.compile(
                        "translate\\(\\s*([-\\d.]+)(?:[\\s,]+([-\\d.]+))?\\s*\\)").matcher(token);
                if (translate.matches()) {
                    double tx = Double.parseDouble(translate.group(1));
                    double ty = translate.group(2) == null ? 0.0 : Double.parseDouble(translate.group(2));
                    matrix = matrix.postConcat(new Matrix(1, 0, 0, 1, tx, ty));
                }
            } else {
                Matcher scale = Pattern.compile(
                        "scale\\(\\s*([-\\d.]+)(?:[\\s,]+([-\\d.]+))?\\s*\\)").matcher(token);
                if (scale.matches()) {
                    double sx = Double.parseDouble(scale.group(1));
                    double sy = scale.group(2) == null ? sx : Double.parseDouble(scale.group(2));
                    matrix = matrix.postConcat(new Matrix(sx, 0, 0, sy, 0, 0));
                }
            }
        }
        return matrix;
    }

    private static int findClosingGroup(String text, int openIndex) {
        int depth = 0;
        int i = openIndex;
        while (i < text.length() - 3) {
            if (text.startsWith("<g", i) && (i + 2 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 2)))) {
                depth++;
                i += 2;
                continue;
            }
            if (text.startsWith("</g>", i)) {
                depth--;
                i += 4;
                if (depth == 0) {
                    return i;
                }
                continue;
            }
            i++;
        }
        return -1;
    }

    public static final class LayerRegion {
        private final String id;
        private final BoundingBox bbox;

        public LayerRegion(String id, BoundingBox bbox) {
            this.id = id;
            this.bbox = bbox;
        }

        public String id() {
            return id;
        }

        public BoundingBox bbox() {
            return bbox;
        }

        public boolean contains(double x, double y) {
            return x >= bbox.getLlx() && x <= bbox.getUrx()
                    && y >= bbox.getLly() && y <= bbox.getUry();
        }
    }
}
