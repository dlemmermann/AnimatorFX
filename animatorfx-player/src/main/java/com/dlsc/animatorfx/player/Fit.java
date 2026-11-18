package com.dlsc.animatorfx.player;

/**
 * How an {@link AnimationView} scales its document. The scale is always uniform and the document
 * is centred in the view.
 */
public enum Fit {

    /** The whole document is visible; the view may show empty space on two sides. */
    CONTAIN,

    /** The document covers the whole view; it may be cut off on two sides. */
    COVER,

    /** The document is shown at its own size. */
    NONE
}
