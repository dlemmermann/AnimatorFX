package com.dlsc.animatorfx.editor.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RulerTicksTest {

    @Test
    void coarseZoomUsesSeconds() {
        RulerTicks ticks = RulerTicks.choose(100, 60);
        assertEquals(1.0, ticks.major(), 1e-12);
        assertFalse(ticks.frames());
        assertEquals("0s", ticks.label(0, 60));
        assertEquals("2s", ticks.label(2, 60));
        assertEquals(10, ticks.minorPerMajor());
    }

    @Test
    void veryCoarseZoomUsesLargerSteps() {
        assertEquals(5.0, RulerTicks.choose(20, 60).major(), 1e-12);
        assertEquals(60.0, RulerTicks.choose(2, 60).major(), 1e-12);
        assertEquals("1:30", RulerTicks.choose(2, 60).label(90, 60));
    }

    @Test
    void fineZoomUsesFramesThatAlignWithSeconds() {
        RulerTicks ticks = RulerTicks.choose(600, 60);
        assertTrue(ticks.frames());
        assertEquals(10 / 60.0, ticks.major(), 1e-12);
        assertEquals("0:00f", ticks.label(0, 60));
        assertEquals("1:10f", ticks.label(1 + 10 / 60.0, 60));
        assertEquals("1:12f", ticks.label(1.2, 60));

        RulerTicks finest = RulerTicks.choose(5000, 60);
        assertEquals(1 / 60.0, finest.major(), 1e-12);
        assertEquals(1, finest.minorPerMajor());
    }

    @Test
    void frameStepsFollowTheFrameRate() {
        // 25 fps has no 2-frame or 10-frame step that lands on whole seconds
        RulerTicks ticks = RulerTicks.choose(400, 25);
        assertTrue(ticks.frames());
        assertEquals(5 / 25.0, ticks.major(), 1e-12);
        assertEquals(5, ticks.minorPerMajor());
    }

    @Test
    void minorTicksAreNeverCloserThanAFewPixels() {
        for (double pps : new double[]{3, 17, 42, 99, 310, 870, 2400}) {
            RulerTicks ticks = RulerTicks.choose(pps, 60);
            assertTrue(ticks.major() * pps >= RulerTicks.MIN_MAJOR_SPACING, "major at " + pps);
            assertTrue(ticks.major() / ticks.minorPerMajor() * pps >= RulerTicks.MIN_MINOR_SPACING - 1e-9,
                    "minor at " + pps);
        }
    }

    @Test
    void withoutAFrameRateThereAreOnlySeconds() {
        RulerTicks ticks = RulerTicks.choose(5000, 0);
        assertFalse(ticks.frames());
        assertEquals(1.0, ticks.major(), 1e-12);
    }
}
