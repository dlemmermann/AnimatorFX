package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.Box;
import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.canvas.Handles;
import com.dlsc.animatorfx.editor.canvas.PathOps;
import com.dlsc.animatorfx.editor.canvas.PathOps.VertexRef;
import com.dlsc.animatorfx.editor.canvas.PathOverlay;
import com.dlsc.animatorfx.editor.canvas.Snapper;
import com.dlsc.animatorfx.editor.canvas.TransformMath;
import com.dlsc.animatorfx.editor.doc.Command;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.Matrices;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.PathData.Contour;
import com.dlsc.animatorfx.model.PathData.Vertex;
import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.Cursor;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Drawing a Bezier path point by point, shared by the pen tool and the motion-path tool.
 *
 * <ul>
 * <li>A click adds a corner; pressing and dragging adds a smooth vertex whose handles follow the
 * drag, mirrored. With alt only the outgoing handle follows. Shift keeps the new segment, or the
 * handle, on 45° steps.</li>
 * <li>Where closing is allowed, the first vertex shows a ring when the pointer is over it, and a
 * click there closes the path and finishes.</li>
 * <li>Enter or a double-click finishes an open path. Backspace removes the last vertex. Escape
 * finishes too, or drops a path that has fewer than two vertices.</li>
 * <li>New vertices snap to the artboard and to other layers; Control switches that off.</li>
 * </ul>
 *
 * The vertices are kept in the coordinates of a parent group. A subclass says which, and turns
 * the vertices into a command; everything it writes while the path is drawn is one undo step.
 */
abstract class PathDrawTool implements Tool {

    private static final double DRAG_THRESHOLD = 3;
    private static final double SNAP_DISTANCE = 6;
    private static final double CLOSE_DISTANCE = 8;

    protected final CanvasView canvas;
    private final PathOverlay overlay = new PathOverlay();
    private final List<Vertex> vertices = new ArrayList<>();

    private boolean attached;
    private boolean drawing;
    private boolean pressing;
    private boolean closing;
    private boolean dragging;
    private boolean written;
    private String parentId;
    private Object gestureKey;
    private Vec2 pressedAt;
    private Vec2 keptIn = Vec2.ZERO;
    private Pointer hover;
    private List<Box> snapTargets = List.of();

    PathDrawTool(CanvasView canvas) {
        this.canvas = canvas;
    }

    // ------------------------------------------------------------------ what subclasses decide

    /** The group in whose coordinates the path is drawn; {@code null} for the document. Asked when a path starts. */
    protected abstract String parentId();

    /** Whether a click on the first vertex closes the path. */
    protected abstract boolean canClose();

    /**
     * The command that puts the path as drawn so far into the document, or {@code null} while
     * there is nothing to show yet.
     *
     * @param vertices in the coordinates of the parent
     * @param finished whether this is the finished path
     */
    protected abstract Command command(List<Vertex> vertices, boolean closed, boolean finished);

    /** Whether what was written is still in the document; an undo in the middle takes it away. */
    protected abstract boolean exists();

    /**
     * The path is finished and written.
     *
     * @param replaced whether this happens because another tool is taking over, which then must
     *                 not be overridden
     */
    protected abstract void finished(boolean replaced);

    /** The name of the mode for the hint at the top of the canvas. */
    protected abstract String modeName();

    /** The path was dropped; nothing of it is left in the document. */
    protected void dropped(boolean replaced) {
    }

    // ------------------------------------------------------------------ state

    /** Whether a path is being drawn. */
    public final boolean isDrawing() {
        return drawing;
    }

    /** How many vertices the path in progress has. */
    public final int getVertexCount() {
        return vertices.size();
    }

    /** The drawing of the path in progress above the canvas. */
    public final PathOverlay getOverlay() {
        return overlay;
    }

    private EditorDocument doc() {
        return canvas.getDocument();
    }

    @Override
    public void activate() {
        if (!attached) {
            attached = true;
            canvas.getOverlay().addDecoration(overlay, this::refresh);
            canvas.getOverlay().setModeHint(modeName(), "Click: corner \u00b7 Drag: curve \u00b7 "
                    + (canClose() ? "Click the first point: close \u00b7 " : "") + "Enter or Esc: finish");
        }
    }

    @Override
    public void finish() {
        if (drawing && doc() != null) {
            end(false, true);
        }
        drawing = false;
        hover = null;
        canvas.getOverlay().setGuides(List.of());
        if (attached) {
            attached = false;
            canvas.getOverlay().removeDecoration(overlay);
            canvas.getOverlay().setModeHint(null, null);
        }
    }

    private void begin() {
        doc().endGesture();
        gestureKey = new Object();
        vertices.clear();
        parentId = parentId();
        snapTargets = canvas.getGeometry().snapTargets(Set.of());
        written = false;
        drawing = true;
    }

    // ------------------------------------------------------------------ pointer

    @Override
    public void pressed(Pointer p) {
        // playback ends what is in progress with finish(), and the tool stays the one in charge
        activate();
        if (drawing && written && !exists()) {
            drawing = false;
        }
        if (!drawing) {
            begin();
        }
        if (p.clicks() >= 2) {
            // the first click of the double-click has placed the last vertex
            if (vertices.size() >= 2) {
                end(false, false);
            } else if (vertices.isEmpty()) {
                // a double-click that the tool only saw the second half of: no path was started
                drawing = false;
            }
            return;
        }
        pressing = true;
        dragging = false;
        closing = canClose() && vertices.size() >= 2 && isNearFirst(p.view());
        if (closing) {
            pressedAt = p.view();
            return;
        }
        Vec2 view = placed(p);
        pressedAt = view;
        keptIn = Vec2.ZERO;
        vertices.add(new Vertex(canvas.getGeometry().viewToGroup(parentId, view), Vec2.ZERO, Vec2.ZERO));
        write(false, false);
    }

