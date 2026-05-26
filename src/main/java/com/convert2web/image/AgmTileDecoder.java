package com.convert2web.image;

import com.convert2web.model.Matrix;
import com.convert2web.ps.PsValue;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodes Adobe AGM raster tiles from an image dictionary and {@code %%BeginBinary} payload.
 */
public final class AgmTileDecoder {
    private static final Logger logger = Logger.getLogger(AgmTileDecoder.class.getName());

    private static final Pattern SEPARATION_D = Pattern.compile("/D\\s*\\[\\s*0\\s+1\\s*\\]");
    private static final Pattern LOOKUP_BLOCK = Pattern.compile(
            "/Lookup\\s*<~([\\s\\S]*?)~>",
            Pattern.MULTILINE);
    private static final Pattern HI_VAL = Pattern.compile("/HiVal\\s+(\\d+)");

    public enum Operator {
        CMYK, SEPARATION, INDEXED
    }

    private AgmTileDecoder() {
    }

    public static Operator operatorKind(String operator) {
        if (operator == null) {
            return Operator.CMYK;
        }
        if (operator.contains("sepimg")) {
            return Operator.SEPARATION;
        }
        if (operator.contains("idximg")) {
            return Operator.INDEXED;
        }
        return Operator.CMYK;
    }

    public static boolean isSeparationDict(Map<String, PsValue> entries) {
        PsValue d = entries.get("D");
        if (!(d instanceof PsValue.ArrayValue)) {
            return false;
        }
        PsValue.ArrayValue array = (PsValue.ArrayValue) d;
        if (array.getElements().size() != 2) {
            return false;
        }
        PsValue first = array.getElements().get(0);
        PsValue second = array.getElements().get(1);
        return intValue(first) == 0 && intValue(second) == 1;
    }

    public static Operator kindForDict(Map<String, PsValue> entries, String operator) {
        Operator op = operatorKind(operator);
        if (op == Operator.SEPARATION || isSeparationDict(entries)) {
            return Operator.SEPARATION;
        }
        return op;
    }

    /**
     * Decodes a tile painted via {@code %%BeginBinary} + {@code sepimg}/{@code img}/{@code idximg}.
     *
     * @param pageContext optional raw page body for indexed palette lookup; may be null
     */
    public static AgmEmbeddedImage decodeBeginBinary(
            Map<String, PsValue> dictEntries,
            String operator,
            String ascii85Payload,
            Matrix displayMatrix,
            String pageContext) {
        int width = intEntry(dictEntries, "W");
        int height = intEntry(dictEntries, "H");
        if (width <= 0 || height <= 0) {
            logDecodeFailure(operator, width, height, "invalid /W or /H in image dictionary");
            return null;
        }
        AgmEmbeddedImage inlineDs = decodeDictionaryDs(dictEntries, operator, width, height, displayMatrix);
        if (inlineDs != null) {
            return inlineDs;
        }
        String payload = ascii85Payload == null ? "" : ascii85Payload.trim();
        if (payload.isEmpty()) {
            logDecodeFailure(operator, width, height, "binary payload too short ({0} chars)", payload.length());
            return null;
        }
        Operator kind = kindForDict(dictEntries, operator);
        try {
            byte[] runLength = decodeExternalDataSource(dictEntries, payload);
            byte[] png = decodePng(kind, pageContext, width, height, runLength, operator);
            if (png.length == 0) {
                return null;
            }
            return new AgmEmbeddedImage(width, height, displayMatrix, png);
        } catch (RuntimeException e) {
            logDecodeFailure(operator, width, height, "{0}", e.getMessage());
            return null;
        }
    }

    public static AgmEmbeddedImage decodeBeginBinary(
            Map<String, PsValue> dictEntries,
            String operator,
            String ascii85Payload,
            Matrix displayMatrix) {
        return decodeBeginBinary(dictEntries, operator, ascii85Payload, displayMatrix, null);
    }

