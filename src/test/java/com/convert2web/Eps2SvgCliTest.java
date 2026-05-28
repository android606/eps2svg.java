package com.convert2web;

import com.convert2web.render.SvgRenderOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Eps2SvgCliTest {
    @Test
    void defaultsToNoSubstitutionAndAbsoluteMetrics() {
        Eps2SvgCli.ParsedCommand parsed = Eps2SvgCli.parse(new String[] {"in.eps", "out.svg"});

        assertFalse(parsed.renderOptions().substituteFonts());
        assertEquals(SvgRenderOptions.FontMetricsMode.ABSOLUTE, parsed.renderOptions().fontMetricsMode());
    }

    @Test
    void substitutionDefaultsToRelativeMetrics() {
        Eps2SvgCli.ParsedCommand parsed = Eps2SvgCli.parse(new String[] {
                "--substitute-fonts", "yes", "in.eps", "out.svg"
        });

        assertTrue(parsed.renderOptions().substituteFonts());
        assertEquals(SvgRenderOptions.FontMetricsMode.RELATIVE, parsed.renderOptions().fontMetricsMode());
    }

    @Test
    void explicitFontMetricsOverridesSubstitutionDefault() {
        Eps2SvgCli.ParsedCommand parsed = Eps2SvgCli.parse(new String[] {
                "--substitute-fonts=yes", "--font-metrics=auto", "in.eps", "out.svg"
        });

        assertTrue(parsed.renderOptions().substituteFonts());
        assertEquals(SvgRenderOptions.FontMetricsMode.AUTO, parsed.renderOptions().fontMetricsMode());
    }

    @Test
    void batchModeParsesInputAndOutputDirs() {
        Eps2SvgCli.ParsedCommand parsed = Eps2SvgCli.parse(new String[] {
                "--batch", "--glob=*.eps", "--substitute-fonts=no", "/in", "/out"
        });

        assertTrue(parsed.batch());
        assertEquals("/in", parsed.inputPath());
        assertEquals("/out", parsed.outputPath());
        assertEquals("*.eps", parsed.globPattern());
        assertFalse(parsed.renderOptions().substituteFonts());
    }
}
