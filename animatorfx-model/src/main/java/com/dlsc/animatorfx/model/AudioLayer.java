package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * A sound clip on the timeline. It sounds within {@code inPoint..outPoint} of its base, starting
 * {@code clipOffset} seconds into the sound, and renders nothing: the transform, opacity, mask,
 * effects and motion path of the base are ignored. A layer that is muted or not visible is
 * silent. Volume is {@code 0..1}, balance {@code -1} (left) to {@code 1} (right). With
 * {@code loop} the clip repeats until the out-point; otherwise it is silent after its end.
 */
public record AudioLayer(LayerBase base, String assetId, double clipOffset, Animatable<Double> volume,
                         Animatable<Double> balance, boolean loop, boolean muted) implements Layer {

    public AudioLayer {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(balance, "balance");
    }

    /**
     * An audio layer with a new random id that plays the asset from its start at full volume,
     * centred, once.
     */
    public static AudioLayer create(String name, String assetId, double inPoint, double outPoint) {
        LayerBase base = LayerBase.create(name, outPoint).withInPoint(inPoint);
        return new AudioLayer(base, assetId, 0, Animatable.of(1.0), Animatable.of(0.0), false, false);
    }

    @Override
    public AudioLayer withBase(LayerBase base) {
        return new AudioLayer(base, assetId, clipOffset, volume, balance, loop, muted);
    }

    public AudioLayer withAssetId(String assetId) {
        return new AudioLayer(base, assetId, clipOffset, volume, balance, loop, muted);
    }

    public AudioLayer withClipOffset(double clipOffset) {
        return new AudioLayer(base, assetId, clipOffset, volume, balance, loop, muted);
    }

    public AudioLayer withVolume(Animatable<Double> volume) {
        return new AudioLayer(base, assetId, clipOffset, volume, balance, loop, muted);
    }

    public AudioLayer withBalance(Animatable<Double> balance) {
        return new AudioLayer(base, assetId, clipOffset, volume, balance, loop, muted);
    }

    public AudioLayer withLoop(boolean loop) {
        return new AudioLayer(base, assetId, clipOffset, volume, balance, loop, muted);
    }

    public AudioLayer withMuted(boolean muted) {
        return new AudioLayer(base, assetId, clipOffset, volume, balance, loop, muted);
    }
}
