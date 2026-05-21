package com.convert2web.ps;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
/**
 * One unit test per standard PostScript operator from the ASCII interpreter plan.
 * Implemented operators should pass; unimplemented operators fail until added.
 */
class PostScriptVmOperatorCoverageTest {

    // --- Stack operators (plan section 3) ---

    @Nested
    @DisplayName("stack")
    class StackOperators {

        @Test
        void pop() {
            PostScriptVm vm = run("1 pop");
            assertEquals(0, vm.operandCount());
        }

        @Test
        void dup() {
            assertEquals(new PsValue.IntegerValue(4), peek("4 dup"));
        }

        @Test
        void exch() {
            PostScriptVm vm = run("1 2 exch");
            assertEquals(new PsValue.IntegerValue(1), vm.peek());
        }

        @Test
        void copy() {
            PostScriptVm vm = run("1 2 2 copy");
            assertEquals(4, vm.operandCount());
            assertEquals(new PsValue.IntegerValue(2), vm.peek());
        }

        @Test
        void indexAtTop() {
            assertEquals(new PsValue.IntegerValue(3), peek("1 2 3 0 index"));
        }

        @Test
        void indexBelowTop() {
            assertEquals(new PsValue.IntegerValue(1), peek("1 2 3 2 index"));
        }

        @Test
        void roll() {
            assertEquals(new PsValue.IntegerValue(2), peek("1 2 3 3 1 roll"));
        }

        @Test
        void rollFiveThree() {
            PostScriptVm vm = run("1 2 3 4 5 5 3 roll");
            assertEquals(new PsValue.IntegerValue(2), vm.peek());
        }

        @Test
        void clear() {
            PostScriptVm vm = run("1 2 3 clear");
            assertEquals(0, vm.operandCount());
        }

        @Test
        void count() {
            assertEquals(new PsValue.IntegerValue(3), peek("1 2 3 count"));
        }

        @Test
        void mark() {
            run("mark");
        }

        @Test
        void cleartomark() {
            run("mark 1 2 cleartomark");
        }

        @Test
        void counttomark() {
            run("mark 1 2 3 counttomark");
        }
    }

    // --- Arithmetic (plan section 3) ---

    @Nested
    @DisplayName("arithmetic")
    class ArithmeticOperators {

        @Test
        void add() {
            assertEquals(new PsValue.IntegerValue(5), peek("2 3 add"));
        }

        @Test
        void sub() {
            assertEquals(new PsValue.IntegerValue(2), peek("5 3 sub"));
        }

        @Test
        void mul() {
            assertEquals(new PsValue.IntegerValue(6), peek("2 3 mul"));
        }

        @Test
        void div() {
            assertEquals(2.5, number(peek("5 2 div")), 1e-9);
        }

        @Test
        void idiv() {
            assertEquals(new PsValue.IntegerValue(2), peek("7 3 idiv"));
        }

        @Test
        void mod() {
            assertEquals(new PsValue.IntegerValue(1), peek("7 3 mod"));
        }

        @Test
        void neg() {
            assertEquals(new PsValue.IntegerValue(-4), peek("4 neg"));
        }

        @Test
        void abs() {
            assertEquals(new PsValue.IntegerValue(4), peek("-4 abs"));
        }

        @Test
        void sqrt() {
            assertEquals(2.0, number(peek("4 sqrt")), 1e-9);
        }

        @Test
        void sin() {
            run("0 sin");
        }

        @Test
        void cos() {
            run("0 cos");
        }

        @Test
        void atan() {
            run("0 1 atan");
        }
    }

    // --- Comparison and logical (plan section 3) ---

    @Nested
    @DisplayName("comparison and logical")
    class ComparisonLogicalOperators {

        @Test
        void eq() {
            assertEquals(new PsValue.BooleanValue(true), peek("3 3 eq"));
        }

        @Test
        void ne() {
            assertEquals(new PsValue.BooleanValue(true), peek("3 4 ne"));
        }

        @Test
        void lt() {
            assertEquals(new PsValue.BooleanValue(true), peek("3 5 lt"));
        }

        @Test
        void le() {
            assertEquals(new PsValue.BooleanValue(true), peek("3 3 le"));
        }

        @Test
        void gt() {
            assertEquals(new PsValue.BooleanValue(true), peek("5 3 gt"));
        }

