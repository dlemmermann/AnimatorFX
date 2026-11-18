package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * A named placeholder that the host application binds to one of its own nodes. The size is the
 * reference size for preview and embedded layout; {@code restTransform} is the transform the
 * node has when the animation does not move it, against which in-place bindings are driven.
 */
public record SlotLayer(LayerBase base, String slotName, double width, double height, Transform restTransform)
        implements Layer {

    public SlotLayer {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(restTransform, "restTransform");
    }

    @Override
    public SlotLayer withBase(LayerBase base) {
        return new SlotLayer(base, slotName, width, height, restTransform);
    }

    public SlotLayer withSlotName(String slotName) {
        return new SlotLayer(base, slotName, width, height, restTransform);
    }

    public SlotLayer withWidth(double width) {
        return new SlotLayer(base, slotName, width, height, restTransform);
    }

    public SlotLayer withHeight(double height) {
        return new SlotLayer(base, slotName, width, height, restTransform);
    }

    public SlotLayer withRestTransform(Transform restTransform) {
        return new SlotLayer(base, slotName, width, height, restTransform);
    }
}
