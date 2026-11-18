package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.editor.doc.Selection.KeyframeRef;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.LayerProperties;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.PropertyRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The edits of the editor. Every factory returns a pure {@link Command}; a command that would
 * change nothing returns the animation instance it was given. Unknown layer ids are ignored,
 * unknown property keys and values of the wrong type throw {@link IllegalArgumentException}.
 */
public final class Commands {

    private Commands() {
    }

    public static Command addLayer(String parentIdOrNull, int index, Layer layer) {
        Objects.requireNonNull(layer, "layer");
        return command("Add Layer", a -> Layers.insert(a, parentIdOrNull, index, layer));
    }

    /**
     * Removes the layers with their children and clears every mask that pointed to a removed layer.
     */
    public static Command removeLayers(Collection<String> ids) {
        List<String> copy = List.copyOf(ids);
        return command(copy.size() == 1 ? "Delete Layer" : "Delete Layers", before -> {
            Animation after = before;
            for (String id : copy) {
                after = Layers.remove(after, id);
            }
            if (after == before) {
                return before;
            }
            Set<String> removed = layerIds(before);
            removed.removeAll(layerIds(after));
            return after.withLayers(mapLayers(after.layers(), layer -> {
                Mask mask = layer.base().mask();
                return mask != null && removed.contains(mask.layerId())
                        ? layer.withBase(layer.base().withMask(null)) : layer;
            }));
        });
    }

    /**
     * Deep-copies the layers with fresh layer and keyframe ids and inserts each copy directly
     * above its original. A layer inside a group that is duplicated as well is not copied twice.
     * Masks inside the copies point to the copied mask layer when that was duplicated too.
     */
    public static Command duplicateLayers(Collection<String> ids) {
        Set<String> wanted = new LinkedHashSet<>(ids);
        return command(wanted.size() == 1 ? "Duplicate Layer" : "Duplicate Layers", before -> {
            List<Layer> originals = new ArrayList<>();
            collectTopMost(before.layers(), wanted, originals);
            Map<String, String> newIds = new HashMap<>();
            for (Layer original : originals) {
                for (Layer layer : subtree(original)) {
                    newIds.put(layer.id(), UUID.randomUUID().toString());
                }
            }
            Animation after = before;
            for (Layer original : originals) {
                Layer copy = copy(original, newIds);
                copy = copy.withBase(copy.base().withName(original.name() + " copy"));
                String parentId = Layers.parentOf(after, original.id()).map(Layer::id).orElse(null);
                List<Layer> siblings = parentId == null ? after.layers()
                        : ((GroupLayer) Layers.find(after, parentId).orElseThrow()).children();
                after = Layers.insert(after, parentId, indexOf(siblings, original.id()) + 1, copy);
            }
            return after;
        });
    }

    public static Command updateLayer(String name, String id, UnaryOperator<Layer> fn) {
        Objects.requireNonNull(fn, "fn");
        return command(name, a -> editLayer(a, id, fn));
    }

    public static Command setProperty(String id, String key, Animatable<?> value) {
        Objects.requireNonNull(value, "value");
        return command("Change " + label(key), a -> editLayer(a, id, layer -> LayerProperties.set(layer, key, value)));
    }

    /**
     * Sets the value of a property at a time, following the auto-key rule: with auto-key on, a
     * keyframe at that time is added or updated (a static property becomes keyframed with that
     * single keyframe). With auto-key off, a static property gets a new static value and an
     * animated property gets its keyframe at that time updated, or a new keyframe when there is
     * none. New keyframes ease linearly; an updated keyframe keeps its id and easing.
     */
    public static Command setValueAt(String id, String key, double time, Object value, boolean autoKey) {
        Objects.requireNonNull(value, "value");
        return command("Change " + label(key), a -> editLayer(a, id,
                layer -> LayerProperties.set(layer, key, valueAt(LayerProperties.get(layer, key), time, value, autoKey))));
    }

    /**
     * Adds a keyframe with the value the property currently has at that time. Does nothing when
     * there is a keyframe at that time already.
     */
    public static Command addKeyframe(String id, String key, double time) {
        return command("Add Keyframe", a -> editLayer(a, id,
                layer -> LayerProperties.set(layer, key, withSampledKeyframe(LayerProperties.get(layer, key), time))));
    }

    public static Command removeKeyframes(Collection<KeyframeRef> refs) {
        return keyframeCommand(refs.size() == 1 ? "Delete Keyframe" : "Delete Keyframes", refs, (value, ids) -> {
            Animatable<?> result = value;
            for (String keyframeId : ids) {
                result = result.withoutKeyframe(keyframeId);
            }
            return result;
        });
    }

