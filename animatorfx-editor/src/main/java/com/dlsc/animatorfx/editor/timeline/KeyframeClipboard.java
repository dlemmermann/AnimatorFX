package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.editor.doc.Commands.KeyframeCopy;
import java.util.List;

/**
 * The in-application clipboard for keyframes.
 */
public final class KeyframeClipboard {

    private static final KeyframeClipboard SHARED = new KeyframeClipboard();

    private List<KeyframeCopy> copies = List.of();

    /** The clipboard that all windows and documents of the application share. */
    public static KeyframeClipboard shared() {
        return SHARED;
    }

    public void set(List<KeyframeCopy> copies) {
        this.copies = List.copyOf(copies);
    }

    public List<KeyframeCopy> get() {
        return copies;
    }

    public boolean isEmpty() {
        return copies.isEmpty();
    }
}
