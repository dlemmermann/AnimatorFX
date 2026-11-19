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
 * itself: selecting a layer or a keyframe clears the cue selection, selecting a cue clears the
 * layers and the keyframes.
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

    public Selection() {
        InvalidationListener layersOrKeyframes = obs -> {
            if (!(layerIds.isEmpty() && keyframes.isEmpty()) && !cueIds.isEmpty()) {
                cueIds.clear();
            }
        };
        layerIds.addListener(layersOrKeyframes);
        keyframes.addListener(layersOrKeyframes);
        cueIds.addListener((InvalidationListener) obs -> {
            if (!cueIds.isEmpty() && !(layerIds.isEmpty() && keyframes.isEmpty())) {
                keyframes.clear();
                layerIds.clear();
            }
        });
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
