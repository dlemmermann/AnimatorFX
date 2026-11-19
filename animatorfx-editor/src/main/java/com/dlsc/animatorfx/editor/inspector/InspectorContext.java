package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.doc.Command;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.Selection.KeyframeRef;
import com.dlsc.animatorfx.editor.inspector.fields.KeyframeToggle;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerProperties;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.PropertyRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * What the inspector sections work on: the document, its selected layers and keyframes and the
 * time at the playhead, plus the edits that sections make. Every edit applies to all selected
 * layers that it concerns and is one undo step; animatable values are written through
 * {@link Commands#setValueAt} with the auto-key setting of the document.
 */
public final class InspectorContext {

    private final EditorDocument document;
    private Animation resolvedFor;
    private List<String> resolvedIds = List.of();
    private List<Layer> layers = List.of();

    public InspectorContext(EditorDocument document) {
        this.document = Objects.requireNonNull(document, "document");
    }

    public EditorDocument document() {
        return document;
    }

    public Animation animation() {
        return document.getAnimation();
    }

    /** The selected layers, in selection order. */
    public List<Layer> layers() {
        Animation animation = document.getAnimation();
        List<String> ids = document.getSelection().layerIds();
        if (animation != resolvedFor || !ids.equals(resolvedIds)) {
            List<Layer> found = new ArrayList<>();
            for (String id : ids) {
                Layers.find(animation, id).ifPresent(found::add);
            }
            layers = List.copyOf(found);
            resolvedFor = animation;
            resolvedIds = List.copyOf(ids);
        }
        return layers;
    }

    public List<KeyframeRef> keyframes() {
        return List.copyOf(document.getSelection().keyframes());
    }

    /** The playhead limited to the duration: the time of the frame on the canvas. */
    public double time() {
        return Math.clamp(document.getPlayhead(), 0, Math.max(0, document.getAnimation().duration()));
    }

    // ------------------------------------------------------------------ reading

    /** Whether every selected layer is of that type; false without a selection. */
    public boolean allLayers(Class<? extends Layer> type) {
        List<Layer> all = layers();
        return !all.isEmpty() && all.stream().allMatch(type::isInstance);
    }

    /** Whether there is a selection and every selected layer is drawn, that is none is an audio layer. */
    public boolean allVisual() {
        List<Layer> all = layers();
        return !all.isEmpty() && all.stream().noneMatch(AudioLayer.class::isInstance);
    }

    /** The property of a layer, when the layer has it. */
    public static Optional<Animatable<?>> property(Layer layer, String key) {
        for (PropertyRef property : LayerProperties.list(layer)) {
            if (property.key().equals(key)) {
                return Optional.of(property.value());
            }
        }
        return Optional.empty();
    }

    /** Whether every selected layer has the animatable property; false without a selection. */
    public boolean allHave(String key) {
        List<Layer> all = layers();
        return !all.isEmpty() && all.stream().allMatch(layer -> property(layer, key).isPresent());
    }

    /** The values of the property at the playhead, one per selected layer that has it. */
    public List<Object> values(String key) {
        List<Object> values = new ArrayList<>();
        double time = time();
        for (Layer layer : layers()) {
            property(layer, key).ifPresent(value -> values.add(value.at(time)));
        }
        return values;
    }

    /** What all the values have in common; {@code null} when they differ or there are none. */
    public static <T> T common(List<T> values) {
        T first = null;
        for (T value : values) {
            if (first == null) {
                first = value;
            } else if (!first.equals(value)) {
                return null;
            }
        }
        return first;
    }

    /** A member of the selected layers that all share; {@code null} when it differs. */
    public <L extends Layer, T> T common(Class<L> type, Function<L, T> member) {
        List<T> values = new ArrayList<>();
        for (Layer layer : layers()) {
            if (type.isInstance(layer)) {
                values.add(member.apply(type.cast(layer)));
            }
        }
        return common(values);
    }

    /**
     * The state of the keyframe diamond of a property: keyed when every selected layer that has
     * it has a keyframe at the playhead, else animated when one of them is animated.
     */
    public KeyframeToggle.State keyState(String key) {
        boolean any = false;
        boolean allKeyed = true;
        boolean animated = false;
        double time = time();
        for (Layer layer : layers()) {
            Animatable<?> value = property(layer, key).orElse(null);
            if (value != null) {
                any = true;
                animated |= value.isAnimated();
                allKeyed &= keyframeAt(value, time) != null;
            }
        }
        return any && allKeyed ? KeyframeToggle.State.KEYED
                : animated ? KeyframeToggle.State.ANIMATED : KeyframeToggle.State.NONE;
    }

    private static Keyframe<?> keyframeAt(Animatable<?> value, double time) {
        for (Keyframe<?> keyframe : value.keyframes()) {
            if (Math.abs(keyframe.time() - time) < Animatable.TIME_EPSILON) {
                return keyframe;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ editing

    /**
     * Sets an animatable property on every selected layer that has it, at the playhead, as one
     * undo step. The operator turns the current value of a layer into its new value, so an edit
     * of one component keeps the others of each layer.
     *
     * @param gestureKey {@code null} for a single edit; an object that identifies a continuous
     *                   adjustment (scrubbing, a drag in a picker) to merge its changes into one
     *                   undo step, to be followed by {@link #endGesture()}
     */
    public void setValue(String key, UnaryOperator<Object> edit, Object gestureKey) {
        double time = time();
        List<Command> commands = new ArrayList<>();
        for (Layer layer : layers()) {
            Animatable<?> current = property(layer, key).orElse(null);
            if (current != null) {
                commands.add(Commands.setValueAt(layer.id(), key, time, edit.apply(current.at(time)),
                        document.isAutoKey()));
            }
        }
        run(Commands.batch(commands.isEmpty() ? "Change" : commands.get(0).name(), commands), gestureKey);
    }

    /**
     * Changes the selected layers of a type with a function, as one undo step: for members that
     * are not animatable.
     */
    public <L extends Layer> void edit(String name, Class<L> type, UnaryOperator<L> change, Object gestureKey) {
        List<Command> commands = new ArrayList<>();
        for (Layer layer : layers()) {
            if (type.isInstance(layer)) {
                commands.add(Commands.updateLayer(name, layer.id(), l -> type.isInstance(l) ? change.apply(type.cast(l)) : l));
            }
        }
        run(Commands.batch(name, commands), gestureKey);
    }

    private void run(Command command, Object gestureKey) {
        if (gestureKey == null) {
            document.execute(command);
        } else {
            document.executeCoalesced(gestureKey, command);
        }
    }

    public void endGesture() {
        document.endGesture();
    }

    /**
     * The click on a keyframe diamond: removes the keyframes at the playhead when every selected
     * layer has one there, and otherwise adds one where it is missing.
     */
    public void toggleKeyframe(String key) {
        double time = time();
        if (keyState(key) == KeyframeToggle.State.KEYED) {
            List<KeyframeRef> refs = new ArrayList<>();
            for (Layer layer : layers()) {
                property(layer, key).map(value -> keyframeAt(value, time))
                        .ifPresent(k -> refs.add(new KeyframeRef(layer.id(), key, k.id())));
            }
            document.execute(Commands.removeKeyframes(refs));
        } else {
            List<Command> commands = new ArrayList<>();
            for (Layer layer : layers()) {
                if (property(layer, key).isPresent()) {
                    commands.add(Commands.addKeyframe(layer.id(), key, time));
                }
            }
            document.execute(Commands.batch("Add Keyframe", commands));
        }
    }
}
