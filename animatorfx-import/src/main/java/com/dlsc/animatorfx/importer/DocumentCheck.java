package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.model.io.AnimationFormatException;
import com.dlsc.animatorfx.model.io.AnimationJson;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The last step of every import: makes sure the document keeps within the limits of an import
 * and can be saved and read back.
 * <ol>
 * <li>The tree of layers is cut to {@link Limits#MAX_DEPTH} levels and {@link Limits#MAX_LAYERS}
 * layers, whatever produced them. This pass uses no recursion, so it is safe on a tree of any
 * depth; everything after it works on a tree of at most {@link Limits#MAX_DEPTH} levels.</li>
 * <li>A layer with a value that is not finite or absurdly large is dropped, a repeated layer id
 * is replaced. An audio layer without a sound is dropped; its volume, balance and clip offset are
 * limited to their ranges.</li>
 * <li>Sound cues are put in order the same way: a cue without a sound or a time is dropped, time
 * and volume are limited to their ranges, a repeated id is replaced.</li>
 * <li>The document is run through the reader of the file format once. Should the reader still
 * object to a layer, that layer (or its mask) goes too.</li>
 * </ol>
 * Wherever layers go, the masks of their siblings are put in order: a mask that names no layer
 * beside the masked one is removed, and a layer that was only there to mask a dropped layer is
 * dropped with it, so that it does not show up as content. Every repair is reported as a warning.
 */
final class DocumentCheck {

    /** How often the reader may find something the passes before did not, before the import fails. */
    private static final int MAX_ATTEMPTS = 16;

    private static final Pattern LAYER_PATH = Pattern.compile("^layers\\[(\\d+)](\\.children\\[(\\d+)])*");
    private static final Pattern INDEX = Pattern.compile("\\[(\\d+)]");
    private static final Pattern CUE_PATH = Pattern.compile("^cues\\[(\\d+)]");

    /** A list of siblings while {@link #bounded} walks it. */
    private static final class Level {
        final GroupLayer group;
        final List<Layer> source;
        final List<Layer> kept = new ArrayList<>();
        final int depth;
        int next;
        boolean changed;

        Level(GroupLayer group, List<Layer> source, int depth) {
            this.group = group;
            this.source = source;
            this.depth = depth;
        }
    }

    private final Warnings warnings;
    private final Set<String> ids = new HashSet<>();
    private final Set<String> sounds = new HashSet<>();

    private DocumentCheck(Warnings warnings, Animation animation) {
        this.warnings = warnings;
        for (Asset asset : animation.assets()) {
            if (asset.kind() == Asset.Kind.AUDIO) {
                sounds.add(asset.id());
            }
        }
    }

    /**
     * @return the document, repaired where necessary; the very instance if nothing had to be done
     * @throws IOException if the document cannot be made valid by dropping layers
     */
    static Animation repair(Animation animation, Warnings warnings) throws IOException {
        DocumentCheck check = new DocumentCheck(warnings, animation);
        List<SoundCue> cues = check.cues(animation.cues(), animation.duration());
        Animation current = cues == animation.cues() ? animation : animation.withCues(cues);
        List<Layer> bounded = check.bounded(current.layers());
        if (bounded != current.layers()) {
            current = current.withLayers(bounded);
        }

        Map<String, Object> json = AnimationJson.toJson(current);
        List<Layer> sound = check.sound(current.layers(), list(json.get("layers")));
        if (sound != current.layers()) {
            current = current.withLayers(sound);
            json = null;
        }
        for (int attempt = 0; ; attempt++) {
            try {
                AnimationJson.fromJson(json != null ? json : AnimationJson.toJson(current));
                return current;
            } catch (AnimationFormatException e) {
                Matcher cue = CUE_PATH.matcher(e.jsonPath());
                if (attempt < MAX_ATTEMPTS && cue.find()) {
                    // the pass over the cues leaves nothing for the reader to find; should it find
                    // something after all, the cue goes
                    int index = Integer.parseInt(cue.group(1));
                    List<SoundCue> remaining = new ArrayList<>(current.cues());
                    if (index < remaining.size()) {
                        remaining.remove(index);
                        warnings.add("Sound cues that the document format does not accept were dropped", "cue",
                                "cues");
                        current = current.withCues(remaining);
                        json = null;
                        continue;
                    }
                }
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
                current = current.withLayers(check.without(current.layers(), indices, 0, maskOnly));
                json = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    /**
     * The cues the format accepts.
     *
     * @return the given list itself if nothing had to change
     */
    private List<SoundCue> cues(List<SoundCue> cues, double duration) {
        List<SoundCue> kept = new ArrayList<>();
        Set<String> cueIds = new HashSet<>();
        boolean changed = false;
        for (SoundCue cue : cues) {
            if (!sounds.contains(cue.assetId())) {
                warnings.add("Sound cues without a sound were dropped", "cue", "cues");
                changed = true;
                continue;
            }
            if (!Double.isFinite(cue.time())) {
                warnings.add("Sound cues without a time were dropped", "cue", "cues");
                changed = true;
                continue;
            }
            SoundCue result = cue;
            if (!cueIds.add(cue.id())) {
                String id = UUID.randomUUID().toString();
                cueIds.add(id);
                result = result.withId(id);
                warnings.add("Sound cues with an id that another cue has got a new id", "cue", "cues");
            }
            double time = Math.clamp(cue.time(), 0.0, Math.max(0, duration));
            double volume = Double.isNaN(cue.volume()) ? 1 : Math.clamp(cue.volume(), 0.0, 1.0);
            if (time != cue.time() || volume != cue.volume()) {
                result = result.withTime(time).withVolume(volume);
                warnings.add("Sound cues with a time or volume out of range were limited to their range", "cue",
                        "cues");
            }
            changed |= result != cue;
            kept.add(result);
        }
        return changed ? kept : cues;
    }

    /**
     * An audio layer the format accepts: volume within 0..1, balance within -1..1, no negative
     * clip offset.
     *
     * @return the given layer itself if it is in order; {@code null} if it has no sound
     */
    private AudioLayer audio(AudioLayer layer) {
        if (!sounds.contains(layer.assetId())) {
            warnings.add("Audio layers without a sound were dropped", "layer", "layers");
            return null;
        }
        Animatable<Double> volume = limited(layer.volume(), value -> Math.clamp(value, 0.0, 1.0));
        Animatable<Double> balance = limited(layer.balance(), value -> Math.clamp(value, -1.0, 1.0));
        double clipOffset = Math.max(0, layer.clipOffset());
        if (volume == layer.volume() && balance == layer.balance() && clipOffset == layer.clipOffset()) {
            return layer;
        }
        warnings.add("Audio layers with values out of range were limited to their range", "layer", "layers");
        return layer.withVolume(volume).withBalance(balance).withClipOffset(clipOffset);
    }

    /** The value with every static or keyframe value limited; the given one itself if all are within the range. */
    private static Animatable<Double> limited(Animatable<Double> source, DoubleUnaryOperator limit) {
        if (!source.isAnimated()) {
            double value = source.at(0);
            double within = limit.applyAsDouble(value);
            return within == value ? source : Animatable.of(within);
        }
        List<Keyframe<Double>> keyframes = new ArrayList<>();
        boolean changed = false;
        for (Keyframe<Double> keyframe : source.keyframes()) {
            double within = limit.applyAsDouble(keyframe.value());
            changed |= within != keyframe.value();
            keyframes.add(new Keyframe<>(keyframe.id(), keyframe.time(), within, keyframe.easing()));
        }
        return changed ? Animatable.keyframed(keyframes) : source;
    }

    /**
     * The layers within the limits: nothing nested deeper than {@link Limits#MAX_DEPTH} (the
     * groups at that depth lose their children) and, counted from the bottom up, nothing beyond
     * layer number {@link Limits#MAX_LAYERS}. Walks the tree with a stack of its own.
     *
     * @return the given list itself if it is within the limits
     */
    private List<Layer> bounded(List<Layer> layers) {
        int count = 0;
        Deque<Level> stack = new ArrayDeque<>();
        stack.push(new Level(null, layers, 1));
        while (true) {
            Level level = stack.peek();
            if (level.next < level.source.size()) {
                Layer layer = level.source.get(level.next++);
                if (count >= Limits.MAX_LAYERS) {
                    Limits.stopped(warnings);
                    level.changed = true;
                } else if (level.depth > Limits.MAX_DEPTH) {
                    Limits.tooDeep(warnings);
                    level.changed = true;
                } else if (layer instanceof GroupLayer group) {
                    count++;
                    stack.push(new Level(group, group.children(), level.depth + 1));
                } else {
                    count++;
                    level.kept.add(layer);
                }
                continue;
            }
            stack.pop();
            level.changed |= level.changed && settle(level.source, level.kept);
            if (stack.isEmpty()) {
                return level.changed ? level.kept : layers;
            }
            Level parent = stack.peek();
            parent.kept.add(level.changed ? level.group.withChildren(level.kept) : level.group);
            parent.changed |= level.changed;
        }
    }

    /**
     * The layers that can stay, with valid masks and ids. Recursive, on a tree that
     * {@link #bounded} has cut to {@link Limits#MAX_DEPTH} levels.
     *
     * @param json the JSON of the layers, in step with them
     * @return the given list itself if nothing had to change
     */
    private List<Layer> sound(List<Layer> layers, List<Object> json) {
        List<Layer> kept = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            Map<?, ?> members = i < json.size() && json.get(i) instanceof Map<?, ?> map ? map : Map.of();
            if (!finite(members)) {
                warnings.add("Layers with values that are not finite or too large were dropped", "layer", "layers");
                changed = true;
                continue;
            }
            if (layer instanceof AudioLayer sound) {
                layer = audio(sound);
                if (layer == null) {
                    changed = true;
                    continue;
                }
                changed |= layer != sound;
            }
            if (!ids.add(layer.id())) {
                String id = UUID.randomUUID().toString();
                ids.add(id);
                layer = layer.withBase(layer.base().withId(id));
                warnings.add("Layers with an id that another layer has got a new id", "layer", "layers");
                changed = true;
            }
            if (layer instanceof GroupLayer group) {
                List<Layer> children = sound(group.children(), list(members.get("children")));
                if (children != group.children()) {
                    layer = group.withChildren(children);
                    changed = true;
                }
            }
            kept.add(layer);
        }
        changed |= settle(layers, kept);
        return changed ? kept : layers;
    }

    /**
     * Puts the masks of a list of siblings in order after layers were dropped from it.
     *
     * @param original the siblings as they were
     * @param kept     the siblings that stay; changed in place
     * @return whether anything was changed
     */
    private boolean settle(List<Layer> original, List<Layer> kept) {
        boolean changed = false;
        Map<String, Layer> byId = new HashMap<>();
        for (Layer layer : kept) {
            byId.putIfAbsent(layer.id(), layer);
        }
        // a mask must name another layer of the same list
        Map<String, Integer> users = new HashMap<>();
        for (int i = 0; i < kept.size(); i++) {
            Layer layer = kept.get(i);
            Mask mask = layer.base().mask();
            if (mask == null) {
                continue;
            }
            if (mask.layerId().equals(layer.id()) || !byId.containsKey(mask.layerId())) {
                Layer unmasked = layer.withBase(layer.base().withMask(null));
                kept.set(i, unmasked);
                byId.put(layer.id(), unmasked);
                warnings.add("Masks that do not refer to a layer beside the masked one were removed", "mask",
                        "masks");
                changed = true;
            } else {
                users.merge(mask.layerId(), 1, Integer::sum);
            }
        }
        if (original.size() == kept.size()) {
            return changed;
        }
        // a layer that only masked layers that are gone was never drawn itself: it goes as well
        Deque<String> unused = new ArrayDeque<>();
        for (Layer layer : original) {
            if (layer.base().mask() != null && !byId.containsKey(layer.id())) {
                unused.push(layer.base().mask().layerId());
            }
        }
        Set<String> gone = new HashSet<>();
        while (!unused.isEmpty()) {
            String id = unused.pop();
            Layer source = byId.get(id);
            if (source == null || gone.contains(id) || users.getOrDefault(id, 0) > 0) {
                continue;
            }
            gone.add(id);
            Mask own = source.base().mask();
            if (own != null && users.merge(own.layerId(), -1, Integer::sum) <= 0) {
                unused.push(own.layerId());
            }
        }
        if (!gone.isEmpty()) {
            kept.removeIf(layer -> gone.contains(layer.id()));
            changed = true;
        }
        return changed;
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
    private List<Layer> without(List<Layer> layers, List<Integer> indices, int level, boolean maskOnly) {
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
            settle(layers, result);
        }
        return result;
    }
}
