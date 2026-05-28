package com.convert2web.ps;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Stream-oriented lexer for ASCII PostScript and EPS prolog/body text.
 */
public final class PostScriptLexer implements AutoCloseable {
    private final Reader reader;
    private int line = 1;
    private int column = 1;
    private int offset;
    private int current = -2;
    private boolean closed;
    private PostScriptToken pushedBack;

    public PostScriptLexer(Reader reader) {
        this.reader = reader;
    }

    public void pushBack(PostScriptToken token) {
        if (token == null) {
            throw new IllegalArgumentException("token");
        }
        if (pushedBack != null) {
            throw new IllegalStateException("Only one pushed-back token is supported");
        }
        pushedBack = token;
    }

    public List<PostScriptToken> tokenizeAll() throws IOException {
        List<PostScriptToken> tokens = new ArrayList<>();
        PostScriptToken token;
        while ((token = nextToken()).getType() != PostScriptTokenType.EOF) {
            tokens.add(token);
        }
        tokens.add(token);
        return tokens;
    }

    public PostScriptToken nextToken() throws IOException {
        if (pushedBack != null) {
            PostScriptToken token = pushedBack;
            pushedBack = null;
            return token;
        }

        skipWhitespaceAndComments();

        int ch = peek();
        if (ch == -1) {
            return new PostScriptToken(PostScriptTokenType.EOF, "", line, column, offset);
        }

        int startLine = line;
        int startColumn = column;
        int startOffset = offset;

        if (ch == '%') {
            PostScriptToken binary = tryReadBeginBinaryComment();
            if (binary != null) {
                return binary;
            }
            readPercentLine();
            return nextToken();
        }

        if (ch == '[') {
            read();
            return new PostScriptToken(PostScriptTokenType.ARRAY_START, "[", startLine, startColumn, startOffset);
        }
        if (ch == ']') {
            read();
            return new PostScriptToken(PostScriptTokenType.ARRAY_END, "]", startLine, startColumn, startOffset);
        }
        if (ch == '{') {
            read();
            return new PostScriptToken(PostScriptTokenType.PROCEDURE_START, "{", startLine, startColumn, startOffset);
        }
        if (ch == '}') {
            read();
            return new PostScriptToken(PostScriptTokenType.PROCEDURE_END, "}", startLine, startColumn, startOffset);
        }
        if (ch == '<') {
            read();
            if (peek() == '<') {
                read();
                return new PostScriptToken(PostScriptTokenType.DICTIONARY_START, "<<", startLine, startColumn, startOffset);
            }
            if (peek() == '~') {
                return readAscii85String(startLine, startColumn, startOffset);
            }
            return readHexString(startLine, startColumn, startOffset);
        }
        if (ch == '>') {
            read();
            if (peek() == '>') {
                read();
                return new PostScriptToken(PostScriptTokenType.DICTIONARY_END, ">>", startLine, startColumn, startOffset);
            }
            throw syntaxError("Unexpected '>'");
        }
        if (ch == '(') {
            return readLiteralString(startLine, startColumn, startOffset);
        }
        if (ch == '/') {
            read();
            if (peek() == '/') {
                read();
                while (true) {
                    int c = read();
                    if (c == -1 || c == '\n' || c == '\r') {
                        break;
                    }
                }
                return nextToken();
            }
            return new PostScriptToken(PostScriptTokenType.LITERAL_NAME, readName(), startLine, startColumn, startOffset);
        }
        if (isNumberStart(ch)) {
            return readNumber(startLine, startColumn, startOffset);
        }
        if (isNameStart(ch)) {
            return readNameToken(startLine, startColumn, startOffset);
        }

        throw syntaxError("Unexpected character: " + (char) ch);
    }

    private PostScriptToken readNameToken(int startLine, int startColumn, int startOffset) throws IOException {
        String name = readName();
        if ("true".equals(name) || "false".equals(name)) {
            return new PostScriptToken(PostScriptTokenType.BOOLEAN, name, startLine, startColumn, startOffset);
        }
        return new PostScriptToken(PostScriptTokenType.NAME, name, startLine, startColumn, startOffset);
    }