    /**
     * Shifts the keyframes in time. Every resulting time is clamped to {@code >= 0}; a moved
     * keyframe that lands on an unmoved keyframe of the same property replaces it. When several
     * moved keyframes end at the same time, the one that was latest before the move stays.
     */
    public static Command moveKeyframes(Collection<KeyframeRef> refs, double deltaSeconds) {
        return keyframeCommand(refs.size() == 1 ? "Move Keyframe" : "Move Keyframes", refs,
                (value, ids) -> moved(value, ids, deltaSeconds));
    }

    public static Command setEasing(Collection<KeyframeRef> refs, Easing easing) {
        Objects.requireNonNull(easing, "easing");
        return keyframeCommand("Change Easing", refs, (value, ids) -> withEasing(value, ids, easing));
    }

    /**
     * Moves the layer as {@link Layers#move} does: the index counts in the target list after the
     * layer has been taken out.
     */
    public static Command moveLayer(String id, String newParentIdOrNull, int index) {
        return command("Move Layer", before -> {
            if (Layers.find(before, id).isEmpty()) {
                return before;
            }
            Animation after = Layers.move(before, id, newParentIdOrNull, index);
            return after.layers().equals(before.layers()) ? before : after;
        });
    }

    public static Command setDocument(String name, UnaryOperator<Animation> fn) {
        Objects.requireNonNull(fn, "fn");
        return command(name, before -> {
            Animation after = Objects.requireNonNull(fn.apply(before), "animation");
            return after.equals(before) ? before : after;
        });
    }

