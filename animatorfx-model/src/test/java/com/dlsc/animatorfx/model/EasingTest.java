package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasingTest {

    @Test
    void linearAndHold() {
        assertEquals(0.3, Easing.LINEAR.apply(0.3));
        assertEquals(0.0, Easing.HOLD.apply(0.99));
        assertEquals(1.0, Easing.HOLD.apply(1.0));
    }

    @Test
    void cubicBezierMatchesCssEase() {
        Easing ease = new Easing.CubicBezier(0.25, 0.1, 0.25, 1);
        assertEquals(0.8024, ease.apply(0.5), 1e-3);
        assertEquals(0.0, ease.apply(0.0));
        assertEquals(1.0, ease.apply(1.0));
    }

    @Test
    void cubicBezierOnTheDiagonalIsLinear() {
        Easing e = new Easing.CubicBezier(1 / 3.0, 1 / 3.0, 2 / 3.0, 2 / 3.0);
        for (double u = 0; u <= 1; u += 0.05) {
            assertEquals(u, e.apply(u), 1e-9);
        }
    }

    @Test
    void cubicBezierIsMonotonicForSteepCurves() {
        Easing e = new Easing.CubicBezier(0.87, 0, 0.13, 1);
        double previous = 0;
        for (int i = 1; i <= 1000; i++) {
            double y = e.apply(i / 1000.0);
            assertTrue(y >= previous - 1e-9, "at " + i);
            previous = y;
        }
        assertEquals(0.5, e.apply(0.5), 1e-6);
    }

    @Test
    void backEasingOvershoots() {
        Easing back = Easing.presets().get("Back Out");
        double max = 0;
        for (int i = 0; i <= 100; i++) {
            max = Math.max(max, back.apply(i / 100.0));
        }
        assertTrue(max > 1.05);
    }

    @Test
    void presetsAreOrderedAndUnmodifiable() {
        Map<String, Easing> presets = Easing.presets();
        assertEquals(List.of(
                "Ease", "Ease In", "Ease Out", "Ease In Out",
                "Sine In", "Sine Out", "Sine In Out",
                "Quad In", "Quad Out", "Quad In Out",
                "Cubic In", "Cubic Out", "Cubic In Out",
                "Quart In", "Quart Out", "Quart In Out",
                "Expo In", "Expo Out", "Expo In Out",
                "Back In", "Back Out", "Back In Out"), List.copyOf(presets.keySet()));
        assertEquals(new Easing.CubicBezier(0.25, 0.1, 0.25, 1), presets.get("Ease"));
        assertEquals(new Easing.CubicBezier(0.42, 0, 1, 1), presets.get("Ease In"));
        assertEquals(new Easing.CubicBezier(0, 0, 0.58, 1), presets.get("Ease Out"));
        assertEquals(new Easing.CubicBezier(0.42, 0, 0.58, 1), presets.get("Ease In Out"));
        assertEquals(new Easing.CubicBezier(0.68, -0.6, 0.32, 1.6), presets.get("Back In Out"));
        assertThrows(UnsupportedOperationException.class, () -> presets.put("X", Easing.LINEAR));
    }
}
