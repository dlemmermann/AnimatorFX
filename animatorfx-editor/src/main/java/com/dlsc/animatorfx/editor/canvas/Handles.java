package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.model.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which handles a selection box shows, and which one is under the pointer. Works in view pixels.
 */
public final class Handles {

    /** How far outside the top edge the rotate handle sits. */
    public static final double ROTATE_DISTANCE = 22;
    /** A side shorter than this has no handle in its middle; the corners would crowd it. */
    public static final double MIN_SIDE = 28;
    /** How close the pointer has to be to grab a handle. */
    public static final double GRAB = 6;

    private Handles() {
    }

    public static List<Handle> visible(Quad quad) {
        List<Handle> handles = new ArrayList<>();
        for (Handle handle : Handle.values()) {
            boolean alongWidth = handle == Handle.N || handle == Handle.S;
            boolean alongHeight = handle == Handle.E || handle == Handle.W;
            if ((alongWidth && quad.width() < MIN_SIDE) || (alongHeight && quad.height() < MIN_SIDE)) {
                continue;
            }
            handles.add(handle);
        }
        return handles;
    }

    /**
     * The visible scale handle nearest to the point, if one is within grabbing distance.
     */
    public static Optional<Handle> at(Quad quad, Vec2 point) {
        Handle best = null;
        double bestDistance = GRAB;
        for (Handle handle : visible(quad)) {
            double distance = distance(quad.handle(handle), point);
            if (distance <= bestDistance) {
                best = handle;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isRotateHandle(Quad quad, Vec2 point) {
        return distance(quad.rotateHandle(ROTATE_DISTANCE), point) <= GRAB;
    }

    public static double distance(Vec2 a, Vec2 b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }
}
