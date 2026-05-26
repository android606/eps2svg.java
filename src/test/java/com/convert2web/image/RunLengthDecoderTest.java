package com.convert2web.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RunLengthDecoderTest {

    @Test
    void decodesLiteralAndRepeatRuns() {
        byte[] input = {1, 65, 66, (byte) 253, 90};
        byte[] decoded = RunLengthDecoder.decode(input);
        assertArrayEquals(new byte[] {65, 66, 90, 90, 90, 90}, decoded);
    }
}