    private static Command command(String name, UnaryOperator<Animation> fn) {
        Objects.requireNonNull(name, "name");
        return new Command() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Animation apply(Animation before) {
                return fn.apply(before);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    private interface KeyframeEdit {
        Animatable<?> apply(Animatable<?> value, Set<String> keyframeIds);
    }

    private static Command keyframeCommand(String name, Collection<KeyframeRef> refs, KeyframeEdit edit) {
        Map<String, Map<String, Set<String>>> byLayer = new LinkedHashMap<>();
        for (KeyframeRef ref : refs) {
            byLayer.computeIfAbsent(ref.layerId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(ref.propertyKey(), k -> new LinkedHashSet<>())
                    .add(ref.keyframeId());
        }
        return command(name, before -> {
            Animation after = before;
            for (Map.Entry<String, Map<String, Set<String>>> entry : byLayer.entrySet()) {
                after = editLayer(after, entry.getKey(), layer -> {
                    Layer result = layer;
                    for (Map.Entry<String, Set<String>> property : entry.getValue().entrySet()) {
                        Animatable<?> value = LayerProperties.get(result, property.getKey());
                        result = LayerProperties.set(result, property.getKey(), edit.apply(value, property.getValue()));
                    }
                    return result;
                });
            }
            return after;
        });
    }

    /** Like {@link Layers#replace}, but a result that equals the layer also counts as "no change". */
    private static Animation editLayer(Animation animation, String id, UnaryOperator<Layer> fn) {
        return Layers.replace(animation, id, layer -> {
            Layer result = fn.apply(layer);
            return layer.equals(result) ? layer : result;
        });
    }

    // The cast is checked afterwards: LayerProperties.set rejects values of the wrong type.
    @SuppressWarnings("unchecked")
    private static <T> Animatable<T> valueAt(Animatable<T> current, double time, Object value, boolean autoKey) {
        T typed = (T) value;
        if (!autoKey && !current.isAnimated()) {
            return Animatable.of(typed);
        }
        Easing easing = Easing.LINEAR;
        for (Keyframe<T> keyframe : current.keyframes()) {
            if (Math.abs(keyframe.time() - time) < Animatable.TIME_EPSILON) {
                easing = keyframe.easing();
            }
        }
        return current.withKeyframe(time, typed, easing);
    }

    private static <T> Animatable<T> withSampledKeyframe(Animatable<T> current, double time) {
        for (Keyframe<T> keyframe : current.keyframes()) {
            if (Math.abs(keyframe.time() - time) < Animatable.TIME_EPSILON) {
                return current;
            }
        }
        return current.withKeyframe(time, current.at(time), Easing.LINEAR);
    }

    private static <T> Animatable<T> moved(Animatable<T> value, Set<String> ids, double delta) {
        if (!value.isAnimated()) {
            return value;
        }
        List<Keyframe<T>> moved = new ArrayList<>();
        List<Keyframe<T>> unmoved = new ArrayList<>();
        for (Keyframe<T> keyframe : value.keyframes()) {
            if (ids.contains(keyframe.id())) {
                double time = Math.max(0, keyframe.time() + delta);
                Keyframe<T> shifted = new Keyframe<>(keyframe.id(), time, keyframe.value(), keyframe.easing());
                moved.removeIf(other -> sameTime(other, shifted));
                moved.add(shifted);
            } else {
                unmoved.add(keyframe);
            }
        }
        if (moved.isEmpty()) {
            return value;
        }
        unmoved.removeIf(keyframe -> moved.stream().anyMatch(other -> sameTime(other, keyframe)));
        unmoved.addAll(moved);
        return Animatable.keyframed(unmoved);
    }

    private static boolean sameTime(Keyframe<?> a, Keyframe<?> b) {
        return Math.abs(a.time() - b.time()) < Animatable.TIME_EPSILON;
    }

    private static <T> Animatable<T> withEasing(Animatable<T> value, Set<String> ids, Easing easing) {
        if (!value.isAnimated()) {
            return value;
        }
        List<Keyframe<T>> list = new ArrayList<>();
        for (Keyframe<T> keyframe : value.keyframes()) {
            list.add(ids.contains(keyframe.id())
                    ? new Keyframe<>(keyframe.id(), keyframe.time(), keyframe.value(), easing) : keyframe);
        }
        return Animatable.keyframed(list);
    }

    private static <T> Animatable<T> withFreshKeyframeIds(Animatable<T> value) {
        List<Keyframe<T>> list = new ArrayList<>();
        for (Keyframe<T> keyframe : value.keyframes()) {
            list.add(new Keyframe<>(UUID.randomUUID().toString(), keyframe.time(), keyframe.value(), keyframe.easing()));
        }
        return Animatable.keyframed(list);
    }

    private static Layer copy(Layer original, Map<String, String> newIds) {
        Layer copy = original;
        if (copy instanceof GroupLayer group) {
            List<Layer> children = new ArrayList<>();
            for (Layer child : group.children()) {
                children.add(copy(child, newIds));
            }
            copy = group.withChildren(children);
        }
        for (PropertyRef ref : LayerProperties.list(copy)) {
            if (ref.value().isAnimated()) {
                copy = LayerProperties.set(copy, ref.key(), withFreshKeyframeIds(ref.value()));
            }
        }
        LayerBase base = copy.base().withId(newIds.get(original.id()));
        Mask mask = base.mask();
        if (mask != null && newIds.containsKey(mask.layerId())) {
            base = base.withMask(new Mask(newIds.get(mask.layerId()), mask.mode()));
        }
        return copy.withBase(base);
    }

    private static void collectTopMost(List<Layer> layers, Set<String> wanted, List<Layer> out) {
        for (Layer layer : layers) {
            if (wanted.contains(layer.id())) {
                out.add(layer);
            } else if (layer instanceof GroupLayer group) {
                collectTopMost(group.children(), wanted, out);
            }
        }
    }

    private static List<Layer> subtree(Layer layer) {
        List<Layer> out = new ArrayList<>();
        out.add(layer);
        if (layer instanceof GroupLayer group) {
            for (Layer child : group.children()) {
                out.addAll(subtree(child));
            }
        }
        return out;
    }

    private static int indexOf(List<Layer> layers, String id) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalStateException("Layer is not in its parent: " + id);
    }

    private static Set<String> layerIds(Animation animation) {
        Set<String> ids = new HashSet<>();
        for (Layer layer : Layers.flatten(animation)) {
            ids.add(layer.id());
        }
        return ids;
    }

    /** Applies the function to every layer, children first, and keeps the instances of unchanged subtrees. */
    private static List<Layer> mapLayers(List<Layer> layers, UnaryOperator<Layer> fn) {
        List<Layer> out = new ArrayList<>(layers.size());
        boolean changed = false;
        for (Layer layer : layers) {
            Layer result = layer;
            if (result instanceof GroupLayer group) {
                List<Layer> children = mapLayers(group.children(), fn);
                if (children != group.children()) {
                    result = group.withChildren(children);
                }
            }
            result = fn.apply(result);
            changed |= result != layer;
            out.add(result);
        }
        return changed ? List.copyOf(out) : layers;
    }

    /** "transform.position" gives "Position", "stroke.dashOffset" gives "Stroke Dash Offset". */
    static String label(String key) {
        String rest = key.replaceFirst("^(transform|geometry)\\.", "").replaceFirst("^effects\\[\\d+]\\.", "effect.");
        StringBuilder out = new StringBuilder();
        for (String word : rest.split("\\.|(?<=[a-z])(?=[A-Z])")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return out.toString();
    }
}
