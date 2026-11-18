package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.ui.Palette;
import com.dlsc.animatorfx.editor.ui.Palette.Tone;
import com.dlsc.animatorfx.model.Animation;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

/**
 * The time ruler above the tracks: adaptive major and minor ticks, labelled in seconds at
 * coarse zoom and in frames at fine zoom ({@link RulerTicks}). A click or drag moves the
 * playhead, snapped to frames unless Alt is held.
 */
public final class TimeRuler extends Region {

    private final TimelineView timeline;
    private final Canvas canvas = new Canvas();
    /** Carries the font of the tick labels, which comes from the stylesheet. */
    private final Label fontProbe = new Label();

    TimeRuler(TimelineView timeline) {
        this.timeline = timeline;
        getStyleClass().add("timeline-ruler");
        setFocusTraversable(true);
        fontProbe.getStyleClass().addAll("timeline-tick", "mono");
        fontProbe.setManaged(false);
        fontProbe.setVisible(false);
        fontProbe.fontProperty().addListener(obs -> timeline.requestLayout());
        getChildren().addAll(canvas, fontProbe);
        setOnMousePressed(this::scrub);
        setOnMouseDragged(this::scrub);
    }

    private void scrub(MouseEvent event) {
        EditorDocument doc = timeline.getDocument();
        if (doc == null || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        requestFocus();
        doc.setPlayhead(timeline.snapTime(timeline.getScale().toTime(event.getX()), event.isAltDown()));
        event.consume();
    }

    void draw() {
        double width = getWidth();
        double height = getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        EditorDocument doc = timeline.getDocument();
        Palette palette = timeline.getPalette();
        g.setFill(palette.get(Tone.PANEL_ALT));
        g.fillRect(0, 0, width, height);
        if (doc == null || width <= 0) {
            return;
        }
        Animation animation = doc.getAnimation();
        TimeScale scale = timeline.getScale();
        RulerTicks ticks = RulerTicks.choose(scale.pixelsPerSecond(), animation.frameRate());
        double minor = ticks.major() / ticks.minorPerMajor();

        g.setFont(fontProbe.getFont());
        g.setTextBaseline(VPos.CENTER);
        g.setLineWidth(1);
        long first = Math.max(0, (long) Math.floor(scale.toTime(0) / minor));
        for (long i = first; ; i++) {
            double time = i * minor;
            double x = Math.round(scale.toX(time)) + 0.5;
            if (x > width) {
                break;
            }
            boolean major = i % ticks.minorPerMajor() == 0;
            g.setStroke(palette.get(major ? Tone.MUTED : Tone.LINE));
            g.strokeLine(x, height - (major ? 8 : 4), x, height - 1);
            if (major) {
                g.setFill(palette.get(Tone.MUTED));
                g.fillText(ticks.label(time, animation.frameRate()), x + 4, height / 2 - 2);
            }
        }
        double end = scale.toX(animation.duration());
        if (end < width) {
            g.setFill(palette.get(Tone.BG, 0.55));
            g.fillRect(Math.max(0, end), 0, width - Math.max(0, end), height);
        }
        g.setStroke(palette.get(Tone.LINE));
        g.strokeLine(0, height - 0.5, width, height - 0.5);
    }
}
