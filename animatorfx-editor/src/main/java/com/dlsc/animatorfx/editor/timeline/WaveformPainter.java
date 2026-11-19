package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.editor.audio.Waveform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Draws the waveform of an audio layer inside its bar: one column per pixel, mirrored around
 * the middle of the bar, as high as the loudest moment the pixel covers. Where the bar has
 * sound but the sound is silent, a hairline remains. Cheap at any zoom: the waveform holds
 * peaks, and a column asks for the maximum of the ones it covers.
 */
final class WaveformPainter {

    private WaveformPainter() {
    }

    /**
     * @param in         the in-point of the layer, where the sound is at {@code clipOffset}
     * @param x1         the left edge of the bar
     * @param x2         the right edge of the bar
     * @param top        the top of the room for the waveform
     * @param height     its height
     * @param viewWidth  the width of the canvas; columns outside of it are not drawn
     */
    static void paint(GraphicsContext g, Waveform waveform, TimeScale scale, double in, double clipOffset, boolean loop,
                      double x1, double x2, double top, double height, double viewWidth, Color color) {
        double duration = waveform.duration();
        if (!(duration > 0) || !(height > 0)) {
            return;
        }
        double middle = top + height / 2;
        double reach = height / 2;
        g.setFill(color);
        int from = (int) Math.max(Math.ceil(x1), 0);
        int to = (int) Math.min(Math.floor(x2), viewWidth);
        for (int x = from; x < to; x++) {
            double t0 = Math.max(in, scale.toTime(x));
            double t1 = scale.toTime(x + 1);
            double media = AudioClips.mediaTime(t0, in, clipOffset, duration, loop);
            if (Double.isNaN(media) || t1 <= t0) {
                continue;
            }
            double span = t1 - t0;
            float peak;
            if (media + span <= duration) {
                peak = waveform.peak(media, media + span);
            } else if (!loop) {
                peak = waveform.peak(media, duration);
            } else if (span >= duration) {
                peak = waveform.peak(0, duration);
            } else {
                // the column covers the end of one repetition and the start of the next
                peak = Math.max(waveform.peak(media, duration), waveform.peak(0, media + span - duration));
            }
            // whole pixels: a column is crisp, and silence is a line one pixel high
            double half = Math.min(1, peak) * reach;
            double y1 = Math.round(middle - half);
            double y2 = Math.max(y1 + 1, Math.round(middle + half));
            g.fillRect(x, y1, 1, y2 - y1);
        }
    }
}
