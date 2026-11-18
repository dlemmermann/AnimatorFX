package com.dlsc.animatorfx.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What every layer has in common. The layer renders only within {@code inPoint..outPoint}
 * (seconds). {@code mask} and {@code motionPath} may be {@code null}.
 */
public record LayerBase(String id, String name, boolean visible, boolean locked, double inPoint, double outPoint,
                        Transform transform, Animatable<Double> opacity, Mask mask,
                        List<Effect> effects, MotionPath motionPath) {

    public LayerBase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(opacity, "opacity");
        effects = List.copyOf(effects);
    }

    /**
     * A visible, unlocked, fully opaque layer base with a new random id, the identity transform
     * and the time range {@code 0..duration}.
     */
    public static LayerBase create(String name, double duration) {
        return new LayerBase(UUID.randomUUID().toString(), name, true, false, 0, duration, Transform.IDENTITY,
                Animatable.of(1.0), null, List.of(), null);
    }

    public LayerBase withId(String id) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }

    public LayerBase withName(String name) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }

    public LayerBase withVisible(boolean visible) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }

    public LayerBase withLocked(boolean locked) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }

    public LayerBase withInPoint(double inPoint) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }

    public LayerBase withOutPoint(double outPoint) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }

    public LayerBase withTransform(Transform transform) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }

    public LayerBase withOpacity(Animatable<Double> opacity) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }

    /**
     * @param mask the mask, or {@code null} for none
     */
    public LayerBase withMask(Mask mask) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }

    public LayerBase withEffects(List<Effect> effects) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }

    /**
     * @param motionPath the motion path, or {@code null} for none
     */
    public LayerBase withMotionPath(MotionPath motionPath) {
        return new LayerBase(id, name, visible, locked, inPoint, outPoint, transform, opacity, mask, effects,
                motionPath);
    }
}
