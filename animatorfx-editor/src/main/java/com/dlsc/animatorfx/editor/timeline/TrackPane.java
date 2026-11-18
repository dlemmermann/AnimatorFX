package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.Selection.KeyframeRef;
import com.dlsc.animatorfx.editor.timeline.TimelineRow.LayerRow;
import com.dlsc.animatorfx.editor.timeline.TimelineRow.PropertyRow;
import com.dlsc.animatorfx.editor.ui.Palette;
import com.dlsc.animatorfx.editor.ui.Palette.Tone;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.SlotLayer;
import java.util.List;
import java.util.Set;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.layout.Region;

/**
 * The tracks of the timeline, drawn on a canvas with the colours of the stylesheet. A layer row
 * shows the bar of its in/out range and, while collapsed, small dots at the times of its
 * keyframes; a property row shows its keyframes as diamonds (squares for hold keyframes) on a
 * thin connector line. The time past the duration is shaded.
 *
 * <p>Pointer handling is in {@link TrackInteraction}: selecting, moving and adding keyframes,
 * the marquee, and trimming and moving the in/out bars.
 */
public final class TrackPane extends Region {

    /** The part of an in/out bar under the pointer. */
    public enum BarPart {START, BODY, END}

    /** What is at a position of the tracks. */
    public sealed interface Hit {

        /** A keyframe of a property row. */
        record Key(int row, KeyframeRef ref, double time) implements Hit {
        }

        /** The in/out bar of a layer row. */
        record Bar(int row, String layerId, BarPart part) implements Hit {
        }

        /** Free space: {@code row} is the index of the row, or -1 below the last one. */
        record Empty(int row) implements Hit {
        }
    }

    static final double KEY_SIZE = 4.5;
    private static final double KEY_REACH = 6;
    private static final double BAR_INSET = 6;
    private static final double BAR_GRIP = 4;

    private final TimelineView timeline;
    private final Canvas canvas = new Canvas();
    private final TrackInteraction interaction;

    TrackPane(TimelineView timeline) {
        this.timeline = timeline;
        getStyleClass().add("timeline-tracks");
        setFocusTraversable(true);
        getChildren().add(canvas);
        interaction = new TrackInteraction(timeline, this);
    }

    /** The menu for the selected keyframes: Easing ▸ Linear / Hold / presets, and Delete. */
    public ContextMenu getKeyframeMenu() {
        return interaction.getKeyframeMenu();
    }

    void cancelGesture() {
        interaction.cancel();
    }

    /** The x coordinate of a time. */
    public double timeToX(double time) {
        return timeline.getScale().toX(time);
    }

    /** The y coordinate of the middle of a row; the row may be out of view. */
    public double rowCenter(int row) {
        return timeline.rowTop(row) + TimelineView.ROW_HEIGHT / 2;
    }

