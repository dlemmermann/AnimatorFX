package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * Moves a layer along a path. The point at {@code progress} ({@code 0..1}) replaces
 * {@code transform.position}; with {@code orientToPath} the path tangent angle is added to
 * {@code transform.rotation}.
 */
public record MotionPath(PathData path, Animatable<Double> progress, boolean orientToPath) {

    public MotionPath {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(progress, "progress");
    }

    public MotionPath withPath(PathData path) {
        return new MotionPath(path, progress, orientToPath);
    }

    public MotionPath withProgress(Animatable<Double> progress) {
        return new MotionPath(path, progress, orientToPath);
    }

    public MotionPath withOrientToPath(boolean orientToPath) {
        return new MotionPath(path, progress, orientToPath);
    }
}
