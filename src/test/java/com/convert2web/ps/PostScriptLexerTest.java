package com.convert2web.ps;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostScriptLexerTest {

    @Test
    void tokenizesNumbersNamesAndOperators() throws Exception {
        List<PostScriptToken> tokens = lex("10 -3.5 1e2 moveto /Font");

        assertEquals(List.of(
                "INTEGER(10)",
                "REAL(-3.5)",
                "REAL(1e2)",
                "NAME(moveto)",
                "LITERAL_NAME(Font)"),
                stringify(tokens.subList(0, 5)));
    }

    @Test
    void tokenizesNestedLiteralString() throws Exception {
        List<PostScriptToken> tokens = lex("(hello (nested) world)");

        assertEquals(1, tokens.size());
        assertEquals(PostScriptTokenType.STRING, tokens.get(0).getType());
        assertEquals("hello (nested) world", tokens.get(0).getText());
    }

    @Test
    void tokenizesEscapedString() throws Exception {
        List<PostScriptToken> tokens = lex("(line\\ncont)");

        assertEquals("line\ncont", tokens.get(0).getText());
    }

    @Test
    void tokenizesAscii85String() throws Exception {
        List<PostScriptToken> tokens = lex("<~Artifex~> 10");

        assertEquals(PostScriptTokenType.STRING, tokens.get(0).getType());
        assertEquals("Artifex", tokens.get(0).getText());
        assertEquals(PostScriptTokenType.INTEGER, tokens.get(1).getType());
    }

    @Test
    void tokenizesHexStringAndArrays() throws Exception {
        List<PostScriptToken> tokens = lex("<4142> [ 1 2 ]");

        assertEquals(PostScriptTokenType.HEX_STRING, tokens.get(0).getType());
        assertEquals("4142", tokens.get(0).getText());
        assertEquals(PostScriptTokenType.ARRAY_START, tokens.get(1).getType());
        assertEquals(PostScriptTokenType.INTEGER, tokens.get(2).getType());
        assertEquals(PostScriptTokenType.ARRAY_END, tokens.get(4).getType());
    }

    @Test
    void tokenizesProceduresAndDictionaries() throws Exception {
        List<PostScriptToken> tokens = lex("{ 1 add } << /a 1 >>");

        assertEquals(PostScriptTokenType.PROCEDURE_START, tokens.get(0).getType());
        assertEquals(PostScriptTokenType.PROCEDURE_END, tokens.get(3).getType());
        assertEquals(PostScriptTokenType.DICTIONARY_START, tokens.get(4).getType());
        assertEquals(PostScriptTokenType.DICTIONARY_END, tokens.get(7).getType());
    }

    @Test
    void tokenizesBeginBinaryBlockAsSingleInvoke() throws Exception {
        String input = "10\n"
                + "%%BeginBinary: 1\n"
                + "sepimg\n"
                + "JcLB&\n"
                + "%%EndBinary\n"
                + "20\n";
        List<PostScriptToken> tokens = lex(input);
        assertEquals(3, tokens.size());
        assertEquals(PostScriptTokenType.INTEGER, tokens.get(0).getType());
        assertEquals(PostScriptTokenType.BEGIN_BINARY_INVOKE, tokens.get(1).getType());
        assertEquals("sepimg", tokens.get(1).getText());
        assertEquals("JcLB&", tokens.get(1).getBinaryPayload());
        assertEquals(PostScriptTokenType.INTEGER, tokens.get(2).getType());
    }

    @Test
    void skipsComments() throws Exception {
        List<PostScriptToken> tokens = lex("10 % comment\n20");

        assertEquals(2, tokens.size());
        assertEquals("10", tokens.get(0).getText());
        assertEquals("20", tokens.get(1).getText());
    }

    @Test
    void tokenizesArrayWithLiteralName() throws Exception {
        List<PostScriptToken> tokens = lex("[/DeviceCMYK]");

        assertEquals(PostScriptTokenType.ARRAY_START, tokens.get(0).getType());
        assertEquals(PostScriptTokenType.LITERAL_NAME, tokens.get(1).getType());
        assertEquals("DeviceCMYK", tokens.get(1).getText());
        assertEquals(PostScriptTokenType.ARRAY_END, tokens.get(2).getType());
    }

    @Test
    void skipsAdobeDoubleSlashComments() throws Exception {
        List<PostScriptToken> tokens = lex("[ //comment\n1 ]");

        assertEquals(PostScriptTokenType.ARRAY_START, tokens.get(0).getType());
        assertEquals(PostScriptTokenType.INTEGER, tokens.get(1).getType());
        assertEquals(PostScriptTokenType.ARRAY_END, tokens.get(2).getType());
    }

    @Test
    void tokenizesBooleans() throws Exception {
        List<PostScriptToken> tokens = lex("true false");

        assertEquals(PostScriptTokenType.BOOLEAN, tokens.get(0).getType());
        assertEquals(PostScriptTokenType.BOOLEAN, tokens.get(1).getType());
    }

    @Test
    void rejectsUnterminatedString() {
        assertThrows(IOException.class, () -> lex("(unterminated"));
    }

    @Test
    void tokenizesIllustratorAtOperator() throws Exception {
        List<PostScriptToken> tokens = lex("cp\n@\n0 0 mo");
        assertEquals(List.of(
                "NAME(cp)",
                "NAME(@)",
                "INTEGER(0)",
                "INTEGER(0)",
                "NAME(mo)"),
                stringify(tokens));
    }

    private static List<PostScriptToken> lex(String input) throws Exception {
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(input))) {
            return lexer.tokenizeAll().stream()
                    .filter(t -> t.getType() != PostScriptTokenType.EOF)
                    .collect(Collectors.toList());
        }
    }

    private static List<String> stringify(List<PostScriptToken> tokens) {
        return tokens.stream()
                .map(t -> t.getType() + "(" + t.getText() + ")")
                .collect(Collectors.toList());
    }
}