    private double[] toView() {
        return canvas.getGeometry().groupToView(parentId);
    }

    private boolean isNearFirst(Vec2 view) {
        return Handles.distance(Matrices.apply(toView(), vertices.get(0).point()), view) <= CLOSE_DISTANCE;
    }

    /** Where a press puts a vertex, in view pixels: on a 45° ray from the last vertex with shift, else snapped. */
    private Vec2 placed(Pointer p) {
        Vec2 view = p.view();
        List<Snapper.Guide> guides = List.of();
        if (p.shift() && !vertices.isEmpty()) {
            Vec2 last = Matrices.apply(toView(), vertices.get(vertices.size() - 1).point());
            view = last.plus(TransformMath.constrainAngle(view.minus(last), 45));
        } else if (!p.snapOff()) {
            Snapper.Result result = Snapper.snapPoint(view.x(), view.y(), true, true, snapTargets, SNAP_DISTANCE);
            view = view.plus(new Vec2(result.dx(), result.dy()));
            guides = result.guides();
        }
        canvas.getOverlay().setGuides(guides);
        return view;
    }

    @Override
    public void dragged(Pointer p) {
        if (!drawing || !pressing || (!dragging && Handles.distance(pressedAt, p.view()) < DRAG_THRESHOLD)) {
            return;
        }
        dragging = true;
        canvas.getOverlay().setGuides(List.of());
        double[] viewToParent = Matrices.invert(toView());
        if (viewToParent == null) {
            return;
        }
        Vec2 pull = p.view().minus(pressedAt);
        if (p.shift()) {
            pull = TransformMath.constrainAngle(pull, 45);
        }
        Vec2 out = Matrices.applyLinear(viewToParent, pull);
        if (closing) {
            // the drag shapes the segment that closes the path; the start of the path stays as drawn
            Vertex first = vertices.get(0);
            vertices.set(0, new Vertex(first.point(), PathOps.scaled(out, -1), first.out()));
        } else {
            int last = vertices.size() - 1;
            if (!p.alt()) {
                keptIn = PathOps.scaled(out, -1);
            }
            vertices.set(last, new Vertex(vertices.get(last).point(), keptIn, out));
        }
        write(false, false);
    }

    @Override
    public void released(Pointer p) {
        if (!drawing || !pressing) {
            return;
        }
        pressing = false;
        hover = p;
        canvas.getOverlay().setGuides(List.of());
        if (closing) {
            closing = false;
            end(true, false);
        }
    }

    @Override
    public void moved(Pointer p) {
        hover = p;
        if (drawing) {
            canvas.requestLayout();
        }
    }

    @Override
    public void exited() {
        hover = null;
        canvas.requestLayout();
    }

    // ------------------------------------------------------------------ keys

    @Override
    public boolean blocksToolShortcuts() {
        return drawing;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!drawing || event.isShortcutDown()) {
            return false;
        }
        switch (event.getCode()) {
            case ENTER -> end(false, false);
            case BACK_SPACE, DELETE -> removeLast();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void removeLast() {
        if (pressing || vertices.isEmpty()) {
            return;
        }
        vertices.remove(vertices.size() - 1);
        if (vertices.isEmpty()) {
            drop(false);
        } else {
            write(false, false);
        }
        canvas.requestLayout();
    }

    @Override
    public boolean cancel() {
        if (!drawing) {
            return false;
        }
        end(false, false);
        return true;
    }

    // ------------------------------------------------------------------ writing

    private void write(boolean closed, boolean finished) {
        Command command = command(List.copyOf(vertices), closed, finished);
        if (command != null) {
            doc().executeCoalesced(gestureKey, command);
            written = true;
        }
    }

    /** Finishes the path, or drops it when it has fewer than two vertices. */
    private void end(boolean closed, boolean replaced) {
        if (vertices.size() < 2) {
            drop(replaced);
            return;
        }
        drawing = false;
        pressing = false;
        write(closed, true);
        doc().endGesture();
        canvas.getOverlay().setGuides(List.of());
        canvas.requestLayout();
        finished(replaced);
    }

    private void drop(boolean replaced) {
        drawing = false;
        pressing = false;
        if (written) {
            doc().cancelGesture();
        }
        vertices.clear();
        canvas.getOverlay().setGuides(List.of());
        canvas.requestLayout();
        dropped(replaced);
    }

    // ------------------------------------------------------------------ drawing

    private void refresh() {
        if (!drawing || vertices.isEmpty() || doc() == null) {
            overlay.clear();
            return;
        }
        double[] toView = toView();
        PathData view = PathOps.transform(new PathData(List.of(new Contour(vertices, false))), toView);
        List<Vertex> inView = view.contours().get(0).vertices();
        overlay.show(view, Set.of(new VertexRef(0, inView.size() - 1)), true);
        boolean overFirst = hover != null && canClose() && vertices.size() >= 2 && isNearFirst(hover.view());
        overlay.setCloseTarget(overFirst || closing ? inView.get(0).point() : null);
        if (hover == null || pressing) {
            overlay.setRubberBand(null);
            return;
        }
        Vertex last = inView.get(inView.size() - 1);
        Vertex next = overFirst ? new Vertex(inView.get(0).point(), inView.get(0).in(), Vec2.ZERO)
                : new Vertex(hover.view(), Vec2.ZERO, Vec2.ZERO);
        overlay.setRubberBand(new PathData(List.of(new Contour(List.of(last, next), false))));
    }

    @Override
    public Cursor cursor(Pointer pointer) {
        return Cursor.CROSSHAIR;
    }
}
