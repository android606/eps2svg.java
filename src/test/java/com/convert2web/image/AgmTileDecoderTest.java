package com.convert2web.image;

import com.convert2web.model.Matrix;
import com.convert2web.ps.PsValue;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgmTileDecoderTest {

    @Test
    void logsWarningWhenPayloadTooShort() {
        Logger logger = Logger.getLogger(AgmTileDecoder.class.getName());
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);
        Level prior = logger.getLevel();
        logger.setLevel(Level.WARNING);
        try {
            assertNull(AgmTileDecoder.decodeBeginBinaryPayload("sepimg", 10, 10, "short", Matrix.identity(), null));
            assertTrue(handler.messages.stream().anyMatch(m -> m.contains("AGM tile decode failed")
                    && m.contains("sepimg")
                    && m.contains("10x10")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(prior);
        }
    }

    @Test
    void decodeBeginBinaryUsesDictionaryDsWhenPayloadIsEmpty() {
        Map<String, PsValue> dict = Map.of(
                "W", new PsValue.IntegerValue(4),
                "H", new PsValue.IntegerValue(1),
                "D", new PsValue.ArrayValue(java.util.List.of(
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1))),
                "DS", new PsValue.StringValue("z"));

        AgmEmbeddedImage image = AgmTileDecoder.decodeBeginBinary(
                dict, "sepimg", "", Matrix.identity(), null);

        assertNotNull(image);
        assertEquals(4, image.getWidth());
        assertEquals(1, image.getHeight());
        assertTrue(image.getPngBytes().length > 0);
    }

    @Test
    void decodeBeginBinaryPrefersInlineDictionaryDs() {
        Map<String, PsValue> dict = Map.of(
                "W", new PsValue.IntegerValue(1),
                "H", new PsValue.IntegerValue(1),
                "D", new PsValue.ArrayValue(java.util.List.of(
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1))),
                "DS", new PsValue.StringValue("z"));

        AgmEmbeddedImage image = AgmTileDecoder.decodeBeginBinary(
                dict, "sepimg", "not-valid-ascii85", Matrix.identity(), null);

        assertNotNull(image);
        assertEquals(1, image.getWidth());
        assertEquals(1, image.getHeight());
        assertTrue(image.getPngBytes().length > 0);
    }

    @Test
    void decodeBeginBinaryUsesDictionaryDsArray() {
        Map<String, PsValue> dict = Map.of(
                "W", new PsValue.IntegerValue(1),
                "H", new PsValue.IntegerValue(1),
                "D", new PsValue.ArrayValue(java.util.List.of(
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1),
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1),
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1),
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1))),
                "DS", new PsValue.ArrayValue(java.util.List.of(
                        new PsValue.StringValue("z"),
                        new PsValue.StringValue("z"),
                        new PsValue.StringValue("z"),
                        new PsValue.StringValue("z"))));

        AgmEmbeddedImage image = AgmTileDecoder.decodeBeginBinary(
                dict, "img", "", Matrix.identity(), null);

        assertNotNull(image);
        assertEquals(1, image.getWidth());
        assertEquals(1, image.getHeight());
        assertTrue(image.getPngBytes().length > 0);
    }

    @Test
    void decodeBeginBinaryInterleavesDictionaryDsCmykComponents() throws Exception {
        Map<String, PsValue> dict = Map.of(
                "W", new PsValue.IntegerValue(4),
                "H", new PsValue.IntegerValue(1),
                "D", new PsValue.ArrayValue(java.util.List.of(
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1),
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1),
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1),
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1))),
                "DS", new PsValue.ArrayValue(java.util.List.of(
                        new PsValue.StringValue("z"),
                        new PsValue.StringValue("z"),
                        new PsValue.StringValue("z"),
                        new PsValue.StringValue("s8W-!"))));

        AgmEmbeddedImage image = AgmTileDecoder.decodeBeginBinary(
                dict, "img", "", Matrix.identity(), null);

        assertNotNull(image);
        java.awt.image.BufferedImage png = ImageIO.read(new ByteArrayInputStream(image.getPngBytes()));
        assertEquals(0x000000, png.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0x000000, png.getRGB(3, 0) & 0xFFFFFF);
    }

    @Test
    void decodeBeginBinarySupportsCommonExecutableDsPipeline() {
        Map<String, PsValue> dict = Map.of(
                "W", new PsValue.IntegerValue(14),
                "H", new PsValue.IntegerValue(42),
                "D", new PsValue.ArrayValue(java.util.List.of(
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1))),
                "DS", PsValue.NameValue.executable("cf"),
                "ASCII85Decode", PsValue.NameValue.executable("fl"),
                "RunLengthDecode", PsValue.NameValue.executable("filter"));

        AgmEmbeddedImage image = AgmTileDecoder.decodeBeginBinary(
                dict, "sepimg", "K)^H&K)^H&Z2]=~>", Matrix.identity(), null);

        assertNotNull(image);
        assertEquals(14, image.getWidth());
        assertEquals(42, image.getHeight());
        assertTrue(image.getPngBytes().length > 0);
    }

    @Test
    void decodeBeginBinaryAcceptsShortCompressedPayload() {
        Map<String, PsValue> dict = Map.of(
                "W", new PsValue.IntegerValue(14),
                "H", new PsValue.IntegerValue(42),
                "D", new PsValue.ArrayValue(java.util.List.of(
                        new PsValue.IntegerValue(0),
                        new PsValue.IntegerValue(1))));

        AgmEmbeddedImage image = AgmTileDecoder.decodeBeginBinary(
                dict, "sepimg", "K)^H&K)^H&Z2]=~>", Matrix.identity(), null);

        assertNotNull(image);
        assertEquals(14, image.getWidth());
        assertEquals(42, image.getHeight());
        assertTrue(image.getPngBytes().length > 0);
    }

    private static final class RecordingHandler extends Handler {
        private final java.util.List<String> messages = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record == null || record.getMessage() == null) {
                return;
            }
            Object[] params = record.getParameters();
            if (params != null && params.length > 0) {
                messages.add(java.text.MessageFormat.format(record.getMessage(), params));
            } else {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
