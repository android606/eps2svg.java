package com.convert2web;

import com.convert2web.model.EpsDocument;
import com.convert2web.model.GraphicsCommand;
import com.convert2web.render.SvgRenderer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsciiEpsConverterTest {

    @Test
    void convertsBasicFillEpsToSvg() throws Exception {
        Path input = Path.of("test/test_images/test_basic_fill.eps");
        AsciiEpsConverter converter = new AsciiEpsConverter();
        EpsDocument document = converter.convertToDocument(input.toString());

        assertFalse(document.getCommands().isEmpty());
        assertInstanceOf(GraphicsCommand.Fill.class, document.getCommands().get(0));

        String svg = new SvgRenderer().render(document);
        assertTrue(svg.contains("viewBox=\"0 0 100 100\""));
        assertTrue(svg.contains("fill=\"rgb(255,0,0)\""));
        assertTrue(svg.contains("<path d=\""));
    }
}