        @Test
        void ge() {
            assertEquals(new PsValue.BooleanValue(true), peek("5 5 ge"));
        }

        @Test
        void and() {
            assertEquals(new PsValue.BooleanValue(false), peek("true false and"));
        }

        @Test
        void or() {
            assertEquals(new PsValue.BooleanValue(true), peek("true false or"));
        }

        @Test
        void not() {
            assertEquals(new PsValue.BooleanValue(false), peek("true not"));
        }
    }

    // --- Dictionary (plan section 3) ---

    @Nested
    @DisplayName("dictionary")
    class DictionaryOperators {

        @Test
        void dict() {
            assertInstanceOf(PsValue.RuntimeDictionaryValue.class, peek("10 dict"));
        }

        @Test
        void begin() {
            run("10 dict begin end");
        }

        @Test
        void end() {
            run("10 dict begin /a 1 def end");
        }

        @Test
        void def() {
            assertEquals(new PsValue.IntegerValue(9), peek("/x 9 def /x load"));
        }

        @Test
        void load() {
            assertEquals(new PsValue.IntegerValue(9), peek("/x 9 def /x load"));
        }

        @Test
        void store() {
            assertEquals(new PsValue.IntegerValue(2), peek("/x 1 def 2 /x store /x load"));
        }

        @Test
        void where() {
            PostScriptVm vm = run("/x 9 def /x where");
            assertEquals(new PsValue.BooleanValue(true), vm.peek());
            vm.pop();
            assertInstanceOf(PsValue.RuntimeDictionaryValue.class, vm.peek());
        }

        @Test
        void known() {
            assertEquals(new PsValue.BooleanValue(true),
                    peek("10 dict begin /a 1 def currentdict /a known end"));
        }

        @Test
        void currentdict() {
            assertInstanceOf(PsValue.RuntimeDictionaryValue.class, peek("currentdict"));
        }

        @Test
        void systemdict() {
            assertInstanceOf(PsValue.RuntimeDictionaryValue.class, peek("systemdict"));
        }

        @Test
        void userdict() {
            assertInstanceOf(PsValue.RuntimeDictionaryValue.class, peek("userdict"));
        }

        @Test
        void bind() {
            assertInstanceOf(PsValue.ProcedureValue.class, peek("{ 1 add } bind"));
        }
    }

    // --- Control flow (plan section 3) ---

    @Nested
    @DisplayName("control flow")
    class ControlFlowOperators {

        @Test
        void exec() {
            assertEquals(new PsValue.IntegerValue(3), peek("{ 1 2 add } exec"));
        }

        @Test
        void ifOperator() {
            assertEquals(new PsValue.IntegerValue(7), peek("true { 7 } if"));
        }

        @Test
        void ifelse() {
            assertEquals(new PsValue.IntegerValue(2), peek("false { 1 } { 2 } ifelse"));
        }

        @Test
        void forOperator() {
            run("1 3 1 { } for");
        }

        @Test
        void repeat() {
            assertEquals(new PsValue.IntegerValue(3), peek("0 { dup 1 add } 3 repeat"));
        }

        @Test
        void loop() {
            run("{ { exit } loop }");
        }

        @Test
        void exit() {
            run("{ { exit } loop }");
        }

        @Test
        void stopped() {
            run("{ (ok) } stopped");
        }
    }

    // --- Path operators (plan section 5) ---

    @Nested
    @DisplayName("path")
    class PathOperators {

        @Test
        void newpath() {
            run("newpath");
        }

        @Test
        void moveto() {
            run("newpath 0 0 moveto");
        }

        @Test
        void rmoveto() {
            run("newpath 0 0 moveto 1 1 rmoveto");
        }

        @Test
        void lineto() {
            run("newpath 0 0 moveto 10 10 lineto");
        }

        @Test
        void rlineto() {
            run("newpath 0 0 moveto 5 5 rlineto");
        }

        @Test
        void curveto() {
            run("newpath 0 0 moveto 0 10 10 10 10 0 curveto");
        }

        @Test
        void rcurveto() {
            run("newpath 0 0 moveto 1 1 2 2 3 3 rcurveto");
        }

        @Test
        void closepath() {
            run("newpath 0 0 moveto 10 0 lineto closepath");
        }

        @Test
        void currentpoint() {
            run("newpath 0 0 moveto currentpoint");
        }

