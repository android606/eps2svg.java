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

        public ParsedCommand(
                String inputPath, String outputPath, SvgRenderOptions renderOptions, boolean help) {
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.renderOptions = renderOptions;
            this.help = help;
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
    }

    public static ParsedCommand parse(String[] args) {
        List<String> positional = new ArrayList<>();
        SvgRenderOptions.Builder options = SvgRenderOptions.builder();
        boolean help = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg)) {
                help = true;
                continue;
            }
            if (isOption(arg)) {
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
                    default:
                        throw new IllegalArgumentException("Unknown option: " + arg);
                }
                continue;
            }
            positional.add(arg);
        }

        String input = positional.size() > 0 ? positional.get(0) : null;
        String output = positional.size() > 1 ? positional.get(1) : null;
        return new ParsedCommand(input, output, options.build(), help);
    }

    private static boolean isOption(String arg) {
        return "--min-width".equals(arg)
                || "--min-height".equals(arg)
                || "--max-width".equals(arg)
                || "--max-height".equals(arg);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index + 1];
    }

    public static void printHelp(java.io.PrintStream out) {
        out.println("Usage: eps2svg [options] <input.eps> <output.svg>");
        out.println();
        out.println("Converts EPS to SVG via the ASCII/binary PostScript pipeline.");
        out.println();
        out.println("Options:");
        out.println("  -h, --help           Show this help");
        out.println("  --min-width <len>    Minimum root width (px, pt, in, cm, mm, em, ex, %, ...)");
        out.println("  --min-height <len>   Minimum root height");
        out.println("  --max-width <len>    Maximum root width (overrides conflicting min)");
        out.println("  --max-height <len>   Maximum root height (overrides conflicting min)");
        out.println();
        out.println("Display limits scale width/height proportionally; viewBox is unchanged.");
        out.println("Examples:");
        out.println("  eps2svg icon.eps icon.svg");
        out.println("  eps2svg --min-width 100 --min-height 100 icon.eps icon.svg");
        out.println("  eps2svg --max-width 8.5in --max-height 11in large.eps large.svg");
    }
}
