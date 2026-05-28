package com.convert2web.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class EpsDocumentTest {

    @Test
    void buildDocumentWithFillCommand() {
        Path path = new Path(List.of(new PathSegment.MoveTo(0, 0), new PathSegment.LineTo(10, 0),
                new PathSegment.LineTo(10, 10), new PathSegment.Close()));

        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 10, 10))
                .addFill(path, PaintStyle.rgb(1, 0, 0), WindingRule.NON_ZERO, Matrix.identity())
                .build();

        assertEquals(new BoundingBox(0, 0, 10, 10), document.getBoundingBox());
        assertEquals(1, document.getCommands().size());

        GraphicsCommand.Fill fill = assertInstanceOf(GraphicsCommand.Fill.class, document.getCommands().get(0));
        assertEquals(PaintStyle.Kind.RGB, fill.getFill().getKind());
        assertEquals(1.0, fill.getFill().getV0(), 1e-9);
        assertEquals(WindingRule.NON_ZERO, fill.getWindingRule());
        assertEquals(4, fill.getPath().getSegments().size());
    }

    @Test
    void fillAndStrokeUseDistinctPaintStyles() {
        Path path = new Path(List.of(new PathSegment.MoveTo(0, 0), new PathSegment.LineTo(5, 5)));

        EpsDocumentBuilder builder = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 5, 5));
        builder.addFill(path, PaintStyle.gray(0.5), WindingRule.NON_ZERO, Matrix.identity());
        builder.addStroke(path, PaintStyle.rgb(0, 0, 1), StrokeStyle.defaults(), Matrix.identity());

        EpsDocument document = builder.build();

        GraphicsCommand.Fill fill = (GraphicsCommand.Fill) document.getCommands().get(0);
        GraphicsCommand.Stroke stroke = (GraphicsCommand.Stroke) document.getCommands().get(1);

        assertEquals(PaintStyle.Kind.GRAY, fill.getFill().getKind());
        assertEquals(PaintStyle.Kind.RGB, stroke.getStrokeColor().getKind());
        assertEquals(0.0, stroke.getStrokeColor().getV0(), 1e-9);
        assertEquals(0.0, stroke.getStrokeColor().getV1(), 1e-9);
        assertEquals(1.0, stroke.getStrokeColor().getV2(), 1e-9);
    }

    @Test
    void recordsCtmOnCommand() {
        Matrix ctm = new Matrix(2, 0, 0, 2, 10, 20);
        Path path = new Path(List.of(new PathSegment.MoveTo(1, 1)));

        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 100))
                .addFill(path, PaintStyle.gray(0), WindingRule.NON_ZERO, ctm)
                .build();

        assertEquals(ctm, document.getCommands().get(0).getCtm());
    }

    @Test
    void optionalHiResBoundingBoxAndMetadata() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 50, 50))
                .setHiResBoundingBox(new BoundingBox(0, 0, 500, 500))
                .putMetadata("Title", "Test")
                .build();

        assertEquals(new BoundingBox(0, 0, 500, 500), document.getHiResBoundingBox());
        assertEquals("Test", document.metadata().get("Title"));
        assertEquals(0, document.getCommands().size());
    }

    @Test
    void defaultBoundingBoxWhenUnset() {
        EpsDocument document = new EpsDocumentBuilder().build();
        assertEquals(new BoundingBox(0, 0, 100, 100), document.getBoundingBox());
        assertNull(document.getHiResBoundingBox());
    }

    @Test
    void addTextOnTopPreservesPaintOrder() {
        EpsDocument document = new EpsDocumentBuilder()
                .setBoundingBox(new BoundingBox(0, 0, 100, 200))
                .addTextOnTop("(Blue)", 9.0, 162.0, "GothamXNarrow-BookItalic", 8.0,
                        PaintStyle.rgb(0.1, 0.1, 0.1), Matrix.identity(), null)
                .addEmbeddedImage(82, 29, new Matrix(0.24, 0, 0, 0.24, 0, 0), new byte[] {1, 2, 3})
                .build();

        assertEquals(2, document.getCommands().size());
        assertInstanceOf(GraphicsCommand.Text.class, document.getCommands().get(0));
        assertInstanceOf(GraphicsCommand.EmbeddedImage.class, document.getCommands().get(1));
    }
}
