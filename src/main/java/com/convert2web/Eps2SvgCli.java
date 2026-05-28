package com.convert2web;

import com.convert2web.render.SvgRenderOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Command-line argument parsing for {@link App} and {@link EpsToSvgConverter}.
 */
public final class Eps2SvgCli {
    private Eps2SvgCli() {
    }

    public static final class ParsedCommand {
        private final String inputPath;
        private final String outputPath;
        private final SvgRenderOptions renderOptions;
        private final boolean help;
        private final boolean batch;
        private final String globPattern;

        public ParsedCommand(
                String inputPath,
                String outputPath,
                SvgRenderOptions renderOptions,
                boolean help,
                boolean batch,
                String globPattern) {
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.renderOptions = renderOptions;
            this.help = help;
            this.batch = batch;
            this.globPattern = globPattern;
        }

        public String inputPath() {
            return inputPath;
        }

        public String outputPath() {
            return outputPath;
        }

        public SvgRenderOptions renderOptions() {
            return renderOptions;
        }

        public boolean help() {
            return help;
        }

        public boolean batch() {
            return batch;
        }

        public String globPattern() {
            return globPattern;
        }
    }

    public static ParsedCommand parse(String[] args) {
        List<String> positional = new ArrayList<>();
        SvgRenderOptions.Builder options = SvgRenderOptions.builder();
        boolean help = false;
        boolean batch = false;
        String globPattern = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg)) {
                help = true;
                continue;
            }
            if (isFlag(arg)) {
                if ("--trace-source".equals(arg)) {
                    options.emitSourceTrace(true);
                } else if ("--batch".equals(arg)) {
                    batch = true;
                }
                continue;
            }
            if (arg.startsWith("--glob=")) {
                globPattern = arg.substring("--glob=".length());
                continue;
            }
            if (arg.startsWith("--substitute-fonts=")) {
                options.substituteFonts(parseYesNo(arg.substring("--substitute-fonts=".length()), arg));
                continue;
            }
            if (arg.startsWith("--font-metrics=")) {
                options.fontMetricsMode(SvgRenderOptions.FontMetricsMode.parse(
                        arg.substring("--font-metrics=".length())));
                continue;
            }
            if (isValuedOption(arg)) {
                String value = requireValue(args, i, arg);
                i++;
                switch (arg) {
                    case "--min-width":
                        options.minWidth(value);
                        break;
                    case "--min-height":
                        options.minHeight(value);
                        break;
                    case "--max-width":
                        options.maxWidth(value);
                        break;
                    case "--max-height":
                        options.maxHeight(value);
                        break;
                    case "--substitute-fonts":
                        options.substituteFonts(parseYesNo(value, arg));
                        break;
                    case "--font-metrics":
                        options.fontMetricsMode(SvgRenderOptions.FontMetricsMode.parse(value));
                        break;
                    case "--glob":
                        globPattern = value;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown option: " + arg);
                }
                continue;
            }
            positional.add(arg);
        }

        String input = positional.size() > 0 ? positional.get(0) : null;
        String output = positional.size() > 1 ? positional.get(1) : null;
        return new ParsedCommand(input, output, options.build(), help, batch, globPattern);
    }

    private static boolean isFlag(String arg) {
        return "--trace-source".equals(arg) || "--batch".equals(arg);
    }

    private static boolean isValuedOption(String arg) {
        return "--min-width".equals(arg)
                || "--min-height".equals(arg)
                || "--max-width".equals(arg)
                || "--max-height".equals(arg)
                || "--substitute-fonts".equals(arg)
                || "--font-metrics".equals(arg)
                || "--glob".equals(arg);
    }

    private static boolean parseYesNo(String value, String option) {
        if ("yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("no".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Expected yes or no for " + option + ": " + value);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index + 1];
    }

    public static void printHelp(java.io.PrintStream out) {
        out.println("Usage: eps2svg [options] <input.eps> <output.svg>");
        out.println("       eps2svg [options] --batch <input-dir> <output-dir>");
        out.println();
        out.println("Converts EPS to SVG via the ASCII/binary PostScript pipeline.");
        out.println();
        out.println("Options:");
        out.println("  -h, --help           Show this help");
        out.println("  --batch              Convert all matching EPS under input-dir into output-dir");
        out.println("  --glob <pattern>     Glob relative to input-dir (default: **/*.eps)");
        out.println("  --min-width <len>    Minimum root width (px, pt, in, cm, mm, em, ex, %, ...)");
        out.println("  --min-height <len>   Minimum root height");
        out.println("  --max-width <len>    Maximum root width (overrides conflicting min)");
        out.println("  --max-height <len>   Maximum root height (overrides conflicting min)");
        out.println("  --trace-source       EPS line/column/offset on graphics elements (data-* + comments)");
        out.println("  --substitute-fonts <yes|no>");
        out.println("                       Emit resolved fallback fonts first when source fonts are unavailable");
        out.println("  --font-metrics <relative|absolute|auto>");
        out.println("                       Text spacing mode: relative dx, absolute x, or renderer-native spacing");
        out.println();
        out.println("Display limits scale width/height proportionally; viewBox is unchanged.");
        out.println("Batch mode preserves subdirectory layout and writes one SVG per EPS.");
        out.println("Examples:");
        out.println("  eps2svg icon.eps icon.svg");
        out.println("  eps2svg --min-width 100 --min-height 100 icon.eps icon.svg");
        out.println("  eps2svg --max-width 8.5in --max-height 11in large.eps large.svg");
        out.println("  eps2svg --batch --substitute-fonts=no /data/eps /data/svg");
        out.println("  eps2svg --batch --glob '*.eps' ./in ./out");
    }
}
