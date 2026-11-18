package com.dlsc.animatorfx.editor.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Easing.CubicBezier;
import com.dlsc.animatorfx.model.Vec2;
import java.util.List;
import org.junit.jupiter.api.Test;

class EasingCurveTest {

    /** A 200 px square: the unit square is 100 px, with 50 px of overshoot room above and below. */
    private final EasingCurve curve = new EasingCurve(200, 200, 10);

    @Test
    void theUnitSquareIsPaddedForOvershoot() {
        assertEquals(10, curve.x(0), 1e-9);
        assertEquals(190, curve.x(1), 1e-9);
        // y runs from -0.5 (bottom) to 1.5 (top)
        assertEquals(190, curve.y(-0.5), 1e-9);
        assertEquals(10, curve.y(1.5), 1e-9);
        assertEquals(145, curve.y(0), 1e-9);
        assertEquals(55, curve.y(1), 1e-9);
    }

    @Test
    void pixelsMapBackToValues() {
        Vec2 value = curve.toValue(curve.x(0.42), curve.y(0.8));
        assertEquals(0.42, value.x(), 1e-9);
        assertEquals(0.8, value.y(), 1e-9);
    }

    @Test
    void aHandleIsClampedToTheUnitRangeInXAndTheOvershootRangeInY() {
        assertEquals(new Vec2(0, 1.5), curve.toValue(-500, -500));
        assertEquals(new Vec2(1, -0.5), curve.toValue(900, 900));
        CubicBezier moved = EasingCurve.withHandle(new CubicBezier(0.25, 0.1, 0.25, 1), 0, curve.toValue(900, 900));
        assertEquals(new CubicBezier(1, -0.5, 0.25, 1), moved);
        assertEquals(new CubicBezier(0.25, 0.1, 0, 1.5),
                EasingCurve.withHandle(new CubicBezier(0.25, 0.1, 0.25, 1), 1, curve.toValue(-5, 0)));
    }

    @Test
    void handleValuesAreRoundedToThreeDecimals() {
        CubicBezier moved = EasingCurve.withHandle(new CubicBezier(0, 0, 1, 1), 0, new Vec2(0.123456, 0.98765));
        assertEquals(new CubicBezier(0.123, 0.988, 1, 1), moved);
    }

    @Test
    void theNearestHandleWithinReachIsHit() {
        CubicBezier bezier = new CubicBezier(0.25, 0.1, 0.75, 0.9);
        assertEquals(0, curve.handleAt(bezier, curve.x(0.25) + 3, curve.y(0.1) - 2));
        assertEquals(1, curve.handleAt(bezier, curve.x(0.75), curve.y(0.9)));
        assertEquals(-1, curve.handleAt(bezier, curve.x(0.5), curve.y(0.5)));
    }

    @Test
    void linearHasAnEquivalentBezierAndHoldHasNone() {
        assertEquals(new CubicBezier(0, 0, 1, 1), EasingCurve.asBezier(Easing.LINEAR));
        assertNull(EasingCurve.asBezier(Easing.HOLD));
        CubicBezier custom = new CubicBezier(0.1, 0.2, 0.3, 0.4);
        assertEquals(custom, EasingCurve.asBezier(custom));
    }

    @Test
    void namesPresetsAndCustomCurves() {
        assertEquals("Ease In Out", EasingCurve.name(Easing.presets().get("Ease In Out")));
        assertEquals("Linear", EasingCurve.name(Easing.LINEAR));
        assertEquals("Linear", EasingCurve.name(new CubicBezier(0, 0, 1, 1)));
        assertEquals("Hold", EasingCurve.name(Easing.HOLD));
        assertEquals("Custom", EasingCurve.name(new CubicBezier(0.1, 0.2, 0.3, 0.4)));
    }

    @Test
    void formatsAndParsesTheCssNotation() {
        assertEquals("cubic-bezier(0.42, 0, 0.58, 1)", EasingCurve.format(new CubicBezier(0.42, 0, 0.58, 1)));
        assertEquals(new CubicBezier(0.22, 1, 0.36, 1), EasingCurve.parse("cubic-bezier(0.22, 1, 0.36, 1)").orElseThrow());
        assertEquals(new CubicBezier(0.22, 1.4, 0.36, -0.2), EasingCurve.parse(" .22 1.4,0.36 , -0.2 ").orElseThrow());
        // x is limited to 0..1 and y to the overshoot range
        assertEquals(new CubicBezier(1, 1.5, 0, -0.5), EasingCurve.parse("cubic-bezier(2, 9, -1, -9)").orElseThrow());
        for (String bad : List.of("", "cubic-bezier(1, 2, 3)", "a, b, c, d", "1 2 3 4 5", "cubic-bezier(NaN, 0, 1, 1)")) {
            assertTrue(EasingCurve.parse(bad).isEmpty(), bad);
        }
    }

    @Test
    void theCommonEasingOfASelectionIsKnownOnlyWhenAllAgree() {
        Easing ease = Easing.presets().get("Ease");
        assertEquals(ease, EasingCurve.common(List.of(ease, ease)).orElseThrow());
        assertTrue(EasingCurve.common(List.of(ease, Easing.LINEAR)).isEmpty());
        assertTrue(EasingCurve.common(List.of()).isEmpty());
    }
}
