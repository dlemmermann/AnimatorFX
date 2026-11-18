package com.dlsc.animatorfx.editor.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dlsc.animatorfx.editor.inspector.fields.HexColors;
import com.dlsc.animatorfx.model.Rgba;
import org.junit.jupiter.api.Test;

class HexColorsTest {

    @Test
    void parsesSixAndEightDigits() {
        assertEquals(new Rgba(1, 0, 0, 1), HexColors.parse("#FF0000").orElseThrow());
        assertEquals(Rgba.parse("#4C9AFF80"), HexColors.parse("#4c9aff80").orElseThrow());
    }

    @Test
    void isLenientAboutTheHashWhitespaceAndShortForms() {
        assertEquals(Rgba.parse("#4C9AFF"), HexColors.parse("  4C9AFF ").orElseThrow());
        assertEquals(Rgba.parse("#FFAA00"), HexColors.parse("#fa0").orElseThrow());
        assertEquals(Rgba.parse("#FFAA0088"), HexColors.parse("fa08").orElseThrow());
    }

    @Test
    void rejectsEverythingElse() {
        for (String text : new String[]{"", "#", "#12", "#12345", "#1234567", "#GGGGGG", "red", "#123456789", null}) {
            assertTrue(HexColors.parse(text).isEmpty(), String.valueOf(text));
        }
    }

    @Test
    void formatsWithoutAlphaWhenOpaque() {
        assertEquals("#4C9AFF", HexColors.format(Rgba.parse("#4C9AFF")));
        assertEquals("#4C9AFF80", HexColors.format(Rgba.parse("#4C9AFF80")));
        assertEquals("#00000000", HexColors.format(Rgba.TRANSPARENT));
    }

    @Test
    void hsvRoundTrips() {
        for (String hex : new String[]{"#4C9AFF", "#FFB454", "#000000", "#FFFFFF", "#12C83A"}) {
            Rgba color = Rgba.parse(hex);
            double[] hsv = HexColors.toHsv(color);
            assertEquals(hex, HexColors.format(HexColors.fromHsv(hsv[0], hsv[1], hsv[2], 1)));
        }
        assertEquals(0.5, HexColors.fromHsv(200, 0.4, 0.9, 0.5).a(), 1e-12);
    }
}