    private static byte[] decodeExternalDataSource(Map<String, PsValue> dictEntries, String payload) {
        // Medium-support for Illustrator's common executable /DS:
        //   /DS cf /ASCII85Decode fl /RunLengthDecode filter
        // The VM does not yet execute file/filter objects, so the synthetic
        // %%BeginBinary token supplies the currentfile bytes here.
        if (isCommonExternalDsPipeline(dictEntries) || !dictEntries.containsKey("DS")) {
            byte[] ascii85 = Ascii85Decoder.decode(payload);
            return RunLengthDecoder.decode(ascii85);
        }
        // For now, unsupported executable /DS forms still use the legacy
        // Illustrator payload path below; full /DS execution is documented
        // as future work.
        byte[] ascii85 = Ascii85Decoder.decode(payload);
        return RunLengthDecoder.decode(ascii85);
    }

    private static boolean isCommonExternalDsPipeline(Map<String, PsValue> dictEntries) {
        PsValue ds = dictEntries.get("DS");
        return ds instanceof PsValue.NameValue
                && "cf".equals(((PsValue.NameValue) ds).getName())
                && dictEntries.containsKey("ASCII85Decode")
                && dictEntries.containsKey("RunLengthDecode");
    }

    private static AgmEmbeddedImage decodeDictionaryDs(
            Map<String, PsValue> dictEntries,
            String operator,
            int width,
            int height,
            Matrix displayMatrix) {
        PsValue ds = dictEntries.get("DS");
        try {
            byte[] samples = decodeDictionaryDsSamples(ds, width, height, operator);
            if (samples.length == 0) {
                return null;
            }
            byte[] png = decodeInlineDsSamples(width, height, samples, operator);
            if (png.length == 0) {
                return null;
            }
            return new AgmEmbeddedImage(width, height, displayMatrix, png);
        } catch (RuntimeException e) {
            logDecodeFailure(operator, width, height, "{0}", e.getMessage());
            return null;
        }
    }

