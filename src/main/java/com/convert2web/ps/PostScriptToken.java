package com.convert2web.ps;

import java.util.Objects;

public final class PostScriptToken {
    private final PostScriptTokenType type;
    private final String text;
    private final int line;
    private final int column;

    public PostScriptToken(PostScriptTokenType type, String text, int line, int column) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.column = column;
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
