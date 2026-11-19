package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.editor.audio.AudioImport;
import com.dlsc.animatorfx.editor.doc.Command;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.SoundCue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The sound cue edits that the cue lane, the timeline header and the marker menu share.
 */
public final class Cues {

    private Cues() {
    }

    /** A cue time: on a frame unless {@code free}, and inside the document. */
    public static double snapped(Animation animation, double time, boolean free) {
        double snapped = free ? time : TimeScale.snap(time, animation.frameRate());
        return Math.clamp(snapped, 0, Math.max(0, animation.duration()));
    }

    /** The audio assets of a document, in the order of its asset list. */
    public static List<Asset> audioAssets(Animation animation) {
        return animation.assets().stream().filter(asset -> asset.kind() == Asset.Kind.AUDIO).toList();
    }

    /**
     * The sound a new cue gets when nothing else is said: the one used last if the document
     * still has it, else the sound of the newest cue, else of the top audio layer, else the
     * newest audio asset. Empty for a document without sounds.
     */
    public static Optional<String> defaultAsset(Animation animation, String lastUsedOrNull) {
        if (lastUsedOrNull != null && Commands.isAudioAsset(animation, lastUsedOrNull)) {
            return Optional.of(lastUsedOrNull);
        }
        if (!animation.cues().isEmpty()) {
            return Optional.of(animation.cues().get(animation.cues().size() - 1).assetId());
        }
        String fromLayer = null;
        for (Layer layer : Layers.flatten(animation)) {
            if (layer instanceof AudioLayer audio) {
                fromLayer = audio.assetId();
            }
        }
        if (fromLayer != null) {
            return Optional.of(fromLayer);
        }
        List<Asset> sounds = audioAssets(animation);
        return sounds.isEmpty() ? Optional.empty() : Optional.of(sounds.get(sounds.size() - 1).id());
    }

    /** Adds a cue for an audio asset at the frame of a time, named after the sound. */
    public static Command add(String cueId, String assetId, double time) {
        return Commands.setDocument("Add Cue", a -> {
            String name = a.assets().stream().filter(asset -> asset.id().equals(assetId))
                    .map(asset -> AudioImport.baseName(asset.name())).findFirst().orElse("Cue");
            return Commands.addCue(new SoundCue(cueId, name, snapped(a, time, false), assetId, 1, false)).apply(a);
        });
    }

    /**
     * Moves cues by a time span. The first one lands on a frame unless {@code free}; the others
     * keep their distance to it; all of them stay inside the document.
     */
    public static Command move(Collection<String> cueIds, double delta, boolean free) {
        List<String> ids = List.copyOf(cueIds);
        return Commands.setDocument(ids.size() == 1 ? "Move Cue" : "Move Cues", a -> {
            List<SoundCue> moving = new ArrayList<>();
            for (String id : ids) {
                a.cues().stream().filter(cue -> cue.id().equals(id)).findFirst().ifPresent(moving::add);
            }
            if (moving.isEmpty() || !Double.isFinite(delta)) {
                return a;
            }
            double first = moving.get(0).time();
            double shift = snapped(a, first + delta, free) - first;
            double earliest = moving.stream().mapToDouble(SoundCue::time).min().orElse(0);
            double latest = moving.stream().mapToDouble(SoundCue::time).max().orElse(0);
            shift = Math.max(shift, -earliest);
            shift = Math.min(shift, Math.max(0, a.duration() - latest));
            double by = shift;
            List<SoundCue> cues = new ArrayList<>();
            for (SoundCue cue : a.cues()) {
                cues.add(ids.contains(cue.id()) ? cue.withTime(Math.clamp(cue.time() + by, 0, Math.max(0, a.duration()))) : cue);
            }
            return a.withCues(cues);
        });
    }
}
