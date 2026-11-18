package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.io.AnimationFormatException;
import com.dlsc.animatorfx.model.io.AnimationJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The last step of every import: makes sure the document can be saved and read back.
 * <p>
 * First the faults that are cheap to find are repaired in one pass over the layers: a layer with
 * a value that is not finite or absurdly large is dropped, as is one nested deeper than
 * {@link Limits#MAX_DEPTH}; a repeated layer id is replaced; a mask that does not name another
 * layer of the same parent is removed. Then the document is run through the reader of the file
 * format once. Should the reader still object to a layer, that layer (or its mask) goes too.
 * Every repair is reported as a warning.
 */
final class DocumentCheck {

    /** How often the reader may find something the first pass did not, before the import fails. */
    private static final int MAX_ATTEMPTS = 16;

    private static final Pattern LAYER_PATH = Pattern.compile("^layers\\[(\\d+)](\\.children\\[(\\d+)])*");
    private static final Pattern INDEX = Pattern.compile("\\[(\\d+)]");

    private final Warnings warnings;
    private final Set<String> ids = new HashSet<>();

    private DocumentCheck(Warnings warnings) {
        this.warnings = warnings;
    }

    /**
     * @return the document, repaired where necessary
     * @throws IOException if the document cannot be made valid by dropping layers
     */
    static Animation repair(Animation animation, Warnings warnings) throws IOException {
        DocumentCheck check = new DocumentCheck(warnings);
        Map<String, Object> json = AnimationJson.toJson(animation);
        List<Layer> layers = check.sound(animation.layers(), list(json.get("layers")), 1);
        Animation current = layers.equals(animation.layers()) ? animation : animation.withLayers(layers);
        for (int attempt = 0; ; attempt++) {
            try {
                AnimationJson.fromJson(current == animation ? json : AnimationJson.toJson(current));
                return current;
            } catch (AnimationFormatException e) {
                Matcher path = LAYER_PATH.matcher(e.jsonPath());
                if (attempt >= MAX_ATTEMPTS || !path.find()) {
                    throw new IOException("The imported document is not valid: " + e.getMessage(), e);
                }
                List<Integer> indices = new ArrayList<>();
                Matcher index = INDEX.matcher(path.group());
                while (index.find()) {
                    indices.add(Integer.parseInt(index.group(1)));
                }
                boolean maskOnly = e.jsonPath().startsWith(".mask", path.end());
                warnings.add(maskOnly ? "Masks that the document format does not accept were removed"
                        : "Layers that the document format does not accept were dropped", "layer", "layers");
                current = current.withLayers(without(current.layers(), indices, 0, maskOnly));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    /**
     * The layers that can stay, with valid masks and ids.
     *
     * @param json the JSON of the layers, in step with them
     */
    private List<Layer> sound(List<Layer> layers, List<Object> json, int depth) {
        List<Layer> kept = new ArrayList<>();
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            Map<?, ?> members = i < json.size() && json.get(i) instanceof Map<?, ?> map ? map : Map.of();
            if (depth > Limits.MAX_DEPTH) {
                Limits.tooDeep(warnings);
                continue;
            }
            if (!finite(members)) {
                warnings.add("Layers with values that are not finite or too large were dropped", "layer", "layers");
                continue;
            }
            if (!ids.add(layer.id())) {
                String id = UUID.randomUUID().toString();
                ids.add(id);
                layer = layer.withBase(layer.base().withId(id));
                warnings.add("Layers with an id that another layer has got a new id", "layer", "layers");
            }
            if (layer instanceof GroupLayer group) {
                List<Layer> children = sound(group.children(), list(members.get("children")), depth + 1);
                if (!children.equals(group.children())) {
                    layer = group.withChildren(children);
                }
            }
            kept.add(layer);
        }
        Set<String> siblings = new HashSet<>();
        for (Layer layer : kept) {
            siblings.add(layer.id());
        }
        for (int i = 0; i < kept.size(); i++) {
            Layer layer = kept.get(i);
            Mask mask = layer.base().mask();
            if (mask != null && (mask.layerId().equals(layer.id()) || !siblings.contains(mask.layerId()))) {
                kept.set(i, layer.withBase(layer.base().withMask(null)));
                warnings.add("Masks that do not refer to a layer beside the masked one were removed", "mask",
                        "masks");
            }
        }
        return kept;
    }

    /** Whether every number of a layer, its children aside, is one a document can hold. */
    private static boolean finite(Object value) {
        if (value instanceof Number number) {
            return Limits.usable(number.doubleValue());
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!"children".equals(entry.getKey()) && !finite(entry.getValue())) {
                    return false;
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object element : list) {
                if (!finite(element)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The layers without the one at the path of indices, or with that one unmasked. */
    private static List<Layer> without(List<Layer> layers, List<Integer> indices, int level, boolean maskOnly) {
        int index = indices.get(level);
        if (index >= layers.size()) {
            return layers;
        }
        List<Layer> result = new ArrayList<>(layers);
        Layer layer = layers.get(index);
        if (level < indices.size() - 1) {
            if (layer instanceof GroupLayer group) {
                result.set(index, group.withChildren(without(group.children(), indices, level + 1, maskOnly)));
            }
        } else if (maskOnly) {
            result.set(index, layer.withBase(layer.base().withMask(null)));
        } else {
            result.remove(index);
        }
        return result;
    }
}
