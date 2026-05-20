package com.convert2web.ps;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostScriptVmTest {

    @Test
    void addPopsTwoOperandsAndPushesSum() {
        PostScriptVm vm = run("1 2 add");
        assertEquals(1, vm.operandCount());
        assertEquals(new PsValue.IntegerValue(3), vm.peek());
    }

    @Test
    void dupCopiesTopOfStack() {
        PostScriptVm vm = run("5 dup");
        assertEquals(2, vm.operandCount());
        assertEquals(new PsValue.IntegerValue(5), vm.peek());
    }

    @Test
    void exchSwapsTopTwoElements() {
        PostScriptVm vm = run("1 2 exch");
        assertEquals(new PsValue.IntegerValue(1), vm.peek());
        vm.pop();
        assertEquals(new PsValue.IntegerValue(2), vm.peek());
    }

    @Test
    void execRunsProcedureBody() {
        PostScriptVm vm = run("{ 1 2 add } exec");
        assertEquals(new PsValue.IntegerValue(3), vm.peek());
    }

    @Test
    void ifExecutesProcedureWhenTrue() {
        PostScriptVm vm = run("true { 7 } if");
        assertEquals(new PsValue.IntegerValue(7), vm.peek());
    }

    @Test
    void ifSkipsProcedureWhenFalse() {
        PostScriptVm vm = run("false { 7 } if");
        assertEquals(0, vm.operandCount());
    }

    @Test
    void ifelseChoosesBranch() {
        PostScriptVm vm = run("false { 1 } { 2 } ifelse");
        assertEquals(new PsValue.IntegerValue(2), vm.peek());
    }

    @Test
    void defAndLoadStoreValuesInUserDict() {
        PostScriptVm vm = run("/x 9 def /x load");
        assertEquals(new PsValue.IntegerValue(9), vm.peek());
    }

    @Test
    void repeatExecutesProcedureCountTimes() {
        PostScriptVm vm = run("0 { dup 1 add } 3 repeat");
        assertEquals(new PsValue.IntegerValue(3), vm.peek());
    }

    @Test
    void dictBeginEndScopesDefinitions() {
        PostScriptVm vm = run("10 dict begin /a 1 def end");
        assertEquals(0, vm.operandCount());
        PostScriptVm vm2 = run("10 dict begin /a 1 def currentdict end");
        PsValue.RuntimeDictionaryValue dict =
                assertInstanceOf(PsValue.RuntimeDictionaryValue.class, vm2.peek());
        assertEquals(new PsValue.IntegerValue(1), dict.getDictionary().get("a"));
    }

    @Test
    void gsaveAndGrestoreSnapshotGraphicsState() {
        PostScriptVm vm = new PostScriptVm();
        run(vm, "gsave");
        vm.getGraphicsState().setLineWidth(5.0);
        run(vm, "grestore");
        assertEquals(1.0, vm.getGraphicsState().getLineWidth(), 1e-9);
    }

    @Test
    void stackUnderflowThrows() throws Exception {
        PostScriptVm vm = new PostScriptVm();
        List<PsValue> program = parse("pop");
        assertThrows(PostScriptVmException.class, () -> vm.executeAll(program));
    }

    @Test
    void parsesAndRunsEpsStyleSnippet() throws Exception {
        String source = "1 2 add 3 eq { (ok) } { (no) } ifelse";
        PostScriptVm vm = run(source);
        assertEquals(PsValue.StringValue.class, vm.peek().getClass());
        assertEquals("ok", ((PsValue.StringValue) vm.peek()).getValue());
    }

    private static PostScriptVm run(String source) {
        PostScriptVm vm = new PostScriptVm();
        run(vm, source);
        return vm;
    }

    private static void run(PostScriptVm vm, String source) {
        try {
            vm.executeAll(parse(source));
        } catch (PostScriptVmException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<PsValue> parse(String source) throws Exception {
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(source))) {
            return new PostScriptParser().parseAll(lexer);
        }
    }
}
