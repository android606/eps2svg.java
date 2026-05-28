package com.convert2web;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Converts many EPS files under an input directory using one {@link EpsToSvgConverter} instance.
 */
public final class Eps2SvgBatchRunner {
    public static final String DEFAULT_GLOB = "**/*.eps";

    private final Path inputRoot;
    private final Path outputRoot;
    private final String globPattern;
    private final EpsToSvgConverter converter;
    private final PrintStream out;
    private final PrintStream err;

    public Eps2SvgBatchRunner(Eps2SvgCli.ParsedCommand command) {
        this(command, System.out, System.err);
    }

    Eps2SvgBatchRunner(Eps2SvgCli.ParsedCommand command, PrintStream out, PrintStream err) {
        this.inputRoot = Path.of(command.inputPath()).toAbsolutePath().normalize();
        this.outputRoot = Path.of(command.outputPath()).toAbsolutePath().normalize();
        this.globPattern = command.globPattern() == null ? DEFAULT_GLOB : command.globPattern();
        this.converter = new EpsToSvgConverter(command.renderOptions());
        this.out = out;
        this.err = err;
    }

    public int run() throws IOException {
        if (!Files.isDirectory(inputRoot)) {
            err.println("Batch input is not a directory: " + inputRoot);
            return 1;
        }
        Files.createDirectories(outputRoot);

        List<Path> inputs = collectInputs(inputRoot, globPattern);
        int ok = 0;
        int fail = 0;

        for (Path input : inputs) {
            Path output = outputPath(inputRoot, outputRoot, input);
            String rel = inputRoot.relativize(input).toString().replace('\\', '/');
            try {
                Files.createDirectories(output.getParent());
                converter.convert(input.toString(), output.toString());
                out.println("OK " + rel);
                ok++;
            } catch (IOException e) {
                err.println("FAIL " + rel + ": " + e.getMessage());
                fail++;
            }
        }

        int total = ok + fail;
        out.println("DONE ok=" + ok + " fail=" + fail + " total=" + total);
        return fail > 0 ? 1 : 0;
    }

    static List<Path> collectInputs(Path inputRoot, String globPattern) throws IOException {
        PathMatcher matcher = usesRecursiveGlob(globPattern)
                ? null
                : FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        List<Path> found = new ArrayList<>();
        try (var walk = Files.walk(inputRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> {
                        Path rel = inputRoot.relativize(path);
                        if (rel.startsWith("..")) {
                            return false;
                        }
                        if (matcher == null) {
                            return rel.toString().toLowerCase(Locale.ROOT).endsWith(".eps");
                        }
                        return matcher.matches(rel);
                    })
                    .forEach(found::add);
        }
        found.sort(Comparator.comparing(path -> inputRoot.relativize(path).toString()));
        return found;
    }

    private static boolean usesRecursiveGlob(String globPattern) {
        return DEFAULT_GLOB.equals(globPattern) || globPattern.contains("**");
    }

    static Path outputPath(Path inputRoot, Path outputRoot, Path input) {
        Path rel = inputRoot.relativize(input);
        String name = rel.getFileName().toString();
        String svgName = name.toLowerCase(Locale.ROOT).endsWith(".eps")
                ? name.substring(0, name.length() - 4) + ".svg"
                : name + ".svg";
        Path parent = rel.getParent();
        Path svgRel = parent == null ? Path.of(svgName) : parent.resolve(svgName);
        return outputRoot.resolve(svgRel);
    }
}
