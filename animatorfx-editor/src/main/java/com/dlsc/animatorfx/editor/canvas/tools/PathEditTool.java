package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.Box;
import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.canvas.Handles;
import com.dlsc.animatorfx.editor.canvas.PathOps;
import com.dlsc.animatorfx.editor.canvas.PathOps.Tangent;
import com.dlsc.animatorfx.editor.canvas.PathOps.TangentMode;
import com.dlsc.animatorfx.editor.canvas.PathOps.VertexRef;
import com.dlsc.animatorfx.editor.canvas.PathOverlay;
import com.dlsc.animatorfx.editor.canvas.Snapper;
import com.dlsc.animatorfx.editor.canvas.TransformMath;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.Matrices;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.PathData.Vertex;
import com.dlsc.animatorfx.model.Vec2;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Path editing, a modal tool: the vertices of a path are shown as small squares and edited
 * directly.
 *
 * <ul>
 * <li>Click selects a vertex, shift-click adds or removes one, a drag on empty canvas selects
 * with a marquee.</li>
 * <li>Dragging a vertex moves the selected vertices, snapping to the artboard and other layers
 * (Control: off; shift: one axis). Arrow keys nudge by 1 px, with shift by 10 px.</li>
 * <li>A selected vertex shows its tangent handles. Dragging one keeps a smooth vertex smooth;
 * alt moves the one handle alone; shift keeps it on 45° steps.</li>
 * <li>Alt-click turns a corner into a smooth vertex and back.</li>
 * <li>A click on the outline inserts a vertex there, which can be dragged on at once.</li>
 * <li>Delete removes the selected vertices. The context menu closes or opens the contour.</li>
 * <li>Escape, Enter or a double-click on empty canvas leaves the mode.</li>
 * </ul>
 *
 * Every gesture is one undo step. What is edited is a {@link PathTarget}: the outline of a path
 * layer, written at the playhead under the auto-key rule (so editing at a second time makes the
 * shape morph), or the motion path of a layer.
 */
public final class PathEditTool implements Tool {

    private static final double GRAB = Handles.GRAB;
    private static final double SEGMENT_GRAB = 5;
    private static final double DRAG_THRESHOLD = 3;
    private static final double SNAP_DISTANCE = 6;

    private enum Mode {IDLE, PENDING, MOVE, TANGENT, MARQUEE}

    /** What is under the pointer. */
    private record Target(VertexRef vertex, VertexRef handleOf, Tangent tangent, PathOps.Hit segment) {

        static final Target NONE = new Target(null, null, null, null);

        boolean isEmpty() {
            return vertex == null && handleOf == null && segment == null;
        }
    }

    private final CanvasView canvas;
    private final PathTarget target;
    private final PathOverlay overlay = new PathOverlay();
    private final Set<VertexRef> selected = new LinkedHashSet<>();

    private boolean active;
    private boolean leaving;
    private Mode mode = Mode.IDLE;
    private Pointer start;
    private Object gestureKey;
    private PathData startPath;
    private double[] viewToPath;
    private VertexRef grabbed;
    private Vec2 grabbedView;
    private Tangent tangent;
    private TangentMode tangentMode;
    private boolean inserted;
    private Set<VertexRef> marqueeBase = Set.of();
    private List<Box> snapTargets = List.of();
    private Vec2 insertPoint;

    public PathEditTool(CanvasView canvas, PathTarget target) {
        this.canvas = canvas;
        this.target = target;
    }

    public PathTarget getTarget() {
        return target;
    }

    /** The selected vertices. */
    public Set<VertexRef> getSelection() {
        return Set.copyOf(selected);
    }

    public void select(Set<VertexRef> vertices) {
        selected.clear();
        selected.addAll(vertices);
        canvas.requestLayout();
    }

    /** The drawing of the path above the canvas. */
    public PathOverlay getOverlay() {
        return overlay;
    }

    private EditorDocument doc() {
        return canvas.getDocument();
    }

    // ------------------------------------------------------------------ life cycle

    @Override
    public void activate() {
        if (!active) {
            active = true;
            canvas.getOverlay().addDecoration(overlay, this::refresh);
            canvas.getOverlay().setSelectionHidden(true);
            canvas.getOverlay().setHover(null);
            canvas.getOverlay().setModeHint(target instanceof PathTarget.Motion ? "Motion path" : "Path editing",
                    "Click a segment to add a point \u00b7 Alt-click: corner / smooth \u00b7 Esc: done");
        }
    }

    @Override
    public void finish() {
        if (!active) {
            return;
        }
        active = false;
        if (isEditing() && doc() != null) {
            doc().endGesture();
        }
        reset();
        canvas.getOverlay().removeDecoration(overlay);
        canvas.getOverlay().setSelectionHidden(false);
        canvas.getOverlay().setModeHint(null, null);
    }

