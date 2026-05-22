package com.convert2web.ps;

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
        switch (token.getType()) {
            case INTEGER:
                return new PsValue.IntegerValue(parseIntegerText(token.getText()));
            case REAL:
                return new PsValue.RealValue(Double.parseDouble(token.getText()));
            case BOOLEAN:
                return new PsValue.BooleanValue(Boolean.parseBoolean(token.getText()));
            case STRING:
                return new PsValue.StringValue(token.getText());
            case HEX_STRING:
                return new PsValue.HexStringValue(token.getText());
            case LITERAL_NAME:
                return PsValue.NameValue.literal(token.getText());
            case NAME:
                return PsValue.NameValue.executable(token.getText());
            case ARRAY_START:
                return parseArray(lexer);
            case PROCEDURE_START:
                return parseProcedure(lexer);
            case DICTIONARY_START:
                return parseDictionary(lexer);
            case EOF:
                throw new PostScriptParseException("Unexpected end of input");
            default:
                throw new PostScriptParseException("Unexpected token: " + token.getType());
        }
    }

    private PsValue.ArrayValue parseArray(PostScriptLexer lexer) throws IOException {
        List<PsValue> elements = new ArrayList<>();
        collectUntilDelimiter(lexer, PostScriptTokenType.ARRAY_END, elements);
        return new PsValue.ArrayValue(elements);
    }

    private PsValue.ProcedureValue parseProcedure(PostScriptLexer lexer) throws IOException {
        List<PsValue> body = new ArrayList<>();
        collectUntilDelimiter(lexer, PostScriptTokenType.PROCEDURE_END, body);
        return new PsValue.ProcedureValue(body);
    }

    private PsValue.DictionaryValue parseDictionary(PostScriptLexer lexer) throws IOException {
        Map<String, PsValue> entries = new LinkedHashMap<>();
        while (true) {
            PostScriptToken token = lexer.nextToken();
            if (token.getType() == PostScriptTokenType.DICTIONARY_END) {
                return new PsValue.DictionaryValue(entries);
            }
            if (token.getType() == PostScriptTokenType.EOF) {
                throw new PostScriptParseException("Unterminated dictionary");
            }
            lexer.pushBack(token);
            PsValue keyValue = parseObject(lexer);
            if (!(keyValue instanceof PsValue.NameValue)) {
                throw new PostScriptParseException("Dictionary key must be a name, got " + keyValue.getKind());
            }
            PsValue.NameValue key = (PsValue.NameValue) keyValue;
            entries.put(key.getName(), parseObject(lexer));
        }
    }

    private void collectUntilDelimiter(
            PostScriptLexer lexer,
            PostScriptTokenType endType,
            List<PsValue> target) throws IOException {
        while (true) {
            PostScriptToken token = lexer.nextToken();
            if (token.getType() == PostScriptTokenType.EOF) {
                throw new PostScriptParseException("Unterminated composite object, expected " + endType);
            }
            if (token.getType() == endType) {
                return;
            }
            lexer.pushBack(token);
            target.add(parseObject(lexer));
        }
    }

    private static long parseIntegerText(String text) throws PostScriptParseException {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new PostScriptParseException("Invalid integer: " + text, e);
        }
    }
}
