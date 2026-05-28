package com.convert2web.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Location in the EPS/PostScript source where an operation was issued.
 * Line and column are 1-based; offset is a 0-based index from the start of the stream.
 */
public final class SourceSpan {
    private final int line;
    private final int column;
    private final int offset;

    public SourceSpan(int line, int column, int offset) {
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        if (column < 1) {
            throw new IllegalArgumentException("column must be >= 1");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    public static SourceSpan of(int line, int column, int offset) {
        return new SourceSpan(line, column, offset);
    }

    public static Optional<SourceSpan> optional(SourceSpan span) {
        return Optional.ofNullable(span);
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    public int offset() {
        return offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SourceSpan)) {
            return false;
        }
        SourceSpan that = (SourceSpan) o;
        return line == that.line && column == that.column && offset == that.offset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, column, offset);
    }

    /** {@code data-eps-line}, {@code data-eps-column}, {@code data-eps-offset} for SVG elements. */
    public String svgDataAttributes() {
        return " data-eps-line=\"" + line
                + "\" data-eps-column=\"" + column
                + "\" data-eps-offset=\"" + offset + "\"";
    }

    /**
     * XML comment placed immediately before a graphics-modifying SVG construct.
     *
     * @param kind operation label (e.g. {@code fill}, {@code clip}, {@code pop-clip})
     */
    public String svgSourceComment(String kind) {
        String safeKind = kind == null || kind.isEmpty() ? "op" : kind.replace("\"", "");
        return "<!-- eps-source kind=\"" + safeKind
                + "\" line=\"" + line
                + "\" column=\"" + column
                + "\" offset=\"" + offset + "\" -->\n";
    }

    @Override
    public String toString() {
        return line + ":" + column + "@" + offset;
    }
}
