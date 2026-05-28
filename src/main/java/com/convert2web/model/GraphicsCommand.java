package com.convert2web.model;

import java.util.List;
import java.util.Optional;

/**
 * One drawing operation recorded in an {@link EpsDocument}.
 */
public abstract class GraphicsCommand {
    public enum Kind {
        FILL,
        STROKE,
        CLIP,
        POP_CLIP,
        EMBEDDED_IMAGE,
        TEXT,
        RASTER_PLACEHOLDER,
        BEGIN_LAYER_GROUP,
        END_LAYER_GROUP
    }

    private final Matrix ctm;
    private final SourceSpan sourceSpan;

    protected GraphicsCommand(Matrix ctm) {
        this(ctm, null);
    }

    protected GraphicsCommand(Matrix ctm, SourceSpan sourceSpan) {
        this.ctm = ctm == null ? Matrix.identity() : ctm;
        this.sourceSpan = sourceSpan;
    }

    public Matrix getCtm() {
        return ctm;
    }

    /** EPS source location of the PostScript operator that recorded this command, if known. */
    public Optional<SourceSpan> getSourceSpan() {
        return Optional.ofNullable(sourceSpan);
    }

    public abstract Kind getKind();

    public static final class Fill extends GraphicsCommand {
        private final Path path;
        private final PaintStyle fill;
        private final WindingRule windingRule;

        public Fill(Path path, PaintStyle fill, WindingRule windingRule, Matrix ctm) {
            this(path, fill, windingRule, ctm, null);
        }

        public Fill(Path path, PaintStyle fill, WindingRule windingRule, Matrix ctm, SourceSpan sourceSpan) {
            super(ctm, sourceSpan);
            this.path = path;
            this.fill = fill;
            this.windingRule = windingRule;
        }

        public Path getPath() {
            return path;
        }

        public PaintStyle getFill() {
            return fill;
        }

        public WindingRule getWindingRule() {
            return windingRule;
        }

        @Override
        public Kind getKind() {
            return Kind.FILL;
        }
    }

    public static final class Stroke extends GraphicsCommand {
        private final Path path;
        private final PaintStyle strokeColor;
        private final StrokeStyle strokeStyle;

        public Stroke(Path path, PaintStyle strokeColor, StrokeStyle strokeStyle, Matrix ctm) {
            this(path, strokeColor, strokeStyle, ctm, null);
        }

        public Stroke(Path path, PaintStyle strokeColor, StrokeStyle strokeStyle, Matrix ctm, SourceSpan sourceSpan) {
            super(ctm, sourceSpan);
            this.path = path;
            this.strokeColor = strokeColor;
            this.strokeStyle = strokeStyle;
        }

        public Path getPath() {
            return path;
        }

        public PaintStyle getStrokeColor() {
            return strokeColor;
        }

        public StrokeStyle getStrokeStyle() {
            return strokeStyle;
        }

        @Override
        public Kind getKind() {
            return Kind.STROKE;
        }
    }

    /**
     * Marks a region where Illustrator pattern paint used an embedded raster tile.
     */
    public static final class RasterPlaceholder extends GraphicsCommand {
        private final BoundingBox region;

        public RasterPlaceholder(BoundingBox region, Matrix ctm) {
            this(region, ctm, null);
        }

        public RasterPlaceholder(BoundingBox region, Matrix ctm, SourceSpan sourceSpan) {
            super(ctm, sourceSpan);
            this.region = region;
        }

        public BoundingBox getRegion() {
            return region;
        }

        @Override
        public Kind getKind() {
            return Kind.RASTER_PLACEHOLDER;
        }
    }

    public static final class Clip extends GraphicsCommand {
        private final Path path;
        private final WindingRule windingRule;

        public Clip(Path path, WindingRule windingRule, Matrix ctm) {
            this(path, windingRule, ctm, null);
        }

        public Clip(Path path, WindingRule windingRule, Matrix ctm, SourceSpan sourceSpan) {
            super(ctm, sourceSpan);
            this.path = path;
            this.windingRule = windingRule;
        }

        public Path getPath() {
            return path;
        }

        public WindingRule getWindingRule() {
            return windingRule;
        }

        @Override
        public Kind getKind() {
            return Kind.CLIP;
        }
    }

    /** Closes one SVG clip group opened by a prior {@link Clip} command. */
    public static final class PopClip extends GraphicsCommand {
        public PopClip() {
            this(null);
        }

        public PopClip(SourceSpan sourceSpan) {
            super(Matrix.identity(), sourceSpan);
        }

        @Override
        public Kind getKind() {
            return Kind.POP_CLIP;
        }
    }

    /** Opens an SVG {@code <g id="...">} for Illustrator layer debugging. */
    public static final class BeginLayerGroup extends GraphicsCommand {
        private final String layerId;

        public BeginLayerGroup(String layerId) {
            this(layerId, null);
        }