    /** The index of the row of a property; -1 when it is not listed. */
    public int rowOf(String layerId, String propertyKey) {
        List<TimelineRow> rows = timeline.getRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof PropertyRow p && p.layerId().equals(layerId) && p.key().equals(propertyKey)) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the row of a layer; -1 when it is inside a collapsed group. */
    public int rowOf(String layerId) {
        List<TimelineRow> rows = timeline.getRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof LayerRow && rows.get(i).layerId().equals(layerId)) {
                return i;
            }
        }
        return -1;
    }

    /** What is under a position: the nearest keyframe within reach, a part of a bar, or free space. */
    public Hit hitAt(double x, double y) {
        EditorDocument doc = timeline.getDocument();
        int index = timeline.rowAt(y);
        if (doc == null || index < 0) {
            return new Hit.Empty(-1);
        }
        TimeScale scale = timeline.getScale();
        TimelineRow row = timeline.getRows().get(index);
        if (row instanceof PropertyRow property) {
            Keyframe<?> nearest = null;
            double distance = KEY_REACH;
            for (Keyframe<?> keyframe : property.property().value().keyframes()) {
                double d = Math.abs(scale.toX(keyframe.time()) - x);
                if (d <= distance) {
                    distance = d;
                    nearest = keyframe;
                }
            }
            if (nearest != null) {
                return new Hit.Key(index, new KeyframeRef(row.layerId(), property.key(), nearest.id()), nearest.time());
            }
            return new Hit.Empty(index);
        }
        double duration = doc.getAnimation().duration();
        double start = scale.toX(barStart(row.layer(), duration));
        double end = scale.toX(barEnd(row.layer(), duration));
        if (x < start - BAR_GRIP || x > end + BAR_GRIP) {
            return new Hit.Empty(index);
        }
        boolean narrow = end - start < 3 * BAR_GRIP;
        BarPart part = x <= start + (narrow ? 0 : BAR_GRIP) ? BarPart.START
                : x >= end - (narrow ? 0 : BAR_GRIP) ? BarPart.END : BarPart.BODY;
        return new Hit.Bar(index, row.layerId(), part);
    }

    private static double barStart(Layer layer, double duration) {
        return Math.clamp(layer.base().inPoint(), 0, Math.max(0, duration));
    }

    private static double barEnd(Layer layer, double duration) {
        return Math.clamp(layer.base().outPoint(), barStart(layer, duration), Math.max(barStart(layer, duration), duration));
    }

    // ------------------------------------------------------------------ drawing

    void draw() {
        double width = getWidth();
        double height = getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        EditorDocument doc = timeline.getDocument();
        if (doc == null || width <= 0 || height <= 0) {
            return;
        }
        Palette palette = timeline.getPalette();
        Animation animation = doc.getAnimation();
        TimeScale scale = timeline.getScale();
        List<TimelineRow> rows = timeline.getRows();
        List<String> selectedLayers = doc.getSelection().layerIds();
        Set<KeyframeRef> selectedKeys = doc.getSelection().keyframes();
        int first = Math.max(0, (int) Math.floor(timeline.getScrollY() / TimelineView.ROW_HEIGHT));
        g.setLineWidth(1);

        for (int i = first; i < rows.size() && timeline.rowTop(i) < height; i++) {
            TimelineRow row = rows.get(i);
            double y = timeline.rowTop(i);
            if (row instanceof PropertyRow) {
                g.setFill(palette.get(Tone.ROW_ALT));
                g.fillRect(0, y, width, TimelineView.ROW_HEIGHT);
            } else if (selectedLayers.contains(row.layerId())) {
                g.setFill(palette.get(Tone.ACCENT_SOFT));
                g.fillRect(0, y, width, TimelineView.ROW_HEIGHT);
            }
        }

        RulerTicks ticks = RulerTicks.choose(scale.pixelsPerSecond(), animation.frameRate());
        g.setStroke(palette.get(Tone.GRID));
        for (long i = Math.max(0, (long) Math.floor(scale.toTime(0) / ticks.major())); ; i++) {
            double x = Math.round(scale.toX(i * ticks.major())) + 0.5;
            if (x > width) {
                break;
            }
            g.strokeLine(x, 0, x, height);
        }

        g.setStroke(palette.get(Tone.LINE));
        for (int i = first; i < rows.size() && timeline.rowTop(i) < height; i++) {
            double y = timeline.rowTop(i) + TimelineView.ROW_HEIGHT - 0.5;
            g.strokeLine(0, y, width, y);
        }

        for (int i = first; i < rows.size() && timeline.rowTop(i) < height; i++) {
            TimelineRow row = rows.get(i);
            double y = timeline.rowTop(i);
            if (row instanceof LayerRow layerRow) {
                drawBar(g, palette, scale, layerRow, y, animation.duration());
            } else {
                drawKeyframes(g, palette, scale, (PropertyRow) row, y, selectedKeys);
            }
        }

        double end = scale.toX(animation.duration());
        if (end < width) {
            g.setFill(palette.get(Tone.BG, 0.55));
            g.fillRect(Math.max(0, end), 0, width - Math.max(0, end), height);
            g.setStroke(palette.get(Tone.LINE));
            g.strokeLine(Math.round(end) + 0.5, 0, Math.round(end) + 0.5, height);
        }

        double[] marquee = interaction.marquee();
        if (marquee != null) {
            double x = Math.round(marquee[0]) + 0.5;
            double y = Math.round(marquee[1] - timeline.getScrollY()) + 0.5;
            g.setFill(palette.get(Tone.ACCENT_SOFT));
            g.fillRect(x, y, marquee[2], marquee[3]);
            g.setStroke(palette.get(Tone.ACCENT));
            g.strokeRect(x, y, Math.round(marquee[2]), Math.round(marquee[3]));
        }
    }

    private void drawBar(GraphicsContext g, Palette palette, TimeScale scale, LayerRow row, double y, double duration) {
        Layer layer = row.layer();
        boolean slot = layer instanceof SlotLayer;
        double x1 = Math.round(scale.toX(barStart(layer, duration)));
        double x2 = Math.max(x1 + 2, Math.round(scale.toX(barEnd(layer, duration))));
        double top = y + BAR_INSET;
        double barHeight = TimelineView.ROW_HEIGHT - 2 * BAR_INSET - 1;
        g.setGlobalAlpha(layer.base().visible() ? 1 : 0.4);
        g.setFill(palette.get(slot ? Tone.KEY_BAR : Tone.ACCENT_BAR));
        g.fillRoundRect(x1, top, x2 - x1, barHeight, 3, 3);
        g.setStroke(palette.get(slot ? Tone.KEY : Tone.ACCENT));
        g.strokeRoundRect(x1 + 0.5, top + 0.5, x2 - x1 - 1, barHeight - 1, 3, 3);
        if (!row.expanded()) {
            // a collapsed layer still tells where its keyframes are
            double cy = top + barHeight / 2;
            g.setFill(palette.get(slot ? Tone.FG : Tone.KEY));
            for (double time : TimelineRows.keyTimes(layer)) {
                double x = Math.round(scale.toX(time)) + 0.5;
                diamond(g, x, cy, 3);
                g.fill();
            }
        }
        g.setGlobalAlpha(1);
    }

    private void drawKeyframes(GraphicsContext g, Palette palette, TimeScale scale, PropertyRow row, double y,
                               Set<KeyframeRef> selected) {
        List<? extends Keyframe<?>> keyframes = row.property().value().keyframes();
        if (keyframes.isEmpty()) {
            return;
        }
        double cy = Math.round(y + (TimelineView.ROW_HEIGHT - 1) / 2) + 0.5;
        if (keyframes.size() > 1) {
            g.setStroke(palette.get(Tone.KEY, 0.45));
            g.strokeLine(scale.toX(keyframes.get(0).time()), cy, scale.toX(keyframes.get(keyframes.size() - 1).time()), cy);
        }
        for (Keyframe<?> keyframe : keyframes) {
            double x = Math.round(scale.toX(keyframe.time())) + 0.5;
            boolean isSelected = selected.contains(new KeyframeRef(row.layerId(), row.key(), keyframe.id()));
            // selected keyframes are a little larger
            double grow = isSelected ? 1 : 0;
            if (keyframe.easing() instanceof Easing.Hold) {
                g.beginPath();
                g.rect(x - 3.5 - grow, cy - 3.5 - grow, 7 + 2 * grow, 7 + 2 * grow);
                g.closePath();
            } else {
                diamond(g, x, cy, KEY_SIZE + grow);
            }
            if (isSelected) {
                g.setFill(palette.get(Tone.ON_ACCENT));
                g.fill();
                g.setStroke(palette.get(Tone.ACCENT));
                g.setLineWidth(1.5);
                g.stroke();
                g.setLineWidth(1);
            } else {
                g.setFill(palette.get(Tone.KEY));
                g.fill();
            }
        }
    }

    private static void diamond(GraphicsContext g, double x, double y, double size) {
        g.beginPath();
        g.moveTo(x, y - size);
        g.lineTo(x + size, y);
        g.lineTo(x, y + size);
        g.lineTo(x - size, y);
        g.closePath();
    }
}
