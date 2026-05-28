package com.convert2web.render;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import com.convert2web.model.EpsDocumentBuilder;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.SourceSpan;
import com.convert2web.model.Path;
import com.convert2web.model.PathSegment;
import com.convert2web.model.StrokeStyle;
import com.convert2web.model.WindingRule;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(svg.contains("<style>image{image-rendering:pixelated;}</style>"));
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

        assertTrue(svg.contains("<clipPath id=\""));
        assertTrue(svg.contains("clip-rule=\"evenodd\""));
        assertTrue(svg.contains("clip-path=\"url(#"));
    }

    @Test
    void popClipClosesClipGroupBeforeLaterPaint() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 10, 10))
                .addClip(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(10, 0),
                                new PathSegment.LineTo(10, 10),
                                new PathSegment.Close())),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .addFill(
                        new Path(List.of(new PathSegment.MoveTo(1, 1))),
                        PaintStyle.gray(0.5),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .addPopClip()
                .addFill(
                        new Path(List.of(new PathSegment.MoveTo(5, 5))),
                        PaintStyle.gray(0.25),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .build();

        String svg = new SvgRenderer().render(document);

        int clipOpen = svg.indexOf("clip-path=\"url(#");
        int clipClose = svg.indexOf("</g>", clipOpen);
        int secondFill = svg.indexOf("M5 5", clipClose);
        assertTrue(clipOpen >= 0);
        assertTrue(clipClose > clipOpen);
        assertTrue(secondFill > clipClose);
        assertFalse(svg.substring(clipClose).contains("clip-path=\"url(#"));
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

        assertTrue(svg.contains("viewBox=\"0 0 52 12\""));
        assertTrue(svg.contains("translate(0,12) scale(1,-1) translate(-279,-389)"));
        assertTrue(svg.contains("M280 390"));
    }

    @Test
    void visibleBoundsIgnoreWhitePageBackground() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 612, 792))
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(612, 0),
                                new PathSegment.LineTo(612, 792),
                                new PathSegment.LineTo(0, 792),
                                new PathSegment.Close())),
                        PaintStyle.rgb(1, 1, 1),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(20, 700),
                                new PathSegment.LineTo(70, 700),
                                new PathSegment.LineTo(70, 740),
                                new PathSegment.Close())),
                        PaintStyle.rgb(1, 0, 0),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .build();

        String svg = new SvgRenderer().render(document);

        assertTrue(svg.contains("width=\"52\""));
        assertTrue(svg.contains("height=\"42\""));
        assertTrue(svg.contains("viewBox=\"0 0 52 42\""));
        assertTrue(svg.contains("translate(0,42) scale(1,-1) translate(-19,-699)"));
    }

    @Test
    void nestedClipRectsDoNotCollapseVisibleViewport() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 200, 240))
                .addClip(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(200, 0),
                                new PathSegment.LineTo(200, 240),
                                new PathSegment.Close())),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .addClip(
                        new Path(List.of(
                                new PathSegment.MoveTo(44, 4),
                                new PathSegment.LineTo(158, 4),
                                new PathSegment.LineTo(158, 168),
                                new PathSegment.Close())),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .addClip(
                        new Path(List.of(
                                new PathSegment.MoveTo(44, 168),
                                new PathSegment.LineTo(158, 168),
                                new PathSegment.LineTo(158, 200),
                                new PathSegment.Close())),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(44, 4),
                                new PathSegment.LineTo(158, 4),
                                new PathSegment.LineTo(158, 200),
                                new PathSegment.Close())),
                        PaintStyle.rgb(0.2, 0.8, 0.3),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(46, 16),
                                new PathSegment.LineTo(150, 16),
                                new PathSegment.LineTo(150, 17),
                                new PathSegment.Close())),
                        PaintStyle.rgb(0, 1, 0),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .build();

        String svg = new SvgRenderer().render(document);

        assertTrue(svg.contains("height=\"197\"") || svg.contains("height=\"198\""));
        assertTrue(!svg.contains("height=\"0.3\""));
        assertTrue(svg.contains("width=\"115\"") || svg.contains("width=\"116\""));
    }

    @Test
    void rendersRasterPlaceholderPattern() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 128, 160))
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(128, 0),
                                new PathSegment.LineTo(128, 160),
                                new PathSegment.Close())),
                        PaintStyle.rgb(1, 1, 1),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .addRasterPlaceholder(new BoundingBox(10, 20, 118, 140), Matrix.identity())
                .build();

        String svg = new SvgRenderer().render(document);

        assertTrue(svg.contains("id=\"raster-placeholder-hatch\""));
        assertTrue(svg.contains("Raster region (placeholder)"));
        assertTrue(svg.contains("stroke-dasharray=\"4 3\""));
        assertTrue(svg.contains("width=\"108\""));
        assertTrue(svg.contains("height=\"120\""));
    }

    @Test
    void minWidthScalesDisplaySizeButNotViewBox() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 15, 15))
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(15, 0),
                                new PathSegment.LineTo(15, 15),
                                new PathSegment.Close())),
                        PaintStyle.rgb(1, 0, 0),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .build();

        SvgRenderOptions options = SvgRenderOptions.builder().minWidth("50").minHeight("50").build();
        String svg = new SvgRenderer(options).render(document);

        assertTrue(svg.contains("width=\"50\""));
        assertTrue(svg.contains("height=\"50\""));
        assertTrue(svg.contains("viewBox=\"0 0 17 17\"") || svg.contains("viewBox=\"0 0 15 15\""));
    }

    @Test
    void maxDimensionsCapDisplaySizeProportionally() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 200, 100))
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(200, 0),
                                new PathSegment.LineTo(200, 100),
                                new PathSegment.Close())),
                        PaintStyle.rgb(0, 0, 1),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .build();

        SvgRenderOptions options = SvgRenderOptions.builder()
                .maxWidth("100")
                .maxHeight("100")
                .build();
        String svg = new SvgRenderer(options).render(document);

        assertTrue(svg.contains("width=\"100\""));
        assertTrue(svg.contains("height=\"50\""));
        assertTrue(svg.contains("viewBox=\"0 0 202 102\"") || svg.contains("viewBox=\"0 0 200 100\""));
    }

    @Test
    void maxTakesPrecedenceOverConflictingMin() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 400, 200))
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(400, 0),
                                new PathSegment.LineTo(400, 200),
                                new PathSegment.Close())),
                        PaintStyle.rgb(1, 0, 0),
                        WindingRule.NON_ZERO,
                        Matrix.identity())
                .build();

        SvgRenderOptions options = SvgRenderOptions.builder()
                .minWidth("800")
                .maxWidth("50")
                .build();
        String svg = new SvgRenderer(options).render(document);

        assertTrue(svg.contains("width=\"50\""));
        assertTrue(svg.contains("height=\"25\""));
    }

    @Test
    void rendersTextElement() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addText("Hello", 10, 20, "Helvetica", 12, PaintStyle.rgb(0, 0, 0), Matrix.identity())
                .build();
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("<text"));
        assertTrue(svg.contains("Hello"));
        assertTrue(svg.contains("transform=\"translate(10 20)\""));
        assertTrue(svg.contains("x=\"0\" y=\"0\""));
        assertTrue(svg.contains("font-size=\"12\""));
    }

    @Test
    void rendersRalewaySemiBoldWithWeightAndFamily() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addText(
                        "Low",
                        17.5552,
                        109.323,
                        "SLWDMH+Raleway-SemiBold",
                        5.24472,
                        PaintStyle.gray(0),
                        Matrix.identity(),
                        new double[] {2.95264, 3.05762, 0})
                .build();
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("font-family=\"'SLWDMH+Raleway-SemiBold', sans-serif\""), svg);
        assertTrue(svg.contains("font-weight=\"600\""), svg);
        assertTrue(svg.contains("font-size=\"5.2447"), svg);
        assertTrue(svg.contains("transform=\"translate(17.5552 109.323)\""), svg);
        assertTrue(svg.contains("x=\"0\" y=\"0\""), svg);
        assertFalse(svg.contains("matrix(5.2447"), svg);
        assertTrue(svg.contains("<tspan x=\"0 2.9526 6.0103\""), svg);
    }

    @Test
    void rendersGothamItalicLabelWithDarkFill() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 200))
                .addText(
                        "(Blue)",
                        9.0044,
                        162.192,
                        "GothamXNarrow-BookItalic",
                        8.0,
                        PaintStyle.rgb(35 / 255.0, 31 / 255.0, 32 / 255.0),
                        Matrix.identity(),
                        new double[] {2.73584, 4.19141, 1.77588, 3.77539, 3.49609, 0})
                .build();
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("font-style=\"italic\""), svg);
        assertTrue(svg.contains("fill=\"rgb(35,31,32)\""), svg);
        assertTrue(svg.contains(">(Blue)</tspan>"), svg);
    }

    @Test
    void rendersTextWithGlyphAdvanceTspan() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addText(
                        "AB",
                        10,
                        20,
                        "Helvetica",
                        12,
                        PaintStyle.rgb(0, 0, 0),
                        Matrix.identity(),
                        new double[] {3, 4, 0})
                .build();
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("<tspan x=\"0 3\""), svg);
        assertTrue(svg.contains(">AB</tspan>"));
    }

    @Test
    void fontMetricsAutoOmitsPerGlyphSpacing() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addText(
                        "AB",
                        10,
                        20,
                        "Helvetica",
                        12,
                        PaintStyle.rgb(0, 0, 0),
                        Matrix.identity(),
                        new double[] {3, 4, 0})
                .build();
        SvgRenderOptions options = SvgRenderOptions.builder()
                .fontMetricsMode(SvgRenderOptions.FontMetricsMode.AUTO)
                .build();
        String svg = new SvgRenderer(options).render(document);
        assertFalse(svg.contains("<tspan"), svg);
        assertTrue(svg.contains(">AB</text>"), svg);
        assertTrue(svg.contains("transform=\"translate(10 20)\""), svg);
    }

    @Test
    void relativeFontMetricsEmitsDxCorrectionsAndSubstituteFirst() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addText(
                        "AB",
                        10,
                        20,
                        "MissingProductionSans",
                        12,
                        PaintStyle.rgb(0, 0, 0),
                        Matrix.identity(),
                        new double[] {3, 4, 0})
                .build();
        SvgRenderOptions options = SvgRenderOptions.builder()
                .substituteFonts(true)
                .build();
        String svg = new SvgRenderer(options).render(document);
        assertTrue(svg.contains("<tspan dx=\"0 "), svg);
        assertFalse(svg.contains("<tspan x=\""), svg);
        assertTrue(svg.contains("MissingProductionSans"), svg);
        assertTrue(svg.contains("EPS font report"), svg);
    }

    @Test
    void relativeFontMetricsWithoutSubstitutionKeepsSourceFamily() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addText(
                        "AB",
                        10,
                        20,
                        "MissingProductionSans",
                        12,
                        PaintStyle.rgb(0, 0, 0),
                        Matrix.identity(),
                        new double[] {3, 4, 0})
                .build();
        SvgRenderOptions options = SvgRenderOptions.builder()
                .fontMetricsMode(SvgRenderOptions.FontMetricsMode.RELATIVE)
                .build();
        String svg = new SvgRenderer(options).render(document);
        assertTrue(svg.contains("<tspan dx=\"0 "), svg);
        assertTrue(svg.contains("font-family=\"MissingProductionSans, sans-serif\""), svg);
    }

    @Test
    void substitutedCondensedLightFontPreservesWeightAndStretch() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addText(
                        "In Range Result",
                        10,
                        20,
                        "Abadi MT Condensed Light",
                        12,
                        PaintStyle.rgb(0, 0, 0),
                        Matrix.identity())
                .build();
        SvgRenderOptions options = SvgRenderOptions.builder()
                .substituteFonts(true)
                .fontMetricsMode(SvgRenderOptions.FontMetricsMode.AUTO)
                .build();
        String svg = new SvgRenderer(options).render(document);
        assertTrue(svg.contains("font-weight=\"300\""), svg);
        assertTrue(svg.contains("font-stretch=\"condensed\""), svg);
    }

    @Test
    void substitutedMediumFontPreservesWeight() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addText(
                        "Range Indicator Notes",
                        10,
                        20,
                        "Gotham-Medium",
                        12,
                        PaintStyle.rgb(0, 0, 0),
                        Matrix.identity())
                .build();
        SvgRenderOptions options = SvgRenderOptions.builder()
                .substituteFonts(true)
                .fontMetricsMode(SvgRenderOptions.FontMetricsMode.AUTO)
                .build();
        String svg = new SvgRenderer(options).render(document);
        assertTrue(svg.contains("font-weight=\"500\""), svg);
    }

    @Test
    void embeddedImageEmitsClipPathOnElement() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        com.convert2web.model.Path clipPath = new com.convert2web.model.Path(List.of(
                new PathSegment.MoveTo(10, 10),
                new PathSegment.LineTo(50, 10),
                new PathSegment.LineTo(50, 40),
                new PathSegment.LineTo(10, 40),
                new PathSegment.Close()));
        GraphicsCommand.Clip clip = new GraphicsCommand.Clip(
                clipPath, WindingRule.NON_ZERO, Matrix.identity());
        EpsDocument document = new EpsDocument(
                new BoundingBox(0, 0, 100, 100),
                null,
                List.of(new GraphicsCommand.EmbeddedImage(
                        4, 4, new Matrix(10, 0, 0, 10, 20, 30), png, List.of(clip))),
                Map.of());
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("<clipPath"));
        assertTrue(svg.contains("clip-path=\"url(#"));
        assertTrue(Pattern.compile("<image[^>]*clip-path=\"url\\(#\\d+\\)\"").matcher(svg).find());
    }

    @Test
    void embeddedImageClipPathUsesImageLocalCoordinates() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        com.convert2web.model.Path clipPath = new com.convert2web.model.Path(List.of(
                new PathSegment.MoveTo(20, 30),
                new PathSegment.LineTo(30, 30),
                new PathSegment.LineTo(30, 40),
                new PathSegment.LineTo(20, 40),
                new PathSegment.Close()));
        GraphicsCommand.Clip clip = new GraphicsCommand.Clip(
                clipPath, WindingRule.NON_ZERO, Matrix.identity());
        EpsDocument document = new EpsDocument(
                new BoundingBox(0, 0, 100, 100),
                null,
                List.of(new GraphicsCommand.EmbeddedImage(
                        4, 4, new Matrix(10, 0, 0, 10, 20, 30), png, List.of(clip))),
                Map.of());
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("d=\"M0 0L4 0L4 4L0 4Z\""));
    }

    @Test
    void embeddedImageOnIllustratorYDownPageUsesMatrixForOnePixelTile() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        EpsDocument document = new EpsDocument(
                new BoundingBox(0, 0, 195, 191),
                null,
                List.of(new GraphicsCommand.EmbeddedImage(
                        1, 1, new Matrix(85.44, 0, 0, 42.48, 9.24, 4.1421), png)),
                Map.of("svg.pageYFlip", "false"));
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("width=\"1\""));
        assertTrue(svg.contains("height=\"1\""));
        assertTrue(svg.contains("transform=\"matrix("));
        assertTrue(svg.contains("85.44"));
        assertTrue(svg.contains("42.48"));
        assertFalse(svg.contains("preserveAspectRatio=\"none\""));
    }

    @Test
    void embeddedImageWithUnitHeightUsesMatrixNotBBoxStretch() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        EpsDocument document = new EpsDocument(
                new BoundingBox(0, 0, 195, 191),
                null,
                List.of(new GraphicsCommand.EmbeddedImage(
                        18, 1, new Matrix(6.577, 0, 0, 30.327, 140.293, 69.4215), png)),
                Map.of("svg.pageYFlip", "false"));
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("width=\"18\""));
        assertTrue(svg.contains("height=\"1\""));
        assertTrue(svg.contains("transform=\"matrix("));
        assertFalse(svg.contains("preserveAspectRatio=\"none\""));
    }

    @Test
    void embeddedImageUsesAffineTransformMatchingLogoTile() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        EpsDocument document = new EpsDocument(
                new BoundingBox(0, 0, 195, 191),
                null,
                List.of(new GraphicsCommand.EmbeddedImage(
                        380, 160, new Matrix(30.0962, 0, 0, 12.6721, 142.386, 138.463), png)),
                Map.of("svg.pageYFlip", "false"));
        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("width=\"380\""));
        assertTrue(svg.contains("height=\"160\""));
        assertTrue(svg.contains("transform=\"matrix("));
        assertTrue(svg.contains("142.386"));
        assertTrue(svg.contains("39.8649"));
        assertFalse(svg.contains("preserveAspectRatio=\"none\""));
    }

    @Test
    void clipEmbeddedImageAndPopClipKeepGroupTagsBalanced() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        com.convert2web.model.Path clipPath = new com.convert2web.model.Path(List.of(
                new PathSegment.MoveTo(0, 0),
                new PathSegment.LineTo(100, 0),
                new PathSegment.LineTo(100, 100),
                new PathSegment.Close()));
        GraphicsCommand.Clip clip = new GraphicsCommand.Clip(
                clipPath, WindingRule.NON_ZERO, Matrix.identity());
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addClip(clipPath, WindingRule.NON_ZERO, Matrix.identity())
                .addEmbeddedImage(4, 4, new Matrix(10, 0, 0, 10, 0, 0), png, List.of(clip))
                .addPopClip()
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
        String svg = new SvgRenderer().render(document);
        int defsEnd = svg.indexOf("</defs>") + "</defs>".length();
        String body = svg.substring(defsEnd, svg.lastIndexOf("</svg>"));
        int groupOpens = body.split("<g ", -1).length - 1;
        int groupCloses = body.split("</g>", -1).length - 1;
        assertEquals(groupOpens, groupCloses, () -> "unbalanced <g> in body: " + body);
    }

    @Test
    void forTestsDefaultsMatchLetterMaxAnd100Min() {
        SvgRenderOptions options = SvgRenderOptions.forTests();
        assertTrue(options.minWidth().isPresent());
        assertTrue(options.maxWidth().isPresent());
        assertEquals(100.0, options.minWidth().get().toPixels(0), 1e-6);
        assertEquals(8.5 * 96, options.maxWidth().get().toPixels(0), 1e-6);
        assertEquals(11 * 96, options.maxHeight().get().toPixels(0), 1e-6);
    }

    @Test
    void emitsSourceTraceAttributesWhenEnabled() {
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
                        Matrix.identity(),
                        SourceSpan.of(99, 4, 5432))
                .build();

        String without = new SvgRenderer().render(document);
        assertFalse(without.contains("data-eps-line"));

        String with = new SvgRenderer(SvgRenderOptions.builder().emitSourceTrace(true).build())
                .render(document);
        assertTrue(with.contains("data-eps-line=\"99\""), with);
        assertTrue(with.contains("data-eps-column=\"4\""), with);
        assertTrue(with.contains("data-eps-offset=\"5432\""), with);
        assertTrue(with.contains("<!-- eps-source kind=\"fill\""), with);
    }

    @Test
    void emitsSourceTraceForClipAndPopClip() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 10, 10))
                .addClip(
                        new Path(List.of(
                                new PathSegment.MoveTo(0, 0),
                                new PathSegment.LineTo(10, 0),
                                new PathSegment.LineTo(10, 10),
                                new PathSegment.Close())),
                        WindingRule.NON_ZERO,
                        Matrix.identity(),
                        SourceSpan.of(10, 2, 100))
                .addFill(
                        new Path(List.of(
                                new PathSegment.MoveTo(1, 1),
                                new PathSegment.LineTo(2, 1),
                                new PathSegment.LineTo(2, 2),
                                new PathSegment.Close())),
                        PaintStyle.rgb(0, 1, 0),
                        WindingRule.NON_ZERO,
                        Matrix.identity(),
                        SourceSpan.of(12, 1, 300))
                .addPopClip(SourceSpan.of(11, 1, 200))
                .build();

        String svg = new SvgRenderer(SvgRenderOptions.builder().emitSourceTrace(true).build())
                .render(document);

        assertTrue(svg.contains("<!-- eps-source kind=\"clip\""), svg);
        assertTrue(svg.contains("data-eps-line=\"10\""), svg);
        assertTrue(svg.contains("<!-- eps-source kind=\"clip-apply\""), svg);
        assertTrue(svg.contains("<!-- eps-source kind=\"pop-clip\" line=\"11\" column=\"1\" offset=\"200\""), svg);
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
