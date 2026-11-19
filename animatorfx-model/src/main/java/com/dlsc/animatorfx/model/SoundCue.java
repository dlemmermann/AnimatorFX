package com.dlsc.animatorfx.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A short sound triggered when playback crosses {@code time} (seconds). The asset is an audio
 * asset; volume is {@code 0..1}. Cue ids are unique per document.
 */
public record SoundCue(String id, String name, double time, String assetId, double volume, boolean muted) {

    public SoundCue {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(assetId, "assetId");
    }

    /**
     * A cue with a new random id at full volume.
     */
    public static SoundCue create(String name, double time, String assetId) {
        return new SoundCue(UUID.randomUUID().toString(), name, time, assetId, 1, false);
    }

    public SoundCue withId(String id) {
        return new SoundCue(id, name, time, assetId, volume, muted);
    }

    public SoundCue withName(String name) {
        return new SoundCue(id, name, time, assetId, volume, muted);
    }

    public SoundCue withTime(double time) {
        return new SoundCue(id, name, time, assetId, volume, muted);
    }

    public SoundCue withAssetId(String assetId) {
        return new SoundCue(id, name, time, assetId, volume, muted);
    }

    public SoundCue withVolume(double volume) {
        return new SoundCue(id, name, time, assetId, volume, muted);
    }

    public SoundCue withMuted(boolean muted) {
        return new SoundCue(id, name, time, assetId, volume, muted);
    }
}
