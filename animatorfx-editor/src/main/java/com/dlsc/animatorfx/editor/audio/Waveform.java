package com.dlsc.animatorfx.editor.audio;

import java.util.Objects;

/**
 * The loudness of a sound over time, for drawing: the absolute peak amplitude ({@code 0..1}, the
 * loudest channel) of every 1/{@value #PEAKS_PER_SECOND} second.
 *
 * @param duration the length of the sound in seconds
 * @param peaks    one peak per interval, from the start of the sound; not to be modified
 */
public record Waveform(double duration, float[] peaks) {

    public static final int PEAKS_PER_SECOND = 200;

    public Waveform {
        Objects.requireNonNull(peaks, "peaks");
    }

    /**
     * The loudest peak between two times of the sound: the maximum of every interval the range
     * touches, so a column of pixels shows the loudest moment it covers at any zoom. 0 outside
     * the sound.
     */
    public float peak(double from, double to) {
        if (!(to >= from) || peaks.length == 0) {
            return 0;
        }
        int first = (int) Math.max(0, Math.floor(from * PEAKS_PER_SECOND));
        // a range that ends exactly on an interval boundary does not reach into the next one
        int last = (int) Math.min(peaks.length - 1, Math.ceil(to * PEAKS_PER_SECOND) - 1);
        if (to == from) {
            last = (int) Math.min(peaks.length - 1, Math.floor(to * PEAKS_PER_SECOND));
        }
        float max = 0;
        for (int i = first; i <= last; i++) {
            max = Math.max(max, peaks[i]);
        }
        return max;
    }
}
