package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * An animatable property of a layer, addressed by a key such as {@code transform.position} or
 * {@code effects[0].radius}. See {@link LayerProperties}.
 */
public record PropertyRef(String key, String label, ValueType type, Animatable<?> value) {

    public enum ValueType {
        /** {@link Double} */
        NUMBER,
        /** {@link Vec2} */
        VEC2,
        /** {@link Rgba} */
        COLOR,
        /** {@link Paint} */
        PAINT,
        /** {@link PathData} */
        PATH
    }

    public PropertyRef {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }
}
