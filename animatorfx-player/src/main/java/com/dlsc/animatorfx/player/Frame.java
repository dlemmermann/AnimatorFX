package com.dlsc.animatorfx.player;

import java.util.Map;

/**
 * A document sampled at one time (seconds): the state of every layer, nested ones included,
 * keyed by layer id.
 */
public record Frame(double time, Map<String, LayerState> layers) {

    public Frame {
        layers = Map.copyOf(layers);
    }

    /**
     * The state of a layer, or {@code null} if the document has no layer with that id.
     */
    public LayerState get(String layerId) {
        return layers.get(layerId);
    }
}
