package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class InterpolationTest {

    private static final Rgba RED = new Rgba(1, 0, 0, 1);
    private static final Rgba BLUE = new Rgba(0, 0, 1, 1);

    @Test
    void numbersPointsAndColours() {
        assertEquals(15.0, Interpolation.lerp(10.0, 20.0, 0.5));
        assertEquals(22.0, Interpolation.lerp(10.0, 20.0, 1.2), 1e-12);
        assertEquals(new Vec2(5, 10), Interpolation.lerp(Vec2.ZERO, new Vec2(10, 20), 0.5));
        assertEquals(new Rgba(0.5, 0, 0.5, 1), Interpolation.lerp(RED, BLUE, 0.5));
    }

    @Test
    void solidPaint() {
        Paint mid = Interpolation.lerp(new Paint.Solid(RED), new Paint.Solid(BLUE), 0.5);
        assertEquals(new Paint.Solid(new Rgba(0.5, 0, 0.5, 1)), mid);
    }

    @Test
    void matchingGradientsInterpolateStopByStop() {
        Paint a = new Paint.LinearGradient(Vec2.ZERO, new Vec2(10, 0),
                List.of(new Paint.Stop(0, RED), new Paint.Stop(1, BLUE)));
        Paint b = new Paint.LinearGradient(new Vec2(10, 10), new Vec2(30, 0),
                List.of(new Paint.Stop(0.5, BLUE), new Paint.Stop(1, BLUE)));
        assertEquals(new Paint.LinearGradient(new Vec2(5, 5), new Vec2(20, 0),
                        List.of(new Paint.Stop(0.25, new Rgba(0.5, 0, 0.5, 1)), new Paint.Stop(1, BLUE))),
                Interpolation.lerp(a, b, 0.5));

        Paint ra = new Paint.RadialGradient(Vec2.ZERO, 10, List.of(new Paint.Stop(0, RED)));
        Paint rb = new Paint.RadialGradient(new Vec2(4, 4), 20, List.of(new Paint.Stop(1, BLUE)));
        assertEquals(new Paint.RadialGradient(new Vec2(2, 2), 15, List.of(new Paint.Stop(0.5, new Rgba(0.5, 0, 0.5, 1)))),
                Interpolation.lerp(ra, rb, 0.5));
    }

    @Test
    void nonMatchingPaintsHold() {
        Paint two = new Paint.LinearGradient(Vec2.ZERO, Vec2.ONE, List.of(new Paint.Stop(0, RED), new Paint.Stop(1, BLUE)));
        Paint three = new Paint.LinearGradient(Vec2.ZERO, Vec2.ONE,
                List.of(new Paint.Stop(0, RED), new Paint.Stop(0.5, RED), new Paint.Stop(1, BLUE)));
        Paint radial = new Paint.RadialGradient(Vec2.ZERO, 5, List.of(new Paint.Stop(0, RED), new Paint.Stop(1, BLUE)));
        assertSame(two, Interpolation.lerp(two, three, 0.99));
        assertSame(three, Interpolation.lerp(two, three, 1));
        assertSame(two, Interpolation.lerp(two, radial, 0.5));
        Paint solid = new Paint.Solid(RED);
        Paint none = new Paint.None();
        assertSame(solid, Interpolation.lerp(solid, none, 0.5));
        assertSame(none, Interpolation.lerp(solid, none, 1.0));
    }

    @Test
    void otherTypesHold() {
        assertEquals("a", Interpolation.lerp("a", "b", 0.99));
        assertEquals("b", Interpolation.lerp("a", "b", 1.0));
        assertEquals(1, Interpolation.lerp(1, 3, 0.5));
    }

    @Test
    void pathsMorph() {
        PathData a = line(0, 0, 10, 0);
        PathData b = line(0, 10, 10, 30);
        assertEquals(line(0, 5, 10, 15), Interpolation.lerp(a, b, 0.5));
    }

    private static PathData line(double x1, double y1, double x2, double y2) {
        return new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(x1, y1), Vec2.ZERO, Vec2.ZERO),
                new PathData.Vertex(new Vec2(x2, y2), Vec2.ZERO, Vec2.ZERO)), false)));
    }
}
