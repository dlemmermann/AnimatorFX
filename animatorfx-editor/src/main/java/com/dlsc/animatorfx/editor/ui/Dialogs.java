package com.dlsc.animatorfx.editor.ui;

import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The modal questions of the file actions. The application uses {@link SystemDialogs}; tests
 * answer them directly.
 */
public interface Dialogs {

    enum CloseChoice {SAVE, DISCARD, CANCEL}

    /**
     * @return the chosen {@code .afx} file; empty when cancelled
     */
    Optional<Path> chooseOpenFile(Window owner);

    /**
     * @return the file to save to; empty when cancelled
     */
    Optional<Path> chooseSaveFile(Window owner, String suggestedName);

    /**
     * Asks what to do with the unsaved changes of a document that is about to close.
     */
    CloseChoice confirmClose(Window owner, String documentTitle);

    /**
     * @return whether the unsaved changes may be thrown away
     */
    boolean confirmRevert(Window owner, String documentTitle);

    void showAbout(Window owner);
}
