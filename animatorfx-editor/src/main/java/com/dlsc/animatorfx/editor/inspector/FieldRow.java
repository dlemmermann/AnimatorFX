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

    /** The width of the label column. */
    public static final double LABEL_WIDTH = 62;
    /** The gap between label, editor and diamond. */
    public static final double SPACING = 6;
    /** The width of the keyframe diamond column. */
    public static final double DIAMOND_WIDTH = 16;

    private final Label label;
    private final HBox labelBox;

    public FieldRow(String text, Node editor, KeyframeToggle toggleOrNull) {
        getStyleClass().add("field-row");
        setAlignment(Pos.TOP_LEFT);
        label = new Label(text);
        label.getStyleClass().add("field-label");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinWidth(0);
        HBox.setHgrow(label, Priority.ALWAYS);
        labelBox = new HBox(label);
        labelBox.getStyleClass().add("field-label-box");
        labelBox.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(editor, Priority.ALWAYS);
        Node slot = toggleOrNull;
        if (slot == null) {
            Region spacer = new Region();
            spacer.getStyleClass().add("keyframe-slot");
            slot = spacer;
        }
        getChildren().addAll(labelBox, editor, slot);
    }

    public Label getLabel() {
        return label;
    }

    /** Puts a small control at the right end of the label column, such as the link toggle of scale. */
    public void setAccessory(Node accessory) {
        labelBox.getChildren().setAll(label, accessory);
    }

    /** Shows or hides the row; a hidden row takes no space. */
    public void show(boolean visible) {
        setVisible(visible);
        setManaged(visible);
    }
}
