package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.player.AnimationView;
import com.dlsc.animatorfx.player.Fit;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * The centre region. For now it only previews the document: the artboard, scaled down to fit, on
 * the grid background, showing the frame at the playhead. Zoom, pan, selection handles and the
 * tools are not here yet; they replace the content of this class, or the whole region through
 * {@link MainWindow#setCanvasContent}.
 */
public final class CanvasPane extends Region {

    private static final double MARGIN = 48;

    private final ObjectProperty<EditorDocument> document = new SimpleObjectProperty<>(this, "document");
    private final AnimationView view = new AnimationView();
    private final StackPane artboard = new StackPane(view);
    private final ChangeListener<Object> refresh = (obs, oldValue, newValue) -> {
        seek();
        requestLayout();
    };

    public CanvasPane() {
        getStyleClass().add("canvas-pane");
        artboard.getStyleClass().add("artboard");
        view.setFit(Fit.CONTAIN);
        view.setAutoPlay(false);
        getChildren().add(artboard);
        setMinSize(0, 0);

        document.addListener((obs, oldDoc, newDoc) -> {
            view.animationProperty().unbind();
            if (oldDoc != null) {
                oldDoc.playheadProperty().removeListener(refresh);
                oldDoc.animationProperty().removeListener(refresh);
            }
            if (newDoc != null) {
                view.animationProperty().bind(newDoc.fileContentProperty());
                newDoc.playheadProperty().addListener(refresh);
                newDoc.animationProperty().addListener(refresh);
            } else {
                view.setAnimation(null);
            }
            artboard.setVisible(newDoc != null);
            seek();
            requestLayout();
        });
        artboard.setVisible(false);
    }

    /**
     * The document that the canvas shows.
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

    private void seek() {
        EditorDocument doc = getDocument();
        if (doc != null) {
            view.seek(doc.getPlayhead());
        }
    }

    @Override
    protected void layoutChildren() {
        EditorDocument doc = getDocument();
        if (doc == null) {
            return;
        }
        Animation animation = doc.getAnimation();
        double availableWidth = Math.max(1, getWidth() - 2 * MARGIN);
        double availableHeight = Math.max(1, getHeight() - 2 * MARGIN);
        double scale = Math.min(1, Math.min(availableWidth / animation.width(), availableHeight / animation.height()));
        double width = Math.max(1, animation.width() * scale);
        double height = Math.max(1, animation.height() * scale);
        artboard.resizeRelocate(snapPositionX((getWidth() - width) / 2), snapPositionY((getHeight() - height) / 2),
                snapSizeX(width), snapSizeY(height));
    }
}
