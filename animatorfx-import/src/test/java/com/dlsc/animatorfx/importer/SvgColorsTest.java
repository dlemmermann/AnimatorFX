package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Rgba;
import org.junit.jupiter.api.Test;

import static com.dlsc.animatorfx.importer.ImportChecks.assertColor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SvgColorsTest {

    @Test
    void hexForms() {
        assertColor(1, 0, 0, 1, SvgColors.parse("#f00"));
        assertColor(1, 0, 0, 0.8, SvgColors.parse("#F00C"));
        assertColor(0x12 / 255.0, 0x34 / 255.0, 0x56 / 255.0, 1, SvgColors.parse("#123456"));
        assertColor(0x12 / 255.0, 0x34 / 255.0, 0x56 / 255.0, 0x78 / 255.0, SvgColors.parse(" #12345678 "));
        assertNull(SvgColors.parse("#12"));
        assertNull(SvgColors.parse("#12345g"));
    }

    @Test
    void functionalForms() {
        assertColor(1, 128 / 255.0, 0, 1, SvgColors.parse("rgb(255, 128, 0)"));
        assertColor(1, 0.5, 0, 1, SvgColors.parse("rgb(100%,50%,0%)"));
        assertColor(0, 0, 1, 0.25, SvgColors.parse("rgba(0 0 255 / 0.25)"));
        assertColor(0, 0, 1, 0.5, SvgColors.parse("RGBA(0, 0, 255, 50%)"));
        assertColor(1, 0, 0, 1, SvgColors.parse("rgb(300, -5, 0)"));
        assertNull(SvgColors.parse("rgb(1, 2)"));
        assertNull(SvgColors.parse("rgb(a, b, c)"));
        assertNull(SvgColors.parse("hsl(0, 100%, 50%)"));
    }

    @Test
    void names() {
        assertColor(1, 0, 0, 1, SvgColors.parse("red"));
        assertColor(0, 128 / 255.0, 0, 1, SvgColors.parse("Green"));
        assertColor(0x66 / 255.0, 0x33 / 255.0, 0x99 / 255.0, 1, SvgColors.parse("#663399"));
        assertColor(0xFA / 255.0, 0xFA / 255.0, 0xD2 / 255.0, 1, SvgColors.parse("lightgoldenrodyellow"));
        assertEquals(SvgColors.parse("gray"), SvgColors.parse("grey"));
        assertEquals(Rgba.TRANSPARENT, SvgColors.parse("transparent"));
        assertNull(SvgColors.parse("nosuchcolour"));
        assertNull(SvgColors.parse(null));
    }

    @Test
    void lengths() {
        assertEquals(12, SvgLength.parse("12", 0, -1));
        assertEquals(12, SvgLength.parse(" 12px ", 0, -1));
        assertEquals(16, SvgLength.parse("12pt", 0, -1), 1e-12);
        assertEquals(96, SvgLength.parse("1in", 0, -1));
        assertEquals(96, SvgLength.parse("2.54cm", 0, -1), 1e-12);
        assertEquals(96, SvgLength.parse("25.4mm", 0, -1), 1e-12);
        assertEquals(50, SvgLength.parse("25%", 200, -1));
        assertEquals(16, SvgLength.parse("1em", 0, -1));
        assertEquals(1000, SvgLength.parse("1e3", 0, -1));
        assertEquals(-1, SvgLength.parse("wide", 0, -1));
        assertEquals(-1, SvgLength.parse(null, 0, -1));
    }
}
