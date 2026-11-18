package com.dlsc.animatorfx.player.render;

import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;

import java.util.List;

/**
 * Converts model paths to JavaFX path elements.
 */
public final class PathConverter {

    private PathConverter() {
    }

    /**
     * Makes {@code elements} describe {@code path}. Elements already in the list are updated in
     * place where their kind matches, so rewriting a path whose shape changed but whose structure
     * did not (the usual case while animating) creates no objects.
     */
    public static void write(PathData path, List<PathElement> elements) {
        int index = 0;
        for (PathData.Contour contour : path.contours()) {
            List<PathData.Vertex> vertices = contour.vertices();
            int count = vertices.size();
            if (count == 0) {
                continue;
            }
            moveTo(elements, index++, vertices.get(0).point());
            int segments = contour.closed() ? count : count - 1;
            for (int i = 0; i < segments; i++) {
                PathData.Vertex from = vertices.get(i);
                PathData.Vertex to = vertices.get((i + 1) % count);
                boolean straight = isZero(from.out()) && isZero(to.in());
                if (straight && i == count - 1) {
                    // the ClosePath draws the straight closing segment
                    continue;
                }
                if (straight) {
                    lineTo(elements, index++, to.point());
                } else {
                    cubicTo(elements, index++, from.point().plus(from.out()), to.point().plus(to.in()), to.point());
                }
            }
            if (contour.closed()) {
                if (index >= elements.size() || !(elements.get(index) instanceof ClosePath)) {
                    put(elements, index, new ClosePath());
                }
                index++;
            }
        }
        if (elements.size() > index) {
            elements.subList(index, elements.size()).clear();
        }
    }

    private static void moveTo(List<PathElement> elements, int index, Vec2 point) {
        if (index < elements.size() && elements.get(index) instanceof MoveTo move) {
            move.setX(point.x());
            move.setY(point.y());
        } else {
            put(elements, index, new MoveTo(point.x(), point.y()));
        }
    }

    private static void lineTo(List<PathElement> elements, int index, Vec2 point) {
        if (index < elements.size() && elements.get(index) instanceof LineTo line) {
            line.setX(point.x());
            line.setY(point.y());
        } else {
            put(elements, index, new LineTo(point.x(), point.y()));
        }
    }

    private static void cubicTo(List<PathElement> elements, int index, Vec2 control1, Vec2 control2, Vec2 point) {
        if (index < elements.size() && elements.get(index) instanceof CubicCurveTo cubic) {
            cubic.setControlX1(control1.x());
            cubic.setControlY1(control1.y());
            cubic.setControlX2(control2.x());
            cubic.setControlY2(control2.y());
            cubic.setX(point.x());
            cubic.setY(point.y());
        } else {
            put(elements, index, new CubicCurveTo(control1.x(), control1.y(), control2.x(), control2.y(),
                    point.x(), point.y()));
        }
    }

    private static void put(List<PathElement> elements, int index, PathElement element) {
        if (index < elements.size()) {
            elements.set(index, element);
        } else {
            elements.add(element);
        }
    }

    private static boolean isZero(Vec2 vector) {
        return vector.x() == 0 && vector.y() == 0;
    }
}
