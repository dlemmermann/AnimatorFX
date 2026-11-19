package com.dlsc.animatorfx.editor.doc;

import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * The selected layers (in selection order) and keyframes of a document, or its selected sound
 * cues. Layers and cues are not selected together: selecting a layer clears the cue selection,
 * selecting a cue clears the layers and their keyframes.
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
        layerIds.addListener((InvalidationListener) obs -> {
            if (!layerIds.isEmpty() && !cueIds.isEmpty()) {
                cueIds.clear();
            }
        });
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
