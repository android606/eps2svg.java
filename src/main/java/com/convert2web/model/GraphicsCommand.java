package com.convert2web.model;

/**
 * One drawing operation recorded in an {@link EpsDocument}.
 */
public abstract class GraphicsCommand {
    public enum Kind {
        FILL,
        STROKE,
        CLIP,
        RASTER_PLACEHOLDER
    }

    private final Matrix ctm;

    protected GraphicsCommand(Matrix ctm) {
        this.ctm = ctm == null ? Matrix.identity() : ctm;
    }

    public Matrix getCtm() {
        return ctm;
    }

    public abstract Kind getKind();

    public static final class Fill extends GraphicsCommand {
        private final Path path;
        private final PaintStyle fill;
        private final WindingRule windingRule;

        public Fill(Path path, PaintStyle fill, WindingRule windingRule, Matrix ctm) {
            super(ctm);
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
            super(ctm);
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
            super(ctm);
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
            super(ctm);
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
}
