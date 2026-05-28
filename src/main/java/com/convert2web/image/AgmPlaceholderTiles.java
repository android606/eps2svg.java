package com.convert2web.image;

import com.convert2web.model.Matrix;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Detects Illustrator sepcs/AGM tiles that decode to blank white rectangles.
 * These are separation placeholders, not visible artwork in SVG.
 */
public final class AgmPlaceholderTiles {
    /** Max displayed area (pt^2) for a tile to be treated as a blank legend placeholder. */
    private static final double MAX_PLACEHOLDER_AREA = 2_500.0;
    private static final int MIN_WHITE_PERCENT = 92;

    private AgmPlaceholderTiles() {
    }

    public static boolean isBlankSepcsPlaceholder(
            byte[] pngBytes, int width, int height, Matrix ctm) {
        if (pngBytes == null || pngBytes.length == 0 || width <= 0 || height <= 0) {
            return true;
        }
        if (AgmImageBounds.displayedArea(new AgmEmbeddedImage(width, height, ctm, pngBytes))
                > MAX_PLACEHOLDER_AREA) {
            return false;
        }
        return isPredominantlyWhitePng(pngBytes);
    }

    private static boolean isPredominantlyWhitePng(byte[] pngBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (image == null) {
                return false;
            }
            int w = image.getWidth();
            int h = image.getHeight();
            if (w <= 0 || h <= 0) {
                return false;
            }
            int white = 0;
            int total = w * h;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = image.getRGB(x, y) & 0xFFFFFF;
                    if (rgb >= 0xF0F0F0) {
                        white++;
                    }
                }
            }
            return white * 100 >= MIN_WHITE_PERCENT * total;
        } catch (IOException e) {
            return false;
        }
    }
}
