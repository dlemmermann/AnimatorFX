package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Layers;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The bottom bar: document size, layer count, selection count and, at the right, the autosave state.
 */
public final class StatusBar extends HBox {

    private final ObjectProperty<EditorDocument> document = new SimpleObjectProperty<>(this, "document");
    private final StringProperty autosaveState = new SimpleStringProperty(this, "autosaveState", "");

    private final Label size = label("status-size");
    private final Label layers = label("status-layers");
    private final Label selection = label("status-selection");
    private final InvalidationListener update = obs -> update();

    public StatusBar() {
        getStyleClass().addAll("status-bar", "mono");
        Label autosave = label("status-autosave");
        autosave.textProperty().bind(autosaveState);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(size, layers, selection, spacer, autosave);

        document.addListener((obs, oldDoc, newDoc) -> {
            if (oldDoc != null) {
                oldDoc.animationProperty().removeListener(update);
                oldDoc.getSelection().layerIds().removeListener(update);
                oldDoc.getSelection().keyframes().removeListener(update);
            }
            if (newDoc != null) {
                newDoc.animationProperty().addListener(update);
                newDoc.getSelection().layerIds().addListener(update);
                newDoc.getSelection().keyframes().addListener(update);
            }
            update();
        });
        update();
    }

    /**
     * The document that the bar describes.
     *
     * @return the document property
     */
    public final ObjectProperty<EditorDocument> documentProperty() {
        return document;
    }

    public final EditorDocument getDocument() {
        return document.get();
    }

    public final void setDocument(EditorDocument value) {
        document.set(value);
    }

    /**
     * The text at the right end: "Saved", "Unsaved changes" or "Autosaved HH:mm".
     *
     * @return the autosave state property
     */
    public final StringProperty autosaveStateProperty() {
        return autosaveState;
    }

    public final String getAutosaveState() {
        return autosaveState.get();
    }

    public final void setAutosaveState(String value) {
        autosaveState.set(value);
    }

    private void update() {
        EditorDocument doc = getDocument();
        if (doc == null) {
            size.setText("");
            layers.setText("");
            selection.setText("");
            return;
        }
        Animation animation = doc.getAnimation();
        size.setText(Formats.size(animation.width(), animation.height()));
        layers.setText(Formats.count(Layers.flatten(animation).size(), "layer"));
        int keyframes = doc.getSelection().keyframes().size();
        selection.setText(Formats.selected(doc.getSelection().layerIds().size())
                + (keyframes == 0 ? "" : " · " + Formats.count(keyframes, "keyframe")));
    }

    private static Label label(String id) {
        Label label = new Label();
        label.setId(id);
        label.setMinWidth(USE_PREF_SIZE);
        return label;
    }
}
