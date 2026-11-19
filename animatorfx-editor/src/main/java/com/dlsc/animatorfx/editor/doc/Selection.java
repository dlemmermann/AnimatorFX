package com.dlsc.animatorfx.editor.doc;

import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * The selected layers (in selection order) and keyframes of a document, or its selected sound
 * cues. Cues are not selected together with layers or keyframes, and the selection sees to that
 * itself: adding a layer or a keyframe clears the cue selection and keeps what was added; adding
 * a cue clears the layers and the keyframes and keeps the cue.
 *
 * <p>The three collections are changed one after the other, so a listener may be called while
 * the old kind is not cleared yet (a cue together with layers, for one notification). The new
 * selection is in place before anything is cleared: a listener never sees an empty selection
 * in between, and the last notification of every change shows the final state.
 */
public final class Selection {

    public record KeyframeRef(String layerId, String propertyKey, String keyframeId) {

        public KeyframeRef {
            Objects.requireNonNull(layerId, "layerId");
            Objects.requireNonNull(propertyKey, "propertyKey");
            Objects.requireNonNull(keyframeId, "keyframeId");
        }
    }

    private final ObservableList<String> layerIds = FXCollections.observableArrayList();
    private final ObservableSet<KeyframeRef> keyframes = FXCollections.observableSet(new LinkedHashSet<>());
    private final ObservableSet<String> cueIds = FXCollections.observableSet(new LinkedHashSet<>());

    /** Set while the selection clears one kind for the other: those changes must not clear back. */
    private boolean exchanging;

    public Selection() {
        InvalidationListener layersOrKeyframes = obs -> {
            if (!exchanging && !(layerIds.isEmpty() && keyframes.isEmpty()) && !cueIds.isEmpty()) {
                exchange(cueIds::clear);
            }
        };
        layerIds.addListener(layersOrKeyframes);
        keyframes.addListener(layersOrKeyframes);
        cueIds.addListener((InvalidationListener) obs -> {
            if (!exchanging && !cueIds.isEmpty() && !(layerIds.isEmpty() && keyframes.isEmpty())) {
                exchange(() -> {
                    keyframes.clear();
                    layerIds.clear();
                });
            }
        });
    }

    private void exchange(Runnable clearing) {
        exchanging = true;
        try {
            clearing.run();
        } finally {
            exchanging = false;
        }
    }

    public ObservableList<String> layerIds() {
        return layerIds;
    }

    public ObservableSet<KeyframeRef> keyframes() {
        return keyframes;
    }

    /** The ids of the selected sound cues. */
    public ObservableSet<String> cueIds() {
        return cueIds;
    }

    public void clear() {
        layerIds.clear();
        keyframes.clear();
        cueIds.clear();
    }
}
