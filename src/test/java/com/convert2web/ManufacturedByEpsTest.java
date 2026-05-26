package com.convert2web;

import com.convert2web.render.SvgRenderer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManufacturedByEpsTest {

    @Test
    void convertsAsciiManufacturedByToVectorSvg() throws Exception {
        Path output = Files.createTempFile("manufactured-by", ".svg");
        try {
            new AsciiEpsConverter().convert(
                    "test/test_images/manufactured_by.eps", output.toString());
            String svg = Files.readString(output);
            assertTrue(svg.contains("<path"), () -> svg.substring(0, Math.min(400, svg.length())));
            assertFalse(svg.contains("data:image/png;base64,"));
            assertFalse(svg.contains("Batik Graphics2D"));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void binaryPageBodyProducesPaths() throws Exception {
        var data = BinaryEpsReader.read("test/test_images/ec_rep-binary.eps");
        String ps = new String(data.postScriptData, java.nio.charset.StandardCharsets.ISO_8859_1);
        String body = AdobeIllustratorPageRunner.extractPageBody(ps);
        var doc = AdobeIllustratorPageRunner.runPageBody(body, data.boundingBox);
        assertNotNull(doc, "page body len=" + (body == null ? 0 : body.length()));
        assertFalse(doc.getCommands().isEmpty());
    }

    @Test
    void convertsBinaryManufacturedByToVectorSvg() throws Exception {
        Path output = Files.createTempFile("ec-rep-binary", ".svg");
        try {
            assertTrue(new BinaryEpsConverter().tryConvert(
                    "test/test_images/ec_rep-binary.eps", output.toString()));
            String svg = Files.readString(output);
            assertTrue(svg.contains("<path"));
            assertFalse(svg.contains("data:image/png;base64,"));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void ghostscriptStreamExtracts() throws Exception {
        String ps = Files.readString(Path.of("test/test_images/manufactured_by.eps"));
        assertNotNull(GhostscriptEpsPageRunner.extractPageStream(ps));
    }

    @Test
    void ghostscriptStreamRunPageBody() throws Exception {
        String ps = Files.readString(Path.of("test/test_images/manufactured_by.eps"));
        String stream = GhostscriptEpsPageRunner.extractPageStream(ps);
        var bbox = new com.convert2web.model.BoundingBox(270, 384, 342, 407);
        var doc = AdobeIllustratorPageRunner.runPageBody(stream, bbox, false);
        assertNotNull(doc, "runPageBody returned null");
        assertFalse(doc.getCommands().isEmpty(), "command count=" + doc.getCommands().size());
    }
}
