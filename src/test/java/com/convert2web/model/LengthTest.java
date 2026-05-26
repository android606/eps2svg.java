package com.convert2web.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LengthTest {

    @Test
    void parsesUnitlessAsPixels() {
        Length length = Length.parse("50");
        assertEquals(Length.Unit.PX, length.unit());
        assertEquals(50.0, length.toPixels(200), 1e-6);
        assertEquals("50", length.format());
    }

    @Test
    void parsesExplicitUnits() {
        assertEquals(96.0, Length.parse("1in").toPixels(0), 1e-6);
        assertEquals(8.5 * 96, Length.parse("8.5in").toPixels(0), 1e-6);
        assertEquals(100.0, Length.parse("50%").toPixels(200), 1e-6);
        assertEquals("8.5in", Length.parse("8.5in").format());
    }

    @Test
    void rejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> Length.parse("wide"));
    }
}
