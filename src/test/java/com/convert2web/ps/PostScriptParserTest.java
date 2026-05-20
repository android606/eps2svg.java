package com.convert2web.ps;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostScriptParserTest {

    @Test
    void parseScalars() throws Exception {
        List<PsValue> values = parseAll("10 -3 true false (hello) <4142> /Font moveto");

        assertEquals(new PsValue.IntegerValue(10), values.get(0));
        assertEquals(new PsValue.IntegerValue(-3), values.get(1));
        assertEquals(new PsValue.BooleanValue(true), values.get(2));
        assertEquals(new PsValue.BooleanValue(false), values.get(3));
        assertEquals(new PsValue.StringValue("hello"), values.get(4));
        assertEquals(new PsValue.HexStringValue("4142"), values.get(5));

        PsValue.NameValue literal = assertInstanceOf(PsValue.NameValue.class, values.get(6));
        assertTrue(literal.isLiteral());
        assertEquals("Font", literal.getName());

        PsValue.NameValue executable = assertInstanceOf(PsValue.NameValue.class, values.get(7));
        assertFalse(executable.isLiteral());
        assertEquals("moveto", executable.getName());
    }

    @Test
    void parseArray() throws Exception {
        PsValue.ArrayValue array = (PsValue.ArrayValue) parseOne("[ 1 2 add ]");

        assertEquals(3, array.getElements().size());
        assertEquals(new PsValue.IntegerValue(1), array.getElements().get(0));
        assertEquals(new PsValue.IntegerValue(2), array.getElements().get(1));
        assertEquals(PsValue.NameValue.executable("add"), array.getElements().get(2));
    }

    @Test
    void parseProcedure() throws Exception {
        PsValue.ProcedureValue proc = (PsValue.ProcedureValue) parseOne("{ 1 add }");

        assertEquals(2, proc.getBody().size());
        assertEquals(new PsValue.IntegerValue(1), proc.getBody().get(0));
        assertEquals(PsValue.NameValue.executable("add"), proc.getBody().get(1));
    }

    @Test
    void parseNestedProcedure() throws Exception {
        PsValue.ProcedureValue outer = (PsValue.ProcedureValue) parseOne("{ { 1 } }");
        PsValue.ProcedureValue inner = (PsValue.ProcedureValue) outer.getBody().get(0);

        assertEquals(1, inner.getBody().size());
        assertEquals(new PsValue.IntegerValue(1), inner.getBody().get(0));
    }

    @Test
    void parseDictionary() throws Exception {
        PsValue.DictionaryValue dict = (PsValue.DictionaryValue) parseOne("<< /a 1 /b 2.5 >>");

        assertEquals(2, dict.getEntries().size());
        assertEquals(new PsValue.IntegerValue(1), dict.getEntries().get("a"));
        assertEquals(new PsValue.RealValue(2.5), dict.getEntries().get("b"));
    }

    @Test
    void parseNestedLiteralStringInProcedure() throws Exception {
        PsValue.ProcedureValue proc = (PsValue.ProcedureValue) parseOne("{ (a (b) c) }");
        PsValue.StringValue str = (PsValue.StringValue) proc.getBody().get(0);

        assertEquals("a (b) c", str.getValue());
    }

    @Test
    void rejectsUnterminatedArray() {
        assertThrows(PostScriptParseException.class, () -> parseOne("[ 1 2"));
    }

    @Test
    void rejectsDictionaryWithNonNameKey() {
        assertThrows(PostScriptParseException.class, () -> parseOne("<< 1 2 >>"));
    }

    private static List<PsValue> parseAll(String input) throws Exception {
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(input))) {
            return new PostScriptParser().parseAll(lexer);
        }
    }

    private static PsValue parseOne(String input) throws Exception {
        List<PsValue> values = parseAll(input);
        assertEquals(1, values.size());
        return values.get(0);
    }
}
