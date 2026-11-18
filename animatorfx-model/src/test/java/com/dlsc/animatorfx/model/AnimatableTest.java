package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatableTest {

    private static Keyframe<Double> key(String id, double time, double value, Easing easing) {
        return new Keyframe<>(id, time, value, easing);
    }

    private static Animatable<Double> ramp(Easing easing) {
        return Animatable.keyframed(List.of(key("a", 1, 10, easing), key("b", 3, 20, Easing.LINEAR)));
    }

    @Test
    void staticValue() {
        Animatable<Double> a = Animatable.of(5.0);
        assertEquals(5.0, a.at(0));
        assertEquals(5.0, a.at(100));
        assertFalse(a.isAnimated());
        assertTrue(a.keyframes().isEmpty());
    }

    @Test
    void sampling() {
        Animatable<Double> a = ramp(Easing.LINEAR);
        assertTrue(a.isAnimated());
        assertEquals(10.0, a.at(0), "before first");
        assertEquals(10.0, a.at(1));
        assertEquals(15.0, a.at(2), "midpoint");
        assertEquals(20.0, a.at(3));
        assertEquals(20.0, a.at(99), "after last");
    }

    @Test
    void easedMidpointUsesTheEasingOfTheLeftKeyframe() {
        Easing ease = new Easing.CubicBezier(0.25, 0.1, 0.25, 1);
        assertEquals(10 + 10 * ease.apply(0.5), ramp(ease).at(2), 1e-12);
        assertEquals(18.024, ramp(ease).at(2), 1e-2);
    }

    @Test
    void holdKeepsTheLeftValueUntilTheNextKeyframe() {
        Animatable<Double> a = ramp(Easing.HOLD);
        assertEquals(10.0, a.at(2.999));
        assertEquals(20.0, a.at(3));
    }

    @Test
    void keyframedSortsByTimeAndRejectsEmpty() {
        Animatable<Double> a = Animatable.keyframed(List.of(key("b", 3, 20, Easing.LINEAR), key("a", 1, 10, Easing.LINEAR)));
        assertEquals(List.of("a", "b"), a.keyframes().stream().map(Keyframe::id).toList());
        assertThrows(IllegalArgumentException.class, () -> Animatable.keyframed(List.of()));
    }

    @Test
    void keyframesAreImmutable() {
        List<Keyframe<Double>> source = new ArrayList<>(List.of(key("a", 1, 10, Easing.LINEAR)));
        Animatable<Double> a = Animatable.keyframed(source);
        source.clear();
        assertEquals(1, a.keyframes().size());
        assertThrows(UnsupportedOperationException.class, () -> a.keyframes().clear());
    }

    @Test
    void withKeyframeTurnsStaticIntoKeyframed() {
        Animatable<Double> a = Animatable.of(5.0).withKeyframe(2, 7.0, Easing.HOLD);
        assertInstanceOf(Animatable.Keyframed.class, a);
        assertEquals(1, a.keyframes().size());
        Keyframe<Double> k = a.keyframes().get(0);
        assertEquals(2, k.time());
        assertEquals(7.0, k.value());
        assertEquals(Easing.HOLD, k.easing());
        assertFalse(k.id().isBlank());
    }

    @Test
    void withKeyframeAddsInTimeOrderWithFreshId() {
        Animatable<Double> a = ramp(Easing.LINEAR).withKeyframe(2, 99.0, Easing.LINEAR);
        assertEquals(3, a.keyframes().size());
        assertEquals(99.0, a.keyframes().get(1).value());
        assertNotEquals("a", a.keyframes().get(1).id());
        assertNotEquals("b", a.keyframes().get(1).id());
        assertEquals(99.0, a.at(2));
    }

    @Test
    void withKeyframeReplacesAtTheSameTimeKeepingTheId() {
        Animatable<Double> a = ramp(Easing.LINEAR).withKeyframe(1 + 5e-7, 42.0, Easing.HOLD);
        assertEquals(2, a.keyframes().size());
        assertEquals(key("a", 1, 42.0, Easing.HOLD), a.keyframes().get(0));
    }

    @Test
    void withoutKeyframe() {
        Animatable<Double> a = ramp(Easing.LINEAR);
        assertSame(a, a.withoutKeyframe("unknown"));
        Animatable<Double> one = a.withoutKeyframe("a");
        assertEquals(List.of("b"), one.keyframes().stream().map(Keyframe::id).toList());
        Animatable<Double> none = one.withoutKeyframe("b");
        assertEquals(Animatable.of(20.0), none);
        assertFalse(none.isAnimated());
        assertSame(none, none.withoutKeyframe("b"));
    }
}
