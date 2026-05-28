package com.convert2web.ps;

import com.convert2web.model.EpsDocument;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.Path;
import com.convert2web.model.PathSegment;
import com.convert2web.model.WindingRule;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Model-level tests: graphics operators append to {@link EpsDocument}.
 */
class PostScriptVmGraphicsTest {

    private static final String ILLUSTRATOR_TEXT_PREAMBLE =
            "userdict begin /mo { moveto } bind def /sh { show } bind def /xsh { xsh } bind def ";

    @Test
    void fillRecordsClosedTrianglePath() {
        EpsDocument doc = runDocument(
                "newpath 0 0 moveto 10 0 lineto 10 10 lineto closepath fill");

        assertEquals(1, doc.getCommands().size());
        GraphicsCommand.Fill fill = assertInstanceOf(GraphicsCommand.Fill.class, doc.getCommands().get(0));
        assertEquals(WindingRule.NON_ZERO, fill.getWindingRule());
        assertEquals(4, fill.getPath().getSegments().size());
        assertMoveTo(fill.getPath(), 0, 0, 0);
        assertLineTo(fill.getPath(), 1, 10, 0);
        assertLineTo(fill.getPath(), 2, 10, 10);
        assertInstanceOf(PathSegment.Close.class, fill.getPath().getSegments().get(3));
    }

    @Test
    void eofillUsesEvenOddWindingRule() {
        EpsDocument doc = runDocument(
                "newpath 0 0 moveto 5 0 lineto 5 5 lineto closepath eofill");

        GraphicsCommand.Fill fill = (GraphicsCommand.Fill) doc.getCommands().get(0);
        assertEquals(WindingRule.EVEN_ODD, fill.getWindingRule());
    }

    @Test
    void strokeRecordsPathAndLineWidth() {
        EpsDocument doc = runDocument(
                "2 setlinewidth newpath 0 0 moveto 20 0 lineto stroke");

        GraphicsCommand.Stroke stroke = assertInstanceOf(GraphicsCommand.Stroke.class, doc.getCommands().get(0));
        assertEquals(2, stroke.getPath().getSegments().size());
        assertEquals(2.0, stroke.getStrokeStyle().getLineWidth(), 1e-9);
        assertEquals(PaintStyle.Kind.GRAY, stroke.getStrokeColor().getKind());
    }

    @Test
    void setrgbcolorAppliesToFill() {
        EpsDocument doc = runDocument(
                "1 0 0 setrgbcolor newpath 0 0 moveto 1 1 lineto 1 0 lineto closepath fill");

        GraphicsCommand.Fill fill = (GraphicsCommand.Fill) doc.getCommands().get(0);
        assertEquals(PaintStyle.Kind.RGB, fill.getFill().getKind());
        assertEquals(1.0, fill.getFill().getV0(), 1e-9);
        assertEquals(0.0, fill.getFill().getV1(), 1e-9);
        assertEquals(0.0, fill.getFill().getV2(), 1e-9);
    }

