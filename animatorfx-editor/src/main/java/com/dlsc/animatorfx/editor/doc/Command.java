package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.model.Animation;

/**
 * A named, pure edit of a document. Returning the instance it was given means "nothing changed":
 * the document then records no undo step.
 */
public interface Command {

    /**
     * The name shown in the Undo and Redo menu items, for example "Move Layer".
     */
    String name();

    Animation apply(Animation before);
}
