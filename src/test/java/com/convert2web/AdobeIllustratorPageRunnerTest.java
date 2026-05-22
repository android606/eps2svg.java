package com.convert2web;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import com.convert2web.model.EpsDocumentBuilder;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.PathSegment;
import com.convert2web.model.StrokeStyle;
import com.convert2web.model.WindingRule;
import com.convert2web.ps.PostScriptLexer;
import com.convert2web.ps.PostScriptParser;
import com.convert2web.ps.PostScriptVm;
import com.convert2web.ps.PsValue;
import com.convert2web.render.SvgRenderer;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdobeIllustratorPageRunnerTest {

    @Test
    void cePageBodyProducesVectorPaths() throws Exception {
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read("test/test_images/CE_0344-binary.eps");
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        String pageBody = AdobeIllustratorPageRunner.extractPageBody(ps);
        assertNotNull(pageBody);

        EpsDocument document = runWithDiagnostics(pageBody, data);
        assertNotNull(document);
        assertFalse(document.getCommands().isEmpty());

        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("<path"));
        assertFalse(svg.contains("data:image/png;base64,"));
        assertFalse(svg.contains("matrix(1 0 0 -1"), "paths should not double-flip Y");
    }

    @Test
    void asciiIllustratorV14666408ProducesVectorPaths() throws Exception {
        String path = "test/test_images/real_live_images/v14666408_en-ca.eps";
        EpsDocument document = new AsciiEpsConverter().convertToDocument(path);
        assertFalse(document.getCommands().isEmpty());

        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("<path"), () -> svg.substring(0, Math.min(400, svg.length())));
        assertFalse(svg.contains("Batik Graphics2D"));
        assertFalse(svg.contains("data:image/png;base64,"));
    }

    @Test
    void sanitizedRealWorldPrepSensorPageProducesPaths() throws Exception {
        Path eps = Path.of(
                "/Users/android/Downloads/All-the-DITA/Image_Libraries/GC059889-00-BASE IMAGES/rtv_G_ill_prep_sensor_A.eps");
        if (!Files.exists(eps)) {
            return;
        }
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(eps.toString());
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        String pageBody = AdobeIllustratorPageRunner.extractPageBody(ps);
        assertNotNull(pageBody);
        EpsDocument document = AdobeIllustratorPageRunner.runPageBody(pageBody, data.boundingBox, ps);
        assertNotNull(document);
        assertFalse(document.getCommands().isEmpty());
    }

    @Test
    void extractPageBodyStripsIllustratorAngleDictMetadata() {
        String body = "np 0 0 mo\n"
                + "< /T 1 /W 480 /H 640 /M[480 0 0 -640 0 640] /BC 8 >\n"
                + "10 10 li f\n";
        String stripped = AdobeIllustratorPageRunner.extractPageBody(
                "%%EndSetup\n" + body + "\n%%PageTrailer\n");
        assertNotNull(stripped);
        assertFalse(stripped.contains("/T 1"));
        assertTrue(stripped.contains("10 10 li"));
    }

    @Test
    void hasVisibleArtRejectsWhiteBackgroundOnly() {
        com.convert2web.model.Path rect = new com.convert2web.model.Path(List.of(
                new PathSegment.MoveTo(0, 0),
                new PathSegment.LineTo(128, 0),
                new PathSegment.LineTo(128, 160),
                new PathSegment.LineTo(0, 160),
                new PathSegment.Close()));
        EpsDocumentBuilder builder = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 128, 160))
                .addFill(rect, PaintStyle.rgb(1, 1, 1), WindingRule.NON_ZERO, Matrix.identity());
        assertFalse(AdobeIllustratorPageRunner.hasVisibleArt(builder.build()));
        builder.addStroke(rect, PaintStyle.rgb(0, 0, 0),
                new StrokeStyle(1, 0, 0, 4, new double[0], 0), Matrix.identity());
        assertTrue(AdobeIllustratorPageRunner.hasVisibleArt(builder.build()));
    }

    @Test
    void sanitizeRemovesDictionaryRemnantsAfterAngleDictStrip() throws Exception {
        String input = "f\nrestore end }\n>>\n/Pattern add_res\n/O 3\n>>\n10 0 li\n";
        String sanitized = AdobeIllustratorPageRunner.sanitizeIllustratorPageText(input);
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(sanitized))) {
            new PostScriptParser().parseAll(lexer);
        }
        assertFalse(sanitized.contains(">>"));
        assertTrue(sanitized.contains("10 0 li"));
    }

    @Test
    void sanitizeKeepsXshSpacingArrayClosingBracket() throws Exception {
        String input = "34.1128 50 mo\n"
                + "(Condition Fir)sh\n"
                + "[6.74121 4.72266 3.65625 0 \n"
                + "]xsh\n"
                + "10 0 li\n";
        String sanitized = AdobeIllustratorPageRunner.sanitizeIllustratorPageText(input);
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(sanitized))) {
            new PostScriptParser().parseAll(lexer);
        }
        assertTrue(sanitized.contains("]xsh"));
    }

    @Test
    void extractPageBodyStripsBeginBinaryBlocks() {
        String body = "1 0 mo\n"
                + "%%BeginBinary: 1\nimg\nJcP<@not-hex-data\n"
                + "%%EndBinary\n"
                + "10 0 li f\n";
        String stripped = AdobeIllustratorPageRunner.extractPageBody(
                "%%EndSetup\n" + body + "\n%%PageTrailer\n");
        assertNotNull(stripped);
        assertFalse(stripped.contains("BeginBinary"));
        assertFalse(stripped.contains("JcP<@"));
        assertTrue(stripped.contains("10 0 li"));
    }

    @Test
    void udiPageBodyProducesVectorPaths() throws Exception {
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read("test/test_images/UDI.eps");
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        String pageBody = AdobeIllustratorPageRunner.extractPageBody(ps);
        assertNotNull(pageBody);

        EpsDocument document = runWithDiagnostics(pageBody, data);
        assertNotNull(document, "VM produced no document; check stderr from runWithDiagnostics");
        assertFalse(document.getCommands().isEmpty());

        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("<path"));
        assertFalse(svg.contains("data:image/png;base64,"));
        assertFalse(svg.contains("matrix(1 0 0 -1"), "paths should not double-flip Y");
    }

    private static EpsDocument runWithDiagnostics(String pageBody, BinaryEpsReader.BinaryEpsData data)
            throws Exception {
        EpsDocument fromRunner = AdobeIllustratorPageRunner.runPageBody(pageBody, data.boundingBox);
        if (fromRunner != null) {
            return fromRunner;
        }
        PostScriptVm vm = new PostScriptVm();
        vm.getDocumentBuilder().setBoundingBox(data.boundingBox);
        String program = ""
                + "userdict begin\n"
                + "/mo { moveto } bind def\n"
                + "/li { lineto } bind def\n"
                + "/cv { curveto } bind def\n"
                + "/cp { closepath } bind def\n"
                + "/clp { clip } bind def\n"
                + "/f { fill } bind def\n"
                + "/np { newpath } bind def\n"
                + "/ct { concat } bind def\n"
                + "/cmyk { setcmykcolor } bind def\n"
                + "/pgsv { gsave } bind def\n"
                + "/pgrs { grestore } bind def\n"
                + "/sop { pop } bind def\n"
                + "/add_res { pop pop pop } bind def\n"
                + "/CSA { pop pop } bind def\n"
                + pageBody + "\nend\n";
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(program))) {
            List<PsValue> tokens = new PostScriptParser().parseAll(lexer);
            vm.executeAll(tokens);
        } catch (Exception e) {
            throw new AssertionError("page VM failed: " + e.getMessage(), e);
        }
        EpsDocument doc = vm.getDocument();
        if (doc.getCommands().isEmpty()) {
            String minimal = ""
                    + "userdict begin\n"
                    + "/mo { moveto } bind def /li { lineto } bind def /cp { closepath } bind def\n"
                    + "/f { fill } bind def /np { newpath } bind def\n"
                    + "np 0 0 mo 10 0 li 10 10 li cp f\n"
                    + "end\n";
            PostScriptVm vm2 = new PostScriptVm();
            vm2.getDocumentBuilder().setBoundingBox(data.boundingBox);
            try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(minimal))) {
                vm2.executeAll(new PostScriptParser().parseAll(lexer));
            }
            throw new AssertionError("page produced 0 commands; minimal smoke got "
                    + vm2.getDocument().getCommands().size());
        }
        return doc;
    }
}
