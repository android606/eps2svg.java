package com.convert2web.render;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import com.convert2web.model.EpsDocumentBuilder;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.Path;
import com.convert2web.model.PathSegment;
import com.convert2web.model.StrokeStyle;
import com.convert2web.model.WindingRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgRendererTest {

    @Test
    void rendersXmlRootWithNamespaceAndViewBox() {
        String svg = renderSampleFill();

        assertTrue(svg.startsWith("<?xml version=\"1.0\""));
        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""));
        assertTrue(svg.contains("viewBox=\"0 0 10 10\""));
        assertTrue(svg.contains("width=\"10\""));
        assertTrue(svg.contains("height=\"10\""));
    }

    @Test
    void rendersFillPathAndColor() {
        String svg = renderSampleFill();

        assertTrue(svg.contains("fill=\"rgb(255,0,0)\""));
        assertTrue(svg.contains("fill-rule=\"nonzero\""));
        assertTrue(svg.contains("M0 0"));
        assertTrue(svg.contains("L10 0"));
        assertTrue(svg.contains("L10 10"));
        assertTrue(svg.contains("Z"));
    }

    @Test
    void rendersStrokeAttributes() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 20, 10))
                .addStroke(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(20, 0))),
                        PaintStyle.gray(0),
                        new StrokeStyle(2.5, 1, 2, 4.0, new double[] {4, 2}, 1.0),
                        Matrix.identity())
                .build();

        String svg = new SvgRenderer().render(document);

        assertTrue(svg.contains("stroke-width=\"2.5\""));
        assertTrue(svg.contains("stroke-linecap=\"round\""));
        assertTrue(svg.contains("stroke-linejoin=\"bevel\""));
        assertTrue(svg.contains("stroke-dasharray=\"4 2\""));
        assertTrue(svg.contains("fill=\"none\""));
    }

    @Test
    void rendersClipPathInDefs() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 10, 10))
                .addClip(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(10, 0),
                                new PathSegment.LineTo(10, 10),
                                new PathSegment.Close())),
                        WindingRule.EVEN_ODD,
                        Matrix.identity())
                .addFill(
                        new Path(List.of(new PathSegment.MoveTo(1, 1))),
                        PaintStyle.gray(0.5),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .build();

        String svg = new SvgRenderer().render(document);

        assertTrue(svg.contains("<clipPath id=\"clip0\">"));
        assertTrue(svg.contains("clip-rule=\"evenodd\""));
        assertTrue(svg.contains("clip-path=\"url(#clip0)\""));
    }

    @Test
    void rendersCtmTransformOnCommand() {
        Matrix ctm = new Matrix(2, 0, 0, 2, 5, 5);
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addFill(
                        new Path(List.of(new PathSegment.MoveTo(0, 0))),
                        PaintStyle.gray(0),
                        WindingRule.NON_ZERO,
                        ctm)
                .build();

        String svg = new SvgRenderer().render(document);

        assertTrue(svg.contains("matrix(2 0 0 2 5 5)"));
    }

    @Test
    void rendersEpsYFlipGroup() {
        String svg = renderSampleFill();
        assertTrue(svg.contains("scale(1,-1)"));
    }

    @Test
    void nonZeroBoundingBoxUsesZeroOriginViewBoxAndPageShift() {
        Matrix ctm = Matrix.identity();
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(270, 384, 342, 407))
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(280, 390),
                                new PathSegment.LineTo(330, 390),
                                new PathSegment.LineTo(330, 400),
                                new PathSegment.Close())),
                        PaintStyle.gray(0),
                        WindingRule.NON_ZERO,
                        ctm)
                .build();

        String svg = new SvgRenderer().render(document);

        assertTrue(svg.contains("viewBox=\"0 0 72 23\""));
        assertTrue(svg.contains("translate(0,23) scale(1,-1) translate(-270,-384)"));
        assertTrue(svg.contains("M280 390"));
    }

    private static String renderSampleFill() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 10, 10))
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(10, 0),
                                new PathSegment.LineTo(10, 10),
                                new PathSegment.Close())),
                        PaintStyle.rgb(1, 0, 0),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .build();
        return new SvgRenderer().render(document);
    }
}