    @Test
    void cairoReRectanglePath() throws Exception {
        PostScriptVm vm = new PostScriptVm();
        String ps = "/re { exch dup neg 3 1 roll 5 3 roll moveto 0 rlineto "
                + "0 exch rlineto 0 rlineto closepath } bind def 0 19.307 22 -20 re";
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(ps))) {
            vm.executeAll(new PostScriptParser().parseAll(lexer));
        }
        String d = com.convert2web.render.SvgPathEncoder.toPathData(vm.getGraphicsState().snapshotPath());
        org.junit.jupiter.api.Assertions.assertTrue(d.contains("M0 19.307"), () -> d);
        org.junit.jupiter.api.Assertions.assertTrue(d.contains("22 -0.693"), () -> d);
    }

    @Test
    void setgrayAppliesToStroke() {
        EpsDocument doc = runDocument(
                "0.25 setgray newpath 0 0 moveto 5 5 lineto stroke");

        GraphicsCommand.Stroke stroke = (GraphicsCommand.Stroke) doc.getCommands().get(0);
        assertEquals(PaintStyle.Kind.GRAY, stroke.getStrokeColor().getKind());
        assertEquals(0.25, stroke.getStrokeColor().getV0(), 1e-9);
    }

    @Test
    void clipRecordsClipCommand() {
        EpsDocument doc = runDocument(
                "newpath 0 0 moveto 10 0 lineto 10 10 lineto closepath clip");

        GraphicsCommand.Clip clip = assertInstanceOf(GraphicsCommand.Clip.class, doc.getCommands().get(0));
        assertEquals(WindingRule.NON_ZERO, clip.getWindingRule());
        assertEquals(4, clip.getPath().getSegments().size());
    }

    @Test
    void eoclipUsesEvenOddWindingRule() {
        EpsDocument doc = runDocument(
                "newpath 0 0 moveto 1 0 lineto 1 1 lineto closepath eoclip");

        GraphicsCommand.Clip clip = (GraphicsCommand.Clip) doc.getCommands().get(0);
        assertEquals(WindingRule.EVEN_ODD, clip.getWindingRule());
    }

    @Test
    void rectfillRecordsRectanglePath() {
        EpsDocument doc = runDocument("0 0 10 20 rectfill");

        GraphicsCommand.Fill fill = (GraphicsCommand.Fill) doc.getCommands().get(0);
        assertMoveTo(fill.getPath(), 0, 0, 0);
        assertLineTo(fill.getPath(), 1, 10, 0);
        assertLineTo(fill.getPath(), 2, 10, 20);
        assertLineTo(fill.getPath(), 3, 0, 20);
    }

    @Test
    void rectstrokeRecordsStrokeCommand() {
        EpsDocument doc = runDocument("0 0 5 5 rectstroke");

        assertInstanceOf(GraphicsCommand.Stroke.class, doc.getCommands().get(0));
    }

    @Test
    void curvetoRecordsCurveSegment() {
        EpsDocument doc = runDocument(
                "newpath 0 0 moveto 0 10 10 10 10 0 curveto fill");

        GraphicsCommand.Fill fill = (GraphicsCommand.Fill) doc.getCommands().get(0);
        PathSegment.CurveTo curve = assertInstanceOf(
                PathSegment.CurveTo.class, fill.getPath().getSegments().get(1));
        assertEquals(0, curve.getX1(), 1e-9);
        assertEquals(10, curve.getY1(), 1e-9);
        assertEquals(10, curve.getX3(), 1e-9);
        assertEquals(0, curve.getY3(), 1e-9);
    }

    @Test
    void illustratorCompoundPathUsesEvenOddFill() {
        EpsDocument doc = runDocument(
                "*u 0 0 moveto 10 0 lineto 10 10 lineto 0 10 lineto f"
                        + " 2 2 moveto 8 2 lineto 8 8 lineto 2 8 lineto f"
                        + " *U");

        assertEquals(1, doc.getCommands().size());
        GraphicsCommand.Fill fill = (GraphicsCommand.Fill) doc.getCommands().get(0);
        assertEquals(WindingRule.EVEN_ODD, fill.getWindingRule());
        long moveTos = fill.getPath().getSegments().stream()
                .filter(s -> s.getType() == com.convert2web.model.PathSegment.Type.MOVE_TO)
                .count();
        assertEquals(2, moveTos);
    }

    @Test
    void curvetoWithFourOperandsUsesCurrentPointAsFirstControl() {
        EpsDocument doc = runDocument(
                "newpath 0 0 moveto 10 20 30 40 curveto fill");

        GraphicsCommand.Fill fill = (GraphicsCommand.Fill) doc.getCommands().get(0);
        PathSegment.CurveTo curve = assertInstanceOf(
                PathSegment.CurveTo.class, fill.getPath().getSegments().get(1));
        assertEquals(0, curve.getX1(), 1e-9);
        assertEquals(0, curve.getY1(), 1e-9);
        assertEquals(10, curve.getX2(), 1e-9);
        assertEquals(30, curve.getX3(), 1e-9);
        assertEquals(40, curve.getY3(), 1e-9);
    }

    @Test
    void translateRecordsCtmOnCommand() {
        EpsDocument doc = runDocument(
                "10 20 translate newpath 0 0 moveto 1 0 lineto fill");

        Matrix ctm = doc.getCommands().get(0).getCtm();
        assertEquals(10, ctm.getE(), 1e-9);
        assertEquals(20, ctm.getF(), 1e-9);
    }

    @Test
    void scaleRecordsCtmOnCommand() {
        EpsDocument doc = runDocument(
                "2 3 scale newpath 0 0 moveto 1 1 lineto fill");

        Matrix ctm = doc.getCommands().get(0).getCtm();
        assertEquals(2, ctm.getA(), 1e-9);
        assertEquals(3, ctm.getD(), 1e-9);
    }

    @Test
    void gsaveGrestoreDoesNotRemoveEmittedCommands() {
        EpsDocument doc = runDocument(
                "newpath 0 0 moveto 1 0 lineto fill "
                        + "gsave 0.5 setgray newpath 0 0 moveto 2 0 lineto fill grestore");

        assertEquals(2, doc.getCommands().size());
        GraphicsCommand.Fill first = (GraphicsCommand.Fill) doc.getCommands().get(0);
        GraphicsCommand.Fill second = (GraphicsCommand.Fill) doc.getCommands().get(1);
        assertEquals(PaintStyle.Kind.GRAY, first.getFill().getKind());
        assertEquals(0.5, second.getFill().getV0(), 1e-9);
    }

    @Test
    void grestoreEmitsPopClipAfterScopedClip() {
        EpsDocument doc = runDocument(
                "gsave newpath 0 0 moveto 10 0 lineto 10 10 lineto 0 10 lineto closepath clip "
                        + "newpath 1 1 moveto 2 2 lineto fill grestore "
                        + "newpath 3 3 moveto 4 4 lineto fill");

        assertEquals(4, doc.getCommands().size());
        assertInstanceOf(GraphicsCommand.Clip.class, doc.getCommands().get(0));
        assertInstanceOf(GraphicsCommand.Fill.class, doc.getCommands().get(1));
        assertInstanceOf(GraphicsCommand.PopClip.class, doc.getCommands().get(2));
        assertInstanceOf(GraphicsCommand.Fill.class, doc.getCommands().get(3));
    }

    @Test
    void multiplePaintOperatorsAppendMultipleCommands() {
        EpsDocument doc = runDocument(
                "newpath 0 0 moveto 1 0 lineto stroke "
                        + "newpath 0 0 moveto 1 1 lineto 1 0 lineto closepath fill");

        assertEquals(2, doc.getCommands().size());
        assertInstanceOf(GraphicsCommand.Stroke.class, doc.getCommands().get(0));
        assertInstanceOf(GraphicsCommand.Fill.class, doc.getCommands().get(1));
    }

    @Test
    void emptyPathStrokeDoesNotAppendCommand() {
        EpsDocument doc = runDocument("stroke");

        assertEquals(0, doc.getCommands().size());
    }

    @Test
    void showRecordsTextCommand() {
        EpsDocument doc = runDocument(
                "/Helvetica findfont 12 scalefont setfont "
                        + "0 0 0 setrgbcolor "
                        + "10 20 moveto (Hello) show");

        assertEquals(1, doc.getCommands().size());
        GraphicsCommand.Text text = assertInstanceOf(GraphicsCommand.Text.class, doc.getCommands().get(0));
        assertEquals("Hello", text.getText());
        assertEquals(10.0, text.getX(), 1e-9);
        assertEquals(20.0, text.getY(), 1e-9);
        assertEquals(12.0, text.getFontSize(), 1e-9);
    }

    @Test
    void illustratorShorthandShowRecordsText() {
        EpsDocument doc = runDocument(
                "userdict begin /sh { show } bind def "
                        + "/Helvetica findfont 10 scalefont setfont "
                        + "9.0045 162.1917 moveto ((Blue)) sh");

        assertEquals(1, doc.getCommands().size());
        GraphicsCommand.Text text = assertInstanceOf(GraphicsCommand.Text.class, doc.getCommands().get(0));
        assertEquals("(Blue)", text.getText());
        assertEquals(9.0045, text.getX(), 1e-9);
        assertEquals(162.1917, text.getY(), 1e-9);
    }

    @Test
    void pendingLegendTextWaitsForCoverTileAcrossOtherImages() {
        PostScriptVm vm = new PostScriptVm();
        vm.getDocumentRecorder().recordText(
                "(Blue)", 9, 162, "GothamXNarrow-BookItalic", 8,
                PaintStyle.rgb(0.1, 0.1, 0.1), Matrix.identity());
        vm.getDocumentRecorder().recordEmbeddedImage(
                82, 29, new Matrix(0.24, 0, 0, 0.24, 80, 10), new byte[] {1});
        vm.getDocumentRecorder().recordEmbeddedImage(
                82, 29, new Matrix(0.24, 0, 0, 0.24, 9.24, 161.08), new byte[] {1}, true);

        EpsDocument doc = vm.getDocument();

        assertInstanceOf(GraphicsCommand.EmbeddedImage.class, doc.getCommands().get(0));
        assertInstanceOf(GraphicsCommand.EmbeddedImage.class, doc.getCommands().get(1));
        assertInstanceOf(GraphicsCommand.Text.class, doc.getCommands().get(2));
    }

    @Test
    void textDoesNotMoveAfterNormalTiles() {
        PostScriptVm vm = new PostScriptVm();
        vm.getDocumentRecorder().recordText(
                "In Range", 46, 109, "Raleway-SemiBold", 5.2,
                PaintStyle.rgb(1, 1, 1), Matrix.identity());
        vm.getDocumentRecorder().recordEmbeddedImage(
                82, 29, new Matrix(0.24, 0, 0, 0.24, 46, 109), new byte[] {1});

        EpsDocument doc = vm.getDocument();

        assertInstanceOf(GraphicsCommand.Text.class, doc.getCommands().get(0));
        assertInstanceOf(GraphicsCommand.EmbeddedImage.class, doc.getCommands().get(1));
    }

    @Test
    void smallRalewayTextPaintsAfterPairedCoverTile() {
        PostScriptVm vm = new PostScriptVm();
        vm.getDocumentRecorder().recordText(
                "B", 149.71, 104.304, "Raleway-Regular", 4.75,
                PaintStyle.rgb(0, 0, 0), Matrix.identity());
        vm.getDocumentRecorder().recordEmbeddedImage(
                12, 17, new Matrix(0.24, 0, 0, 0.2204, 149.935, 100.7631), new byte[] {1}, true);

        EpsDocument doc = vm.getDocument();

        assertInstanceOf(GraphicsCommand.EmbeddedImage.class, doc.getCommands().get(0));
        assertInstanceOf(GraphicsCommand.Text.class, doc.getCommands().get(1));
    }

    @Test
    void pendingTextPaintsAfterPairedPlaceholderImage() {
        PostScriptVm vm = new PostScriptVm();
        vm.getDocumentRecorder().recordText(
                "(Blue)", 9, 162, "GothamXNarrow-BookItalic", 8,
                PaintStyle.rgb(0.1, 0.1, 0.1), Matrix.identity());
        vm.getDocumentRecorder().recordEmbeddedImage(
                82, 29, new Matrix(0.24, 0, 0, 0.24, 9.24, 161.08), new byte[] {1}, true);

        EpsDocument doc = vm.getDocument();

        assertInstanceOf(GraphicsCommand.EmbeddedImage.class, doc.getCommands().get(0));
        assertInstanceOf(GraphicsCommand.Text.class, doc.getCommands().get(1));
    }

    @Test
    void xshRecordsStringWithGlyphAdvances() {
        EpsDocument doc = runDocument(
                ILLUSTRATOR_TEXT_PREAMBLE
                        + "10 20 mo (AB) [3 4 0] xsh");

        assertEquals(1, doc.getCommands().size());
        GraphicsCommand.Text text = assertInstanceOf(GraphicsCommand.Text.class, doc.getCommands().get(0));
        assertEquals("AB", text.getText());
        assertEquals(10.0, text.getX(), 1e-9);
        assertEquals(20.0, text.getY(), 1e-9);
        assertNotNull(text.getGlyphAdvances());
        assertEquals(3, text.getGlyphAdvances().length);
        assertEquals(3.0, text.getGlyphAdvances()[0], 1e-9);
        assertEquals(4.0, text.getGlyphAdvances()[1], 1e-9);
    }

    @Test
    void xshNormalizesIllustratorWordSeparatorInInRange() {
        EpsDocument doc = runDocument(
                ILLUSTRATOR_TEXT_PREAMBLE
                        + "SLWDMG+Raleway-Regular*1 [5.24472 0 0 -5.4903 0 0 ]msf "
                        + "46.0044 109.181 mo (In\\312Range) "
                        + "[1.42676 3.13086 1.29541 3.43018 2.95801 3.13086 3.29932 0] xsh");

        GraphicsCommand.Text label = doc.getCommands().stream()
                .filter(GraphicsCommand.Text.class::isInstance)
                .map(GraphicsCommand.Text.class::cast)
                .filter(t -> t.getText().contains("Range"))
                .findFirst()
                .orElseThrow();
        assertEquals("In Range", label.getText());
    }

    @Test
    void msfSetsRalewaySemiBoldForLowLabel() {
        EpsDocument doc = runDocument(
                ILLUSTRATOR_TEXT_PREAMBLE
                        + "SLWDMH+Raleway-SemiBold*1 [5.24472 0 0 -5.4903 0 0 ]msf "
                        + "17.5552 109.323 mo (Low) [2.95264 3.05762 0] xsh");

        GraphicsCommand.Text low = doc.getCommands().stream()
                .filter(GraphicsCommand.Text.class::isInstance)
                .map(GraphicsCommand.Text.class::cast)
                .filter(t -> "Low".equals(t.getText()))
                .findFirst()
                .orElseThrow();
        assertEquals("Raleway-SemiBold", low.getFontName());
        assertEquals(5.24472, low.getFontSize(), 1e-3);
        assertTrue(low.getCtm().equals(Matrix.identity()), "text matrix must not be stored on command CTM");
    }

    @Test
    void msfAndXshMatchIllustratorBeforeMealSnippet() {
        EpsDocument doc = runDocument(
                ILLUSTRATOR_TEXT_PREAMBLE
                        + "147.752 95.0576 mo (B) sh "
                        + "151.015 95.0576 mo (efore Meal) "
                        + "[2.73242 1.77344 2.7002 2.26855 2.5957 1.09863 3.96973 2.75293 2.65723 0] xsh");

        assertEquals(2, doc.getCommands().size());
        GraphicsCommand.Text b = assertInstanceOf(GraphicsCommand.Text.class, doc.getCommands().get(0));
        GraphicsCommand.Text rest = assertInstanceOf(GraphicsCommand.Text.class, doc.getCommands().get(1));
        assertEquals("B", b.getText());
        assertEquals("efore Meal", rest.getText());
        assertEquals(151.015, rest.getX(), 1e-9);
        assertTrue(rest.hasGlyphAdvances());
    }

    private static void assertMoveTo(Path path, int index, double x, double y) {
        PathSegment.MoveTo move = assertInstanceOf(PathSegment.MoveTo.class, path.getSegments().get(index));
        assertEquals(x, move.getX(), 1e-9);
        assertEquals(y, move.getY(), 1e-9);
    }

    private static void assertLineTo(Path path, int index, double x, double y) {
        PathSegment.LineTo line = assertInstanceOf(PathSegment.LineTo.class, path.getSegments().get(index));
        assertEquals(x, line.getX(), 1e-9);
        assertEquals(y, line.getY(), 1e-9);
    }

    @Test
    void recordsSourceSpanOnClipOperator() {
        String source = "0 0 moveto 10 0 lineto 10 10 lineto closepath clip";
        EpsDocument doc = runDocument(source);

        GraphicsCommand.Clip clip = assertInstanceOf(GraphicsCommand.Clip.class, doc.getCommands().get(0));
        assertTrue(clip.getSourceSpan().isPresent());
        assertEquals(1, clip.getSourceSpan().get().line());
    }

    @Test
    void recordsSourceSpanOnPopClipFromGrestore() {
        String source =
                "0 0 moveto 10 0 lineto 10 10 lineto closepath clip "
                        + "gsave 0 0 moveto 5 0 lineto 5 5 lineto closepath clip grestore";
        EpsDocument doc = runDocument(source);

        GraphicsCommand.PopClip pop = doc.getCommands().stream()
                .filter(GraphicsCommand.PopClip.class::isInstance)
                .map(GraphicsCommand.PopClip.class::cast)
                .findFirst()
                .orElseThrow();
        assertTrue(pop.getSourceSpan().isPresent());
    }

    @Test
    void recordsSourceSpanOnFillOperator() {
        String source = "0 0 moveto 10 0 lineto 10 10 lineto 0 10 lineto closepath fill";
        EpsDocument doc = runDocument(source);

        GraphicsCommand.Fill fill = assertInstanceOf(GraphicsCommand.Fill.class, doc.getCommands().get(0));
        assertTrue(fill.getSourceSpan().isPresent(), fill.getSourceSpan().toString());
        assertEquals(1, fill.getSourceSpan().get().line());
        assertTrue(fill.getSourceSpan().get().offset() > 0);
    }

    private static EpsDocument runDocument(String source) {
        PostScriptVm vm = new PostScriptVm();
        try (PostScriptLexer lexer = new PostScriptLexer(new StringReader(source))) {
            List<PsValue> program = new PostScriptParser().parseAll(lexer);
            vm.executeAll(program);
        } catch (PostScriptVmException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return vm.getDocument();
    }
}
