package com.dlsc.animatorfx.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One cubic segment of a contour with absolute control points. A segment whose handles are both
 * zero is a straight line and is treated as such, so that splitting it yields corners again and
 * its parameter is proportional to distance.
 */
record Cubic(Vec2 p0, Vec2 p1, Vec2 p2, Vec2 p3) {

    private static final int MIN_STEPS = 8;
    private static final int MAX_STEPS = 64;

    static Cubic between(PathData.Vertex from, PathData.Vertex to) {
        return new Cubic(from.point(), from.point().plus(from.out()), to.point().plus(to.in()), to.point());
    }

    static List<Cubic> segments(PathData.Contour contour) {
        List<PathData.Vertex> v = contour.vertices();
        List<Cubic> segments = new ArrayList<>(v.size());
        for (int i = 0; i + 1 < v.size(); i++) {
            segments.add(between(v.get(i), v.get(i + 1)));
        }
        if (contour.closed() && !v.isEmpty()) {
            segments.add(between(v.get(v.size() - 1), v.get(0)));
        }
        return segments;
    }

    /**
     * Turns consecutive segments (each starting where the previous one ends) into the vertices of
     * an open contour.
     */
    static List<PathData.Vertex> vertices(List<Cubic> chain) {
        List<PathData.Vertex> vertices = new ArrayList<>(chain.size() + 1);
        Vec2 in = Vec2.ZERO;
        for (Cubic c : chain) {
            vertices.add(new PathData.Vertex(c.p0, in, c.p1.minus(c.p0)));
            in = c.p2.minus(c.p3);
        }
        if (!chain.isEmpty()) {
            vertices.add(new PathData.Vertex(chain.get(chain.size() - 1).p3, in, Vec2.ZERO));
        }
        return vertices;
    }

    boolean isLine() {
        return p1.equals(p0) && p2.equals(p3);
    }

    Vec2 pointAt(double t) {
        if (isLine()) {
            return p0.lerp(p3, t);
        }
        double s = 1 - t;
        double a = s * s * s;
        double b = 3 * s * s * t;
        double c = 3 * s * t * t;
        double d = t * t * t;
        return new Vec2(a * p0.x() + b * p1.x() + c * p2.x() + d * p3.x(),
                a * p0.y() + b * p1.y() + c * p2.y() + d * p3.y());
    }

    Vec2 derivativeAt(double t) {
        if (isLine()) {
            return p3.minus(p0);
        }
        double s = 1 - t;
        return p1.minus(p0).times(3 * s * s).plus(p2.minus(p1).times(6 * s * t)).plus(p3.minus(p2).times(3 * t * t));
    }

    /** De Casteljau split into the parts before and after {@code t}. */
    Cubic[] split(double t) {
        if (isLine()) {
            Vec2 m = p0.lerp(p3, t);
            return new Cubic[] {new Cubic(p0, p0, m, m), new Cubic(m, m, p3, p3)};
        }
        Vec2 q0 = p0.lerp(p1, t);
        Vec2 q1 = p1.lerp(p2, t);
        Vec2 q2 = p2.lerp(p3, t);
        Vec2 r0 = q0.lerp(q1, t);
        Vec2 r1 = q1.lerp(q2, t);
        Vec2 m = r0.lerp(r1, t);
        return new Cubic[] {new Cubic(p0, q0, r0, m), new Cubic(m, r1, q2, p3)};
    }

    /** The part between the parameters {@code t0 <= t1}. */
    Cubic sub(double t0, double t1) {
        if (t1 <= 0) {
            return new Cubic(p0, p0, p0, p0);
        }
        Cubic head = t1 >= 1 ? this : split(t1)[0];
        return t0 <= 0 ? head : head.split(t0 / t1)[1];
    }

    /** Number of straight pieces used to flatten this segment. */
    int steps() {
        if (isLine()) {
            return 1;
        }
        double controlLength = distance(p0, p1) + distance(p1, p2) + distance(p2, p3);
        return Math.clamp((int) Math.ceil(controlLength / 4), MIN_STEPS, MAX_STEPS);
    }

    double length() {
        int steps = steps();
        double length = 0;
        Vec2 previous = p0;
        for (int i = 1; i <= steps; i++) {
            Vec2 next = pointAt((double) i / steps);
            length += distance(previous, next);
            previous = next;
        }
        return length;
    }

    static double distance(Vec2 a, Vec2 b) {
        return Math.hypot(b.x() - a.x(), b.y() - a.y());
    }
}
