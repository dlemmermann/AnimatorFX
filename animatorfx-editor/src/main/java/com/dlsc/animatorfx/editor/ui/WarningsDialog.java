package com.dlsc.animatorfx.editor.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

import java.util.List;

/**
 * Lists the warnings of an import, one per line, as text that can be selected and copied.
 */
final class WarningsDialog {

    private WarningsDialog() {
    }

    static void show(Window owner, Theme theme, String title, List<String> warnings) {
        TextArea text = new TextArea(String.join("\n", warnings));
        text.setId("import-warnings");
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefColumnCount(48);
        text.setPrefRowCount(Math.clamp(warnings.size() + 1, 4, 16));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Import Warnings");
        dialog.setHeaderText(title);
        dialog.setResizable(true);
        dialog.getDialogPane().setContent(text);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.initOwner(owner);
        theme.install(dialog.getDialogPane().getScene());
        try {
            dialog.showAndWait();
        } finally {
            theme.uninstall(dialog.getDialogPane().getScene());
        }
    }
}
