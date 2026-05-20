package com.convert2web.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable path built from PostScript path construction operators.
 */
public final class Path {
    private final List<PathSegment> segments;

    public Path(List<PathSegment> segments) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }

    public List<PathSegment> getSegments() {
        return segments;
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }
}