    private void leave() {
        canvas.setModalTool(null);
    }

    /** Draws the path; leaves the mode when the path has gone or its layer is no longer selected. */
    private void refresh() {
        PathData path = doc() == null ? null : target.read(canvas).orElse(null);
        double[] toView = path == null ? null : target.toView(canvas);
        if (toView == null || !doc().getSelection().layerIds().contains(target.layerId())) {
            overlay.clear();
            if (!leaving) {
                // not from here: this runs during the layout of the canvas
                leaving = true;
                Platform.runLater(() -> {
                    if (canvas.getModalTool() == this) {
                        leave();
                    }
                });
            }
            return;
        }
        selected.removeIf(ref -> PathOps.vertex(path, ref).isEmpty());
        overlay.show(PathOps.transform(path, toView), selected, true);
        overlay.setInsertPoint(mode == Mode.IDLE ? insertPoint : null);
    }

    // ------------------------------------------------------------------ hit testing

    private Target targetAt(PathData viewPath, Vec2 point) {
        for (VertexRef ref : selected) {
            Vertex vertex = PathOps.vertex(viewPath, ref).orElse(null);
            if (vertex == null) {
                continue;
            }
            for (Tangent which : Tangent.values()) {
                Vec2 relative = which == Tangent.IN ? vertex.in() : vertex.out();
                if (Math.hypot(relative.x(), relative.y()) >= 0.5
                        && Handles.distance(vertex.point().plus(relative), point) <= GRAB) {
                    return new Target(null, ref, which, null);
                }
            }
        }
        VertexRef nearest = null;
        double best = GRAB;
        for (int c = 0; c < viewPath.contours().size(); c++) {
            List<Vertex> vertices = viewPath.contours().get(c).vertices();
            for (int i = 0; i < vertices.size(); i++) {
                double distance = Handles.distance(vertices.get(i).point(), point);
                if (distance <= best) {
                    best = distance;
                    nearest = new VertexRef(c, i);
                }
            }
        }
        if (nearest != null) {
            return new Target(nearest, null, null, null);
        }
        Optional<PathOps.Hit> segment = PathOps.nearest(viewPath, point).filter(hit -> hit.distance() <= SEGMENT_GRAB);
        return segment.map(hit -> new Target(null, null, null, hit)).orElse(Target.NONE);
    }

    private PathData viewPath() {
        PathData path = target.read(canvas).orElse(null);
        double[] toView = path == null ? null : target.toView(canvas);
        return toView == null ? null : PathOps.transform(path, toView);
    }

    // ------------------------------------------------------------------ press

    @Override
    public void pressed(Pointer p) {
        // playback ends what is in progress with finish(), and the tool stays the one in charge
        activate();
        doc().endGesture();
        start = p;
        gestureKey = new Object();
        mode = Mode.IDLE;
        inserted = false;
        insertPoint = null;
        PathData path = target.read(canvas).orElse(null);
        double[] toView = path == null ? null : target.toView(canvas);
        viewToPath = toView == null ? null : Matrices.invert(toView);
        if (viewToPath == null) {
            return;
        }
        PathData view = PathOps.transform(path, toView);
        Target at = targetAt(view, p.view());
        startPath = path;
        snapTargets = canvas.getGeometry().snapTargets(Set.of(target.layerId()));

        if (at.handleOf() != null) {
            grabbed = at.handleOf();
            tangent = at.tangent();
            tangentMode = PathOps.dragMode(PathOps.vertex(path, grabbed).orElseThrow());
            mode = Mode.TANGENT;
        } else if (at.vertex() != null) {
            pressedVertex(p, path, view, at.vertex());
        } else if (at.segment() != null) {
            PathOps.Hit hit = at.segment();
            // the parameter of a point on a Bezier segment is the same in every affine space
            startPath = PathOps.insertVertex(path, hit.contour(), hit.segment(), hit.t());
            write(startPath);
            inserted = true;
            grabbed = new VertexRef(hit.contour(), hit.segment() + 1);
            grabbedView = hit.point();
            selected.clear();
            selected.add(grabbed);
            mode = Mode.PENDING;
        } else if (p.clicks() >= 2) {
            leave();
        } else {
            marqueeBase = p.shift() ? Set.copyOf(selected) : Set.of();
            mode = Mode.MARQUEE;
        }
    }

    private void pressedVertex(Pointer p, PathData path, PathData view, VertexRef vertex) {
        if (p.alt()) {
            doc().execute(target.write(canvas, PathOps.toggleSmooth(path, vertex)));
            selected.clear();
            selected.add(vertex);
            return;
        }
        if (p.shift()) {
            if (!selected.remove(vertex)) {
                selected.add(vertex);
            } else {
                return;
            }
        } else if (!selected.contains(vertex)) {
            selected.clear();
            selected.add(vertex);
        }
        grabbed = vertex;
        grabbedView = PathOps.vertex(view, vertex).orElseThrow().point();
        mode = Mode.PENDING;
    }

