package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * The visible part of a shape layer's stroke, as fractions {@code 0..1} of the path length.
 */
public record Trim(Animatable<Double> start, Animatable<Double> end, Animatable<Double> offset) {

    public Trim {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(offset, "offset");
    }

    public Trim withStart(Animatable<Double> start) {
        return new Trim(start, end, offset);
    }

    public Trim withEnd(Animatable<Double> end) {
        return new Trim(start, end, offset);
    }

    public Trim withOffset(Animatable<Double> offset) {
        return new Trim(start, end, offset);
    }
}
