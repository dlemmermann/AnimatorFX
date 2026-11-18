package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.editor.doc.Command;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a drop of layer rows in the layer tree into a command.
 */
public final class LayerDrops {

    /** Relative to the row the pointer is over; rows show the top-most layer first. */
    public enum Where {
        /** Directly over the anchor layer, in its parent. */
        ABOVE,
        /** Directly under the anchor layer, in its parent. */
        BELOW,
        /** Into the anchor group, on top of its children. */
        INTO
    }

    /**
     * @param anchorId the layer of the row the pointer is over
     */
    public record Drop(String anchorId, Where where) {
    }

    private LayerDrops() {
    }

    /**
     * The command that moves the layers to the drop position, keeping their order; one undo
     * step built from {@link Commands#moveLayer}. Layers inside another moved layer travel with
     * it. Empty when the drop is impossible: onto one of the moved layers or into one of them,
     * or into a layer that is not a group.
     */
    public static Optional<Command> command(Animation animation, Collection<String> layerIds, Drop drop) {
        Set<String> wanted = new HashSet<>(layerIds);
        List<Layer> moving = new ArrayList<>();
        collectTopMost(animation.layers(), wanted, moving);
        Layer anchor = Layers.find(animation, drop.anchorId()).orElse(null);
        if (moving.isEmpty() || anchor == null) {
            return Optional.empty();
        }
        for (Layer layer : moving) {
            if (contains(layer, drop.anchorId())) {
                return Optional.empty();
            }
        }
        String parentId;
        if (drop.where() == Where.INTO) {
            if (!(anchor instanceof GroupLayer)) {
                return Optional.empty();
            }
            parentId = anchor.id();
        } else {
            parentId = Layers.parentOf(animation, anchor.id()).map(Layer::id).orElse(null);
        }

        List<Commands.LayerMove> moves = new ArrayList<>();
        Animation current = animation;
        String below = null;
        for (Layer layer : moving) {
            current = Layers.remove(current, layer.id());
            List<Layer> siblings = children(current, parentId);
            int index;
            if (below != null) {
                index = indexOf(siblings, below) + 1;
            } else if (drop.where() == Where.INTO) {
                index = siblings.size();
            } else {
                index = indexOf(siblings, anchor.id()) + (drop.where() == Where.ABOVE ? 1 : 0);
            }
            moves.add(new Commands.LayerMove(layer.id(), parentId, index));
            current = Layers.insert(current, parentId, index, layer);
            below = layer.id();
        }
        return Optional.of(Commands.moveLayers(moves));
    }

    private static List<Layer> children(Animation animation, String parentId) {
        if (parentId == null) {
            return animation.layers();
        }
        return ((GroupLayer) Layers.find(animation, parentId).orElseThrow()).children();
    }

    private static int indexOf(List<Layer> layers, String id) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalStateException("Layer is not in its parent: " + id);
    }

    private static boolean contains(Layer layer, String id) {
        if (layer.id().equals(id)) {
            return true;
        }
        if (layer instanceof GroupLayer group) {
            for (Layer child : group.children()) {
                if (contains(child, id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The wanted layers bottom to top, leaving out those inside another wanted layer. */
    private static void collectTopMost(List<Layer> layers, Set<String> wanted, List<Layer> out) {
        for (Layer layer : layers) {
            if (wanted.contains(layer.id())) {
                out.add(layer);
            } else if (layer instanceof GroupLayer group) {
                collectTopMost(group.children(), wanted, out);
            }
        }
    }
}
