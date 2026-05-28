package com.convert2web.ps;

import com.convert2web.model.SourceSpan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses PostScript tokens into {@link PsValue} objects.
 */
public final class PostScriptParser {
    public List<PsValue> parseAll(PostScriptLexer lexer) throws IOException {
        List<PsValue> values = new ArrayList<>();
        while (true) {
            PostScriptToken token = lexer.nextToken();
            if (token.getType() == PostScriptTokenType.EOF) {
                break;
            }
            lexer.pushBack(token);
            values.add(parseObject(lexer));
        }
        return values;
    }

    public PsValue parseObject(PostScriptLexer lexer) throws IOException {
        PostScriptToken token = lexer.nextToken();
        if (token.getType() == PostScriptTokenType.BEGIN_BINARY_INVOKE) {
            return new PsValue.AgmBinaryInvokeValue(
                    token.getText(), token.getBinaryPayload(), span(token));
        }
        switch (token.getType()) {
            case INTEGER:
                return new PsValue.IntegerValue(parseIntegerText(token.getText()), span(token));
            case REAL:
                return new PsValue.RealValue(Double.parseDouble(token.getText()), span(token));
            case BOOLEAN:
                return new PsValue.BooleanValue(Boolean.parseBoolean(token.getText()), span(token));
            case STRING:
                return new PsValue.StringValue(token.getText(), span(token));
            case HEX_STRING:
                return new PsValue.HexStringValue(token.getText(), span(token));
            case LITERAL_NAME:
                return PsValue.NameValue.literal(token.getText(), span(token));
            case NAME:
                return PsValue.NameValue.executable(token.getText(), span(token));
            case ARRAY_START:
                return parseArray(lexer, token);
            case PROCEDURE_START:
                return parseProcedure(lexer, token);
            case DICTIONARY_START:
                return parseDictionary(lexer, token);
            case EOF:
                throw new PostScriptParseException("Unexpected end of input");
            default:
                throw new PostScriptParseException("Unexpected token: " + token.getType());
        }
    }

    private PsValue.ArrayValue parseArray(PostScriptLexer lexer, PostScriptToken start) throws IOException {
        List<PsValue> elements = new ArrayList<>();
        collectUntilDelimiter(lexer, PostScriptTokenType.ARRAY_END, elements);
        return new PsValue.ArrayValue(elements, span(start));
    }

    private PsValue.ProcedureValue parseProcedure(PostScriptLexer lexer, PostScriptToken start) throws IOException {
        List<PsValue> body = new ArrayList<>();
        collectUntilDelimiter(lexer, PostScriptTokenType.PROCEDURE_END, body);
        return new PsValue.ProcedureValue(body, span(start));
    }

    private PsValue.DictionaryValue parseDictionary(PostScriptLexer lexer, PostScriptToken start) throws IOException {
        Map<String, PsValue> entries = new LinkedHashMap<>();
        while (true) {
            PostScriptToken token = lexer.nextToken();
            if (token.getType() == PostScriptTokenType.DICTIONARY_END) {
                return new PsValue.DictionaryValue(entries, span(start));
            }
            if (token.getType() == PostScriptTokenType.EOF) {
                throw new PostScriptParseException("Unterminated dictionary");
            }
            if (token.getType() != PostScriptTokenType.LITERAL_NAME) {
                throw new PostScriptParseException("Dictionary key must be a literal name");
            }
            String key = token.getText();
            entries.put(key, parseObject(lexer));
        }
    }

    private void collectUntilDelimiter(
            PostScriptLexer lexer,
            PostScriptTokenType endType,
            List<PsValue> target) throws IOException {
        while (true) {
            PostScriptToken token = lexer.nextToken();
            if (token.getType() == endType) {
                return;
            }
            if (token.getType() == PostScriptTokenType.EOF) {
                throw new PostScriptParseException("Unterminated composite object");
            }
            lexer.pushBack(token);
            target.add(parseObject(lexer));
        }
    }

    private static SourceSpan span(PostScriptToken token) {
        return SourceSpan.of(token.getLine(), token.getColumn(), token.getOffset());
    }

    private static long parseIntegerText(String text) throws PostScriptParseException {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new PostScriptParseException("Invalid integer: " + text, ex);
        }
    }
}
