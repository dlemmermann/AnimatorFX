package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.doc.Matrices;
import com.dlsc.animatorfx.model.Vec2;

import java.util.List;

/**
 * A box after a transform: its four corners, named for the untransformed box.
 */
public record Quad(Vec2 topLeft, Vec2 topRight, Vec2 bottomRight, Vec2 bottomLeft) {

    public static Quad of(Box box, double[] matrix) {
        return new Quad(Matrices.apply(matrix, box.point(0, 0)), Matrices.apply(matrix, box.point(1, 0)),
                Matrices.apply(matrix, box.point(1, 1)), Matrices.apply(matrix, box.point(0, 1)));
    }

    public static Quad of(Box box) {
        return of(box, Matrices.identity());
    }

    public List<Vec2> corners() {
        return List.of(topLeft, topRight, bottomRight, bottomLeft);
    }

    /**
     * The point at the given fractions of the box's width and height.
     */
    public Vec2 point(double fx, double fy) {
        return topLeft.lerp(topRight, fx).lerp(bottomLeft.lerp(bottomRight, fx), fy);
    }

    public Vec2 handle(Handle handle) {
        return point(handle.fx(), handle.fy());
    }

    public Vec2 center() {
        return point(0.5, 0.5);
    }

    /**
     * Where the rotate handle sits: the given distance outside the middle of the top edge.
     */
    public Vec2 rotateHandle(double distance) {
        Vec2 top = point(0.5, 0);
        Vec2 out = top.minus(center());
        double length = Math.hypot(out.x(), out.y());
        if (length < 1e-9) {
            // a box without height: perpendicular to the top edge
            Vec2 edge = topRight.minus(topLeft);
            double edgeLength = Math.hypot(edge.x(), edge.y());
            out = edgeLength < 1e-9 ? new Vec2(0, -1) : new Vec2(edge.y() / edgeLength, -edge.x() / edgeLength);
            length = 1;
        }
        return top.plus(out.times(distance / length));
    }

    public Box bounds() {
        return Box.around(corners());
    }

    public double width() {
        return distance(topLeft, topRight);
    }

    public double height() {
        return distance(topLeft, bottomLeft);
    }

    /**
     * Whether the point is inside or on the edge. A quad without area, such as the box of a
     * layer scaled to zero, contains nothing.
     */
    public boolean contains(Vec2 p) {
        List<Vec2> c = corners();
        double doubleArea = 0;
        for (int i = 0; i < 4; i++) {
            Vec2 a = c.get(i);
            Vec2 b = c.get((i + 1) % 4);
            doubleArea += a.x() * b.y() - b.x() * a.y();
        }
        if (Math.abs(doubleArea) < 1e-9) {
            return false;
        }
        boolean positive = false;
        boolean negative = false;
        for (int i = 0; i < 4; i++) {
            Vec2 a = c.get(i);
            Vec2 b = c.get((i + 1) % 4);
            double cross = (b.x() - a.x()) * (p.y() - a.y()) - (b.y() - a.y()) * (p.x() - a.x());
            positive |= cross > 0;
            negative |= cross < 0;
        }
        return !(positive && negative);
    }

    private static double distance(Vec2 a, Vec2 b) {
        return Math.hypot(b.x() - a.x(), b.y() - a.y());
    }
}
