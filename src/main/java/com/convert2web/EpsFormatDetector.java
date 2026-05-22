package com.convert2web;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Detects whether an input file is EPS/PostScript before conversion.
 */
public final class EpsFormatDetector {
    private static final int HEADER_SCAN_BYTES = 8192;

    public enum Format {
        ASCII_EPS,
        BINARY_EPS,
        PDF,
        PNG,
        JPEG,
        OTHER
    }

    private EpsFormatDetector() {
    }

    /**
     * @throws IOException if the file is not a supported EPS input
     */
    public static void validateEpsFile(String path) throws IOException {
        Format format = detectFormat(path);
        if (format == Format.ASCII_EPS || format == Format.BINARY_EPS) {
            return;
        }
        throw new IOException(formatErrorMessage(path, format));
    }

    public static Format detectFormat(String path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            int len = (int) Math.min(HEADER_SCAN_BYTES, raf.length());
            if (len <= 0) {
                return Format.OTHER;
            }
            byte[] header = new byte[len];
            raf.readFully(header);

            if (isDosBinaryMagic(header)) {
                return embeddedPostScriptPresent(path) ? Format.BINARY_EPS : Format.OTHER;
            }

            int start = skipLeadingWhitespace(header);
            if (startsWithAscii(header, start, "%PDF-")) {
                return Format.PDF;
            }
            if (startsWithAscii(header, start, "%!PS")) {
                return Format.ASCII_EPS;
            }
            if (header.length >= 4 && header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                return Format.PNG;
            }
            if (header.length >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return Format.JPEG;
            }
            if (containsLineStartingWith(header, "%!PS")) {
                return Format.ASCII_EPS;
            }
            return Format.OTHER;
        }
    }

    private static boolean embeddedPostScriptPresent(String path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            byte[] magic = new byte[4];
            if (raf.read(magic) != 4 || !isDosBinaryMagic(magic)) {
                return false;
            }
            int psOffset = readDWord(raf);
            int psLength = readDWord(raf);
            if (psOffset <= 0 || psLength <= 0 || psOffset + psLength > raf.length()) {
                return false;
            }
            int scan = Math.min(psLength, 65536);
            byte[] psHeader = new byte[scan];
            raf.seek(psOffset);
            raf.readFully(psHeader);
            return containsLineStartingWith(psHeader, "%!PS");
        }
    }

    private static boolean isDosBinaryMagic(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == (byte) 0xC5
                && bytes[1] == (byte) 0xD0
                && bytes[2] == (byte) 0xD3
                && bytes[3] == (byte) 0xC6;
    }

    private static int readDWord(RandomAccessFile raf) throws IOException {
        byte[] b = new byte[4];
        raf.readFully(b);
        return ((b[3] & 0xFF) << 24) | ((b[2] & 0xFF) << 16) | ((b[1] & 0xFF) << 8) | (b[0] & 0xFF);
    }

    private static int skipLeadingWhitespace(byte[] header) {
        int i = 0;
        while (i < header.length) {
            byte b = header[i];
            if (b == ' ' || b == '\t' || b == '\r' || b == '\n' || b == '\f') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static boolean startsWithAscii(byte[] header, int offset, String ascii) {
        if (offset + ascii.length() > header.length) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (header[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsLineStartingWith(byte[] header, String marker) {
        String text = new String(header, StandardCharsets.ISO_8859_1);
        for (String line : text.split("\\r\\n|\\r|\\n")) {
            if (line.startsWith(marker)) {
                return true;
            }
        }
        return text.contains(marker);
    }

    static String formatErrorMessage(String path, Format format) {
        String name = new java.io.File(path).getName();
        switch (format) {
            case PDF:
                return "Not an EPS file: \"" + name + "\" is a PDF document. "
                        + "Provide a PostScript or DOS binary EPS file.";
            case PNG:
                return "Not an EPS file: \"" + name + "\" is a PNG image.";
            case JPEG:
                return "Not an EPS file: \"" + name + "\" is a JPEG image.";
            case OTHER:
            default:
                return "Not an EPS file: \"" + name
                        + "\" does not contain a PostScript header (%!PS) or DOS binary EPS magic.";
        }
    }
}
