package com.convert2web;

import com.convert2web.model.BoundingBox;
import com.convert2web.model.EpsDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports which conversion tier succeeds per fixture. Run with:
 * {@code mvn test -Dtest=FullPrologTierDiagnosticTest#reportTierCoverage}
 */
class FullPrologTierDiagnosticTest {

    @Test
    void reportTierCoverage() throws IOException {
        List<Path> epsFiles = new ArrayList<>();
        Files.walkFileTree(Path.of("test"), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".eps")) {
                    epsFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        System.out.println("EPS tier report (full > prolog+page > page > fail):");
        int fullOk = 0;
        int prologOk = 0;
        int pageOk = 0;
        int fail = 0;
        for (Path path : epsFiles) {
            String tier = tierFor(path);
            System.out.println(tier + "\t" + path);
            if ("full".equals(tier)) {
                fullOk++;
            } else if ("prolog+page".equals(tier)) {
                prologOk++;
            } else if ("page".equals(tier)) {
                pageOk++;
            } else {
                fail++;
            }
        }
        System.out.printf("Total %d: full=%d prolog+page=%d page=%d fail=%d%n",
                epsFiles.size(), fullOk, prologOk, pageOk, fail);

        // Illustrator binaries should use full tier (page-only is fallback).
        int illustratorPageOnly = 0;
        for (Path path : epsFiles) {
            String name = path.toString();
            if (name.contains("binary.eps") || name.endsWith("-binary.eps")
                    || name.contains("UDI.eps") || name.contains("reuse_1.eps")) {
                if ("page".equals(tierFor(path))) {
                    illustratorPageOnly++;
                }
            }
        }
        if (illustratorPageOnly > 0) {
            throw new AssertionError("Expected full tier for Illustrator binaries, got page-only="
                    + illustratorPageOnly);
        }
    }

    private static String tierFor(Path path) throws IOException {
        String ps = readPostScript(path);
        BoundingBox bbox = bboxFromHeader(ps);

        if (AdobeIllustratorPageRunner.isAdobeIllustratorEps(ps)) {
            EpsDocument d = AdobeIllustratorPageRunner.runFullPostScript(ps, bbox);
            if (hasPaths(d)) {
                return "full";
            }
            d = AdobeIllustratorPageRunner.runPrologThenPageBody(ps, bbox);
            if (hasPaths(d)) {
                return "prolog+page";
            }
            String body = AdobeIllustratorPageRunner.extractPageBody(ps);
            if (body != null) {
                d = AdobeIllustratorPageRunner.runPageBody(body, bbox, ps);
                if (hasPaths(d)) {
                    return "page";
                }
            }
            return "FAIL";
        }

        EpsDocument full = runAsciiFull(ps, bbox);
        if (hasPaths(full)) {
            return "full";
        }
        try {
            new AsciiEpsConverter().convertToDocument(path.toString());
            return "ascii-pipeline";
        } catch (Exception e) {
            return "FAIL";
        }
    }

    private static EpsDocument runAsciiFull(String ps, BoundingBox bbox) {
        return AdobeIllustratorPageRunner.runFullPostScript(ps, bbox, false);
    }

    private static boolean hasPaths(EpsDocument d) {
        return d != null && !d.getCommands().isEmpty();
    }

    private static String readPostScript(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        if (data.length >= 4 && data[0] == (byte) 0xC5) {
            int off = ((data[7] & 0xFF) << 24) | ((data[6] & 0xFF) << 16)
                    | ((data[5] & 0xFF) << 8) | (data[4] & 0xFF);
            int ln = ((data[11] & 0xFF) << 24) | ((data[10] & 0xFF) << 16)
                    | ((data[9] & 0xFF) << 8) | (data[8] & 0xFF);
            return new String(data, off, Math.min(ln, data.length - off), StandardCharsets.ISO_8859_1);
        }
        return Files.readString(path, StandardCharsets.ISO_8859_1);
    }

    private static BoundingBox bboxFromHeader(String ps) {
        for (String line : ps.split("\\r\\n|\\r|\\n")) {
            if (line.startsWith("%%BoundingBox:")) {
                String[] p = line.substring(14).trim().split("\\s+");
                if (p.length == 4) {
                    return new BoundingBox(
                            Double.parseDouble(p[0]),
                            Double.parseDouble(p[1]),
                            Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]));
                }
            }
        }
        return null;
    }
}