    private static byte[] decodeDictionaryDsSamples(
            PsValue ds, int width, int height, String operator) {
        if (ds instanceof PsValue.StringValue) {
            String content = ((PsValue.StringValue) ds).getValue();
            return content.trim().isEmpty() ? new byte[0] : decodeAscii85SampleContent(content);
        }
        if (!(ds instanceof PsValue.ArrayValue)) {
            return new byte[0];
        }
        java.util.List<PsValue> elements = ((PsValue.ArrayValue) ds).getElements();
        java.util.List<byte[]> decoded = new java.util.ArrayList<>();
        int total = 0;
        for (PsValue element : elements) {
            if (!(element instanceof PsValue.StringValue)) {
                return new byte[0];
            }
            byte[] component = decodeAscii85SampleContent(((PsValue.StringValue) element).getValue());
            decoded.add(component);
            total += component.length;
        }
        if (decoded.size() == 4 && total == 4L * width * height) {
            return interleaveCmykComponentPlanes(width, height, decoded);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] component : decoded) {
            out.write(component, 0, component.length);
        }
        return out.toByteArray();
    }

    private static byte[] interleaveCmykComponentPlanes(
            int width, int height, java.util.List<byte[]> components) {
        byte[] samples = new byte[4 * width * height];
        for (int y = 0; y < height; y++) {
            int rowBase = y * width * 4;
            int componentRowBase = y * width;
            for (int component = 0; component < 4; component++) {
                byte[] source = components.get(component);
                System.arraycopy(source, componentRowBase, samples, rowBase + component * width, width);
            }
        }
        return samples;
    }

    /** Decodes when width and height are already known (static page-body scan). */
    public static AgmEmbeddedImage decodeBeginBinaryPayload(
            String operator,
            int width,
            int height,
            String ascii85Payload,
            Matrix displayMatrix,
            String pageContext) {
        if (width <= 0 || height <= 0) {
            logDecodeFailure(operator, width, height, "invalid dimensions");
            return null;
        }
        String payload = ascii85Payload == null ? "" : ascii85Payload.trim();
        if (payload.isEmpty()) {
            logDecodeFailure(operator, width, height, "binary payload too short ({0} chars)", payload.length());
            return null;
        }
        Operator kind = operatorKind(operator);
        try {
            byte[] ascii85 = Ascii85Decoder.decode(payload);
            byte[] runLength = RunLengthDecoder.decode(ascii85);
            byte[] png = decodePng(kind, pageContext, width, height, runLength, operator);
            if (png.length == 0) {
                return null;
            }
            return new AgmEmbeddedImage(width, height, displayMatrix, png);
        } catch (RuntimeException e) {
            logDecodeFailure(operator, width, height, "{0}", e.getMessage());
            return null;
        }
    }

    /** Decodes inline {@code /DS} ASCII85 samples (no {@code %%BeginBinary}). */
    public static AgmEmbeddedImage decodeInlineDs(
            String dictText,
            int width,
            int height,
            Matrix displayMatrix) {
        String dsContent = extractDsSampleContent(dictText);
        if (dsContent == null) {
            logDecodeFailure("inline/DS", width, height, "missing /DS sample in image dictionary");
            return null;
        }
        try {
            byte[] samples = decodeAscii85SampleContent(dsContent);
            byte[] png = decodeInlineDsSamples(width, height, samples, "inline/DS");
            if (png.length == 0) {
                return null;
            }
            return new AgmEmbeddedImage(width, height, displayMatrix, png);
        } catch (RuntimeException e) {
            logDecodeFailure("inline/DS", width, height, "{0}", e.getMessage());
            return null;
        }
    }

    private static void logDecodeFailure(
            String operator, int width, int height, String message, Object... args) {
        String detail = args.length == 0 ? message : java.text.MessageFormat.format(message, args);
        logger.log(Level.WARNING,
                "AGM tile decode failed ({0} {1}x{2}): {3}",
                new Object[] {operator == null ? "unknown" : operator, width, height, detail});
    }

    public static boolean isSeparationDictText(String dict) {
        return SEPARATION_D.matcher(dict).find();
    }

    private static byte[] decodePng(
            Operator kind,
            String pageContext,
            int width,
            int height,
            byte[] runLength,
            String operator) {
        switch (kind) {
            case SEPARATION:
                return decodeSeparation(width, height, runLength, operator);
            case INDEXED:
                return decodeIndexed(pageContext, width, height, runLength, operator);
            default:
                return decodeCmyk(width, height, runLength, operator);
        }
    }

    private static byte[] decodeCmyk(int width, int height, byte[] runLength, String operator) {
        long expected = 4L * width * height;
        if (runLength.length != expected) {
            logDecodeFailure(operator, width, height,
                    "CMYK size mismatch: got {0} bytes, expected {1}", runLength.length, expected);
            return new byte[0];
        }
        return CmykRaster.toPng(width, height, runLength);
    }

    private static byte[] decodeSeparation(int width, int height, byte[] runLength, String operator) {
        long expected = (long) width * height;
        if (runLength.length < expected) {
            logDecodeFailure(operator, width, height,
                    "separation size mismatch: got {0} bytes, expected {1}", runLength.length, expected);
            return new byte[0];
        }
        byte[] gray = runLength;
        if (runLength.length > expected) {
            gray = new byte[(int) expected];
            System.arraycopy(runLength, 0, gray, 0, (int) expected);
        }
        return GrayscaleRaster.toPng(width, height, gray);
    }

    private static byte[] decodeIndexed(
            String pageContext, int width, int height, byte[] runLength, String operator) {
        long expected = (long) width * height;
        if (runLength.length != expected) {
            logDecodeFailure(operator, width, height,
                    "indexed size mismatch: got {0} bytes, expected {1}", runLength.length, expected);
            return new byte[0];
        }
        byte[] palette = parseIndexedPalette(pageContext);
        if (palette.length < 4) {
            logDecodeFailure(operator, width, height, "indexed image missing palette");
            return new byte[0];
        }
        return IndexedRaster.toPng(width, height, runLength, palette);
    }

    private static byte[] parseIndexedPalette(String pageContext) {
        if (pageContext == null || pageContext.isEmpty()) {
            return new byte[0];
        }
        int idxcs = pageContext.lastIndexOf("idxcs");
        if (idxcs < 0) {
            return new byte[0];
        }
        String window = pageContext.substring(Math.max(0, idxcs - 6000), idxcs);
        Matcher lookupMatcher = LOOKUP_BLOCK.matcher(window);
        if (!lookupMatcher.find()) {
            return new byte[0];
        }
        int hiVal = 255;
        Matcher hiValMatcher = HI_VAL.matcher(window);
        if (hiValMatcher.find()) {
            hiVal = Integer.parseInt(hiValMatcher.group(1));
        }
        byte[] decoded = Ascii85Decoder.decode("<~" + lookupMatcher.group(1) + "~>");
        int expected = (hiVal + 1) * 4;
        if (decoded.length < expected) {
            logger.log(Level.FINE, "AGM palette size mismatch: got {0}, expected {1}",
                    new Object[] {decoded.length, expected});
            return new byte[0];
        }
        if (decoded.length == expected) {
            return decoded;
        }
        byte[] palette = new byte[expected];
        System.arraycopy(decoded, 0, palette, 0, expected);
        return palette;
    }

    private static String extractDsSampleContent(String dict) {
        int ds = dict.indexOf("/DS");
        if (ds < 0) {
            return null;
        }
        int scan = ds + 3;
        while (scan < dict.length() && Character.isWhitespace(dict.charAt(scan))) {
            scan++;
        }
        if (dict.startsWith("<~", scan)) {
            int close = dict.indexOf("~>", scan);
            if (close < 0) {
                return null;
            }
            return dict.substring(scan, close + 2);
        }
        return extractDsArrayContent(dict);
    }

    private static String extractDsArrayContent(String dict) {
        int ds = dict.indexOf("/DS");
        if (ds < 0) {
            return null;
        }
        int open = dict.indexOf('[', ds);
        if (open < 0) {
            return null;
        }
        int depth = 1;
        int i = open + 1;
        while (i < dict.length() && depth > 0) {
            char ch = dict.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
            }
            i++;
        }
        if (depth != 0) {
            return null;
        }
        return dict.substring(open + 1, i - 1);
    }

    private static byte[] decodeAscii85SampleContent(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("<~")) {
            return Ascii85Decoder.decode(trimmed);
        }
        if (!trimmed.isEmpty() && !trimmed.contains("<~")) {
            return Ascii85Decoder.decode("<~" + trimmed + "~>");
        }
        Pattern ascii85Block = Pattern.compile("<~([\\s\\S]*?)~>");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        Matcher matcher = ascii85Block.matcher(content);
        while (matcher.find()) {
            byte[] decoded = Ascii85Decoder.decode("<~" + matcher.group(1) + "~>");
            out.write(decoded, 0, decoded.length);
        }
        return out.toByteArray();
    }

    private static byte[] decodeInlineDsSamples(int width, int height, byte[] samples, String operator) {
        long cmykExpected = 4L * width * height;
        if (samples.length >= cmykExpected) {
            byte[] cmyk = samples;
            if (samples.length > cmykExpected) {
                cmyk = new byte[(int) cmykExpected];
                System.arraycopy(samples, 0, cmyk, 0, (int) cmykExpected);
            }
            return CmykRaster.toPng(width, height, cmyk);
        }
        long grayExpected = (long) width * height;
        if (samples.length >= grayExpected) {
            byte[] gray = samples;
            if (samples.length > grayExpected) {
                gray = new byte[(int) grayExpected];
                System.arraycopy(samples, 0, gray, 0, (int) grayExpected);
            }
            return GrayscaleRaster.toPng(width, height, gray);
        }
        logDecodeFailure(operator, width, height,
                "inline /DS size mismatch: got {0} bytes, expected {1} or {2}",
                samples.length, cmykExpected, grayExpected);
        return new byte[0];
    }

    static int intEntry(Map<String, PsValue> entries, String key) {
        PsValue value = entries.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing /" + key);
        }
        return intValue(value);
    }

    static int intValue(PsValue value) {
        if (value instanceof PsValue.IntegerValue) {
            return Math.toIntExact(((PsValue.IntegerValue) value).getValue());
        }
        if (value instanceof PsValue.RealValue) {
            return (int) ((PsValue.RealValue) value).getValue();
        }
        throw new IllegalArgumentException("Expected integer, got " + value.getKind());
    }
}
