package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.Group;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;

import java.util.ArrayList;
import java.util.List;

/**
 * The grid behind the artboard. Its lines sit on document coordinates and move with the pan, but
 * they are drawn in view pixels, one pixel wide at any zoom, and the spacing adapts so the lines
 * neither crowd nor thin out. Every fifth line is a major line.
 */
final class CanvasGrid extends Group {

    private static final double[] STEPS = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000};
    private static final double MIN_SPACING = 16;
    private static final int MAJOR_EVERY = 5;

    private final Path minor = new Path();
    private final Path major = new Path();
    private double lastWidth = -1;
    private double lastHeight = -1;
    private double lastZoom = -1;
    private Vec2 lastOrigin;

    CanvasGrid() {
        setManaged(false);
        setMouseTransparent(true);
        minor.getStyleClass().add("canvas-grid");
        major.getStyleClass().addAll("canvas-grid", "canvas-grid-major");
        getChildren().addAll(minor, major);
    }

    /** The distance between two lines in document pixels at the zoom. */
    static double step(double zoom) {
        for (double step : STEPS) {
            if (step * zoom >= MIN_SPACING) {
                return step;
            }
        }
        return STEPS[STEPS.length - 1];
    }

    void update(double width, double height, Vec2 origin, double zoom) {
        if (width == lastWidth && height == lastHeight && zoom == lastZoom && origin.equals(lastOrigin)) {
            return;
        }
        lastWidth = width;
        lastHeight = height;
        lastZoom = zoom;
        lastOrigin = origin;
        List<PathElement> minorLines = new ArrayList<>();
        List<PathElement> majorLines = new ArrayList<>();
        double spacing = step(zoom) * zoom;
        long first = (long) Math.ceil(-origin.x() / spacing);
        for (long i = first; origin.x() + i * spacing <= width; i++) {
            double x = Math.rint(origin.x() + i * spacing) + 0.5;
            List<PathElement> target = Math.floorMod(i, MAJOR_EVERY) == 0 ? majorLines : minorLines;
            target.add(new MoveTo(x, 0));
            target.add(new LineTo(x, height));
        }
        first = (long) Math.ceil(-origin.y() / spacing);
        for (long i = first; origin.y() + i * spacing <= height; i++) {
            double y = Math.rint(origin.y() + i * spacing) + 0.5;
            List<PathElement> target = Math.floorMod(i, MAJOR_EVERY) == 0 ? majorLines : minorLines;
            target.add(new MoveTo(0, y));
            target.add(new LineTo(width, y));
        }
        minor.getElements().setAll(minorLines);
        major.getElements().setAll(majorLines);
    }
}
