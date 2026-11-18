package com.dlsc.animatorfx.editor.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimeScaleTest {

    @Test
    void timeAndPixelsMapBothWays() {
        TimeScale scale = new TimeScale(200, 0.5);
        assertEquals(TimeScale.INSET, scale.toX(0.5), 1e-9);
        assertEquals(TimeScale.INSET + 200, scale.toX(1.5), 1e-9);
        assertEquals(1.5, scale.toTime(TimeScale.INSET + 200), 1e-9);
        for (double t : new double[]{0, 0.37, 2.9}) {
            assertEquals(t, scale.toTime(scale.toX(t)), 1e-9);
        }
        assertEquals(0.25, scale.toDuration(50), 1e-9);
    }

    @Test
    void zoomOneFitsTheWholeDuration() {
        TimeScale scale = TimeScale.of(1, 0, 840, 3, 60);
        assertEquals(TimeScale.INSET, scale.toX(0), 1e-9);
        assertEquals(840 - TimeScale.END_SLACK, scale.toX(3), 1e-9);
        assertEquals(0, TimeScale.maxScroll(1, 840, 3, 60), 1e-9);
    }

    @Test
    void zoomIsLimitedSoThatAFrameNeverGetsWiderThanTheLimit() {
        double max = TimeScale.maxZoom(840, 3, 60);
        TimeScale scale = TimeScale.of(1000, 0, 840, 3, 60);
        assertEquals(TimeScale.MAX_FRAME_WIDTH * 60, scale.pixelsPerSecond(), 1e-6);
        assertEquals(max, TimeScale.clampZoom(1000, 840, 3, 60), 1e-9);
        assertEquals(TimeScale.MIN_ZOOM, TimeScale.clampZoom(0.01, 840, 3, 60), 1e-9);
    }

    @Test
    void scrollIsLimitedToTheContent() {
        // zoom 2: half the duration is visible, so the left edge can go up to half the duration
        assertEquals(1.5, TimeScale.maxScroll(2, 840, 3, 60), 1e-9);
        TimeScale scale = TimeScale.of(2, 99, 840, 3, 60);
        assertEquals(1.5, scale.scroll(), 1e-9);
        assertEquals(840 - TimeScale.END_SLACK, scale.toX(3), 1e-6);
        assertEquals(0, TimeScale.of(2, -4, 840, 3, 60).scroll(), 1e-9);
    }

    @Test
    void zoomingKeepsTheTimeUnderThePointer() {
        TimeScale before = TimeScale.of(1, 0, 840, 3, 60);
        double x = 400;
        double time = before.toTime(x);
        double scroll = TimeScale.scrollToKeep(time, x, 3, 840, 3, 60);
        TimeScale after = TimeScale.of(3, scroll, 840, 3, 60);
        assertEquals(x, after.toX(time), 1e-6);
    }

    @Test
    void snapsToFrames() {
        assertEquals(0.5, TimeScale.snap(0.507, 60), 1e-12);
        assertEquals(31 / 60.0, TimeScale.snap(0.51, 60), 1e-12);
        assertEquals(0.52, TimeScale.snap(0.51, 25), 1e-12);
        assertEquals(0, TimeScale.snap(-0.2, 60), 1e-12);
        // no frame rate: nothing to snap to
        assertEquals(0.507, TimeScale.snap(0.507, 0), 1e-12);
        assertEquals(1 / 24.0, TimeScale.frame(24), 1e-12);
    }

    @Test
    void aTinyOrEmptyViewportStillGivesAUsableScale() {
        TimeScale scale = TimeScale.of(1, 0, 0, 3, 60);
        assertTrue(scale.pixelsPerSecond() > 0);
        assertTrue(TimeScale.of(1, 0, 500, 0, 60).pixelsPerSecond() > 0);
    }
}
