package com.convert2web.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Renderer-neutral result of interpreting an EPS file.
 */
public final class EpsDocument {
    private final BoundingBox boundingBox;
    private final BoundingBox hiResBoundingBox;
    private final List<GraphicsCommand> commands;
    private final Map<String, String> metadata;

    public EpsDocument(
            BoundingBox boundingBox,
            BoundingBox hiResBoundingBox,
            List<GraphicsCommand> commands,
            Map<String, String> metadata) {
        this.boundingBox = Objects.requireNonNull(boundingBox, "boundingBox");
        this.hiResBoundingBox = hiResBoundingBox;
        this.commands = List.copyOf(commands);
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public BoundingBox getHiResBoundingBox() {
        return hiResBoundingBox;
    }

    public List<GraphicsCommand> getCommands() {
        return commands;
    }

    public Map<String, String> metadata() {
        return metadata;
    }
}
