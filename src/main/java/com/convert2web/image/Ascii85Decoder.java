package com.convert2web.image;

import java.io.ByteArrayOutputStream;

/**
 * Decodes Adobe ASCII85 text (with or without {@code <~ ~>} delimiters).
 */
public final class Ascii85Decoder {
    private Ascii85Decoder() {
    }

    public static byte[] decode(String text) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }
        String data = text.trim();
        if (data.startsWith("<~")) {
            data = data.substring(2);
        }
        if (data.endsWith("~>")) {
            data = data.substring(0, data.length() - 2);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length());
        char[] group = new char[5];
        int groupLen = 0;
        for (int i = 0; i < data.length(); i++) {
            char ch = data.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch == '~') {
                break;
            }
            if (ch == 'z') {
                if (groupLen != 0) {
                    throw new IllegalArgumentException("z in partial ASCII85 group");
                }
                out.write(0);
                out.write(0);
                out.write(0);
                out.write(0);
                continue;
            }
            group[groupLen++] = ch;
            if (groupLen == 5) {
                appendGroup(out, group, 4);
                groupLen = 0;
            }
        }
        if (groupLen > 0) {
            for (int i = groupLen; i < 5; i++) {
                group[i] = 'u';
            }
            appendGroup(out, group, groupLen - 1);
        }
        return out.toByteArray();
    }

    private static void appendGroup(ByteArrayOutputStream out, char[] group, int emitBytes) {
        long value = 0;
        for (int i = 0; i < 5; i++) {
            value = value * 85 + (group[i] - '!');
        }
        for (int i = 3; i >= 4 - emitBytes; i--) {
            out.write((int) ((value >> (8 * i)) & 0xFF));
        }
    }
}
