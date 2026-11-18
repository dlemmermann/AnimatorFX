package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PathMorphTest {

    @Test
    void equalVertexCountsLerpPointAndTangents() {
        PathData a = new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(0, 0), new Vec2(-2, 0), new Vec2(2, 0)),
                Paths.corner(10, 0)), false)));
        PathData b = new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(0, 10), new Vec2(-4, 2), new Vec2(4, 6)),
                Paths.corner(30, 20)), false)));
        PathData expected = new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(0, 5), new Vec2(-3, 1), new Vec2(3, 3)),
                Paths.corner(20, 10)), false)));
        assertEquals(expected, PathMorph.morph(a, b, 0.5));
        assertEquals(a, PathMorph.morph(a, b, 0));
        assertEquals(b, PathMorph.morph(a, b, 1));
    }

    @Test
    void fewerVerticesAreSubdividedOnTheLongestSegment() {
        PathData triangle = Paths.polygon(true, 0, 0, 100, 0, 0, 50);
        PathData square = Paths.square();

        PathData mid = PathMorph.morph(triangle, square, 0.5);
        assertEquals(4, mid.contours().get(0).vertices().size());
        assertEquals(true, mid.contours().get(0).closed());

        PathData start = PathMorph.morph(triangle, square, 0);
        assertEquals(4, start.contours().get(0).vertices().size());
        // the hypotenuse (100,0)-(0,50) is the longest segment and receives the extra vertex
        assertEquals(Paths.polygon(true, 0, 0, 100, 0, 50, 25, 0, 50), start);
        assertSameShape(triangle, start);

        assertEquals(square, PathMorph.morph(triangle, square, 1));
        // the shorter path may also be the second argument
        assertEquals(4, PathMorph.morph(square, triangle, 0.5).contours().get(0).vertices().size());
        assertSameShape(triangle, PathMorph.morph(square, triangle, 1));
    }

    @Test
    void subdividingCurvesKeepsTheShape() {
        PathData circle = Paths.circle(50);
        PathData octagon = Paths.polygon(true, 50, 0, 35, 35, 0, 50, -35, 35, -50, 0, -35, -35, 0, -50, 35, -35);
        PathData start = PathMorph.morph(circle, octagon, 0);
        assertEquals(8, start.contours().get(0).vertices().size());
        assertSameShape(circle, start);
    }

    @Test
    void openAndClosedHold() {
        PathData open = Paths.polygon(false, 0, 0, 100, 0, 100, 100);
        PathData closed = Paths.polygon(true, 0, 0, 100, 0, 100, 100);
        assertSame(open, PathMorph.morph(open, closed, 0.5));
        assertSame(closed, PathMorph.morph(open, closed, 1));
    }

    @Test
    void differentContourCountsHold() {
        PathData one = Paths.square();
        PathData two = new PathData(List.of(one.contours().get(0), one.contours().get(0)));
        assertSame(one, PathMorph.morph(one, two, 0.5));
        assertSame(two, PathMorph.morph(one, two, 1));
        assertSame(PathData.EMPTY, PathMorph.morph(PathData.EMPTY, one, 0.5));
    }

    @Test
    void singlePointGrowsIntoAShape() {
        PathData point = Paths.polygon(false, 10, 10);
        PathData line = Paths.polygon(false, 0, 0, 20, 0, 40, 0);
        assertEquals(Paths.polygon(false, 5, 5, 15, 5, 25, 5), PathMorph.morph(point, line, 0.5));
    }

    @Test
    void closedSingleVertexLoopIsSubdivided() {
        PathData loop = new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(0, 0), new Vec2(-40, 60), new Vec2(40, 60))), true)));
        PathData start = PathMorph.morph(loop, Paths.polygon(true, 0, 0, 10, 0, 10, 10), 0);
        assertEquals(3, start.contours().get(0).vertices().size());
        assertSameShape(loop, start);
    }

    @Test
    void emptyContourHolds() {
        PathData empty = new PathData(List.of(new PathData.Contour(List.of(), false)));
        PathData line = Paths.polygon(false, 0, 0, 20, 0);
        assertSame(empty, PathMorph.morph(empty, line, 0.5));
        assertSame(line, PathMorph.morph(empty, line, 1));
    }

    private static void assertSameShape(PathData expected, PathData actual) {
        PathMeasure e = new PathMeasure(expected);
        PathMeasure a = new PathMeasure(actual);
        assertEquals(e.length(), a.length(), 0.1);
        for (int i = 0; i <= 40; i++) {
            Vec2 p = e.pointAt(i / 40.0);
            Vec2 q = a.pointAt(i / 40.0);
            assertEquals(p.x(), q.x(), 0.1, "x at " + i);
            assertEquals(p.y(), q.y(), 0.1, "y at " + i);
        }
    }
}
