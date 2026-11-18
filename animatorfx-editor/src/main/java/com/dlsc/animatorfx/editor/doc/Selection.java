package com.dlsc.animatorfx.editor.doc;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * The selected layers (in selection order) and keyframes of a document.
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

    public ObservableList<String> layerIds() {
        return layerIds;
    }

    public ObservableSet<KeyframeRef> keyframes() {
        return keyframes;
    }

    public void clear() {
        layerIds.clear();
        keyframes.clear();
    }
}
