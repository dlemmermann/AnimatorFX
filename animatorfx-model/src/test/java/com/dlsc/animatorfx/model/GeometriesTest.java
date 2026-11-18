package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometriesTest {

    private static final double EPS = 1e-9;
    /** The implementation's kappa has ten decimals. */
    private static final double HANDLE_EPS = 1e-6;
    /** PathMeasure flattens curves, which slightly underestimates arc lengths. */
    private static final double LENGTH_EPS = 0.5;

    private static PathData.Contour single(PathData path) {
        assertEquals(1, path.contours().size());
        return path.contours().get(0);
    }

    /** Bounds of the anchor points as {minX, minY, maxX, maxY}. */
    private static void assertBounds(double minX, double minY, double maxX, double maxY, PathData.Contour contour) {
        List<Vec2> points = contour.vertices().stream().map(PathData.Vertex::point).toList();
        assertEquals(minX, points.stream().mapToDouble(Vec2::x).min().orElseThrow(), EPS);
        assertEquals(minY, points.stream().mapToDouble(Vec2::y).min().orElseThrow(), EPS);
        assertEquals(maxX, points.stream().mapToDouble(Vec2::x).max().orElseThrow(), EPS);
        assertEquals(maxY, points.stream().mapToDouble(Vec2::y).max().orElseThrow(), EPS);
    }

    private static void assertPoint(double x, double y, Vec2 actual) {
        assertEquals(x, actual.x(), EPS);
        assertEquals(y, actual.y(), EPS);
    }

    private static Geometry.Rect rect(double w, double h, double radius) {
        return new Geometry.Rect(Animatable.of(new Vec2(w, h)), Animatable.of(radius));
    }

    @Test
    void rectWithoutRadiusHasFourCorners() {
        PathData.Contour c = single(Geometries.toPath(rect(100, 50, 0), 0));
        assertTrue(c.closed());
        assertEquals(4, c.vertices().size());
        assertBounds(0, 0, 100, 50, c);
        for (PathData.Vertex v : c.vertices()) {
            assertEquals(Vec2.ZERO, v.in());
            assertEquals(Vec2.ZERO, v.out());
        }
    }

    @Test
    void rectWithRadiusHasEightVerticesAndRoundCorners() {
        PathData path = Geometries.toPath(rect(100, 50, 10), 0);
        PathData.Contour c = single(path);
        assertTrue(c.closed());
        assertEquals(8, c.vertices().size());
        assertBounds(0, 0, 100, 50, c);
        // four straight sides of 80/30/80/30 plus a full circle of radius 10
        assertEquals(220 + 2 * Math.PI * 10, new PathMeasure(path).length(), LENGTH_EPS);
        double k = 10 * Paths.KAPPA;
        long tangents = c.vertices().stream()
                .filter(v -> Math.abs(Math.hypot(v.in().x() + v.out().x(), v.in().y() + v.out().y()) - k) < HANDLE_EPS)
                .count();
        assertEquals(8, tangents);
    }

    @Test
    void rectRadiusIsClampedToHalfTheSmallerSide() {
        PathData path = Geometries.toPath(rect(100, 50, 500), 0);
        assertEquals(8, single(path).vertices().size());
        assertBounds(0, 0, 100, 50, single(path));
        assertEquals(100 + 2 * Math.PI * 25, new PathMeasure(path).length(), LENGTH_EPS);
    }

    @Test
    void ellipseHasFourVerticesWithTangents() {
        PathData path = Geometries.toPath(new Geometry.Ellipse(Animatable.of(new Vec2(100, 100))), 0);
        PathData.Contour c = single(path);
        assertTrue(c.closed());
        assertEquals(4, c.vertices().size());
        assertBounds(0, 0, 100, 100, c);
        for (PathData.Vertex v : c.vertices()) {
            assertEquals(50 * Paths.KAPPA, Math.hypot(v.in().x(), v.in().y()), HANDLE_EPS);
            assertEquals(50 * Paths.KAPPA, Math.hypot(v.out().x(), v.out().y()), HANDLE_EPS);
            assertPoint(0, 0, v.in().plus(v.out()));
        }
        assertEquals(2 * Math.PI * 50, new PathMeasure(path).length(), LENGTH_EPS);
    }

    @Test
    void ellipseTangentsFollowBothRadii() {
        PathData.Contour c = single(Geometries.toPath(new Geometry.Ellipse(Animatable.of(new Vec2(200, 100))), 0));
        assertBounds(0, 0, 200, 100, c);
        for (PathData.Vertex v : c.vertices()) {
            boolean onHorizontalAxis = Math.abs(v.point().y() - 50) < EPS;
            double expected = (onHorizontalAxis ? 50 : 100) * Paths.KAPPA;
            assertEquals(expected, Math.hypot(v.out().x(), v.out().y()), HANDLE_EPS);
        }
    }

    @Test
    void lineIsOneOpenContour() {
        PathData.Contour c = single(Geometries.toPath(
                new Geometry.Line(Animatable.of(new Vec2(10, 20)), Animatable.of(new Vec2(110, 60))), 0));
        assertFalse(c.closed());
        assertEquals(List.of(Paths.corner(10, 20), Paths.corner(110, 60)), c.vertices());
    }

    @Test
    void polygonStartsAtTheTop() {
        PathData.Contour c = single(Geometries.toPath(new Geometry.Polygon(4, Animatable.of(50.0)), 0));
        assertTrue(c.closed());
        assertEquals(4, c.vertices().size());
        assertBounds(0, 0, 100, 100, c);
        assertPoint(50, 0, c.vertices().get(0).point());
        assertPoint(100, 50, c.vertices().get(1).point());
        assertEquals(Vec2.ZERO, c.vertices().get(0).out());
    }

    @Test
    void starAlternatesOuterAndInnerVertices() {
        PathData.Contour c = single(Geometries.toPath(
                new Geometry.Star(5, Animatable.of(50.0), Animatable.of(20.0)), 0));
        assertTrue(c.closed());
        assertEquals(10, c.vertices().size());
        assertPoint(50, 0, c.vertices().get(0).point());
        Vec2 centre = new Vec2(50, 50);
        for (int i = 0; i < 10; i++) {
            Vec2 d = c.vertices().get(i).point().minus(centre);
            assertEquals(i % 2 == 0 ? 50 : 20, Math.hypot(d.x(), d.y()), EPS);
        }
        // a five-pointed star is mirror-symmetric about x = 50 and its lowest points are outer vertices
        assertBounds(50 - 50 * Math.cos(Math.toRadians(18)), 0, 50 + 50 * Math.cos(Math.toRadians(18)),
                50 + 50 * Math.cos(Math.toRadians(36)), c);
    }

    @Test
    void pathGeometryReturnsTheSampledPath() {
        PathData square = Paths.square();
        assertSame(square, Geometries.toPath(new Geometry.PathGeometry(Animatable.of(square)), 3));
    }

    @Test
    void geometryIsSampledAtTheGivenTime() {
        Animatable<Vec2> size = Animatable.of(Vec2.ZERO).withKeyframe(0, new Vec2(10, 10), Easing.LINEAR)
                .withKeyframe(1, new Vec2(30, 50), Easing.LINEAR);
        PathData.Contour c = single(Geometries.toPath(new Geometry.Rect(size, Animatable.of(0.0)), 0.5));
        assertBounds(0, 0, 20, 30, c);
        assertNotEquals(c, single(Geometries.toPath(new Geometry.Rect(size, Animatable.of(0.0)), 1)));
    }

    @Test
    void degenerateVertexCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Geometry.Polygon(2, Animatable.of(10.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Geometry.Star(1, Animatable.of(10.0), Animatable.of(5.0)));
    }
}
