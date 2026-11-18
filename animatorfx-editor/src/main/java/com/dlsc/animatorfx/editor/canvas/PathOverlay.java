package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.canvas.PathOps.VertexRef;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.player.render.PathConverter;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Draws a Bezier path for editing, in view pixels: its outline, a small square on every vertex
 * (filled when selected) and, for the selected vertices, their two round tangent handles with
 * the lines that connect them. The pen tool also shows the segment that the next click would
 * add and a ring around the first vertex when a click there would close the path.
 *
 * <p>A tool adds one to the canvas overlay with
 * {@link Overlay#addDecoration(Node, Runnable)} and calls {@link #show} from the update function.
 * The nodes are pooled; all styling is by style class ({@code path-outline}, {@code path-vertex},
 * {@code path-tangent-line}, {@code path-tangent-handle}, {@code path-rubber-band},
 * {@code path-close-ring}, {@code path-insert-point}).
 */
public final class PathOverlay extends Group {

    /** The side of a vertex square: the size of the selection handles. */
    public static final double VERTEX_SIZE = 7;
    public static final double HANDLE_RADIUS = 3.5;

    private static final String SELECTED = "selected";

    private final Path outline = new Path();
    private final Path rubberBand = new Path();
    private final Group lines = new Group();
    private final Group squares = new Group();
    private final Group handles = new Group();
    private final Circle closeRing = new Circle(8);
    private final Circle insertPoint = new Circle(3.5);
    private final List<Line> linePool = new ArrayList<>();
    private final List<Rectangle> squarePool = new ArrayList<>();
    private final List<Circle> handlePool = new ArrayList<>();

    public PathOverlay() {
        outline.getStyleClass().add("path-outline");
        rubberBand.getStyleClass().add("path-rubber-band");
        closeRing.getStyleClass().add("path-close-ring");
        insertPoint.getStyleClass().add("path-insert-point");
        setMouseTransparent(true);
        getChildren().addAll(outline, rubberBand, lines, squares, handles, closeRing, insertPoint);
        clear();
    }

    /**
     * Shows a path whose coordinates are view pixels.
     *
     * @param selected    the vertices drawn filled and with their handles
     * @param withOutline whether to trace the outline; off when the vertices alone are wanted
     */
    public void show(PathData viewPath, Collection<VertexRef> selected, boolean withOutline) {
        outline.setVisible(withOutline);
        if (withOutline) {
            PathConverter.write(viewPath, outline.getElements());
        }
        int squareCount = 0;
        int lineCount = 0;
        int handleCount = 0;
        for (int c = 0; c < viewPath.contours().size(); c++) {
            List<PathData.Vertex> vertices = viewPath.contours().get(c).vertices();
            for (int i = 0; i < vertices.size(); i++) {
                PathData.Vertex vertex = vertices.get(i);
                boolean isSelected = selected.contains(new VertexRef(c, i));
                Rectangle square = pooled(squarePool, squares, squareCount++, PathOverlay::newSquare);
                square.setX(Math.rint(vertex.point().x() - VERTEX_SIZE / 2) + 0.5);
                square.setY(Math.rint(vertex.point().y() - VERTEX_SIZE / 2) + 0.5);
                setSelected(square, isSelected);
                if (!isSelected) {
                    continue;
                }
                for (Vec2 tangent : List.of(vertex.in(), vertex.out())) {
                    if (Math.hypot(tangent.x(), tangent.y()) < 0.5) {
                        continue;
                    }
                    Vec2 end = vertex.point().plus(tangent);
                    Line line = pooled(linePool, lines, lineCount++, PathOverlay::newLine);
                    line.setStartX(vertex.point().x());
                    line.setStartY(vertex.point().y());
                    line.setEndX(end.x());
                    line.setEndY(end.y());
                    Circle handle = pooled(handlePool, handles, handleCount++, PathOverlay::newHandle);
                    handle.setCenterX(end.x());
                    handle.setCenterY(end.y());
                }
            }
        }
        hideFrom(squarePool, squareCount);
        hideFrom(linePool, lineCount);
        hideFrom(handlePool, handleCount);
    }

    /** The segment that the next click would add, as a path in view pixels; {@code null} for none. */
    public void setRubberBand(PathData viewSegment) {
        rubberBand.setVisible(viewSegment != null);
        if (viewSegment != null) {
            PathConverter.write(viewSegment, rubberBand.getElements());
        }
    }

    /** Where a click would close the path; {@code null} for nowhere. */
    public void setCloseTarget(Vec2 viewPoint) {
        place(closeRing, viewPoint);
    }

    /** Where a click would insert a vertex; {@code null} for nowhere. */
    public void setInsertPoint(Vec2 viewPoint) {
        place(insertPoint, viewPoint);
    }

    public void clear() {
        outline.setVisible(false);
        rubberBand.setVisible(false);
        closeRing.setVisible(false);
        insertPoint.setVisible(false);
        hideFrom(squarePool, 0);
        hideFrom(linePool, 0);
        hideFrom(handlePool, 0);
    }

    /** How many vertices are showing. */
    public int getVertexCount() {
        return (int) squarePool.stream().filter(Node::isVisible).count();
    }

    /** How many tangent handles are showing. */
    public int getHandleCount() {
        return (int) handlePool.stream().filter(Node::isVisible).count();
    }

    public boolean isCloseTargetShown() {
        return closeRing.isVisible();
    }

    public boolean isInsertPointShown() {
        return insertPoint.isVisible();
    }

    private static void place(Circle circle, Vec2 at) {
        circle.setVisible(at != null);
        if (at != null) {
            circle.setCenterX(at.x());
            circle.setCenterY(at.y());
        }
    }

    private static Rectangle newSquare() {
        Rectangle square = new Rectangle(VERTEX_SIZE, VERTEX_SIZE);
        square.getStyleClass().add("path-vertex");
        return square;
    }

    private static Line newLine() {
        Line line = new Line();
        line.getStyleClass().add("path-tangent-line");
        return line;
    }

    private static Circle newHandle() {
        Circle circle = new Circle(HANDLE_RADIUS);
        circle.getStyleClass().add("path-tangent-handle");
        return circle;
    }

    private static void setSelected(Node node, boolean selected) {
        boolean has = node.getStyleClass().contains(SELECTED);
        if (selected && !has) {
            node.getStyleClass().add(SELECTED);
        } else if (!selected && has) {
            node.getStyleClass().remove(SELECTED);
        }
    }

    private static <T extends Node> T pooled(List<T> pool, Group parent, int index, Supplier<T> factory) {
        while (pool.size() <= index) {
            T node = factory.get();
            pool.add(node);
            parent.getChildren().add(node);
        }
        T node = pool.get(index);
        node.setVisible(true);
        return node;
    }

    private static void hideFrom(List<? extends Node> pool, int count) {
        for (int i = count; i < pool.size(); i++) {
            pool.get(i).setVisible(false);
        }
    }
}
