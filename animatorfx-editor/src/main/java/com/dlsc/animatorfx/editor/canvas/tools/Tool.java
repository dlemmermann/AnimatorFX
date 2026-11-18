package com.dlsc.animatorfx.editor.canvas.tools;

import javafx.scene.Cursor;
import javafx.scene.input.KeyEvent;

/**
 * What the canvas does with the pointer and the keyboard. A tool is registered for a
 * {@link com.dlsc.animatorfx.editor.ui.ToolKind} with
 * {@link com.dlsc.animatorfx.editor.canvas.CanvasView#setTool}, or takes over temporarily as
 * the modal tool ({@link com.dlsc.animatorfx.editor.canvas.CanvasView#setModalTool}), which is
 * how an editing mode such as path editing plugs in.
 *
 * <p>A tool gets the canvas in its constructor. It changes the document only through
 * {@code executeCoalesced} during a drag and {@code endGesture} or {@code cancelGesture} at its end,
 * and draws its feedback through the canvas overlay. All methods have empty defaults.
 */
public interface Tool {

    /** The tool became the one that receives input. */
    default void activate() {
    }

    /**
     * Wraps up whatever is in progress: the tool is being replaced, or the document changes.
     * A drag is ended as if the button had been released, an inline editor commits.
     */
    default void finish() {
    }

    /** The primary button went down. */
    default void pressed(Pointer pointer) {
    }

    default void dragged(Pointer pointer) {
    }

    default void released(Pointer pointer) {
    }

    /** The pointer moved without a button. */
    default void moved(Pointer pointer) {
    }

    /** The pointer left the canvas. */
    default void exited() {
    }

    /**
     * A key was pressed while no text input had the focus.
     *
     * @return whether the tool used the key
     */
    default boolean keyPressed(KeyEvent event) {
        return false;
    }

    /**
     * Escape: abandons the gesture or editor in progress and restores the document.
     *
     * @return whether there was something to cancel
     */
    default boolean cancel() {
        return false;
    }

    /** The cursor to show at the pointer. */
    default Cursor cursor(Pointer pointer) {
        return Cursor.DEFAULT;
    }

    /**
     * The canvas was laid out: zoom, pan, size or the frame may have changed. Inline controls
     * reposition themselves here. Called for every registered tool and the modal tool, also
     * while they are not the one receiving input.
     */
    default void viewChanged() {
    }
}
