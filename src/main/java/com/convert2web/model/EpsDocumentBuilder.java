package com.convert2web.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an {@link EpsDocument} as the interpreter records drawing operations.
 */
public final class EpsDocumentBuilder {
    private BoundingBox boundingBox;
    private BoundingBox hiResBoundingBox;
    private final List<GraphicsCommand> commands = new ArrayList<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();

    public EpsDocumentBuilder setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
        return this;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public EpsDocumentBuilder setHiResBoundingBox(BoundingBox hiResBoundingBox) {
        this.hiResBoundingBox = hiResBoundingBox;
        return this;
    }

    public EpsDocumentBuilder putMetadata(String key, String value) {
        metadata.put(key, value);
        return this;
    }

    public EpsDocumentBuilder addFill(Path path, PaintStyle fill, WindingRule windingRule, Matrix ctm) {
        commands.add(new GraphicsCommand.Fill(path, fill, windingRule, ctm));
        return this;
    }

    /** Inserts a fill so it paints behind subsequently recorded artwork. */
    public EpsDocumentBuilder addFillAtFront(Path path, PaintStyle fill, WindingRule windingRule, Matrix ctm) {
        commands.add(0, new GraphicsCommand.Fill(path, fill, windingRule, ctm));
        return this;
    }

    public EpsDocumentBuilder addStroke(Path path, PaintStyle strokeColor, StrokeStyle strokeStyle, Matrix ctm) {
        commands.add(new GraphicsCommand.Stroke(path, strokeColor, strokeStyle, ctm));
        return this;
    }

    public EpsDocumentBuilder addClip(Path path, WindingRule windingRule, Matrix ctm) {
        commands.add(new GraphicsCommand.Clip(path, windingRule, ctm));
        return this;
    }

    public EpsDocumentBuilder addRasterPlaceholder(BoundingBox region, Matrix ctm) {
        commands.add(new GraphicsCommand.RasterPlaceholder(region, ctm));
        return this;
    }

    public EpsDocument build() {
        if (boundingBox == null) {
            boundingBox = new BoundingBox(0, 0, 100, 100);
        }
        return new EpsDocument(boundingBox, hiResBoundingBox, commands, metadata);
    }
}
