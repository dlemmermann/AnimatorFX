package com.dlsc.animatorfx.model;

import java.util.ArrayList;
import java.util.List;

/** Test fixtures. */
final class Paths {

    static final double KAPPA = 0.5522847498307936;

    private Paths() {
    }

    static PathData.Vertex corner(double x, double y) {
        return new PathData.Vertex(new Vec2(x, y), Vec2.ZERO, Vec2.ZERO);
    }

    static PathData polygon(boolean closed, double... xy) {
        List<PathData.Vertex> vertices = new ArrayList<>();
        for (int i = 0; i < xy.length; i += 2) {
            vertices.add(corner(xy[i], xy[i + 1]));
        }
        return new PathData(List.of(new PathData.Contour(vertices, closed)));
    }

    static PathData square() {
        return polygon(true, 0, 0, 100, 0, 100, 100, 0, 100);
    }

    /** Clockwise (y down) circle around the origin starting at (r, 0). */
    static PathData circle(double r) {
        double k = r * KAPPA;
        return new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(r, 0), new Vec2(0, -k), new Vec2(0, k)),
                new PathData.Vertex(new Vec2(0, r), new Vec2(k, 0), new Vec2(-k, 0)),
                new PathData.Vertex(new Vec2(-r, 0), new Vec2(0, k), new Vec2(0, -k)),
                new PathData.Vertex(new Vec2(0, -r), new Vec2(-k, 0), new Vec2(k, 0))), true)));
    }
}
