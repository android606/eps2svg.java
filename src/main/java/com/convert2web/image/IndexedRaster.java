package com.convert2web.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Indexed-color samples with a CMYK lookup table to PNG. */
public final class IndexedRaster {
    private IndexedRaster() {
    }

    public static byte[] toPng(int width, int height, byte[] indices, byte[] paletteCmyk) {
        if (indices.length < width * height) {
            throw new IllegalArgumentException(
                    "index length " + indices.length + " < " + width * height);
        }
        int colors = paletteCmyk.length / 4;
        if (colors <= 0) {
            throw new IllegalArgumentException("empty palette");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            int rowBase = y * width;
            for (int x = 0; x < width; x++) {
                int index = indices[rowBase + x] & 0xFF;
                if (index >= colors) {
                    index = 0;
                }
                int offset = index * 4;
                int rgb = CmykRaster.cmykByteToRgb(
                        paletteCmyk[offset],
                        paletteCmyk[offset + 1],
                        paletteCmyk[offset + 2],
                        paletteCmyk[offset + 3]);
                image.setRGB(x, y, rgb);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PNG encode failed", e);
        }
    }
}
