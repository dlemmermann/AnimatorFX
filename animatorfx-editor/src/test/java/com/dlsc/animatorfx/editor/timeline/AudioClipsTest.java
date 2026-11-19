package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.editor.timeline.AudioClips.Layout;
import com.dlsc.animatorfx.editor.timeline.AudioClips.Segment;
import com.dlsc.animatorfx.editor.timeline.AudioClips.Trim;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the sound of an audio layer lies under its bar, and what the bar gestures do to it. */
class AudioClipsTest {

    private static final double UNKNOWN = Double.NaN;

    @Test
    void aClipShorterThanItsBarEndsInsideTheBar() {
        Layout layout = AudioClips.layout(1, 3, 0, 1.5, false);

        assertEquals(List.of(new Segment(1, 2.5, 0)), layout.segments());
        assertEquals(2.5, layout.soundEnd());
    }

    @Test
    void theClipOffsetIsTheSoundAtTheInPoint() {
        Layout layout = AudioClips.layout(1, 3, 0.5, 1.5, false);

        assertEquals(List.of(new Segment(1, 2, 0.5)), layout.segments());
        assertEquals(2, layout.soundEnd());
        assertEquals(0.5, AudioClips.mediaTime(1, 1, 0.5, 1.5, false));
        assertEquals(1.25, AudioClips.mediaTime(1.75, 1, 0.5, 1.5, false), 1e-12);
        assertTrue(Double.isNaN(AudioClips.mediaTime(2.5, 1, 0.5, 1.5, false)), "past the end of the sound");
        assertTrue(Double.isNaN(AudioClips.mediaTime(0.5, 1, 0.5, 1.5, false)), "before the bar");
    }

    @Test
    void aClipLongerThanItsBarIsCutByTheOutPoint() {
        Layout layout = AudioClips.layout(0, 1, 0.25, 5, false);

        assertEquals(List.of(new Segment(0, 1, 0.25)), layout.segments());
        assertEquals(1, layout.soundEnd());
    }

    @Test
    void anOffsetPastTheEndOfTheClipLeavesTheBarSilent() {
        Layout layout = AudioClips.layout(0, 2, 3, 1.5, false);

        assertEquals(List.of(), layout.segments());
        assertEquals(0, layout.soundEnd());
    }

    @Test
    void aLoopingClipRepeatsEveryClipDuration() {
        Layout layout = AudioClips.layout(1, 3, 0.25, 0.75, true);

        assertEquals(List.of(new Segment(1, 1.5, 0.25), new Segment(1.5, 2.25, 0), new Segment(2.25, 3, 0)),
                layout.segments());
        assertEquals(3, layout.soundEnd());
        assertEquals(List.of(1.5, 2.25), layout.loopBoundaries());
        assertEquals(0.25, AudioClips.mediaTime(1, 1, 0.25, 0.75, true), 1e-12);
        assertEquals(0, AudioClips.mediaTime(1.5, 1, 0.25, 0.75, true), 1e-12);
        assertEquals(0.5, AudioClips.mediaTime(2.75, 1, 0.25, 0.75, true), 1e-12);
    }

    @Test
    void aLoopingClipWrapsAnOffsetLongerThanTheClip() {
        Layout layout = AudioClips.layout(0, 1, 1.75, 0.5, true);

        assertEquals(new Segment(0, 0.25, 0.25), layout.segments().get(0));
        assertEquals(new Segment(0.25, 0.75, 0), layout.segments().get(1));
    }

    @Test
    void aClipOfUnknownLengthFillsTheBar() {
        Layout layout = AudioClips.layout(1, 3, 0.5, UNKNOWN, true);

        assertEquals(List.of(new Segment(1, 3, 0.5)), layout.segments());
        assertEquals(3, layout.soundEnd());
        assertEquals(List.of(), layout.loopBoundaries());
    }

    @Test
    void slidingMovesTheSoundWithThePointer() {
        // dragging to the right shows an earlier part of the sound at the in-point
        assertEquals(0.3, AudioClips.slide(0.5, 0.2, 2), 1e-12);
        assertEquals(0.9, AudioClips.slide(0.5, -0.4, 2), 1e-12);
        assertEquals(0, AudioClips.slide(0.5, 1, 2));
        assertEquals(2, AudioClips.slide(0.5, -5, 2));
        assertEquals(5.5, AudioClips.slide(0.5, -5, UNKNOWN));
        assertEquals(0.5, AudioClips.slide(0.5, Double.NaN, 2));
    }

    @Test
    void trimmingTheStartKeepsTheSoundWhereItIsInTime() {
        assertEquals(new Trim(1.5, 0.5), AudioClips.trimStart(1, 3, 0, 1.5, 0.1));
        assertEquals(new Trim(0.75, 0.25), AudioClips.trimStart(1, 3, 0.5, 0.75, 0.1));
    }

    @Test
    void trimmingTheStartStopsAtTheStartOfTheSoundAndBeforeTheOutPoint() {
        assertEquals(new Trim(0.5, 0), AudioClips.trimStart(1, 3, 0.5, 0.2, 0.1), "the sound starts at 0.5");
        assertEquals(new Trim(2.9, 2.4), AudioClips.trimStart(1, 3, 0.5, 5, 0.1));
        assertEquals(new Trim(0, 0.25), AudioClips.trimStart(0.5, 3, 0.75, -2, 0.1), "never before time 0");
    }
}
