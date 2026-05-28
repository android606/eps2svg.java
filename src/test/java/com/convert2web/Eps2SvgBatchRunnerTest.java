package com.convert2web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Eps2SvgBatchRunnerTest {
    private static final Path FIXTURE_ROOT = Path.of("test/test_images");

    @Test
    void collectInputsMatchesRecursiveGlob(@TempDir Path temp) throws IOException {
        Path nested = temp.resolve("nested");
        Files.createDirectories(nested);
        Files.writeString(temp.resolve("top.eps"), "%!PS\n");
        Files.writeString(nested.resolve("child.eps"), "%!PS\n");
        Files.writeString(temp.resolve("skip.txt"), "x");

        List<Path> all = Eps2SvgBatchRunner.collectInputs(temp, "**/*.eps");
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(p -> p.getFileName().toString().equals("top.eps")));
        assertTrue(all.stream().anyMatch(p -> p.getFileName().toString().equals("child.eps")));
    }

    @Test
    void collectInputsHonorsShallowGlob(@TempDir Path temp) throws IOException {
        Path nested = temp.resolve("nested");
        Files.createDirectories(nested);
        Files.writeString(temp.resolve("top.eps"), "%!PS\n");
        Files.writeString(nested.resolve("child.eps"), "%!PS\n");

        List<Path> topOnly = Eps2SvgBatchRunner.collectInputs(temp, "*.eps");
        assertEquals(1, topOnly.size());
        assertEquals("top.eps", topOnly.get(0).getFileName().toString());
    }

    @Test
    void outputPathPreservesRelativeLayout(@TempDir Path temp) {
        Path inputRoot = temp.resolve("in");
        Path outputRoot = temp.resolve("out");
        Path input = inputRoot.resolve("a/b/file.eps");

        Path output = Eps2SvgBatchRunner.outputPath(inputRoot, outputRoot, input);
        assertEquals(outputRoot.resolve("a/b/file.svg"), output);
    }

    @Test
    void batchRunConvertsAsciiFixtures(@TempDir Path temp) throws IOException {
        Path inputRoot = temp.resolve("in");
        Path outputRoot = temp.resolve("out");
        Files.createDirectories(inputRoot);
        Files.copy(FIXTURE_ROOT.resolve("test_basic_fill.eps"), inputRoot.resolve("one.eps"));
        Files.copy(FIXTURE_ROOT.resolve("test_basic_stroke.eps"), inputRoot.resolve("two.eps"));

        Eps2SvgCli.ParsedCommand command = Eps2SvgCli.parse(new String[] {
                "--batch", inputRoot.toString(), outputRoot.toString()
        });

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        int exit = new Eps2SvgBatchRunner(command, new PrintStream(outBuf), new PrintStream(errBuf)).run();

        assertEquals(0, exit);
        assertTrue(Files.exists(outputRoot.resolve("one.svg")));
        assertTrue(Files.exists(outputRoot.resolve("two.svg")));
        String progress = outBuf.toString();
        assertTrue(progress.contains("OK one.eps"));
        assertTrue(progress.contains("OK two.eps"));
        assertTrue(progress.contains("DONE ok=2 fail=0 total=2"));
    }
}
