package com.convert2web.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Converts CMYK raster samples to PNG bytes for SVG embedding.
 * Adobe AGM {@code %%BeginBinary} tiles use row-interleaved planes:
 * per scanline C[width], M[width], Y[width], K[width].
 */
public final class CmykRaster {
    private CmykRaster() {
    }

    public static byte[] toPng(int width, int height, byte[] cmykSamples) {
        if (cmykSamples.length != 4L * width * height) {
            throw new IllegalArgumentException("expected " + (4L * width * height)
                    + " CMYK bytes, got " + cmykSamples.length);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int rowBytes = width * 4;
        for (int y = 0; y < height; y++) {
            int rowBase = y * rowBytes;
            for (int x = 0; x < width; x++) {
                int r = cmykByteToChannel(cmykSamples[rowBase + x], cmykSamples[rowBase + 3 * width + x]);
                int g = cmykByteToChannel(cmykSamples[rowBase + width + x], cmykSamples[rowBase + 3 * width + x]);
                int b = cmykByteToChannel(cmykSamples[rowBase + 2 * width + x], cmykSamples[rowBase + 3 * width + x]);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            return png.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PNG encode failed", e);
        }
    }

    static int cmykByteToRgb(byte c, byte m, byte y, byte k) {
        int r = cmykByteToChannel(c, k);
        int g = cmykByteToChannel(m, k);
        int b = cmykByteToChannel(y, k);
        return (r << 16) | (g << 8) | b;
    }

    private static int cmykByteToChannel(byte inkByte, byte blackByte) {
        return toChannel((inkByte & 0xFF) / 255.0, (blackByte & 0xFF) / 255.0);
    }

    private static int toChannel(double ink, double black) {
        double value = (1.0 - ink) * (1.0 - black);
        return Math.max(0, Math.min(255, (int) Math.round(value * 255.0)));
    }
}
