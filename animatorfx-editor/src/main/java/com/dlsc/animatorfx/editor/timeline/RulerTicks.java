package com.dlsc.animatorfx.editor.timeline;

import java.util.Locale;

/**
 * The tick spacing of the time ruler for a zoom level: labelled major ticks in seconds at coarse
 * zoom and in frames at fine zoom, each divided into minor ticks.
 *
 * @param major         the time between two labelled ticks, in seconds
 * @param minorPerMajor how many minor intervals one major interval has; 1 = no minor ticks
 * @param frames        whether labels count frames ({@code 1:12f}) rather than seconds ({@code 2s})
 */
public record RulerTicks(double major, int minorPerMajor, boolean frames) {

    public static final double MIN_MAJOR_SPACING = 64;
    public static final double MIN_MINOR_SPACING = 6;

    private static final int[] FRAME_STEPS = {1, 2, 5, 10, 15, 30};
    private static final double[] SECOND_STEPS = {1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600};
    private static final int[] DIVISIONS = {10, 5, 4, 2};

    /**
     * The finest spacing whose labels do not crowd. Frame steps are those that divide the frame
     * rate, so every whole second gets a tick.
     */
    public static RulerTicks choose(double pixelsPerSecond, double frameRate) {
        int rate = frameRate > 0 && frameRate == Math.rint(frameRate) ? (int) frameRate : 0;
        if (rate > 0) {
            for (int step : FRAME_STEPS) {
                if (step < rate && rate % step == 0 && step / (double) rate * pixelsPerSecond >= MIN_MAJOR_SPACING) {
                    return new RulerTicks(step / (double) rate, divisions(step / (double) rate, pixelsPerSecond, step), true);
                }
            }
        }
        double seconds = SECOND_STEPS[SECOND_STEPS.length - 1];
        for (int i = SECOND_STEPS.length - 1; i >= 0 && SECOND_STEPS[i] * pixelsPerSecond >= MIN_MAJOR_SPACING; i--) {
            seconds = SECOND_STEPS[i];
        }
        while (seconds * pixelsPerSecond < MIN_MAJOR_SPACING) {
            seconds *= 2;
        }
        return new RulerTicks(seconds, divisions(seconds, pixelsPerSecond, 0), false);
    }

    /** The most minor intervals that stay apart; in frame mode they have to be whole frames. */
    private static int divisions(double major, double pixelsPerSecond, int frameStep) {
        for (int n : DIVISIONS) {
            boolean whole = frameStep == 0 || frameStep % n == 0;
            if (whole && major / n * pixelsPerSecond >= MIN_MINOR_SPACING) {
                return n;
            }
        }
        return 1;
    }

    /**
     * The label of a major tick: {@code 2s} or, from a minute on, {@code 1:30} in seconds mode;
     * {@code seconds:frame} with an {@code f} in frame mode, such as {@code 1:12f}.
     */
    public String label(double time, double frameRate) {
        if (frames) {
            long frame = Math.round(time * frameRate);
            long rate = Math.round(frameRate);
            return String.format(Locale.ROOT, "%d:%02df", frame / rate, frame % rate);
        }
        long seconds = Math.round(time);
        if (major >= 60 || seconds >= 60) {
            return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        }
        return seconds + "s";
    }
}
