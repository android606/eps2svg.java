package com.convert2web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinaryEpsConverterTest {

    @Test
    void convertsUdiBinaryEpsToVectorSvg() throws Exception {
        Path input = Path.of("test/test_images/UDI.eps");
        Path output = Files.createTempFile("udi", ".svg");
        try {
            boolean ok = new BinaryEpsConverter().tryConvert(input.toString(), output.toString());
            assertTrue(ok);
            String svg = Files.readString(output);
            assertTrue(svg.contains("viewBox=\"0 0 71 43\""), () -> "got: " + svg.substring(0, Math.min(200, svg.length())));
            assertTrue(svg.contains("<path"));
            assertFalse(svg.contains("data:image/png;base64,"));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void convertsEcRepBinaryToVectorSvg() throws Exception {
        Path input = Path.of("test/test_images/ec_rep-binary.eps");
        Path output = Files.createTempFile("ec-rep-binary", ".svg");
        try {
            assertTrue(new BinaryEpsConverter().tryConvert(input.toString(), output.toString()));
            String svg = Files.readString(output);
            assertTrue(svg.contains("viewBox=\"0 0 72 23\""), () -> svg.substring(0, Math.min(200, svg.length())));
            assertTrue(svg.contains("<path"));
            assertTrue(svg.contains("M277.0381"), () -> "expected letter glyphs: " + svg.substring(0, 500));
            assertTrue(svg.contains("scale(1,-1)"), () -> "non-zero bbox needs EPS Y-flip");
            assertTrue(svg.contains("fill-rule=\"evenodd\""), () -> "compound glyphs need holes");
            assertTrue(svg.contains("M340.2295 407"), () -> "outer frame should paint behind text");
            assertFalse(svg.contains("data:image/png;base64,"));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void convertsRemainingRealLiveBinaryEpsToVectorSvg() throws Exception {
        String[] names = {
                "v15624285",
                "v1641556",
                "v1885220",
                "v1658963",
                "v1658963_en-ca"
        };
        for (String name : names) {
            Path input = Path.of("test/test_images/real_live_images", name + ".eps");
            Path output = Files.createTempFile(name, ".svg");
            try {
                assertTrue(new BinaryEpsConverter().tryConvert(input.toString(), output.toString()), name);
                String svg = Files.readString(output);
                assertTrue(svg.contains("<path"), name + ": " + svg.substring(0, Math.min(200, svg.length())));
                assertFalse(svg.contains("data:image/png;base64,"), name);
            } finally {
                Files.deleteIfExists(output);
            }
        }
    }

    @Test
    void convertsMdAndCeBinaryEpsToVectorSvg() throws Exception {
        for (String name : new String[] {"MD-binary.eps", "CE_0344-binary.eps"}) {
            Path input = Path.of("test/test_images", name);
            Path output = Files.createTempFile("binary-eps", ".svg");
            try {
                assertTrue(new BinaryEpsConverter().tryConvert(input.toString(), output.toString()));
                String svg = Files.readString(output);
                assertTrue(svg.contains("<path"), name);
                assertFalse(svg.contains("data:image/png;base64,"), name);
            } finally {
                Files.deleteIfExists(output);
            }
        }
    }
}
