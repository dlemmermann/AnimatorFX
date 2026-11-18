package com.dlsc.animatorfx.model;

import java.util.List;
import java.util.Objects;

/**
 * The outline of a shape or text layer. An empty dash array means a solid line.
 */
public record Stroke(Animatable<Paint> paint, Animatable<Double> width, Cap cap, Join join, double miterLimit,
                     List<Double> dashArray, Animatable<Double> dashOffset) {

    public enum Cap {BUTT, ROUND, SQUARE}

    public enum Join {MITER, ROUND, BEVEL}

    public Stroke {
        Objects.requireNonNull(paint, "paint");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(cap, "cap");
        Objects.requireNonNull(join, "join");
        dashArray = List.copyOf(dashArray);
        Objects.requireNonNull(dashOffset, "dashOffset");
    }

    public Stroke withPaint(Animatable<Paint> paint) {
        return new Stroke(paint, width, cap, join, miterLimit, dashArray, dashOffset);
    }

    public Stroke withWidth(Animatable<Double> width) {
        return new Stroke(paint, width, cap, join, miterLimit, dashArray, dashOffset);
    }

    public Stroke withCap(Cap cap) {
        return new Stroke(paint, width, cap, join, miterLimit, dashArray, dashOffset);
    }

    public Stroke withJoin(Join join) {
        return new Stroke(paint, width, cap, join, miterLimit, dashArray, dashOffset);
    }

    public Stroke withMiterLimit(double miterLimit) {
        return new Stroke(paint, width, cap, join, miterLimit, dashArray, dashOffset);
    }

    public Stroke withDashArray(List<Double> dashArray) {
        return new Stroke(paint, width, cap, join, miterLimit, dashArray, dashOffset);
    }

    public Stroke withDashOffset(Animatable<Double> dashOffset) {
        return new Stroke(paint, width, cap, join, miterLimit, dashArray, dashOffset);
    }
}
