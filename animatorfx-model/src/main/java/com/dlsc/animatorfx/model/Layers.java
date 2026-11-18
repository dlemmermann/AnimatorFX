package com.dlsc.animatorfx.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Queries and edits of the layer tree of an animation. Edits return a new animation that shares
 * every untouched layer and subtree with the original.
 */
public final class Layers {

    private Layers() {
    }

    public static Optional<Layer> find(Animation animation, String id) {
        return Optional.ofNullable(find(animation.layers(), id));
    }

    /**
     * All layers depth-first, each group before its children.
     */
    public static List<Layer> flatten(Animation animation) {
        List<Layer> out = new ArrayList<>();
        collect(animation.layers(), out);
        return List.copyOf(out);
    }

    /**
     * The group that directly contains the layer; empty for a top-level layer or an unknown id.
     */
    public static Optional<GroupLayer> parentOf(Animation animation, String id) {
        return Optional.ofNullable(parentOf(animation.layers(), id));
    }

    /**
     * Replaces the layer with the result of the function. An unknown id, or a function that
     * returns the layer it was given, returns the same animation instance.
     */
    public static Animation replace(Animation animation, String id, UnaryOperator<Layer> fn) {
        return withLayers(animation, edit(animation.layers(), id,
                layer -> Objects.requireNonNull(fn.apply(layer), "replacement layer")));
    }

    /**
     * Removes the layer, with its children. An unknown id returns the same animation instance.
     */
    public static Animation remove(Animation animation, String id) {
        return withLayers(animation, edit(animation.layers(), id, layer -> null));
    }

    /**
     * Inserts the layer into the group with the given id, or at the top level when the parent
     * id is {@code null}.
     *
     * @throws IllegalArgumentException  if the parent id is unknown or not a group, or if the id of
     *                                   the layer or of one of its descendants is already in use
     * @throws IndexOutOfBoundsException if the index is outside {@code 0..size} of the target list
     */
    public static Animation insert(Animation animation, String parentIdOrNull, int index, Layer layer) {
        Objects.requireNonNull(layer, "layer");
        Set<String> used = new HashSet<>();
        for (Layer existing : flatten(animation)) {
            used.add(existing.id());
        }
        List<Layer> inserted = new ArrayList<>();
        collect(List.of(layer), inserted);
        for (Layer l : inserted) {
            if (!used.add(l.id())) {
                throw new IllegalArgumentException("Layer id is already in use: " + l.id());
            }
        }

        if (parentIdOrNull == null) {
            return animation.withLayers(insertAt(animation.layers(), index, layer));
        }
        Layer parent = find(animation.layers(), parentIdOrNull);
        if (parent == null) {
            throw new IllegalArgumentException("Unknown parent layer: " + parentIdOrNull);
        }
        if (!(parent instanceof GroupLayer group)) {
            throw new IllegalArgumentException("Parent layer is not a group: " + parentIdOrNull);
        }
        GroupLayer changed = group.withChildren(insertAt(group.children(), index, layer));
        return animation.withLayers(edit(animation.layers(), parentIdOrNull, p -> changed));
    }

    /**
     * Moves the layer, with its children, into the group with the given id, or to the top level
     * when the new parent id is {@code null}. The index counts in the target list after the
     * layer has been taken out of the tree.
     *
     * @throws IllegalArgumentException  if the layer id is unknown, if the new parent id is unknown
     *                                   or not a group, or if the new parent is the layer itself or
     *                                   one of its descendants
     * @throws IndexOutOfBoundsException if the index is outside {@code 0..size} of the target list
     */
    public static Animation move(Animation animation, String id, String newParentIdOrNull, int index) {
        Layer layer = find(animation.layers(), id);
        if (layer == null) {
            throw new IllegalArgumentException("Unknown layer: " + id);
        }
        if (newParentIdOrNull != null && find(List.of(layer), newParentIdOrNull) != null) {
            throw new IllegalArgumentException("Cannot move layer " + id + " into itself or one of its descendants");
        }
        return insert(remove(animation, id), newParentIdOrNull, index, layer);
    }

    /**
     * All slot layers, in {@link #flatten} order.
     */
    public static List<SlotLayer> slots(Animation animation) {
        List<SlotLayer> out = new ArrayList<>();
        for (Layer layer : flatten(animation)) {
            if (layer instanceof SlotLayer slot) {
                out.add(slot);
            }
        }
        return List.copyOf(out);
    }

    private static Layer find(List<Layer> layers, String id) {
        for (Layer layer : layers) {
            if (layer.id().equals(id)) {
                return layer;
            }
            if (layer instanceof GroupLayer group) {
                Layer found = find(group.children(), id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static GroupLayer parentOf(List<Layer> layers, String id) {
        for (Layer layer : layers) {
            if (layer instanceof GroupLayer group) {
                for (Layer child : group.children()) {
                    if (child.id().equals(id)) {
                        return group;
                    }
                }
                GroupLayer found = parentOf(group.children(), id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void collect(List<Layer> layers, List<Layer> out) {
        for (Layer layer : layers) {
            out.add(layer);
            if (layer instanceof GroupLayer group) {
                collect(group.children(), out);
            }
        }
    }

    private static Animation withLayers(Animation animation, List<Layer> layers) {
        return layers == animation.layers() ? animation : animation.withLayers(layers);
    }

    /**
     * Applies the function to the layer with the given id, where a {@code null} result removes
     * the layer. Returns the same list instance when nothing changed; otherwise only the lists
     * and groups on the way to the layer are copied.
     */
    private static List<Layer> edit(List<Layer> layers, String id, UnaryOperator<Layer> fn) {
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            Layer result;
            if (layer.id().equals(id)) {
                result = fn.apply(layer);
            } else if (layer instanceof GroupLayer group) {
                List<Layer> children = edit(group.children(), id, fn);
                if (children == group.children()) {
                    continue;
                }
                result = group.withChildren(children);
            } else {
                continue;
            }
            if (result == layer) {
                return layers;
            }
            List<Layer> copy = new ArrayList<>(layers);
            if (result == null) {
                copy.remove(i);
            } else {
                copy.set(i, result);
            }
            return List.copyOf(copy);
        }
        return layers;
    }

    private static List<Layer> insertAt(List<Layer> layers, int index, Layer layer) {
        List<Layer> copy = new ArrayList<>(layers);
        copy.add(index, layer);
        return List.copyOf(copy);
    }
}
