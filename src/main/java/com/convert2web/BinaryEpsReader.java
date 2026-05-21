package com.convert2web;

import com.convert2web.model.BoundingBox;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Reads DOS binary EPS structure (magic C5D0D3C6).
 */
public final class BinaryEpsReader {
    private BinaryEpsReader() {
    }

    /** True when the file starts with the DOS binary EPS magic (C5 D0 D3 C6). */
    public static boolean isDosBinaryEps(String epsFilePath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(epsFilePath, "r")) {
            byte[] magic = new byte[4];
            if (raf.read(magic) != 4) {
                return false;
            }
            return magic[0] == (byte) 0xC5 && magic[1] == (byte) 0xD0
                    && magic[2] == (byte) 0xD3 && magic[3] == (byte) 0xC6;
        }
    }

    public static BinaryEpsData read(String epsFilePath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(epsFilePath, "r")) {
            byte[] magic = new byte[4];
            if (raf.read(magic) != 4) {
                return null;
            }
            if (magic[0] != (byte) 0xC5 || magic[1] != (byte) 0xD0
                    || magic[2] != (byte) 0xD3 || magic[3] != (byte) 0xC6) {
                return null;
            }

            BinaryEpsData data = new BinaryEpsData();
            data.postScriptOffset = readDWord(raf);
            data.postScriptLength = readDWord(raf);
            int wmfOffset = readDWord(raf);
            int wmfLength = readDWord(raf);
            int tiffOffset = readDWord(raf);
            int tiffLength = readDWord(raf);

            if (tiffOffset > 0 && tiffLength > 0) {
                data.previewType = "TIFF";
                data.previewData = new byte[tiffLength];
                raf.seek(tiffOffset);
                raf.readFully(data.previewData);
            } else if (wmfOffset > 0 && wmfLength > 0) {
                data.previewType = "WMF";
                data.previewData = new byte[wmfLength];
                raf.seek(wmfOffset);
                raf.readFully(data.previewData);
            }

            if (data.postScriptOffset > 0 && data.postScriptLength > 0) {
                data.postScriptData = new byte[data.postScriptLength];
                raf.seek(data.postScriptOffset);
                raf.readFully(data.postScriptData);
                data.boundingBox = extractBoundingBox(data.postScriptData);
            }
            return data;
        }
    }

    private static BoundingBox extractBoundingBox(byte[] psData) {
        String header = new String(psData, 0, Math.min(psData.length, 65536), StandardCharsets.ISO_8859_1);
        for (String line : header.split("\\r\\n|\\r|\\n")) {
            if (line.startsWith("%%BoundingBox:")) {
                String[] parts = line.substring("%%BoundingBox:".length()).trim().split("\\s+");
                if (parts.length == 4) {
                    return new BoundingBox(
                            Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]));
                }
            }
        }
        return null;
    }

    private static int readDWord(RandomAccessFile raf) throws IOException {
        byte[] buffer = new byte[4];
        raf.readFully(buffer);
        return ((buffer[3] & 0xFF) << 24)
                | ((buffer[2] & 0xFF) << 16)
                | ((buffer[1] & 0xFF) << 8)
                | (buffer[0] & 0xFF);
    }

    public static final class BinaryEpsData {
        public int postScriptOffset;
        public int postScriptLength;
        public byte[] postScriptData;
        public byte[] previewData;
        public String previewType;
        public BoundingBox boundingBox;
    }
}
