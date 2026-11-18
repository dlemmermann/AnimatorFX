package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransformTest {

    private static final double EPS = 1e-9;

    private static Vec2 apply(double[] m, double x, double y) {
        return new Vec2(m[0] * x + m[1] * y + m[2], m[3] * x + m[4] * y + m[5]);
    }

    private static void assertPoint(double x, double y, Vec2 actual) {
        assertEquals(x, actual.x(), EPS);
        assertEquals(y, actual.y(), EPS);
    }

    private static Transform transform(Vec2 anchor, Vec2 position, Vec2 scale, double rotation, Vec2 skew) {
        return new Transform(Animatable.of(anchor), Animatable.of(position), Animatable.of(scale),
                Animatable.of(rotation), Animatable.of(skew));
    }

    @Test
    void identity() {
        assertArrayEquals(new double[]{1, 0, 0, 0, 1, 0}, Transform.IDENTITY.matrixAt(0), EPS);
    }

    @Test
    void pureTranslation() {
        assertArrayEquals(new double[]{1, 0, 30, 0, 1, 40}, Transform.at(30, 40).matrixAt(0), EPS);
    }

    @Test
    void rotationAboutAnchorIsClockwise() {
        double[] m = transform(new Vec2(10, 10), new Vec2(50, 50), Vec2.ONE, 90, Vec2.ZERO).matrixAt(0);
        assertPoint(50, 50, apply(m, 10, 10));
        assertPoint(50, 60, apply(m, 20, 10));
        assertPoint(40, 50, apply(m, 10, 20));
    }

    @Test
    void scaleAboutAnchor() {
        double[] m = transform(new Vec2(10, 10), new Vec2(10, 10), new Vec2(2, 3), 0, Vec2.ZERO).matrixAt(0);
        assertArrayEquals(new double[]{2, 0, -10, 0, 3, -20}, m, EPS);
        assertPoint(10, 10, apply(m, 10, 10));
        assertPoint(30, 40, apply(m, 20, 20));
    }

    @Test
    void skewIsInDegreesAndAppliedAfterScale() {
        double[] m = transform(Vec2.ZERO, Vec2.ZERO, new Vec2(2, 1), 0, new Vec2(45, 0)).matrixAt(0);
        assertPoint(2, 0, apply(m, 1, 0));
        assertPoint(1, 1, apply(m, 0, 1));
    }

    @Test
    void samplesAtTheGivenTime() {
        Animatable<Vec2> position = Animatable.of(Vec2.ZERO).withKeyframe(0, Vec2.ZERO, Easing.LINEAR)
                .withKeyframe(2, new Vec2(100, 20), Easing.LINEAR);
        double[] m = Transform.IDENTITY.withPosition(position).matrixAt(1);
        assertArrayEquals(new double[]{1, 0, 50, 0, 1, 10}, m, EPS);
    }
}
