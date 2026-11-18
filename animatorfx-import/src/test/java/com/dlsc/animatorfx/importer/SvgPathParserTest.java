package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgPathParserTest {

    private static final double EPS = 1e-9;

    private static void assertVec(double x, double y, Vec2 actual) {
        assertEquals(x, actual.x(), EPS, "x of " + actual);
        assertEquals(y, actual.y(), EPS, "y of " + actual);
    }

    private static void assertCorner(double x, double y, PathData.Vertex v) {
        assertVec(x, y, v.point());
        assertVec(0, 0, v.in());
        assertVec(0, 0, v.out());
    }

    private static PathData.Contour only(PathData path) {
        assertEquals(1, path.contours().size());
        return path.contours().get(0);
    }

    /** The point of the cubic from a to b at parameter t. */
    private static Vec2 pointOn(PathData.Vertex a, PathData.Vertex b, double t) {
        Vec2 p0 = a.point();
        Vec2 p1 = a.point().plus(a.out());
        Vec2 p2 = b.point().plus(b.in());
        Vec2 p3 = b.point();
        double s = 1 - t;
        return p0.times(s * s * s).plus(p1.times(3 * s * s * t)).plus(p2.times(3 * s * t * t)).plus(p3.times(t * t * t));
    }

    @Test
    void implicitLineToAfterMoveAndClose() {
        PathData.Contour c = only(SvgPathParser.parse("M0 0 L10 0 10 10z"));
        assertTrue(c.closed());
        assertEquals(3, c.vertices().size());
        assertCorner(0, 0, c.vertices().get(0));
        assertCorner(10, 0, c.vertices().get(1));
        assertCorner(10, 10, c.vertices().get(2));
    }

    @Test
    void implicitLineToAfterMoveTo() {
        PathData.Contour c = only(SvgPathParser.parse("M1 2 3 4 5 6"));
        assertFalse(c.closed());
        assertEquals(3, c.vertices().size());
        assertCorner(5, 6, c.vertices().get(2));

        c = only(SvgPathParser.parse("m1 2 3 4 5 6"));
        assertCorner(4, 6, c.vertices().get(1));
        assertCorner(9, 12, c.vertices().get(2));
    }

    @Test
    void relativeCommandsAndHorizontalVertical() {
        PathData.Contour c = only(SvgPathParser.parse("m10 10 l5 0 h5 v5 H0 V0"));
        List<PathData.Vertex> v = c.vertices();
        assertEquals(6, v.size());
        assertCorner(10, 10, v.get(0));
        assertCorner(15, 10, v.get(1));
        assertCorner(20, 10, v.get(2));
        assertCorner(20, 15, v.get(3));
        assertCorner(0, 15, v.get(4));
        assertCorner(0, 0, v.get(5));
    }

    @Test
    void cubicTangentsAreRelativeToTheirVertex() {
        PathData.Contour c = only(SvgPathParser.parse("M10 10 C20 0 30 0 40 10"));
        assertEquals(2, c.vertices().size());
        assertVec(10, -10, c.vertices().get(0).out());
        assertVec(0, 0, c.vertices().get(0).in());
        assertVec(-10, -10, c.vertices().get(1).in());
        assertVec(40, 10, c.vertices().get(1).point());

        c = only(SvgPathParser.parse("M10 10 c10 -10 20 -10 30 0"));
        assertVec(10, -10, c.vertices().get(0).out());
        assertVec(-10, -10, c.vertices().get(1).in());
    }

    @Test
    void smoothCubicReflectsThePreviousControlPoint() {
        PathData.Contour c = only(SvgPathParser.parse("M0 0 C0 10 10 10 10 0 S20 -10 20 0"));
        assertEquals(3, c.vertices().size());
        assertVec(0, 10, c.vertices().get(1).in());
        assertVec(0, -10, c.vertices().get(1).out());
        assertVec(0, -10, c.vertices().get(2).in());

        // without a preceding cubic the first control point is the current point
        c = only(SvgPathParser.parse("M0 0 L5 0 s5 5 10 0"));
        assertVec(0, 0, c.vertices().get(1).out());
        assertVec(-5, 5, c.vertices().get(2).in());
    }

    @Test
    void quadraticIsElevatedExactly() {
        PathData.Contour c = only(SvgPathParser.parse("M0 0 Q30 60 60 0"));
        assertVec(20, 40, c.vertices().get(0).out());
        assertVec(-20, 40, c.vertices().get(1).in());
        // the quadratic's midpoint: 0.25*P0 + 0.5*C + 0.25*P1
        assertVec(30, 30, pointOn(c.vertices().get(0), c.vertices().get(1), 0.5));
    }

    @Test
    void smoothQuadraticReflectsTheQuadraticControlPoint() {
        PathData.Contour c = only(SvgPathParser.parse("M0 0 q30 60 60 0 t60 0"));
        assertEquals(3, c.vertices().size());
        // reflected control point is (90, -60)
        assertVec(20, -40, c.vertices().get(1).out());
        assertVec(-20, -40, c.vertices().get(2).in());
        assertVec(120, 0, c.vertices().get(2).point());

        // T without a preceding quadratic is a straight line
        c = only(SvgPathParser.parse("M0 0 T10 10"));
        assertCorner(0, 0, c.vertices().get(0));
        assertCorner(10, 10, c.vertices().get(1));
    }

    @Test
    void arcEndsExactlyAndStaysOnTheCircle() {
        PathData.Contour c = only(SvgPathParser.parse("M10 0 A10 10 0 0 1 -10 0"));
        List<PathData.Vertex> v = c.vertices();
        assertVec(-10, 0, v.get(v.size() - 1).point());
        // a half circle is split into two quarter arcs
        assertEquals(3, v.size());
        // sweep = 1 runs clockwise on screen (y down): through (0, 10)
        assertVec(0, 10, v.get(1).point());
        for (int i = 0; i + 1 < v.size(); i++) {
            for (double t = 0; t <= 1; t += 0.125) {
                Vec2 p = pointOn(v.get(i), v.get(i + 1), t);
                assertEquals(10, Math.hypot(p.x(), p.y()), 0.003);
            }
        }
    }

    @Test
    void arcFlagsSelectOneOfFourArcs() {
        // two circles of radius 10 pass through (0,0) and (10,10): centres (10,0) and (0,10)
        PathData.Contour small = only(SvgPathParser.parse("M0 0 A10 10 0 0 0 10 10"));
        assertEquals(2, small.vertices().size());
        Vec2 mid = pointOn(small.vertices().get(0), small.vertices().get(1), 0.5);
        assertEquals(10, Math.hypot(mid.x() - 10, mid.y()), 0.003);
        assertEquals(10 - Math.sqrt(50), mid.x(), 0.003);
        assertEquals(Math.sqrt(50), mid.y(), 0.003);

        PathData.Contour large = only(SvgPathParser.parse("M0 0 A10 10 0 1 0 10 10"));
        assertEquals(4, large.vertices().size());
        assertVec(10, 10, large.vertices().get(3).point());
        for (int i = 0; i + 1 < 4; i++) {
            Vec2 p = pointOn(large.vertices().get(i), large.vertices().get(i + 1), 0.5);
            assertEquals(10, Math.hypot(p.x(), p.y() - 10), 0.003);
        }
        assertVec(-10, 10, large.vertices().get(1).point());
    }

    @Test
    void arcRadiiAreScaledUpWhenTooSmallAndRotationApplies() {
        // radii 1 cannot span 20: scaled to a half circle of radius 10
        PathData.Contour c = only(SvgPathParser.parse("M0 0 a1 1 0 0 1 20 0"));
        assertEquals(3, c.vertices().size());
        assertVec(10, -10, c.vertices().get(1).point());

        // an ellipse 20x10 rotated by 90° is 10 wide and 20 high
        c = only(SvgPathParser.parse("M0 0 A20 10 90 0 1 20 0"));
        assertVec(20, 0, c.vertices().get(c.vertices().size() - 1).point());
        double minY = 0;
        for (int i = 0; i + 1 < c.vertices().size(); i++) {
            for (double t = 0; t <= 1; t += 0.05) {
                minY = Math.min(minY, pointOn(c.vertices().get(i), c.vertices().get(i + 1), t).y());
            }
        }
        assertEquals(-20, minY, 0.02);
    }

    @Test
    void arcWithZeroRadiusOrNoExtent() {
        PathData.Contour c = only(SvgPathParser.parse("M0 0 A0 5 0 0 1 10 10"));
        assertEquals(2, c.vertices().size());
        assertCorner(10, 10, c.vertices().get(1));

        // identical end points: the arc is omitted
        c = only(SvgPathParser.parse("M0 0 A5 5 0 0 1 0 0 L3 3"));
        assertEquals(2, c.vertices().size());
    }

    @Test
    void arcFlagsWithoutSeparators() {
        PathData.Contour c = only(SvgPathParser.parse("M0 0a1 1 0 01 2 2"));
        PathData.Contour spaced = only(SvgPathParser.parse("M0 0 a1 1 0 0 1 2 2"));
        assertEquals(spaced, c);
        assertVec(2, 2, c.vertices().get(c.vertices().size() - 1).point());

        c = only(SvgPathParser.parse("M0 0a1 1 0 1110 0"));
        assertVec(10, 0, c.vertices().get(c.vertices().size() - 1).point());
    }

    @Test
    void numbersWithoutSeparators() {
        PathData.Contour c = only(SvgPathParser.parse("M1.5.5-1-2L1e1-1E-1,.5e+1,3"));
        List<PathData.Vertex> v = c.vertices();
        assertEquals(4, v.size());
        assertCorner(1.5, 0.5, v.get(0));
        assertCorner(-1, -2, v.get(1));
        assertCorner(10, -0.1, v.get(2));
        assertCorner(5, 3, v.get(3));
    }

    @Test
    void severalSubpathsAndCloseReturnsToTheSubpathStart() {
        PathData path = SvgPathParser.parse("M10 10 h10 v10 z l-5 -5 M50 50 L60 60");
        assertEquals(3, path.contours().size());
        assertTrue(path.contours().get(0).closed());
        assertEquals(3, path.contours().get(0).vertices().size());
        // drawing after z starts a new subpath at the start point of the closed one
        PathData.Contour second = path.contours().get(1);
        assertFalse(second.closed());
        assertCorner(10, 10, second.vertices().get(0));
        assertCorner(5, 5, second.vertices().get(1));
        assertCorner(50, 50, path.contours().get(2).vertices().get(0));
    }

    @Test
    void closingSegmentBackToTheStartMergesIntoTheFirstVertex() {
        PathData.Contour c = only(SvgPathParser.parse("M0 0 L10 0 L10 10 L0 0 Z"));
        assertTrue(c.closed());
        assertEquals(3, c.vertices().size());

        // a curve returning to the start hands its incoming tangent to the first vertex
        c = only(SvgPathParser.parse("M0 0 C0 -10 10 -10 10 0 C10 10 0 10 0 0 Z"));
        assertEquals(2, c.vertices().size());
        assertVec(0, 10, c.vertices().get(0).in());
        assertVec(0, -10, c.vertices().get(0).out());

        // an explicit return without Z stays open, with the duplicate vertex
        c = only(SvgPathParser.parse("M0 0 L10 0 L10 10 L0 0"));
        assertFalse(c.closed());
        assertEquals(4, c.vertices().size());
    }

    @Test
    void malformedDataKeepsWhatParsed() {
        PathData.Contour c = only(SvgPathParser.parse("M0 0 L10 0 L10 oops 20 20"));
        assertEquals(2, c.vertices().size());

        c = only(SvgPathParser.parse("M0 0 L10 0 X 5 5 L20 20"));
        assertEquals(2, c.vertices().size());

        // an incomplete coordinate pair is dropped
        c = only(SvgPathParser.parse("M0 0 L10 0 L20"));
        assertEquals(2, c.vertices().size());

        assertEquals(PathData.EMPTY, SvgPathParser.parse(""));
        assertEquals(PathData.EMPTY, SvgPathParser.parse("L10 10"));
        assertEquals(PathData.EMPTY, SvgPathParser.parse("garbage"));
    }

    @Test
    void aLoneMoveToDrawsNothing() {
        assertEquals(PathData.EMPTY, SvgPathParser.parse("M10 10"));
        assertEquals(1, SvgPathParser.parse("M10 10 M20 20 L30 30").contours().size());
    }
}
