package com.convert2web.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatrixTest {

    @Test
    void postConcatMatchesPostScriptConcat() {
        Matrix flipY = new Matrix(1, 0, 0, -1, 0, 240.123);
        Matrix scale = new Matrix(114.004, 0, 0, 163.598, 44.2625, 72.1628);
        Matrix combined = Matrix.identity().postConcat(flipY).postConcat(scale);

        assertEquals(114.004, combined.getA(), 1e-6);
        assertEquals(0, combined.getB(), 1e-6);
        assertEquals(0, combined.getC(), 1e-6);
        assertEquals(-163.598, combined.getD(), 1e-6);
        assertEquals(44.2625, combined.getE(), 1e-6);
        assertEquals(167.9602, combined.getF(), 1e-3);
    }

    @Test
    void transformUsesConcatenatedMatrix() {
        Matrix combined = new Matrix(1, 0, 0, -1, 0, 240.123)
                .postConcat(new Matrix(114.004, 0, 0, 163.598, 44.2625, 72.1628));
        assertEquals(44.2625, combined.transformX(0, 0), 1e-6);
        assertEquals(167.9602, combined.transformY(0, 0), 1e-3);
        assertEquals(158.2665, combined.transformX(1, 1), 1e-3);
        assertEquals(4.3622, combined.transformY(1, 1), 1e-3);
    }
}
