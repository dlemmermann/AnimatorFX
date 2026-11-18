package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * Masks a layer with another layer of the same parent. The mask layer is not rendered itself.
 */
public record Mask(String layerId, Mode mode) {

    public enum Mode {
        /** The mask layer's geometry clips. */
        CLIP,
        /** The mask layer's alpha is multiplied in. */
        ALPHA
    }

    public Mask {
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(mode, "mode");
    }
}