    private PostScriptToken readNumber(int startLine, int startColumn, int startOffset) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (peek() == '+' || peek() == '-') {
            sb.append((char) read());
        }
        while (isDigit(peek())) {
            sb.append((char) read());
        }
        boolean isReal = false;
        if (peek() == '.') {
            isReal = true;
            sb.append((char) read());
            while (isDigit(peek())) {
                sb.append((char) read());
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            isReal = true;
            sb.append((char) read());
            if (peek() == '+' || peek() == '-') {
                sb.append((char) read());
            }
            if (!isDigit(peek())) {
                throw syntaxError("Invalid exponent in number");
            }
            while (isDigit(peek())) {
                sb.append((char) read());
            }
        }
        PostScriptTokenType type = isReal ? PostScriptTokenType.REAL : PostScriptTokenType.INTEGER;
        return new PostScriptToken(type, sb.toString(), startLine, startColumn, startOffset);
    }

    private PostScriptToken readLiteralString(int startLine, int startColumn, int startOffset) throws IOException {
        read(); // '('
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            int ch = read();
            if (ch == -1) {
                throw syntaxError("Unterminated string");
            }
            if (ch == '(') {
                depth++;
                sb.append('(');
            } else if (ch == ')') {
                depth--;
                if (depth > 0) {
                    sb.append(')');
                }
            } else if (ch == '\\') {
                int esc = read();
                if (esc == -1) {
                    throw syntaxError("Unterminated escape in string");
                }
                switch (esc) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case '\\':
                    case '(':
                    case ')':
                        sb.append((char) esc);
                        break;
                    case '\r':
                        if (peek() == '\n') {
                            read();
                        }
                        break;
                    case '\n':
                        break;
                    default:
                        if (esc >= '0' && esc <= '7') {
                            int value = esc - '0';
                            for (int i = 0; i < 2 && peek() >= '0' && peek() <= '7'; i++) {
                                value = value * 8 + (read() - '0');
                            }
                            sb.append((char) value);
                        } else {
                            sb.append((char) esc);
                        }
                        break;
                }
            } else {
                sb.append((char) ch);
            }
        }
        return new PostScriptToken(PostScriptTokenType.STRING, sb.toString(), startLine, startColumn, startOffset);
    }

    private PostScriptToken readHexString(int startLine, int startColumn, int startOffset) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = peek();
            if (ch == -1 || ch == '>') {
                break;
            }
            if (isHexDigit(ch)) {
                sb.append((char) read());
            } else if (isWhitespace(ch)) {
                read();
            } else {
                throw syntaxError("Invalid character in hex string");
            }
        }
        if (peek() != '>') {
            throw syntaxError("Unterminated hex string");
        }
        read();
        return new PostScriptToken(PostScriptTokenType.HEX_STRING, sb.toString(), startLine, startColumn, startOffset);
    }

    private PostScriptToken readAscii85String(int startLine, int startColumn, int startOffset) throws IOException {
        read(); // ~
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = peek();
            if (ch == -1) {
                throw syntaxError("Unterminated ASCII85 string");
            }
            if (ch == '~') {
                read();
                if (peek() == '>') {
                    read();
                    break;
                }
                sb.append('~');
                continue;
            }
            sb.append((char) read());
        }
        return new PostScriptToken(PostScriptTokenType.STRING, sb.toString(), startLine, startColumn, startOffset);
    }

    private String readName() throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = peek();
            if (ch == -1 || isDelimiter(ch)) {
                break;
            }
            sb.append((char) read());
        }
        if (sb.length() == 0) {
            throw syntaxError("Empty name");
        }
        return sb.toString();
    }

    /**
     * Reads {@code %%BeginBinary: N} ... {@code %%EndBinary} as one executable AGM paint token.
     * A line starting with {@code %} that is not {@code %%BeginBinary} is consumed as a comment.
     */
    private PostScriptToken tryReadBeginBinaryComment() throws IOException {
        if (peek() != '%' || !reader.markSupported()) {
            return null;
        }
        reader.mark(8192);
        int savedLine = line;
        int savedColumn = column;
        int savedOffset = offset;
        String header = readPercentLine();
        reader.reset();
        line = savedLine;
        column = savedColumn;
        offset = savedOffset;
        current = -2;
        if (!header.trim().startsWith("%%BeginBinary")) {
            return null;
        }
        int startLine = line;
        int startColumn = column;
        int startOffset = offset;
        readPercentLine();
        String operator = readLineTrimmed();
        if (operator.isEmpty()) {
            throw syntaxError("Missing AGM operator after %%BeginBinary");
        }
        StringBuilder payload = new StringBuilder();
        while (true) {
            String dataLine = readLineRaw();
            if (dataLine == null) {
                throw syntaxError("Unterminated %%BeginBinary block");
            }
            if ("%%EndBinary".equals(dataLine.trim())) {
                break;
            }
            payload.append(dataLine);
        }
        return new PostScriptToken(
                PostScriptTokenType.BEGIN_BINARY_INVOKE,
                operator,
                payload.toString(),
                startLine,
                startColumn,
                startOffset);
    }

    private String readPercentLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append((char) read());
        while (peek() != -1 && peek() != '\n' && peek() != '\r') {
            sb.append((char) read());
        }
        skipLineBreak();
        return sb.toString();
    }

    private String readLineTrimmed() throws IOException {
        String line = readLineRaw();
        return line == null ? "" : line.trim();
    }

    private String readLineRaw() throws IOException {
        if (peek() == -1) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = peek();
            if (ch == -1) {
                break;
            }
            if (ch == '\r') {
                read();
                if (peek() == '\n') {
                    read();
                }
                break;
            }
            if (ch == '\n') {
                read();
                break;
            }
            sb.append((char) read());
        }
        return sb.toString();
    }

    private void skipLineBreak() throws IOException {
        if (peek() == '\r') {
            read();
        }
        if (peek() == '\n') {
            read();
        }
    }

    private void skipWhitespaceAndComments() throws IOException {
        while (true) {
            int ch = peek();
            if (ch == -1) {
                return;
            }
            if (isWhitespace(ch)) {
                read();
                continue;
            }
            // Leave '%' lines for {@link #nextToken()} (comments or {@code %%BeginBinary} blocks).
            if (ch == '%') {
                return;
            }
            if (ch == '/' && peekNext() == '/') {
                read();
                read();
                while (true) {
                    int c = read();
                    if (c == -1 || c == '\n' || c == '\r') {
                        break;
                    }
                }
                continue;
            }
            return;
        }
    }

    private int peek() throws IOException {
        if (current == -2) {
            current = reader.read();
        }
        return current;
    }

    private int peekNext() throws IOException {
        if (peek() == -1) {
            return -1;
        }
        if (!reader.markSupported()) {
            throw new IOException("Reader must support mark() for PostScriptLexer");
        }
        reader.mark(1);
        int second = reader.read();
        reader.reset();
        return second;
    }

    private int read() throws IOException {
        int ch = peek();
        if (ch == -1) {
            return -1;
        }
        current = -2;
        offset++;
        if (ch == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return ch;
    }

    private static boolean isWhitespace(int ch) {
        return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' || ch == '\f' || ch == '\0';
    }

    private static boolean isDelimiter(int ch) {
        return isWhitespace(ch) || ch == '(' || ch == ')' || ch == '<' || ch == '>'
                || ch == '[' || ch == ']' || ch == '{' || ch == '}' || ch == '/'
                || ch == '%';
    }

    private static boolean isDigit(int ch) {
        return ch >= '0' && ch <= '9';
    }

    private static boolean isHexDigit(int ch) {
        return isDigit(ch) || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    private static boolean isNumberStart(int ch) {
        return isDigit(ch) || ch == '+' || ch == '-' || ch == '.';
    }

    private static boolean isNameStart(int ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                || ch == '_' || ch == '$' || ch == '*' || ch == '\'' || ch == '@';
    }

    private IOException syntaxError(String message) {
        return new IOException(message + " at line " + line + ", column " + column);
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            reader.close();
            closed = true;
        }
    }
}
