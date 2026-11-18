package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * The outline of a shape layer in layer coordinates. {@link Geometries#toPath} converts any
 * geometry to a Bezier path.
 */
public sealed interface Geometry permits Geometry.Rect, Geometry.Ellipse, Geometry.Line, Geometry.Polygon,
        Geometry.Star, Geometry.PathGeometry {

    /** Spans {@code (0,0)..size}. */
    record Rect(Animatable<Vec2> size, Animatable<Double> cornerRadius) implements Geometry {
        public Rect {
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(cornerRadius, "cornerRadius");
        }
    }

    /** Spans {@code (0,0)..size}. */
    record Ellipse(Animatable<Vec2> size) implements Geometry {
        public Ellipse {
            Objects.requireNonNull(size, "size");
        }
    }

    record Line(Animatable<Vec2> start, Animatable<Vec2> end) implements Geometry {
        public Line {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
        }
    }

    /** A regular polygon centred on {@code (radius, radius)}, first vertex pointing up. */
    record Polygon(int sides, Animatable<Double> radius) implements Geometry {
        public Polygon {
            if (sides < 3) {
                throw new IllegalArgumentException("A polygon needs at least 3 sides but got: " + sides);
            }
            Objects.requireNonNull(radius, "radius");
        }
    }

    /** A star centred on {@code (outerRadius, outerRadius)}, first point pointing up. */
    record Star(int points, Animatable<Double> outerRadius, Animatable<Double> innerRadius) implements Geometry {
        public Star {
            if (points < 2) {
                throw new IllegalArgumentException("A star needs at least 2 points but got: " + points);
            }
            Objects.requireNonNull(outerRadius, "outerRadius");
            Objects.requireNonNull(innerRadius, "innerRadius");
        }
    }

    record PathGeometry(Animatable<PathData> path) implements Geometry {
        public PathGeometry {
            Objects.requireNonNull(path, "path");
        }
    }
}
