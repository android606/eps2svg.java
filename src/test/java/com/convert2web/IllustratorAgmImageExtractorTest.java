package com.convert2web;

import com.convert2web.image.AgmEmbeddedImage;
import com.convert2web.image.IllustratorAgmImageExtractor;
import com.convert2web.model.EpsDocument;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.render.SvgRenderer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IllustratorAgmImageExtractorTest {

    @Test
    void extractsTwoCmykImagesFromHomeExampleLayered() throws Exception {
        Path eps = Path.of(
                "/Users/android/Downloads/All-the-DITA/Image_Libraries/GC059885-00-BASE IMAGES/rta_AB_ill_Home_Example_Layered_CMYK.eps");
        if (!Files.exists(eps)) {
            return;
        }
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(eps.toString());
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        String rawPage = AdobeIllustratorPageRunner.extractRawPageBody(ps);
        assertEquals(2, IllustratorAgmImageExtractor.extract(rawPage).size());
    }

    @Test
    void convertsRasterOnlyScreenMockupFromAgmTiles() throws Exception {
        Path eps = Path.of(
                "/Users/android/Downloads/All-the-DITA/Image_Libraries/GC059889-00-BASE IMAGES/rtv_G_ss_HomeScreen_CMYK.eps");
        if (!Files.exists(eps)) {
            return;
        }
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(eps.toString());
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        EpsDocument document = AdobeIllustratorPageRunner.convertIllustratorPostScript(ps, data.boundingBox);
        assertTrue(document != null);
        assertTrue(AdobeIllustratorPageRunner.hasVisibleArt(document));
        long embedded = document.getCommands().stream()
                .filter(GraphicsCommand.EmbeddedImage.class::isInstance)
                .count();
        assertTrue(embedded >= 1);
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("data:image/png;base64,"));
    }

    @Test
    void extractsIdximgTilesWithLookupPalette() throws Exception {
        Path eps = Path.of("/Users/android/Downloads/All-the-DITA/v8225530.eps");
        if (!Files.exists(eps)) {
            return;
        }
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(eps.toString());
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        String rawPage = AdobeIllustratorPageRunner.extractRawPageBody(ps);
        assertEquals(1, IllustratorAgmImageExtractor.extract(rawPage).size());
    }

    @Test
    void sepimg25x38TileCtmDoesNotBakeImageMatrixIntoCtm() throws Exception {
        Path eps = Path.of("/Users/android/Downloads/All-the-DITA/v16415663.eps");
        if (!Files.exists(eps)) {
            return;
        }
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(eps.toString());
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        String rawPage = AdobeIllustratorPageRunner.extractRawPageBody(ps);
        AgmEmbeddedImage tile = IllustratorAgmImageExtractor.extract(rawPage).stream()
                .filter(image -> image.getWidth() == 25 && image.getHeight() == 38)
                .findFirst()
                .orElseThrow();
        assertTrue(Math.abs(tile.getCtm().getA()) < 10.0, "CTM must not include /M width scale");
        assertEquals(6.0, Math.abs(tile.getCtm().getA()), 0.05);
    }

    @Test
    void logoTileCtmComposesPageYFlip() throws Exception {
        Path eps = Path.of("/Users/android/Downloads/All-the-DITA/v16415663.eps");
        if (!Files.exists(eps)) {
            return;
        }
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(eps.toString());
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        String rawPage = AdobeIllustratorPageRunner.extractRawPageBody(ps);
        AgmEmbeddedImage logo = IllustratorAgmImageExtractor.extract(rawPage).stream()
                .filter(image -> image.getWidth() == 380 && image.getHeight() == 160)
                .findFirst()
                .orElseThrow();
        assertTrue(logo.getCtm().getD() < 0, "logo tile CTM should include page Y-flip");
        assertEquals(142.386, logo.getCtm().getE(), 0.05);
    }

    @Test
    void convertsV16415663WithoutArrayEndParseFailure() throws Exception {
        Path eps = Path.of("/Users/android/Downloads/All-the-DITA/v16415663.eps");
        if (!Files.exists(eps)) {
            return;
        }
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(eps.toString());
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        String rawPage = AdobeIllustratorPageRunner.extractRawPageBody(ps);
        assertTrue(IllustratorAgmImageExtractor.extract(rawPage).size() >= 115);
        BinaryEpsConverter converter = new BinaryEpsConverter();
        Path svg = Path.of("test/output/all-the-dita/v16415663.svg");
        Files.createDirectories(svg.getParent());
        assertTrue(converter.tryConvert(eps.toString(), svg.toString()));
        assertTrue(Files.size(svg) > 10_000);
        String content = Files.readString(svg);
        assertTrue(content.contains("<svg"));
        assertTrue(content.split("data:image/png;base64,").length > 75);
    }

    @Test
    void extractsSepimgCmykTiles() throws Exception {
        Path eps = Path.of("/Users/android/Downloads/All-the-DITA/v10422158.eps");
        if (!Files.exists(eps)) {
            return;
        }
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(eps.toString());
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        String rawPage = AdobeIllustratorPageRunner.extractRawPageBody(ps);
        assertEquals(1, IllustratorAgmImageExtractor.extract(rawPage).size());
    }

    @Test
    void mergedDocumentRendersEmbeddedPngInSvg() throws Exception {
        Path eps = Path.of(
                "/Users/android/Downloads/All-the-DITA/Image_Libraries/GC059885-00-BASE IMAGES/rta_AB_ill_Home_Example_Layered_CMYK.eps");
        if (!Files.exists(eps)) {
            return;
        }
        BinaryEpsReader.BinaryEpsData data = BinaryEpsReader.read(eps.toString());
        String ps = new String(data.postScriptData, StandardCharsets.ISO_8859_1);
        EpsDocument document = AdobeIllustratorPageRunner.convertIllustratorPostScript(ps, data.boundingBox);
        long embedded = document.getCommands().stream()
                .filter(GraphicsCommand.EmbeddedImage.class::isInstance)
                .count();
        assertEquals(2, embedded);
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("data:image/png;base64,"));
        assertTrue(svg.contains("<image"));
        assertTrue(svg.contains("transform=\"matrix("));
        assertFalse(svg.contains("preserveAspectRatio=\"none\""));
    }
}
