package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RgbaTest {

    @Test
    void hexRoundTrip() {
        Rgba c = Rgba.parse("#11223344");
        assertEquals(0x11 / 255.0, c.r(), 1e-12);
        assertEquals(0x22 / 255.0, c.g(), 1e-12);
        assertEquals(0x33 / 255.0, c.b(), 1e-12);
        assertEquals(0x44 / 255.0, c.a(), 1e-12);
        assertEquals("#11223344", c.toHex());
    }

    @Test
    void sixDigitInputIsOpaque() {
        Rgba c = Rgba.parse("#aabbcc");
        assertEquals(1.0, c.a());
        assertEquals("#AABBCCFF", c.toHex());
    }

    @Test
    void constants() {
        assertEquals("#000000FF", Rgba.BLACK.toHex());
        assertEquals("#FFFFFFFF", Rgba.WHITE.toHex());
        assertEquals("#00000000", Rgba.TRANSPARENT.toHex());
    }

    @Test
    void toHexClampsOutOfRangeComponents() {
        assertEquals("#FF0080FF", new Rgba(1.5, -0.2, 128 / 255.0, 1).toHex());
    }

    @Test
    void rejectsMalformedInput() {
        for (String bad : new String[] {"112233", "#12345", "#GG0000", "#+1+2+3", "#112233445", ""}) {
            assertThrows(IllegalArgumentException.class, () -> Rgba.parse(bad), bad);
        }
    }

    @Test
    void lerpIsLinearAndStaysInRange() {
        assertEquals(new Rgba(0.5, 0.5, 0.5, 0.5), Rgba.TRANSPARENT.lerp(Rgba.WHITE, 0.5));
        assertEquals(Rgba.WHITE, Rgba.BLACK.lerp(Rgba.WHITE, 1.3));
        assertEquals(new Rgba(0, 0, 0, 1), Rgba.BLACK.lerp(Rgba.WHITE, -0.3));
    }
}
