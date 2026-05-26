package com.convert2web.image;

import java.io.ByteArrayOutputStream;

/**
 * PostScript {@code /RunLengthDecode} filter.
 */
public final class RunLengthDecoder {
    private RunLengthDecoder() {
    }

    public static byte[] decode(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        int i = 0;
        while (i < input.length) {
            int b = input[i++] & 0xFF;
            if (b < 128) {
                int count = b + 1;
                if (i + count > input.length) {
                    count = input.length - i;
                }
                out.write(input, i, count);
                i += count;
            } else if (b == 128) {
                // EOD
            } else {
                if (i >= input.length) {
                    break;
                }
                int count = 257 - b;
                byte value = input[i++];
                for (int n = 0; n < count; n++) {
                    out.write(value);
                }
            }
        }
        return out.toByteArray();
    }
}
