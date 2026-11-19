package com.dlsc.animatorfx.player.internal.audio;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Makes the sound of one document audible. A player has one output per document and calls it on
 * the JavaFX application thread only. Not part of the library's API: it exists for the player,
 * for tooling and for tests.
 */
public interface AudioOutput {

    /**
     * The length of an audio asset in seconds; empty until it is known.
     */
    OptionalDouble duration(String assetId);

    /**
     * Makes the voices match the targets: starts, stops and repositions them and sets volume,
     * balance and rate. Called on every tick while the player is playing. Ends a snippet.
     */
    void apply(List<VoiceTarget> targets);

    /**
     * Plays a sound once, to its end, independently of the voices.
     */
    void playCue(String assetId, double volume);

    /**
     * Plays the audible targets from their media time for the given number of seconds and then
     * stops them. A later snippet or {@link #apply} takes over from one that still sounds.
     */
    void snippet(List<VoiceTarget> targets, double seconds);

    /**
     * Stops all voices and any snippet. Cues that already started play to their end.
     */
    void silence();

    /**
     * Stops everything and releases all resources. Further calls do nothing.
     */
    void dispose();
}
