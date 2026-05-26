package com.convert2web.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Single-plane separation samples (K or gray) to PNG. */
public final class GrayscaleRaster {
    private GrayscaleRaster() {
    }

    public static byte[] toPng(int width, int height, byte[] samples) {
        if (samples.length < width * height) {
            throw new IllegalArgumentException(
                    "sample length " + samples.length + " < " + width * height);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            int rowBase = y * width;
            for (int x = 0; x < width; x++) {
                int k = samples[rowBase + x] & 0xFF;
                int gray = 255 - k;
                image.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
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
