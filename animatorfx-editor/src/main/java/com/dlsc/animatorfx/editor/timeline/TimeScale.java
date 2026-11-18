package com.dlsc.animatorfx.editor.timeline;

/**
 * The mapping between time and horizontal pixels of the timeline tracks. Zoom 1 shows the whole
 * duration in the viewport; the scroll position is the time at the left edge of the content.
 *
 * @param pixelsPerSecond the width of one second
 * @param scroll          the time, in seconds, that sits {@link #INSET} pixels from the left edge
 */
public record TimeScale(double pixelsPerSecond, double scroll) {

    /** Room before time zero, so the first keyframe and the playhead head are not cut off. */
    public static final double INSET = 12;
    /** Room after the duration at zoom 1: the tracks extend a little past the end. */
    public static final double END_SLACK = 28;
    /** Zooming in stops when one frame is this wide. */
    public static final double MAX_FRAME_WIDTH = 24;
    public static final double MIN_ZOOM = 0.5;

    private static final double FALLBACK_FRAME_RATE = 60;

    /**
     * The scale for a view state, with zoom and scroll limited to what the viewport can show.
     */
    public static TimeScale of(double zoom, double scroll, double viewportWidth, double duration, double frameRate) {
        double z = clampZoom(zoom, viewportWidth, duration, frameRate);
        double max = maxScroll(z, viewportWidth, duration, frameRate);
        return new TimeScale(fit(viewportWidth, duration) * z, Math.clamp(Double.isFinite(scroll) ? scroll : 0, 0, max));
    }

    /** Pixels per second at zoom 1. */
    private static double fit(double viewportWidth, double duration) {
        return Math.max(40, viewportWidth - INSET - END_SLACK) / Math.max(duration, 1e-3);
    }

    public static double maxZoom(double viewportWidth, double duration, double frameRate) {
        double rate = frameRate > 0 ? frameRate : FALLBACK_FRAME_RATE;
        return Math.max(1, MAX_FRAME_WIDTH * rate / fit(viewportWidth, duration));
    }

    public static double clampZoom(double zoom, double viewportWidth, double duration, double frameRate) {
        return Math.clamp(Double.isFinite(zoom) ? zoom : 1, MIN_ZOOM, maxZoom(viewportWidth, duration, frameRate));
    }

    /** The largest scroll position: the end of the duration then sits at the right edge, less the slack. */
    public static double maxScroll(double zoom, double viewportWidth, double duration, double frameRate) {
        double z = clampZoom(zoom, viewportWidth, duration, frameRate);
        double visible = Math.max(40, viewportWidth - INSET - END_SLACK) / (fit(viewportWidth, duration) * z);
        return Math.max(0, Math.max(duration, 1e-3) - visible);
    }

    /**
     * The scroll position that keeps a time under a viewport x coordinate at another zoom; pass
     * it through {@link #of} to limit it.
     */
    public static double scrollToKeep(double time, double x, double zoom, double viewportWidth, double duration,
                                      double frameRate) {
        double pps = fit(viewportWidth, duration) * clampZoom(zoom, viewportWidth, duration, frameRate);
        return time - (x - INSET) / pps;
    }

    /** The nearest frame boundary, never before zero; the time itself without a frame rate. */
    public static double snap(double time, double frameRate) {
        if (!(frameRate > 0)) {
            return time;
        }
        return Math.max(0, Math.round(time * frameRate) / frameRate);
    }

    /** The length of one frame in seconds. */
    public static double frame(double frameRate) {
        return 1 / (frameRate > 0 ? frameRate : FALLBACK_FRAME_RATE);
    }

    public double toX(double time) {
        return INSET + (time - scroll) * pixelsPerSecond;
    }

    public double toTime(double x) {
        return scroll + (x - INSET) / pixelsPerSecond;
    }

    /** A width in pixels as a time span. */
    public double toDuration(double pixels) {
        return pixels / pixelsPerSecond;
    }
}
