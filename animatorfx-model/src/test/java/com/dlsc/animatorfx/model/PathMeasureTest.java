package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathMeasureTest {

    private final PathMeasure square = new PathMeasure(Paths.square());

    @Test
    void squareLengthAndPoints() {
        assertEquals(400, square.length(), 1e-9);
        assertEquals(new Vec2(0, 0), square.pointAt(0));
        assertEquals(new Vec2(100, 0), square.pointAt(0.25));
        assertEquals(new Vec2(100, 50), square.pointAt(0.375));
        assertEquals(new Vec2(0, 0), square.pointAt(1));
        assertEquals(new Vec2(0, 0), square.pointAt(-3));
        assertEquals(new Vec2(0, 0), square.pointAt(7));
    }

    @Test
    void squareAngles() {
        assertEquals(0, square.angleAt(0.1), 1e-9);
        assertEquals(90, square.angleAt(0.3), 1e-9);
        assertEquals(180, square.angleAt(0.6), 1e-9);
        assertEquals(-90, square.angleAt(0.9), 1e-9);
    }

    @Test
    void openPathHasNoClosingSegment() {
        assertEquals(300, new PathMeasure(Paths.polygon(false, 0, 0, 100, 0, 100, 100, 0, 100)).length(), 1e-9);
    }

    @Test
    void circle() {
        PathMeasure circle = new PathMeasure(Paths.circle(100));
        assertEquals(2 * Math.PI * 100, circle.length(), 0.5);
        for (int i = 0; i <= 16; i++) {
            double f = i / 16.0;
            double angle = f * 2 * Math.PI;
            Vec2 p = circle.pointAt(f);
            assertEquals(100 * Math.cos(angle), p.x(), 0.1, "x at " + f);
            assertEquals(100 * Math.sin(angle), p.y(), 0.1, "y at " + f);
        }
        // tangent of a clockwise circle is the radius angle plus 90 degrees, without flattening jitter
        assertEquals(90 + 45, circle.angleAt(0.125), 0.1);
        assertEquals(90 + 10, circle.angleAt(10 / 360.0), 0.1);
        assertEquals(-90 + 20, circle.angleAt(200 / 360.0), 0.1);
    }

    @Test
    void severalContoursAreMeasuredInOrder() {
        PathData two = new PathData(List.of(
                Paths.polygon(false, 0, 0, 100, 0).contours().get(0),
                Paths.polygon(false, 0, 50, 300, 50).contours().get(0)));
        PathMeasure m = new PathMeasure(two);
        assertEquals(400, m.length(), 1e-9);
        assertEquals(new Vec2(100, 50), m.pointAt(0.5));
        assertEquals(new Vec2(50, 0), m.pointAt(0.125));
    }

    @Test
    void degeneratePaths() {
        PathMeasure empty = new PathMeasure(PathData.EMPTY);
        assertEquals(0, empty.length());
        assertEquals(Vec2.ZERO, empty.pointAt(0.5));
        assertEquals(0, empty.angleAt(0.5));
        assertSame(PathData.EMPTY, empty.trim(0, 1, 0));

        PathMeasure point = new PathMeasure(Paths.polygon(false, 7, 9));
        assertEquals(0, point.length());
        assertEquals(new Vec2(7, 9), point.pointAt(0.5));
        assertSame(PathData.EMPTY, point.trim(0, 1, 0));
    }

    @Test
    void trimHalfOfTheSquare() {
        PathData half = square.trim(0, 0.5, 0);
        assertEquals(200, new PathMeasure(half).length(), 1e-9);
        assertEquals(Paths.polygon(false, 0, 0, 100, 0, 100, 100), half);
    }

    @Test
    void trimInsideOneSegmentAndAcrossSegments() {
        assertEquals(Paths.polygon(false, 20, 0, 80, 0), square.trim(0.05, 0.2, 0));
        assertEquals(Paths.polygon(false, 50, 0, 100, 0, 100, 100, 50, 100), square.trim(0.125, 0.625, 0));
        // start and end may be swapped
        assertEquals(Paths.polygon(false, 20, 0, 80, 0), square.trim(0.2, 0.05, 0));
    }

    @Test
    void fullAndEmptyTrim() {
        assertEquals(Paths.square(), square.trim(0, 1, 0));
        assertEquals(Paths.square(), square.trim(0, 1, 0.3));
        assertSame(PathData.EMPTY, square.trim(0.4, 0.4, 0));
    }

    @Test
    void offsetShiftsTheRange() {
        assertEquals(Paths.polygon(false, 100, 0, 100, 100), square.trim(0, 0.25, 0.25));
        assertEquals(Paths.polygon(false, 100, 0, 100, 100), square.trim(0, 0.25, 1.25));
        assertEquals(Paths.polygon(false, 0, 100, 0, 0), square.trim(0, 0.25, -0.25));
    }

    @Test
    void trimWrapsThroughTheStartOfAClosedContour() {
        PathData wrapped = square.trim(0.75, 1, 0.125);
        assertEquals(Paths.polygon(false, 0, 50, 0, 0, 50, 0), wrapped);
        assertEquals(100, new PathMeasure(wrapped).length(), 1e-9);
    }

    @Test
    void wrappingAnOpenPathYieldsTwoPieces() {
        PathMeasure line = new PathMeasure(Paths.polygon(false, 0, 0, 100, 0));
        PathData wrapped = line.trim(0, 0.5, 0.75);
        assertEquals(new PathData(List.of(
                Paths.polygon(false, 75, 0, 100, 0).contours().get(0),
                Paths.polygon(false, 0, 0, 25, 0).contours().get(0))), wrapped);
    }

    @Test
    void trimKeepsWholeContoursClosed() {
        PathData.Contour first = Paths.square().contours().get(0);
        PathData.Contour second = Paths.polygon(true, 200, 0, 300, 0, 300, 100, 200, 100).contours().get(0);
        PathData trimmed = new PathMeasure(new PathData(List.of(first, second))).trim(0, 0.75, 0);
        assertEquals(2, trimmed.contours().size());
        assertTrue(trimmed.contours().get(0).closed());
        assertFalse(trimmed.contours().get(1).closed());
        assertEquals(Paths.polygon(false, 200, 0, 300, 0, 300, 100), new PathData(List.of(trimmed.contours().get(1))));
    }

    @Test
    void trimmedCurvesStayCurves() {
        PathMeasure circle = new PathMeasure(Paths.circle(100));
        PathData quarter = circle.trim(0.125, 0.375, 0);
        PathData.Contour contour = quarter.contours().get(0);
        assertFalse(contour.closed());
        assertEquals(3, contour.vertices().size());
        PathMeasure m = new PathMeasure(quarter);
        assertEquals(circle.length() / 4, m.length(), 0.2);
        for (int i = 0; i <= 8; i++) {
            Vec2 p = m.pointAt(i / 8.0);
            assertEquals(100, Math.hypot(p.x(), p.y()), 0.1, "radius at " + i);
        }
        Vec2 start = m.pointAt(0);
        assertEquals(100 * Math.cos(Math.PI / 4), start.x(), 0.1);
        assertEquals(100 * Math.sin(Math.PI / 4), start.y(), 0.1);
    }
}
