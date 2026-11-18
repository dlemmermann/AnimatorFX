package com.dlsc.animatorfx.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts geometry to Bezier paths.
 */
public final class Geometries {

    /** Handle length of a cubic approximating a quarter circle of radius 1. */
    private static final double KAPPA = 0.5522847498;

    private Geometries() {
    }

    /**
     * The outline of the geometry at the given time. Every geometry yields one contour, closed
     * and running clockwise (y down), except a line, which is open, and a path geometry, which
     * is returned as sampled.
     */
    public static PathData toPath(Geometry geometry, double time) {
        return switch (geometry) {
            case Geometry.Rect r -> rect(r.size().at(time), r.cornerRadius().at(time));
            case Geometry.Ellipse e -> ellipse(e.size().at(time));
            case Geometry.Line l -> single(List.of(corner(l.start().at(time)), corner(l.end().at(time))), false);
            case Geometry.Polygon p -> {
                double radius = p.radius().at(time);
                yield radial(p.sides(), radius, radius, radius);
            }
            case Geometry.Star s -> {
                double outer = s.outerRadius().at(time);
                yield radial(s.points() * 2, outer, outer, s.innerRadius().at(time));
            }
            case Geometry.PathGeometry p -> p.path().at(time);
        };
    }

    private static PathData rect(Vec2 size, double cornerRadius) {
        double w = size.x();
        double h = size.y();
        double r = Math.min(cornerRadius, Math.min(Math.abs(w), Math.abs(h)) / 2);
        if (!(r > 0)) {
            return single(List.of(corner(0, 0), corner(w, 0), corner(w, h), corner(0, h)), true);
        }
        // the radius runs towards the opposite side, also for a negative size
        double rx = Math.copySign(r, w);
        double ry = Math.copySign(r, h);
        double kx = rx * KAPPA;
        double ky = ry * KAPPA;
        return single(List.of(
                vertex(rx, 0, -kx, 0, 0, 0),
                vertex(w - rx, 0, 0, 0, kx, 0),
                vertex(w, ry, 0, -ky, 0, 0),
                vertex(w, h - ry, 0, 0, 0, ky),
                vertex(w - rx, h, kx, 0, 0, 0),
                vertex(rx, h, 0, 0, -kx, 0),
                vertex(0, h - ry, 0, ky, 0, 0),
                vertex(0, ry, 0, 0, 0, -ky)), true);
    }

    private static PathData ellipse(Vec2 size) {
        double rx = size.x() / 2;
        double ry = size.y() / 2;
        double kx = rx * KAPPA;
        double ky = ry * KAPPA;
        return single(List.of(
                vertex(rx, 0, -kx, 0, kx, 0),
                vertex(2 * rx, ry, 0, -ky, 0, ky),
                vertex(rx, 2 * ry, kx, 0, -kx, 0),
                vertex(0, ry, 0, ky, 0, -ky)), true);
    }

    /**
     * Corner vertices spread evenly around {@code (centre, centre)}, starting at the top, with
     * even vertices on {@code evenRadius} and odd ones on {@code oddRadius}.
     */
    private static PathData radial(int count, double centre, double evenRadius, double oddRadius) {
        List<PathData.Vertex> vertices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count - Math.PI / 2;
            double radius = i % 2 == 0 ? evenRadius : oddRadius;
            vertices.add(corner(centre + radius * Math.cos(angle), centre + radius * Math.sin(angle)));
        }
        return single(vertices, true);
    }

    private static PathData single(List<PathData.Vertex> vertices, boolean closed) {
        return new PathData(List.of(new PathData.Contour(vertices, closed)));
    }

    private static PathData.Vertex corner(Vec2 point) {
        return new PathData.Vertex(point, Vec2.ZERO, Vec2.ZERO);
    }

    private static PathData.Vertex corner(double x, double y) {
        return corner(new Vec2(x, y));
    }

    private static PathData.Vertex vertex(double x, double y, double inX, double inY, double outX, double outY) {
        return new PathData.Vertex(new Vec2(x, y), new Vec2(inX, inY), new Vec2(outX, outY));
    }
}
