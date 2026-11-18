package com.dlsc.animatorfx.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertex-by-vertex interpolation between two paths.
 */
public final class PathMorph {

    private PathMorph() {
    }

    /**
     * Interpolates every vertex (point and both tangents) from {@code a} to {@code b}. A contour
     * with fewer vertices than its counterpart is subdivided on its longest segments first, which
     * does not change its shape. Paths that cannot be matched hold ({@code a} for {@code u < 1},
     * otherwise {@code b}): different contour counts, an open contour facing a closed one, or an
     * empty contour facing a non-empty one.
     */
    public static PathData morph(PathData a, PathData b, double u) {
        if (!compatible(a, b)) {
            return u < 1 ? a : b;
        }
        List<PathData.Contour> contours = new ArrayList<>(a.contours().size());
        for (int i = 0; i < a.contours().size(); i++) {
            PathData.Contour ca = a.contours().get(i);
            PathData.Contour cb = b.contours().get(i);
            int count = Math.max(ca.vertices().size(), cb.vertices().size());
            List<PathData.Vertex> va = subdivide(ca, count);
            List<PathData.Vertex> vb = subdivide(cb, count);
            List<PathData.Vertex> vertices = new ArrayList<>(count);
            for (int j = 0; j < count; j++) {
                PathData.Vertex x = va.get(j);
                PathData.Vertex y = vb.get(j);
                vertices.add(new PathData.Vertex(x.point().lerp(y.point(), u), x.in().lerp(y.in(), u),
                        x.out().lerp(y.out(), u)));
            }
            contours.add(new PathData.Contour(vertices, ca.closed()));
        }
        return new PathData(contours);
    }

    private static boolean compatible(PathData a, PathData b) {
        if (a.contours().size() != b.contours().size()) {
            return false;
        }
        for (int i = 0; i < a.contours().size(); i++) {
            PathData.Contour ca = a.contours().get(i);
            PathData.Contour cb = b.contours().get(i);
            if (ca.closed() != cb.closed() || ca.vertices().isEmpty() != cb.vertices().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<PathData.Vertex> subdivide(PathData.Contour contour, int count) {
        if (contour.vertices().size() >= count) {
            return contour.vertices();
        }
        List<PathData.Vertex> vertices = new ArrayList<>(contour.vertices());
        List<Cubic> segments = Cubic.segments(contour);
        if (segments.isEmpty()) {
            // a single open point has nothing to split
            PathData.Vertex only = vertices.get(0);
            while (vertices.size() < count) {
                vertices.add(new PathData.Vertex(only.point(), Vec2.ZERO, Vec2.ZERO));
            }
            return vertices;
        }
        List<Double> lengths = new ArrayList<>(segments.size());
        for (Cubic segment : segments) {
            lengths.add(segment.length());
        }
        while (vertices.size() < count) {
            int longest = 0;
            for (int i = 1; i < lengths.size(); i++) {
                if (lengths.get(i) > lengths.get(longest)) {
                    longest = i;
                }
            }
            Cubic[] halves = segments.get(longest).split(0.5);
            segments.set(longest, halves[0]);
            segments.add(longest + 1, halves[1]);
            lengths.set(longest, halves[0].length());
            lengths.add(longest + 1, halves[1].length());

            // segment i runs from vertex i to vertex i + 1 (wrapping to 0 when closed)
            Cubic h0 = halves[0];
            Cubic h1 = halves[1];
            PathData.Vertex from = vertices.get(longest);
            vertices.set(longest, new PathData.Vertex(from.point(), from.in(), h0.p1().minus(h0.p0())));
            int toIndex = (longest + 1) % vertices.size();
            PathData.Vertex to = vertices.get(toIndex);
            vertices.set(toIndex, new PathData.Vertex(to.point(), h1.p2().minus(h1.p3()), to.out()));
            vertices.add(longest + 1, new PathData.Vertex(h0.p3(), h0.p2().minus(h0.p3()), h1.p1().minus(h1.p0())));
        }
        return vertices;
    }
}