        @Test
        void arc() {
            run("newpath 0 0 10 0 90 arc");
        }

        @Test
        void arcn() {
            run("newpath 0 0 10 0 90 arcn");
        }

        @Test
        void arct() {
            run("0 0 10 0 90 arct");
        }
    }

    // --- Paint operators (plan section 5) ---

    @Nested
    @DisplayName("paint")
    class PaintOperators {

        @Test
        void stroke() {
            run("newpath 0 0 moveto 10 0 lineto stroke");
        }

        @Test
        void fill() {
            run("newpath 0 0 moveto 10 0 lineto 10 10 lineto closepath fill");
        }

        @Test
        void eofill() {
            run("newpath 0 0 moveto 10 0 lineto 10 10 lineto closepath eofill");
        }

        @Test
        void clip() {
            run("newpath 0 0 moveto 10 0 lineto 10 10 lineto closepath clip");
        }

        @Test
        void eoclip() {
            run("newpath 0 0 moveto 10 0 lineto 10 10 lineto closepath eoclip");
        }

        @Test
        void rectfill() {
            run("0 0 10 10 rectfill");
        }

        @Test
        void rectstroke() {
            run("0 0 10 10 rectstroke");
        }

        @Test
        void rectclip() {
            run("0 0 10 10 rectclip");
        }
    }

    // --- Graphics state (plan section 5) ---

    @Nested
    @DisplayName("graphics state")
    class GraphicsStateOperators {

        @Test
        void gsave() {
            PostScriptVm vm = new PostScriptVm();
            run(vm, "gsave");
            vm.getGraphicsState().setLineWidth(9.0);
            run(vm, "grestore");
            assertEquals(1.0, vm.getGraphicsState().getLineWidth(), 1e-9);
        }

        @Test
        void grestore() {
            PostScriptVm vm = new PostScriptVm();
            run(vm, "gsave");
            vm.getGraphicsState().setLineWidth(9.0);
            run(vm, "grestore");
            assertEquals(1.0, vm.getGraphicsState().getLineWidth(), 1e-9);
        }

        @Test
        void setlinewidth() {
            run("2 setlinewidth");
        }

        @Test
        void setlinecap() {
            run("1 setlinecap");
        }

        @Test
        void setlinejoin() {
            run("1 setlinejoin");
        }

        @Test
        void setmiterlimit() {
            run("4 setmiterlimit");
        }

        @Test
        void setdash() {
            run("[] 0 setdash");
        }

        @Test
        void setflat() {
            run("1 setflat");
        }

        @Test
        void setgray() {
            run("0.5 setgray");
        }

        @Test
        void setrgbcolor() {
            run("1 0 0 setrgbcolor");
        }

        @Test
        void setcmykcolor() {
            run("0 1 0 0 setcmykcolor");
        }
    }

    // --- Transform operators (plan section 5) ---

    @Nested
    @DisplayName("transform")
    class TransformOperators {

        @Test
        void matrix() {
            run("matrix");
        }

        @Test
        void initmatrix() {
            run("initmatrix");
        }

        @Test
        void currentmatrix() {
            run("currentmatrix");
        }

        @Test
        void setmatrix() {
            run("matrix setmatrix");
        }

        @Test
        void concat() {
            run("matrix concat");
        }

        @Test
        void concatmatrix() {
            run("matrix matrix matrix concatmatrix");
        }

        @Test
        void translate() {
            run("10 20 translate");
        }

        @Test
        void scale() {
            run("2 2 scale");
        }

        @Test
        void rotate() {
            run("45 rotate");
        }

        @Test
        void transform() {
            run("0 0 transform");
        }

        @Test
        void itransform() {
            run("0 0 itransform");
        }

        @Test
        void dtransform() {
            run("1 0 dtransform");
        }

        @Test
        void idtransform() {
            run("1 0 idtransform");
        }

        @Test
        void invertmatrix() {
            run("matrix matrix invertmatrix");
        }
    }

    private static PsValue peek(String source) {
        return run(source).peek();
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

    private static double number(PsValue value) {
        if (value instanceof PsValue.IntegerValue) {
            return ((PsValue.IntegerValue) value).getValue();
        }
        if (value instanceof PsValue.RealValue) {
            return ((PsValue.RealValue) value).getValue();
        }
        throw new AssertionError("expected number, got " + value);
    }
}
