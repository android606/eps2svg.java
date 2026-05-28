package com.convert2web.ps;

import java.util.Objects;

public final class PostScriptToken {
    private final PostScriptTokenType type;
    private final String text;
    private final String binaryPayload;
    private final int line;
    private final int column;
    private final int offset;

    public PostScriptToken(PostScriptTokenType type, String text, int line, int column, int offset) {
        this(type, text, null, line, column, offset);
    }

    public PostScriptToken(
            PostScriptTokenType type,
            String agmOperator,
            String binaryPayload,
            int line,
            int column,
            int offset) {
        this.type = type;
        this.text = agmOperator;
        this.binaryPayload = binaryPayload;
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    /** Payload between {@code %%BeginBinary} operator line and {@code %%EndBinary}. */
    public String getBinaryPayload() {
        return binaryPayload;
    }

    public PostScriptTokenType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    /** 0-based character index from the start of the PostScript stream. */
    public int getOffset() {
        return offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PostScriptToken)) {
            return false;
        }
        PostScriptToken that = (PostScriptToken) o;
        return type == that.type && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, text);
    }

    @Override
    public String toString() {
        return type + "(" + text + ")";
    }
}
