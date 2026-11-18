package com.dlsc.animatorfx.model;

import java.util.List;
import java.util.Objects;

/**
 * A layer whose transform, opacity, mask and effects apply to all its children (ordered bottom
 * to top).
 */
public record GroupLayer(LayerBase base, List<Layer> children) implements Layer {

    public GroupLayer {
        Objects.requireNonNull(base, "base");
        children = List.copyOf(children);
    }

    @Override
    public GroupLayer withBase(LayerBase base) {
        return new GroupLayer(base, children);
    }

    public GroupLayer withChildren(List<Layer> children) {
        return new GroupLayer(base, children);
    }
}
