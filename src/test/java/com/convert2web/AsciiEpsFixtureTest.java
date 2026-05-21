package com.convert2web;

import com.convert2web.model.EpsDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * End-to-end ASCII VM conversion for fixtures that previously fell back to Batik.
 */
class AsciiEpsFixtureTest {

    @Test
    void arcTest() throws Exception {
        assertVmProducesCommands("test/test_images/arc_test.eps");
    }

    @Test
    void arcnTest() throws Exception {
        assertVmProducesCommands("test/test_images/arcn_test.eps");
    }

    @Test
    void arctTest() throws Exception {
        assertVmProducesCommands("test/test_images/arct_test.eps");
    }

    @Test
    void caution() throws Exception {
        assertVmProducesCommands("test/test_images/caution.eps");
    }

    @Test
    void ecRep() throws Exception {
        assertVmProducesCommands("test/test_images/ec_rep.eps");
    }

    @Test
    void subtleCurveTest() throws Exception {
        assertVmProducesCommands("test/test_images/subtle_curve_test.eps");
    }

    @Test
    void curvetoTest() throws Exception {
        assertVmProducesCommands("test/test_images/curveto_test.eps");
    }

    @Test
    void curves() throws Exception {
        assertVmProducesCommands("test/test_images/curves.eps");
    }

    private static void assertVmProducesCommands(String inputPath) throws Exception {
        EpsDocument document = new AsciiEpsConverter().convertToDocument(inputPath);
        assertFalse(document.getCommands().isEmpty(), inputPath);
    }
}
