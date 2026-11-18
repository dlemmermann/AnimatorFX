package com.dlsc.animatorfx.model;

import java.util.List;
import java.util.Objects;

/**
 * A Bezier path made of contours.
 */
public record PathData(List<Contour> contours) {

    public static final PathData EMPTY = new PathData(List.of());

    public PathData {
        contours = List.copyOf(contours);
    }

    /**
     * Consecutive vertices are joined by cubic segments; a closed contour also joins the last
     * vertex to the first.
     */
    public record Contour(List<Vertex> vertices, boolean closed) {
        public Contour {
            vertices = List.copyOf(vertices);
        }
    }

    /**
     * An anchor point with its incoming and outgoing tangent handles. The handles are relative to
     * the point; {@link Vec2#ZERO} for both makes a corner.
     */
    public record Vertex(Vec2 point, Vec2 in, Vec2 out) {
        public Vertex {
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(in, "in");
            Objects.requireNonNull(out, "out");
        }
    }
}
