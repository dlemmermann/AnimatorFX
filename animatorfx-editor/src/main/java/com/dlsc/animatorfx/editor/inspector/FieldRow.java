package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.inspector.fields.KeyframeToggle;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * One row of an inspector section: a label, an editor that takes the remaining width, and the
 * place of the keyframe diamond at the right, which stays empty for values that cannot be
 * animated so that all editors line up.
 */
public final class FieldRow extends HBox {

    private final Label label;

    public FieldRow(String text, Node editor, KeyframeToggle toggleOrNull) {
        getStyleClass().add("field-row");
        setAlignment(Pos.TOP_LEFT);
        label = new Label(text);
        label.getStyleClass().add("field-label");
        HBox.setHgrow(editor, Priority.ALWAYS);
        Node slot = toggleOrNull;
        if (slot == null) {
            Region spacer = new Region();
            spacer.getStyleClass().add("keyframe-slot");
            slot = spacer;
        }
        getChildren().addAll(label, editor, slot);
    }

    public Label getLabel() {
        return label;
    }

    /** Shows or hides the row; a hidden row takes no space. */
    public void show(boolean visible) {
        setVisible(visible);
        setManaged(visible);
    }
}
