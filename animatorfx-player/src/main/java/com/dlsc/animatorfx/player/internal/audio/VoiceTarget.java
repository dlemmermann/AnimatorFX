package com.dlsc.animatorfx.player.internal.audio;

import java.util.Objects;

/**
 * What the voice of one audio layer should be doing right now.
 *
 * @param layerId   the audio layer; the key of its voice
 * @param assetId   the audio asset the voice plays
 * @param audible   whether the voice sounds at all; the remaining values are 0 when it does not
 * @param mediaTime the position in the sound in seconds; within the clip for a looping layer
 *                  whose clip duration is known
 * @param volume    {@code 0..1}, the runtime volume included
 * @param balance   {@code -1} (left) to {@code 1} (right)
 * @param rate      the playback speed, within {@code (0, 8]}
 */
public record VoiceTarget(String layerId, String assetId, boolean audible, double mediaTime, double volume,
                          double balance, double rate) {

    public VoiceTarget {
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(assetId, "assetId");
    }

    static VoiceTarget silent(String layerId, String assetId) {
        return new VoiceTarget(layerId, assetId, false, 0, 0, 0, 0);
    }
}
