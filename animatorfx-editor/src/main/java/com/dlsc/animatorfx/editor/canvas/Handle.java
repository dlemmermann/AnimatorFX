package com.dlsc.animatorfx.editor.canvas;

/**
 * The eight scale handles of a selection box, named by compass direction for an unrotated box.
 */
public enum Handle {

    NW(0, 0), N(0.5, 0), NE(1, 0), E(1, 0.5), SE(1, 1), S(0.5, 1), SW(0, 1), W(0, 0.5);

    private final double fx;
    private final double fy;

    Handle(double fx, double fy) {
        this.fx = fx;
        this.fy = fy;
    }

    /** Where the handle sits along the width of the box: 0 left, 0.5 middle, 1 right. */
    public double fx() {
        return fx;
    }

    /** Where the handle sits along the height of the box: 0 top, 0.5 middle, 1 bottom. */
    public double fy() {
        return fy;
    }

    public boolean movesX() {
        return fx != 0.5;
    }

    public boolean movesY() {
        return fy != 0.5;
    }

    public boolean isCorner() {
        return movesX() && movesY();
    }

    /**
     * The handle across the box, which stays put while this one is dragged.
     */
    public Handle opposite() {
        return values()[(ordinal() + 4) % 8];
    }
}
