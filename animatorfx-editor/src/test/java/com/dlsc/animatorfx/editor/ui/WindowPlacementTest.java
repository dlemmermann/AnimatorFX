package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.session.SessionState.WindowState;
import com.dlsc.animatorfx.editor.ui.WindowPlacement.Placement;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowPlacementTest {

    private static final Rectangle2D LARGE = new Rectangle2D(0, 25, 2560, 1415);
    private static final Rectangle2D SMALL = new Rectangle2D(0, 25, 1280, 775);

    @Test
    void withoutSavedBoundsTheWindowIsCentredAtTheDefaultSize() {
        Placement placement = WindowPlacement.resolve(null, List.of(LARGE));

        assertEquals(new Placement(560, 282.5, 1440, 900, false), placement);
    }

    @Test
    void theDefaultSizeIsClampedToASmallScreen() {
        Placement placement = WindowPlacement.resolve(null, List.of(SMALL));

        assertEquals(new Placement(0, 25, 1280, 775, false), placement);
    }

    @Test
    void savedBoundsOnAScreenAreKept() {
        WindowState saved = new WindowState(100, 80, 1200, 760, true, 0);

        assertEquals(new Placement(100, 80, 1200, 760, true), WindowPlacement.resolve(saved, List.of(LARGE)));
    }

    @Test
    void savedBoundsOnASecondScreenAreKept() {
        Rectangle2D second = new Rectangle2D(2560, 0, 1920, 1080);
        WindowState saved = new WindowState(2700, 100, 1000, 700, false, 1);

        assertEquals(new Placement(2700, 100, 1000, 700, false),
                WindowPlacement.resolve(saved, List.of(LARGE, second)));
    }

    @Test
    void boundsOffEveryScreenFallBackToCentredDefaults() {
        WindowState saved = new WindowState(5000, 3000, 1000, 700, true, 1);

        Placement placement = WindowPlacement.resolve(saved, List.of(LARGE));

        assertEquals(new Placement(560, 282.5, 1440, 900, false), placement);
    }

    @Test
    void aWindowThatOnlyPeeksOntoAScreenCountsAsOffScreen() {
        WindowState saved = new WindowState(2550, 1430, 1000, 700, false, 0);

        assertFalse(WindowPlacement.isVisible(saved, List.of(LARGE)));
        assertTrue(WindowPlacement.isVisible(new WindowState(2000, 1000, 1000, 700, false, 0), List.of(LARGE)));
    }

    @Test
    void aDegenerateSavedSizeFallsBackToDefaults() {
        WindowState saved = new WindowState(100, 100, 0, 0, false, 0);

        assertEquals(new Placement(560, 282.5, 1440, 900, false), WindowPlacement.resolve(saved, List.of(LARGE)));
    }
}
