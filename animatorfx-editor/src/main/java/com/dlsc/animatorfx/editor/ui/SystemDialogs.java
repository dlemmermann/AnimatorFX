package com.dlsc.animatorfx.editor.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * File choosers and alerts of the platform, styled with the theme of the editor.
 */
public final class SystemDialogs implements Dialogs {

    private static final FileChooser.ExtensionFilter AFX =
            new FileChooser.ExtensionFilter("AnimatorFX documents (*.afx)", "*.afx");
    private static final String EXTENSION = ".afx";

    private final Theme theme;
    private Path lastDirectory;

    public SystemDialogs(Theme theme) {
        this.theme = theme;
    }

    @Override
    public Optional<Path> chooseOpenFile(Window owner) {
        FileChooser chooser = chooser("Open");
        return remember(chooser.showOpenDialog(owner));
    }

    @Override
    public Optional<Path> chooseSaveFile(Window owner, String suggestedName) {
        FileChooser chooser = chooser("Save As");
        chooser.setInitialFileName(suggestedName.endsWith(EXTENSION) ? suggestedName : suggestedName + EXTENSION);
        return remember(chooser.showSaveDialog(owner)).map(path -> path.getFileName().toString().contains(".")
                ? path : path.resolveSibling(path.getFileName() + EXTENSION));
    }

    @Override
    public CloseChoice confirmClose(Window owner, String documentTitle) {
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Your changes will be lost if you don't save them.",
                save, discard, cancel);
        alert.setTitle("Close Document");
        alert.setHeaderText("Save the changes to \"" + documentTitle + "\"?");
        ButtonType answer = show(alert, owner).orElse(cancel);
        return answer == save ? CloseChoice.SAVE : answer == discard ? CloseChoice.DISCARD : CloseChoice.CANCEL;
    }

    @Override
    public boolean confirmRevert(Window owner, String documentTitle) {
        ButtonType revert = new ButtonType("Revert", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "All changes since the last save will be lost.",
                revert, ButtonType.CANCEL);
        alert.setTitle("Revert");
        alert.setHeaderText("Revert \"" + documentTitle + "\" to the saved file?");
        return show(alert, owner).orElse(ButtonType.CANCEL) == revert;
    }

    @Override
    public void showAbout(Window owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "An animation editor for JavaFX.\nDocuments play back with the animatorfx-player library.",
                ButtonType.OK);
        alert.setTitle("About AnimatorFX");
        alert.setHeaderText("AnimatorFX");
        show(alert, owner);
    }

    private <T> Optional<T> show(Dialog<T> dialog, Window owner) {
        dialog.initOwner(owner);
        theme.install(dialog.getDialogPane().getScene());
        try {
            return dialog.showAndWait();
        } finally {
            theme.uninstall(dialog.getDialogPane().getScene());
        }
    }

    private FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(AFX);
        if (lastDirectory != null && Files.isDirectory(lastDirectory)) {
            chooser.setInitialDirectory(lastDirectory.toFile());
        }
        return chooser;
    }

    private Optional<Path> remember(File file) {
        if (file == null) {
            return Optional.empty();
        }
        Path path = file.toPath();
        lastDirectory = path.toAbsolutePath().getParent();
        return Optional.of(path);
    }
}
