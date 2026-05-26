package com.convert2web.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CmykRasterTest {

    @Test
    void decodesRowInterleavedCmykScanlines() throws Exception {
        int width = 2;
        int height = 2;
        // row 0: C=(10,20) M=(0,0) Y=(0,0) K=(0,0) -> light red / lighter red
        // row 1: all zero -> white
        byte[] samples = new byte[] {
                10, 20, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
        };
        byte[] png = CmykRaster.toPng(width, height, samples);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
        int topLeft = image.getRGB(0, 0) & 0xFFFFFF;
        int topRight = image.getRGB(1, 0) & 0xFFFFFF;
        int bottomLeft = image.getRGB(0, 1) & 0xFFFFFF;
        int bottomRight = image.getRGB(1, 1) & 0xFFFFFF;
        assertTrue(topLeft != 0xFFFFFF);
        assertTrue(topRight != topLeft);
        assertEquals(0xFFFFFF, bottomLeft);
        assertEquals(0xFFFFFF, bottomRight);
    }
}
