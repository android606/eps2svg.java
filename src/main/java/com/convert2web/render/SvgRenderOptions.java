package com.convert2web.render;

import com.convert2web.model.Length;

import java.util.Optional;

/**
 * Optional min/max display size for root {@code width} and {@code height} attributes.
 * {@code viewBox} is not modified; scaling is always proportional.
 */
public final class SvgRenderOptions {
    private final Length minWidth;
    private final Length minHeight;
    private final Length maxWidth;
    private final Length maxHeight;
    private final boolean emitSourceTrace;

    private SvgRenderOptions(
            Length minWidth,
            Length minHeight,
            Length maxWidth,
            Length maxHeight,
            boolean emitSourceTrace) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.emitSourceTrace = emitSourceTrace;
    }

    public static SvgRenderOptions none() {
        return new SvgRenderOptions(null, null, null, null, false);
    }

    /** Defaults for CLI tests and shell scripts: min 100px, max US letter size. */
    public static SvgRenderOptions forTests() {
        return builder()
                .minWidth("100")
                .minHeight("100")
                .maxWidth("8.5in")
                .maxHeight("11in")
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Length> minWidth() {
        return Optional.ofNullable(minWidth);
    }

    public Optional<Length> minHeight() {
        return Optional.ofNullable(minHeight);
    }

    public Optional<Length> maxWidth() {
        return Optional.ofNullable(maxWidth);
    }

    public Optional<Length> maxHeight() {
        return Optional.ofNullable(maxHeight);
    }

    public boolean hasDisplayLimits() {
        return minWidth != null || minHeight != null || maxWidth != null || maxHeight != null;
    }

    /** When true, SVG elements include {@code data-eps-line}, {@code data-eps-column}, {@code data-eps-offset}. */
    public boolean emitSourceTrace() {
        return emitSourceTrace;
    }

    public static final class Builder {
        private Length minWidth;
        private Length minHeight;
        private Length maxWidth;
        private Length maxHeight;
        private boolean emitSourceTrace;

        public Builder minWidth(String length) {
            this.minWidth = length == null ? null : Length.parse(length);
            return this;
        }

        public Builder minHeight(String length) {
            this.minHeight = length == null ? null : Length.parse(length);
            return this;
        }

        public Builder maxWidth(String length) {
            this.maxWidth = length == null ? null : Length.parse(length);
            return this;
        }

        public Builder maxHeight(String length) {
            this.maxHeight = length == null ? null : Length.parse(length);
            return this;
        }

        public Builder emitSourceTrace(boolean emitSourceTrace) {
            this.emitSourceTrace = emitSourceTrace;
            return this;
        }

        public SvgRenderOptions build() {
            return new SvgRenderOptions(minWidth, minHeight, maxWidth, maxHeight, emitSourceTrace);
        }
    }
}
