package com.convert2web.image;

import com.convert2web.model.Matrix;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgmPlaceholderTilesTest {

    @Test
    void detectsSmallWhiteLegendTile() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAFIAAAAdCAIAAAC2QKx1AAAAOklEQVR4Xu3PoQEAAAiAMP9/WrsvjEUas6T5wdC2pG1J25K2JW1L2pa0LWlb0rakbUnbkrYlbUvQ7QNVJ8O4mwNDRQAAAABJRU5ErkJggg==");
        Matrix ctm = new Matrix(0.24, 0, 0, 0.24, 0, 0);
        assertTrue(AgmPlaceholderTiles.isBlankSepcsPlaceholder(png, 82, 29, ctm));
    }

    @Test
    void keepsLargeNonWhiteTiles() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        assertFalse(AgmPlaceholderTiles.isBlankSepcsPlaceholder(
                png, 1, 1, new Matrix(100, 0, 0, 100, 0, 0)));
    }
}
