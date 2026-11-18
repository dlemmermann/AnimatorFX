package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.canvas.PathOps.Tangent;
import com.dlsc.animatorfx.editor.canvas.PathOps.TangentMode;
import com.dlsc.animatorfx.editor.canvas.PathOps.VertexRef;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Geometries;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.PathData.Contour;
import com.dlsc.animatorfx.model.PathData.Vertex;
import com.dlsc.animatorfx.model.PathMeasure;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathOpsTest {

    private static Vertex corner(double x, double y) {
        return new Vertex(new Vec2(x, y), Vec2.ZERO, Vec2.ZERO);
    }

    private static Vertex vertex(double x, double y, double inX, double inY, double outX, double outY) {
        return new Vertex(new Vec2(x, y), new Vec2(inX, inY), new Vec2(outX, outY));
    }

    private static PathData path(boolean closed, Vertex... vertices) {
        return new PathData(List.of(new Contour(List.of(vertices), closed)));
    }

    private static PathData ellipse() {
        return Geometries.toPath(new Geometry.Ellipse(Animatable.of(new Vec2(200, 120))), 0);
    }

    private static final PathData CURVE = path(false,
            vertex(0, 0, 0, 0, 60, -80), vertex(200, 40, -30, 90, 40, 20), corner(300, 0));

    private static void assertVec(double x, double y, Vec2 actual) {
        assertEquals(x, actual.x(), 1e-9, "x of " + actual);
        assertEquals(y, actual.y(), 1e-9, "y of " + actual);
    }

    /** Every point of one outline lies on the other, checked on a dense sampling of both. */
    private static void assertSameOutline(PathData expected, PathData actual) {
        for (int i = 0; i <= 400; i++) {
            Vec2 point = new PathMeasure(expected).pointAt(i / 400.0);
            assertTrue(PathOps.nearest(actual, point).orElseThrow().distance() < 0.02, "point " + i + " left the outline");
            Vec2 back = new PathMeasure(actual).pointAt(i / 400.0);
            assertTrue(PathOps.nearest(expected, back).orElseThrow().distance() < 0.02, "point " + i + " is new");
        }
    }

    // ------------------------------------------------------------------ insert

    @Test
    void insertingAVertexOnACurveKeepsTheOutline() {
        PathData result = PathOps.insertVertex(CURVE, 0, 0, 0.3);

        assertEquals(4, result.contours().get(0).vertices().size());
        assertSameOutline(CURVE, result);
        Vertex inserted = result.contours().get(0).vertices().get(1);
        assertVec(inserted.point().x(), inserted.point().y(), PathOps.pointAt(CURVE, 0, 0, 0.3));
        assertTrue(PathOps.isSmooth(inserted), "a vertex inserted on a curve is smooth");
        // the far handles of the neighbours are untouched
        assertEquals(CURVE.contours().get(0).vertices().get(0).in(), result.contours().get(0).vertices().get(0).in());
        assertEquals(CURVE.contours().get(0).vertices().get(1).out(), result.contours().get(0).vertices().get(2).out());
    }

    @Test
    void insertingOnTheClosingSegmentOfAClosedContourKeepsTheOutline() {
        PathData ellipse = ellipse();

        PathData result = PathOps.insertVertex(ellipse, 0, 3, 0.5);

        List<Vertex> vertices = result.contours().get(0).vertices();
        assertEquals(5, vertices.size());
        assertTrue(result.contours().get(0).closed());
        assertSameOutline(ellipse, result);
        // the new vertex is the last one: between the old last vertex and the first
        assertVec(vertices.get(4).point().x(), vertices.get(4).point().y(), PathOps.pointAt(ellipse, 0, 3, 0.5));
    }

    @Test
    void insertingOnAStraightSegmentMakesACorner() {
        PathData line = path(false, corner(0, 0), corner(100, 0));

        PathData result = PathOps.insertVertex(line, 0, 0, 0.25);

        assertEquals(List.of(corner(0, 0), corner(25, 0), corner(100, 0)), result.contours().get(0).vertices());
    }

    @Test
    void insertingOutsideThePathChangesNothing() {
        assertSame(CURVE, PathOps.insertVertex(CURVE, 0, 2, 0.5));
        assertSame(CURVE, PathOps.insertVertex(CURVE, 1, 0, 0.5));
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deletingAVertexRemovesItAndKeepsTheOthers() {
        PathData result = PathOps.deleteVertices(CURVE, Set.of(new VertexRef(0, 1)));

        List<Vertex> vertices = result.contours().get(0).vertices();
        assertEquals(List.of(CURVE.contours().get(0).vertices().get(0), corner(300, 0)), vertices);
    }

    @Test
    void anOpenContourLeftWithOneVertexIsRemoved() {
        PathData two = new PathData(List.of(new Contour(List.of(corner(0, 0), corner(10, 0)), false),
                new Contour(List.of(corner(0, 50), corner(10, 50), corner(20, 50)), false)));

        PathData result = PathOps.deleteVertices(two, Set.of(new VertexRef(0, 1), new VertexRef(1, 0)));

        assertEquals(1, result.contours().size());
        assertEquals(List.of(corner(10, 50), corner(20, 50)), result.contours().get(0).vertices());
    }

    @Test
    void aClosedTriangleThatLosesAVertexBecomesAnOpenLine() {
        PathData triangle = path(true, corner(0, 0), corner(100, 0), corner(50, 80));

        PathData result = PathOps.deleteVertices(triangle, Set.of(new VertexRef(0, 2)));

        assertEquals(new Contour(List.of(corner(0, 0), corner(100, 0)), false), result.contours().get(0));
    }

    @Test
    void deletingEverythingLeavesAnEmptyPath() {
        PathData line = path(false, corner(0, 0), corner(100, 0));

        assertTrue(PathOps.deleteVertices(line, Set.of(new VertexRef(0, 0))).contours().isEmpty());
    }

    // ------------------------------------------------------------------ move, tangents

    @Test
    void movingVerticesShiftsThePointsAndKeepsTheTangents() {
        PathData result = PathOps.moveVertices(CURVE, Set.of(new VertexRef(0, 1), new VertexRef(0, 9)), new Vec2(5, -7));

        Vertex moved = result.contours().get(0).vertices().get(1);
        assertEquals(vertex(205, 33, -30, 90, 40, 20), moved);
        assertEquals(CURVE.contours().get(0).vertices().get(0), result.contours().get(0).vertices().get(0));
    }

    @Test
    void aSymmetricTangentMirrorsTheOtherOne() {
        PathData result = PathOps.setTangent(CURVE, new VertexRef(0, 1), Tangent.OUT, new Vec2(10, 30),
                TangentMode.SYMMETRIC);

        assertEquals(vertex(200, 40, -10, -30, 10, 30), result.contours().get(0).vertices().get(1));
    }

    @Test
    void anAlignedTangentTurnsTheOtherOneAndKeepsItsLength() {
        PathData start = path(false, corner(0, 0), vertex(100, 0, -50, 0, 20, 0), corner(200, 0));

        PathData result = PathOps.setTangent(start, new VertexRef(0, 1), Tangent.OUT, new Vec2(0, 30), TangentMode.ALIGNED);

        Vertex vertex = result.contours().get(0).vertices().get(1);
        assertVec(0, 30, vertex.out());
        assertVec(0, -50, vertex.in());
    }

    @Test
    void aBrokenTangentLeavesTheOtherOneAlone() {
        PathData result = PathOps.setTangent(CURVE, new VertexRef(0, 1), Tangent.IN, new Vec2(-5, 5), TangentMode.BROKEN);

        assertEquals(vertex(200, 40, -5, 5, 40, 20), result.contours().get(0).vertices().get(1));
    }

    @Test
    void theModeOfADragFollowsTheVertex() {
        assertEquals(TangentMode.SYMMETRIC, PathOps.dragMode(vertex(0, 0, -10, 5, 10, -5)));
        assertEquals(TangentMode.ALIGNED, PathOps.dragMode(vertex(0, 0, -30, 15, 10, -5)));
        assertEquals(TangentMode.BROKEN, PathOps.dragMode(vertex(0, 0, -10, 5, 10, 5)));
        assertEquals(TangentMode.BROKEN, PathOps.dragMode(vertex(0, 0, 0, 0, 10, 5)));
    }

    // ------------------------------------------------------------------ corner and smooth

    @Test
    void aCornerBecomesSmoothWithTangentsAlongItsNeighboursAndAThirdOfTheChords() {
        PathData start = path(false, corner(0, 0), corner(90, 0), corner(90, 60));

        PathData result = PathOps.toggleSmooth(start, new VertexRef(0, 1));

        Vertex vertex = result.contours().get(0).vertices().get(1);
        // direction (0,0) -> (90,60), lengths 90 / 3 and 60 / 3
        double length = Math.hypot(90, 60);
        assertVec(-30 * 90 / length, -30 * 60 / length, vertex.in());
        assertVec(20 * 90 / length, 20 * 60 / length, vertex.out());
        assertTrue(PathOps.isSmooth(vertex));
    }

    @Test
    void anEndVertexBecomesSmoothTowardsItsOnlyNeighbour() {
        PathData start = path(false, corner(0, 0), corner(90, 0));

        Vertex vertex = PathOps.toggleSmooth(start, new VertexRef(0, 0)).contours().get(0).vertices().get(0);

        assertVec(30, 0, vertex.out());
        assertVec(-30, 0, vertex.in());
    }

    @Test
    void theFirstVertexOfAClosedContourUsesTheLastOneAsItsNeighbour() {
        PathData start = path(true, corner(0, 0), corner(90, 0), corner(0, 90));

        Vertex vertex = PathOps.toggleSmooth(start, new VertexRef(0, 0)).contours().get(0).vertices().get(0);

        // direction (0,90) -> (90,0)
        assertVec(30 * Math.sqrt(0.5), -30 * Math.sqrt(0.5), vertex.out());
        assertVec(-30 * Math.sqrt(0.5), 30 * Math.sqrt(0.5), vertex.in());
    }

    @Test
    void aVertexWithTangentsBecomesACorner() {
        PathData result = PathOps.toggleSmooth(CURVE, new VertexRef(0, 1));

        assertEquals(corner(200, 40), result.contours().get(0).vertices().get(1));
        assertTrue(PathOps.isCorner(result.contours().get(0).vertices().get(1)));
    }

    // ------------------------------------------------------------------ close, nearest, translate, transform

    @Test
    void closingAndOpeningAContour() {
        PathData closed = PathOps.setClosed(CURVE, 0, true);

        assertTrue(closed.contours().get(0).closed());
        assertEquals(CURVE, PathOps.setClosed(closed, 0, false));
        assertSame(CURVE, PathOps.setClosed(CURVE, 0, false));
        assertEquals(3, PathOps.segmentCount(closed.contours().get(0)));
        assertEquals(2, PathOps.segmentCount(CURVE.contours().get(0)));
    }

    @Test
    void theNearestPointTellsContourSegmentParameterAndDistance() {
        PathData two = new PathData(List.of(new Contour(List.of(corner(0, 0), corner(100, 0), corner(100, 100)), false),
                new Contour(List.of(corner(0, 300), corner(100, 300)), false)));

        PathOps.Hit onSecondSegment = PathOps.nearest(two, new Vec2(110, 25)).orElseThrow();
        assertEquals(0, onSecondSegment.contour());
        assertEquals(1, onSecondSegment.segment());
        assertEquals(0.25, onSecondSegment.t(), 1e-3);
        assertEquals(10, onSecondSegment.distance(), 1e-3);
        assertEquals(100, onSecondSegment.point().x(), 1e-3);

        PathOps.Hit onOtherContour = PathOps.nearest(two, new Vec2(40, 290)).orElseThrow();
        assertEquals(1, onOtherContour.contour());
        assertEquals(0, onOtherContour.segment());
        assertEquals(0.4, onOtherContour.t(), 1e-3);

        assertTrue(PathOps.nearest(PathData.EMPTY, Vec2.ZERO).isEmpty());
    }

    @Test
    void theNearestPointOnACurveIsOnTheCurve() {
        Vec2 on = PathOps.pointAt(CURVE, 0, 1, 0.62);

        PathOps.Hit hit = PathOps.nearest(CURVE, on).orElseThrow();

        assertEquals(1, hit.segment());
        assertEquals(0.62, hit.t(), 1e-3);
        assertEquals(0, hit.distance(), 1e-3);
    }

    @Test
    void translatingMovesEveryPoint() {
        PathData result = PathOps.translate(CURVE, new Vec2(10, 20));

        assertEquals(vertex(210, 60, -30, 90, 40, 20), result.contours().get(0).vertices().get(1));
        assertSame(CURVE, PathOps.translate(CURVE, Vec2.ZERO));
    }

    @Test
    void aMatrixTransformsPointsWithAndTangentsWithoutItsTranslation() {
        // scale by 2, then move by (100, 50)
        double[] matrix = {2, 0, 100, 0, 2, 50};

        PathData result = PathOps.transform(CURVE, matrix);

        assertEquals(vertex(500, 130, -60, 180, 80, 40), result.contours().get(0).vertices().get(1));
    }

    @Test
    void theBoundsFollowTheCurveNotItsHandles() {
        Box box = PathOps.bounds(ellipse());

        assertEquals(0, box.minX(), 0.01);
        assertEquals(0, box.minY(), 0.01);
        assertEquals(200, box.maxX(), 0.01);
        assertEquals(120, box.maxY(), 0.01);

        // a hump whose handles reach up to y = -80 only gets to y = -60
        Box hump = PathOps.bounds(path(false, vertex(0, 0, 0, 0, 0, -80), vertex(100, 0, 0, -80, 0, 0)));
        assertEquals(-60, hump.minY(), 1e-9);
        assertFalse(hump.minY() < -60.001);
    }

    // ------------------------------------------------------------------ drawing a path

    @Test
    void appendingAndRemovingTheLastVertex() {
        PathData one = PathOps.append(PathData.EMPTY, corner(0, 0));
        PathData two = PathOps.append(one, corner(10, 0));

        assertEquals(path(false, corner(0, 0), corner(10, 0)), two);
        assertEquals(one, PathOps.removeLast(two));
        assertTrue(PathOps.removeLast(one).contours().isEmpty());
    }
}