    private void write(PathData path) {
        doc().executeCoalesced(gestureKey, target.write(canvas, path));
    }

    // ------------------------------------------------------------------ drag

    @Override
    public void dragged(Pointer p) {
        if (mode == Mode.PENDING) {
            if (Handles.distance(start.view(), p.view()) < DRAG_THRESHOLD) {
                return;
            }
            mode = Mode.MOVE;
        }
        switch (mode) {
            case MOVE -> move(p);
            case TANGENT -> dragTangent(p);
            case MARQUEE -> marquee(p);
            default -> {
            }
        }
    }

    private void move(Pointer p) {
        Vec2 delta = p.view().minus(start.view());
        if (p.shift()) {
            delta = TransformMath.constrainAxis(delta);
        }
        List<Snapper.Guide> guides = List.of();
        if (!p.snapOff()) {
            Vec2 at = grabbedView.plus(delta);
            boolean freeX = !p.shift() || delta.y() == 0;
            boolean freeY = !p.shift() || delta.x() == 0;
            Snapper.Result result = Snapper.snapPoint(at.x(), at.y(), freeX, freeY, snapTargets, SNAP_DISTANCE);
            delta = delta.plus(new Vec2(result.dx(), result.dy()));
            guides = result.guides();
        }
        canvas.getOverlay().setGuides(guides);
        write(PathOps.moveVertices(startPath, selected, tidy(Matrices.applyLinear(viewToPath, delta))));
    }

    private static Vec2 tidy(Vec2 v) {
        return new Vec2(Math.rint(v.x() * 100) / 100, Math.rint(v.y() * 100) / 100);
    }

    private void dragTangent(Pointer p) {
        Vertex vertex = PathOps.vertex(startPath, grabbed).orElse(null);
        double[] toView = Matrices.invert(viewToPath);
        if (vertex == null || toView == null) {
            return;
        }
        Vec2 relativeView = p.view().minus(Matrices.apply(toView, vertex.point()));
        if (p.shift()) {
            relativeView = TransformMath.constrainAngle(relativeView, 45);
        }
        Vec2 relative = tidy(Matrices.applyLinear(viewToPath, relativeView));
        write(PathOps.setTangent(startPath, grabbed, tangent, relative, p.alt() ? TangentMode.BROKEN : tangentMode));
    }

    private void marquee(Pointer p) {
        Box box = Box.between(start.view(), p.view());
        canvas.getOverlay().setMarquee(box);
        PathData view = viewPath();
        selected.clear();
        selected.addAll(marqueeBase);
        if (view == null) {
            return;
        }
        for (int c = 0; c < view.contours().size(); c++) {
            List<Vertex> vertices = view.contours().get(c).vertices();
            for (int i = 0; i < vertices.size(); i++) {
                if (box.contains(vertices.get(i).point().x(), vertices.get(i).point().y())) {
                    selected.add(new VertexRef(c, i));
                }
            }
        }
    }

    // ------------------------------------------------------------------ end

    private boolean isEditing() {
        return mode == Mode.MOVE || mode == Mode.TANGENT || (mode == Mode.PENDING && inserted);
    }

    @Override
    public void released(Pointer p) {
        if (mode == Mode.PENDING && !inserted && !p.shift()) {
            selected.clear();
            selected.add(grabbed);
        }
        if (isEditing()) {
            // back where it started: nothing happened, also no keyframe from auto-key
            if (!inserted && p.view().equals(start.view())) {
                doc().cancelGesture();
            } else {
                doc().endGesture();
            }
        }
        reset();
    }

    @Override
    public boolean cancel() {
        if (mode == Mode.IDLE) {
            return false;
        }
        if (isEditing()) {
            doc().cancelGesture();
        } else if (mode == Mode.MARQUEE) {
            selected.clear();
            selected.addAll(marqueeBase);
        }
        reset();
        return true;
    }

    private void reset() {
        mode = Mode.IDLE;
        canvas.getOverlay().setGuides(List.of());
        canvas.getOverlay().setMarquee(null);
    }

