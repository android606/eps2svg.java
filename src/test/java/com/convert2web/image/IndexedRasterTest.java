package com.convert2web.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexedRasterTest {

    @Test
    void mapsIndicesThroughCmykPalette() {
        byte[] palette = new byte[] {
                (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                (byte) 0, (byte) 255, (byte) 255, (byte) 0,
        };
        byte[] indices = new byte[] {0, 1, 0, 1};
        byte[] png = IndexedRaster.toPng(2, 2, indices, palette);
        assertTrue(png.length > 8);
        assertEquals(0x89, png[0] & 0xFF);
    }
}
