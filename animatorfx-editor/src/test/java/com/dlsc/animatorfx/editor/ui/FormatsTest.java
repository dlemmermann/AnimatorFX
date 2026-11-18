package com.dlsc.animatorfx.editor.ui;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatsTest {

    @Test
    void timecodeIsMinutesSecondsAndMilliseconds() {
        assertEquals("00:00.000", Formats.timecode(0));
        assertEquals("00:01.140", Formats.timecode(1.14));
        assertEquals("01:03.500", Formats.timecode(63.5));
        assertEquals("00:01.000", Formats.timecode(0.9996));
        assertEquals("00:00.000", Formats.timecode(-2));
        assertEquals("00:00.000", Formats.timecode(Double.NaN));
    }

    @Test
    void zoomIsAWholePercentage() {
        assertEquals("100%", Formats.zoom(1));
        assertEquals("125%", Formats.zoom(1.25));
        assertEquals("33%", Formats.zoom(1 / 3.0));
    }

    @Test
    void numbersDropAPointlessFraction() {
        assertEquals("60 fps", Formats.frameRate(60));
        assertEquals("29.97 fps", Formats.frameRate(29.97));
        assertEquals("800 × 600", Formats.size(800, 600));
        assertEquals("12.5 × 40", Formats.size(12.5, 40));
    }

    @Test
    void countsUseSingularAndPlural() {
        assertEquals("1 layer", Formats.count(1, "layer"));
        assertEquals("3 layers", Formats.count(3, "layer"));
        assertEquals("0 selected", Formats.selected(0));
    }

    @Test
    void autosaveStateNamesSavedUnsavedAndAutosaved() {
        assertEquals("Saved", Formats.autosaveState(false, null));
        assertEquals("Saved", Formats.autosaveState(false, LocalTime.of(11, 9)));
        assertEquals("Unsaved changes", Formats.autosaveState(true, null));
        assertEquals("Autosaved 11:09", Formats.autosaveState(true, LocalTime.of(11, 9, 30)));
    }
}
