package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.Commands.KeyframeCopy;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.Selection.KeyframeRef;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerProperties;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.PropertyRef;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The commands on the selected keyframes of the timeline document: delete, copy, paste at the
 * playhead, and easing. Each is one undo step and does nothing without a document.
 */
public final class KeyframeActions {

    private final TimelineView timeline;
    private KeyframeClipboard clipboard = KeyframeClipboard.shared();

    KeyframeActions(TimelineView timeline) {
        this.timeline = timeline;
    }

    private EditorDocument doc() {
        return timeline.getDocument();
    }

    private List<KeyframeRef> selected() {
        return doc() == null ? List.of() : List.copyOf(doc().getSelection().keyframes());
    }

    /** The clipboard for copy and paste; the application-wide one unless replaced. */
    public KeyframeClipboard getClipboard() {
        return clipboard;
    }

    public void setClipboard(KeyframeClipboard clipboard) {
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
    }

    public boolean hasSelection() {
        return !selected().isEmpty();
    }

    public void delete() {
        List<KeyframeRef> refs = selected();
        if (!refs.isEmpty()) {
            doc().execute(Commands.removeKeyframes(refs));
        }
    }

    /** @return whether there were keyframes to copy */
    public boolean copy() {
        List<KeyframeCopy> copies = doc() == null ? List.of() : Commands.copyKeyframes(doc().getAnimation(), selected());
        if (copies.isEmpty()) {
            return false;
        }
        clipboard.set(copies);
        return true;
    }

    /**
     * Pastes the copied keyframes onto the properties they came from, the earliest one at the
     * playhead, and selects them.
     */
    public void paste() {
        EditorDocument doc = doc();
        if (doc == null || clipboard.isEmpty()) {
            return;
        }
        double time = timeline.getTime();
        doc.execute(Commands.pasteKeyframes(clipboard.get(), time));
        Set<String> layers = new LinkedHashSet<>(doc.getSelection().layerIds());
        List<KeyframeRef> pasted = new ArrayList<>();
        for (KeyframeCopy copy : clipboard.get()) {
            Layer layer = Layers.find(doc.getAnimation(), copy.layerId()).orElse(null);
            if (layer == null) {
                continue;
            }
            for (PropertyRef property : LayerProperties.list(layer)) {
                if (property.key().equals(copy.propertyKey())) {
                    for (Keyframe<?> keyframe : property.value().keyframes()) {
                        if (Math.abs(keyframe.time() - (time + copy.offset())) < Animatable.TIME_EPSILON) {
                            pasted.add(new KeyframeRef(layer.id(), property.key(), keyframe.id()));
                            layers.add(layer.id());
                        }
                    }
                }
            }
        }
        if (!pasted.isEmpty()) {
            select(layers, pasted);
        }
    }

    public void setEasing(Easing easing) {
        List<KeyframeRef> refs = selected();
        if (!refs.isEmpty()) {
            doc().execute(Commands.setEasing(refs, easing));
        }
    }

    /**
     * Selects the keyframes, and their layers along with them: the layers first, because
     * selected keyframes always belong to selected layers.
     */
    public void select(Set<String> layerIds, List<KeyframeRef> keyframes) {
        EditorDocument doc = doc();
        if (!List.copyOf(layerIds).equals(List.copyOf(doc.getSelection().layerIds()))) {
            doc.getSelection().layerIds().setAll(layerIds);
        }
        Set<KeyframeRef> wanted = new LinkedHashSet<>(keyframes);
        doc.getSelection().keyframes().retainAll(wanted);
        doc.getSelection().keyframes().addAll(wanted);
    }
}