        public BeginLayerGroup(String layerId, SourceSpan sourceSpan) {
            super(Matrix.identity(), sourceSpan);
            this.layerId = layerId == null ? "" : layerId;
        }

        public String getLayerId() {
            return layerId;
        }

        @Override
        public Kind getKind() {
            return Kind.BEGIN_LAYER_GROUP;
        }
    }

    /** Closes the innermost layer group opened by {@link BeginLayerGroup}. */
    public static final class EndLayerGroup extends GraphicsCommand {
        public EndLayerGroup() {
            this(null);
        }

        public EndLayerGroup(SourceSpan sourceSpan) {
            super(Matrix.identity(), sourceSpan);
        }

        @Override
        public Kind getKind() {
            return Kind.END_LAYER_GROUP;
        }
    }

    /** Live text recorded from {@code show} / Illustrator {@code sh} / {@code xsh}. */
    public static final class Text extends GraphicsCommand {
        private final String text;
        private final double x;
        private final double y;
        private final String fontName;
        private final double fontSize;
        private final PaintStyle fill;
        private final double[] glyphAdvances;
        private final List<Clip> clipStack;

        public Text(
                String text,
                double x,
                double y,
                String fontName,
                double fontSize,
                PaintStyle fill,
                Matrix ctm) {
            this(text, x, y, fontName, fontSize, fill, ctm, null, null);
        }

        public Text(
                String text,
                double x,
                double y,
                String fontName,
                double fontSize,
                PaintStyle fill,
                Matrix ctm,
                double[] glyphAdvances) {
            this(text, x, y, fontName, fontSize, fill, ctm, glyphAdvances, null);
        }

        public Text(
                String text,
                double x,
                double y,
                String fontName,
                double fontSize,
                PaintStyle fill,
                Matrix ctm,
                double[] glyphAdvances,
                SourceSpan sourceSpan) {
            this(text, x, y, fontName, fontSize, fill, ctm, glyphAdvances, List.of(), sourceSpan);
        }

        public Text(
                String text,
                double x,
                double y,
                String fontName,
                double fontSize,
                PaintStyle fill,
                Matrix ctm,
                double[] glyphAdvances,
                List<Clip> clipStack,
                SourceSpan sourceSpan) {
            super(ctm, sourceSpan);
            this.text = text == null ? "" : text;
            this.x = x;
            this.y = y;
            this.fontName = fontName == null || fontName.isEmpty() ? "Helvetica" : fontName;
            this.fontSize = fontSize;
            this.fill = fill == null ? PaintStyle.gray(0) : fill;
            this.glyphAdvances = glyphAdvances == null || glyphAdvances.length == 0
                    ? null
                    : glyphAdvances.clone();
            this.clipStack = clipStack == null || clipStack.isEmpty()
                    ? List.of()
                    : List.copyOf(clipStack);
        }

        public String getText() {
            return text;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public String getFontName() {
            return fontName;
        }

        public double getFontSize() {
            return fontSize;
        }

        public PaintStyle getFill() {
            return fill;
        }

        /** Per-glyph x displacements from Illustrator {@code xsh} (replace default width), or null. */
        public double[] getGlyphAdvances() {
            return glyphAdvances == null ? null : glyphAdvances.clone();
        }

        public boolean hasGlyphAdvances() {
            return glyphAdvances != null && glyphAdvances.length > 0;
        }

        public List<Clip> getClipStack() {
            return clipStack;
        }

        @Override
        public Kind getKind() {
            return Kind.TEXT;
        }
    }

    /** Decoded raster image embedded in Illustrator EPS (AGM binary tile). */
    public static final class EmbeddedImage extends GraphicsCommand {
        private final int width;
        private final int height;
        private final byte[] pngBytes;
        private final List<Clip> clipStack;

        public EmbeddedImage(int width, int height, Matrix ctm, byte[] pngBytes) {
            this(width, height, ctm, pngBytes, List.of(), null);
        }

        public EmbeddedImage(
                int width,
                int height,
                Matrix ctm,
                byte[] pngBytes,
                List<Clip> clipStack) {
            this(width, height, ctm, pngBytes, clipStack, null);
        }

        public EmbeddedImage(
                int width,
                int height,
                Matrix ctm,
                byte[] pngBytes,
                List<Clip> clipStack,
                SourceSpan sourceSpan) {
            super(ctm, sourceSpan);
            this.width = width;
            this.height = height;
            this.pngBytes = pngBytes == null ? new byte[0] : pngBytes.clone();
            this.clipStack = clipStack == null || clipStack.isEmpty()
                    ? List.of()
                    : List.copyOf(clipStack);
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public byte[] getPngBytes() {
            return pngBytes.clone();
        }

        /** Clips active when the tile was painted ({@code gsave}/{@code clp} before {@code sepimg}). */
        public List<Clip> getClipStack() {
            return clipStack;
        }

        @Override
        public Kind getKind() {
            return Kind.EMBEDDED_IMAGE;
        }
    }
}
