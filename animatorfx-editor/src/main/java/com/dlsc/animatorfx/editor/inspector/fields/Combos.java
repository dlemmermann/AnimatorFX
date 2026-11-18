package com.dlsc.animatorfx.editor.inspector.fields;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

/**
 * The compact combo boxes of the inspector. Their prompt text stands in for a value that the
 * selection does not agree on ("Mixed") or that is not one of the choices ("Custom"), so it has
 * to show whenever the value is {@code null}, also after a value had been chosen.
 */
public final class Combos {

    private Combos() {
    }

    /** Styles the combo box and makes it show its prompt text whenever it has no value. */
    public static <T> void compact(ComboBox<T> box) {
        box.getStyleClass().add("compact-combo");
        ButtonCell<T> cell = new ButtonCell<>(box);
        box.setButtonCell(cell);
        box.promptTextProperty().addListener(obs -> cell.show());
        box.valueProperty().addListener(obs -> cell.show());
    }

    private static final class ButtonCell<T> extends ListCell<T> {

        private final ComboBox<T> box;

        ButtonCell(ComboBox<T> box) {
            this.box = box;
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            show();
        }

        void show() {
            T value = box.getValue();
            StringConverter<T> converter = box.getConverter();
            setGraphic(null);
            setText(value == null ? box.getPromptText() : converter != null ? converter.toString(value) : value.toString());
        }
    }
}
