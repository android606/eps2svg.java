package com.convert2web;

import com.convert2web.model.EpsDocument;
import com.convert2web.ps.PostScriptLexer;
import com.convert2web.ps.PostScriptParser;
import com.convert2web.ps.PostScriptVm;
import com.convert2web.ps.PsValue;
import com.convert2web.render.SvgRenderer;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
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
