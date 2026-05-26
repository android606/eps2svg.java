package com.convert2web.image;

import com.convert2web.model.GraphicsCommand;
import com.convert2web.model.Matrix;

/**
 * One decoded AGM {@code %%BeginBinary} CMYK image from an Illustrator page body.
 */
public final class AgmEmbeddedImage {
    private final int width;
    private final int height;
    private final Matrix ctm;
    private final byte[] pngBytes;

    public AgmEmbeddedImage(int width, int height, Matrix ctm, byte[] pngBytes) {
        this.width = width;
        this.height = height;
        this.ctm = ctm == null ? Matrix.identity() : ctm;
        this.pngBytes = pngBytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Matrix getCtm() {
        return ctm;
    }

    public byte[] getPngBytes() {
        return pngBytes;
    }

    public GraphicsCommand.EmbeddedImage toCommand() {
        return new GraphicsCommand.EmbeddedImage(width, height, ctm, pngBytes);
    }
}
