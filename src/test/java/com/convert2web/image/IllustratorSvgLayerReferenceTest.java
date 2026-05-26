package com.convert2web.image;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IllustratorSvgLayerReferenceTest {

    @Test
    void parsesLayerIdsFromIllustratorExport() {
        Path reference = Path.of("test/output/all-the-dita/v16415663-illustrator.svg");
        if (!reference.toFile().isFile()) {
            return;
        }
        var parsed = IllustratorSvgLayerReference.parseFile(reference);
        assertTrue(parsed.isPresent());
        assertFalse(parsed.get().layers().isEmpty());
        assertTrue(parsed.get().layers().stream().anyMatch(layer -> "Background_Image".equals(layer.id())));
        assertTrue(parsed.get().layers().stream().anyMatch(layer -> "Frazier_Meter".equals(layer.id())));
    }
}
