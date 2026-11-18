package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * A named time range (seconds) of an animation. Names are unique per document.
 */
public record Marker(String name, double start, double end) {

    public Marker {
        Objects.requireNonNull(name, "name");
    }
}
