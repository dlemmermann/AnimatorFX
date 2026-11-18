package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.model.Vec2;

/**
 * Where the pointer is during a canvas gesture, with the modifier keys that tools care about.
 *
 * @param view    the position in canvas view pixels
 * @param shift   constrain: keep the aspect ratio, one axis, 15° steps, a square
 * @param alt     work from the centre or anchor instead of the opposite corner
 * @param snapOff snapping is switched off for now (the Control key)
 * @param clicks  the click count of a press: 2 for a double-click
 */
public record Pointer(Vec2 view, boolean shift, boolean alt, boolean snapOff, int clicks) {

    public static Pointer at(double x, double y) {
        return new Pointer(new Vec2(x, y), false, false, false, 1);
    }
}