    // ------------------------------------------------------------------ keys and menu

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isShortcutDown() || event.isAltDown() || mode != Mode.IDLE) {
            return false;
        }
        double step = event.isShiftDown() ? 10 : 1;
        switch (event.getCode()) {
            case ENTER -> leave();
            case DELETE, BACK_SPACE -> deleteSelected();
            case LEFT -> nudge(new Vec2(-step, 0));
            case RIGHT -> nudge(new Vec2(step, 0));
            case UP -> nudge(new Vec2(0, -step));
            case DOWN -> nudge(new Vec2(0, step));
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Removes the selected vertices as one undo step; the last two points of a path stay. */
    public void deleteSelected() {
        PathData path = target.read(canvas).orElse(null);
        if (path == null || selected.isEmpty()) {
            return;
        }
        PathData result = PathOps.deleteVertices(path, selected);
        if (result.contours().isEmpty()) {
            canvas.notice("A path needs at least two points. Leave path editing and delete the layer instead.");
            return;
        }
        doc().execute(target.write(canvas, result));
        selected.clear();
        canvas.requestLayout();
    }

    /** Moves the selected vertices by a distance in document pixels, without snapping. */
    private void nudge(Vec2 delta) {
        PathData path = target.read(canvas).orElse(null);
        double[] toView = path == null ? null : target.toView(canvas);
        double[] inverse = toView == null ? null : Matrices.invert(toView);
        if (inverse == null || selected.isEmpty()) {
            return;
        }
        Vec2 inPath = Matrices.applyLinear(inverse, delta.times(canvas.getZoom()));
        // consecutive nudges of the same vertices undo as one step
        doc().executeCoalesced(List.of("path-nudge", target, Set.copyOf(selected)),
                target.write(canvas, PathOps.moveVertices(path, selected, inPath)));
    }

    /** Closes or opens a contour, as one undo step. */
    public void setClosed(int contour, boolean closed) {
        target.read(canvas).ifPresent(path -> doc().execute(target.write(canvas, PathOps.setClosed(path, contour, closed))));
    }

    @Override
    public List<MenuItem> menuItems(Pointer p) {
        PathData path = target.read(canvas).orElse(null);
        PathData view = viewPath();
        if (path == null || view == null || mode != Mode.IDLE) {
            return List.of();
        }
        Target at = targetAt(view, p.view());
        List<MenuItem> items = new ArrayList<>();
        VertexRef vertex = at.vertex();
        if (vertex != null) {
            if (!selected.contains(vertex)) {
                select(Set.of(vertex));
            }
            boolean corner = PathOps.isCorner(PathOps.vertex(path, vertex).orElseThrow());
            items.add(item("path-menu-smooth", corner ? "Make Smooth" : "Make Corner",
                    () -> doc().execute(target.write(canvas, PathOps.toggleSmooth(path, vertex)))));
            items.add(item("path-menu-delete", selected.size() > 1 ? "Delete Points" : "Delete Point", this::deleteSelected));
        } else if (at.segment() != null) {
            PathOps.Hit hit = at.segment();
            items.add(item("path-menu-insert", "Add Point", () -> {
                doc().execute(target.write(canvas, PathOps.insertVertex(path, hit.contour(), hit.segment(), hit.t())));
                select(Set.of(new VertexRef(hit.contour(), hit.segment() + 1)));
            }));
        }
        int contour = vertex != null ? vertex.contour() : at.segment() != null ? at.segment().contour()
                : selected.isEmpty() ? 0 : selected.iterator().next().contour();
        // a motion path has a start and an end: it stays open
        if (contour < path.contours().size() && target instanceof PathTarget.Shape) {
            boolean closed = path.contours().get(contour).closed();
            if (!items.isEmpty()) {
                items.add(new SeparatorMenuItem());
            }
            MenuItem toggle = item("path-menu-close", closed ? "Open Path" : "Close Path", () -> setClosed(contour, !closed));
            toggle.setDisable(!closed && path.contours().get(contour).vertices().size() < 3);
            items.add(toggle);
        }
        items.add(new SeparatorMenuItem());
        items.add(item("path-menu-done", "Done Editing", this::leave));
        return items;
    }

    private static MenuItem item(String id, String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setId(id);
        item.setOnAction(e -> action.run());
        return item;
    }

    // ------------------------------------------------------------------ hover and cursor

    @Override
    public void moved(Pointer p) {
        PathData view = viewPath();
        Target at = view == null ? Target.NONE : targetAt(view, p.view());
        Vec2 point = at.segment() == null ? null : at.segment().point();
        if (point != null || insertPoint != null) {
            insertPoint = point;
            canvas.requestLayout();
        }
    }

    @Override
    public void exited() {
        if (insertPoint != null) {
            insertPoint = null;
            canvas.requestLayout();
        }
    }

    @Override
    public Cursor cursor(Pointer p) {
        if (mode == Mode.MOVE || mode == Mode.TANGENT) {
            return Cursor.MOVE;
        }
        PathData view = viewPath();
        Target at = view == null ? Target.NONE : targetAt(view, p.view());
        if (at.isEmpty()) {
            return Cursor.DEFAULT;
        }
        // the crosshair says: a click here adds a point
        return at.segment() != null ? Cursor.CROSSHAIR : Cursor.MOVE;
    }
}
