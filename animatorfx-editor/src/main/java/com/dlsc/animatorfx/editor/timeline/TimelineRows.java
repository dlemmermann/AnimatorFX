package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerProperties;
import com.dlsc.animatorfx.model.PropertyRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds the rows of the timeline from a document and answers questions about keyframe times.
 */
public final class TimelineRows {

    private TimelineRows() {
    }

    /**
     * The visible rows, top-most layer first. An expanded layer is followed by its animated
     * properties, or by all of them with {@code showAll}; an expanded group lists its property
     * rows before its children.
     */
    public static List<TimelineRow> build(Animation animation, Set<String> expandedLayerIds, boolean showAll) {
        List<TimelineRow> rows = new ArrayList<>();
        collect(animation.layers(), 0, expandedLayerIds, showAll, rows);
        return rows;
    }

    private static void collect(List<Layer> layers, int depth, Set<String> expanded, boolean showAll,
                                List<TimelineRow> out) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            boolean open = expanded.contains(layer.id());
            out.add(new TimelineRow.LayerRow(layer, depth, open));
            if (!open) {
                continue;
            }
            for (PropertyRef property : LayerProperties.list(layer)) {
                if (showAll || property.value().isAnimated()) {
                    out.add(new TimelineRow.PropertyRow(layer, property, depth + 1));
                }
            }
            if (layer instanceof GroupLayer group) {
                collect(group.children(), depth + 1, expanded, showAll, out);
            }
        }
    }

    /** The distinct times at which any property of the layer has a keyframe, ascending. */
    public static List<Double> keyTimes(Layer layer) {
        TreeSet<Double> times = new TreeSet<>();
        for (PropertyRef property : LayerProperties.list(layer)) {
            for (Keyframe<?> keyframe : property.value().keyframes()) {
                times.add(keyframe.time());
            }
        }
        return List.copyOf(times);
    }

    public static Optional<Keyframe<?>> keyframeAt(Animatable<?> value, double time) {
        for (Keyframe<?> keyframe : value.keyframes()) {
            if (Math.abs(keyframe.time() - time) < Animatable.TIME_EPSILON) {
                return Optional.of(keyframe);
            }
        }
        return Optional.empty();
    }

    /** The time of the last keyframe before the time. */
    public static Optional<Double> previousKeyTime(Animatable<?> value, double time) {
        Double found = null;
        for (Keyframe<?> keyframe : value.keyframes()) {
            if (keyframe.time() < time - Animatable.TIME_EPSILON) {
                found = keyframe.time();
            }
        }
        return Optional.ofNullable(found);
    }

    /** The time of the first keyframe after the time. */
    public static Optional<Double> nextKeyTime(Animatable<?> value, double time) {
        for (Keyframe<?> keyframe : value.keyframes()) {
            if (keyframe.time() > time + Animatable.TIME_EPSILON) {
                return Optional.of(keyframe.time());
            }
        }
        return Optional.empty();
    }
}
