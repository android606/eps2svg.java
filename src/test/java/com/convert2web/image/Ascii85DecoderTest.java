package com.convert2web.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Ascii85DecoderTest {

    @Test
    void decodesZeroGroup() {
        assertArrayEquals(new byte[] {0, 0, 0, 0}, Ascii85Decoder.decode("z"));
    }

    @Test
    void decodesPartialFinalGroup() {
        byte[] decoded = Ascii85Decoder.decode("rrE");
        assertEquals(2, decoded.length);
    }
}
