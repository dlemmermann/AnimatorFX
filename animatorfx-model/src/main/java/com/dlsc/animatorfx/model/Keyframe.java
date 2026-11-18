package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * A value at a point in time (seconds). The easing describes the travel from this keyframe to the next.
 */
public record Keyframe<T>(String id, double time, T value, Easing easing) {

    public Keyframe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(easing, "easing");
    }
}
