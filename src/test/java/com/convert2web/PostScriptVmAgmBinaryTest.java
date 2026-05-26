package com.convert2web;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.ps.PostScriptLexer;
import com.convert2web.ps.PostScriptParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostScriptVmAgmBinaryTest {

    @Test
    void parsesBeginBinaryInPageBodyProgram() throws Exception {
        String page = ""
                + "gsave\n"
                + "[1 0 0 1 0 0] ct\n"
                + "snap_to_device\n"
                + "<< /T 1 /W 2 /H 2 /D[0 1] /BC 8 >>\n"
                + "%%BeginBinary: 1\n"
                + "sepimg\n"
                + "JcLB&JcLB&JcLB&JcLB&JcLB&JcLB&JcLB&JcLB&JcLB&JcLB&JcLB&JcLB&\n"
                + "%%EndBinary\n"
                + "grestore\n";
        try (PostScriptLexer lexer = new PostScriptLexer(new java.io.StringReader(page))) {
            new PostScriptParser().parseAll(lexer);
        }
    }

    @Test
    void vmRecordsEmbeddedImagesFromV16415663WhenPresent() throws Exception {
        Path eps = Path.of("/Users/android/Downloads/All-the-DITA/v16415663.eps");
        if (!Files.exists(eps)) {
            return;
        }
        byte[] bytes = Files.readAllBytes(eps);
        String ps = new String(bytes, StandardCharsets.ISO_8859_1);
        BoundingBox bbox = new BoundingBox(0, 0, 196, 190);
        EpsDocument document = AdobeIllustratorPageRunner.runFullPostScript(ps, bbox);
        if (document == null) {
            document = AdobeIllustratorPageRunner.runPageBody(
                    AdobeIllustratorPageRunner.extractPageBody(ps),
                    bbox,
                    ps);
        }
        assertTrue(document != null);
        long embedded = document.getCommands().stream()
                .filter(GraphicsCommand.EmbeddedImage.class::isInstance)
                .count();
        assertTrue(embedded >= 50, "expected VM-recorded tiles, got " + embedded);
        int lastFillBeforeImage = -1;
        boolean imageAfterFill = false;
        for (int i = 0; i < document.getCommands().size(); i++) {
            GraphicsCommand command = document.getCommands().get(i);
            if (command instanceof GraphicsCommand.Fill) {
                lastFillBeforeImage = i;
            } else if (command instanceof GraphicsCommand.EmbeddedImage) {
                if (lastFillBeforeImage >= 0) {
                    imageAfterFill = true;
                    break;
                }
            }
        }
        assertTrue(imageAfterFill, "at least one tile should paint after a vector fill");
    }
}
