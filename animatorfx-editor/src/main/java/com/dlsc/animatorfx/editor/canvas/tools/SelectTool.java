package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.Box;
import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.canvas.Handle;
import com.dlsc.animatorfx.editor.canvas.Handles;
import com.dlsc.animatorfx.editor.canvas.LayerGeometry;
import com.dlsc.animatorfx.editor.canvas.Quad;
import com.dlsc.animatorfx.editor.canvas.Snapper;
import com.dlsc.animatorfx.editor.canvas.TransformMath;
import com.dlsc.animatorfx.editor.canvas.TransformMath.Values;
import com.dlsc.animatorfx.editor.doc.Command;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.Matrices;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Vec2;
import javafx.collections.ObservableList;
import javafx.scene.Cursor;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Selects and transforms layers.
 *
 * <ul>
 * <li>Click selects the top-most layer under the pointer, shift-click adds or removes one, a
 * drag on empty canvas selects with a marquee. A group is selected as a whole; a double-click
 * enters it (or is taken by a {@link CanvasView#getDoubleClickHandlers() handler}, such as text
 * editing), and a click outside leaves it.</li>
 * <li>Dragging a selected layer moves the selection; shift keeps the move on one axis. Arrow
 * keys nudge by 1 px, with shift by 10 px.</li>
 * <li>A single selected layer has eight scale handles (the opposite handle stays fixed; alt
 * scales about the anchor, shift keeps the aspect ratio), a rotate handle above the top edge
 * (shift snaps to 15°) and its anchor point, which moves with alt held.</li>
 * <li>Moves and scales snap to the artboard and to other layers unless Control is held.</li>
 * </ul>
 *
 * Several selected layers can be moved together; scaling and rotating work on one layer.
 */
public final class SelectTool implements Tool {

    public static final String ANCHOR = "transform.anchor";
    public static final String POSITION = "transform.position";
    public static final String SCALE = "transform.scale";
    public static final String ROTATION = "transform.rotation";

    private static final double DRAG_THRESHOLD = 3;
    private static final double SNAP_DISTANCE = 6;

    private enum Mode {IDLE, PENDING, MOVE, SCALE, ROTATE, ANCHOR, MARQUEE}

    /** A layer that moves with the drag: where it started and how view distances turn into its parent's. */
    private record Mover(String id, Vec2 start, double[] viewToParent) {
    }

    private final CanvasView canvas;
    private Mode mode = Mode.IDLE;
    private Pointer start;
    private Object gestureKey;

    private List<Mover> movers = List.of();
    private Box startBox;
    private List<Box> targets = List.of();
    private String clickedId;
    private boolean reduceOnRelease;
    private List<String> marqueeBase = List.of();

    private String layerId;
    private Values startValues;
    private Box local;
    private Quad startQuad;
    private double[] viewToParent;
    private Handle handle;
    private Vec2 grabOffset = Vec2.ZERO;
    private Vec2 lastInParent;
    /** The rotation that follows the pointer exactly; what is written may be this snapped to 15°. */
    private double rotation;
    /** Whether this scale drag has written a position, which from then on must be kept up to date. */
    private boolean positionWritten;

    public SelectTool(CanvasView canvas) {
        this.canvas = canvas;
    }

    private EditorDocument doc() {
        return canvas.getDocument();
    }

    private ObservableList<String> selection() {
        return doc().getSelection().layerIds();
    }

    // ------------------------------------------------------------------ press

    @Override
    public void pressed(Pointer p) {
        LayerGeometry geometry = canvas.getGeometry();
        ObservableList<String> selection = selection();
        doc().endGesture();
        start = p;
        gestureKey = new Object();
        mode = Mode.IDLE;
        canvas.getOverlay().setHover(null);

        if (selection.size() == 1 && grabHandle(p, selection.get(0))) {
            return;
        }
        Optional<String> hit = geometry.layerAt(p.view());
        if (p.clicks() >= 2 && hit.isPresent()) {
            Layer layer = geometry.layer(hit.get()).orElseThrow();
            for (Predicate<Layer> handler : List.copyOf(canvas.getDoubleClickHandlers())) {
                if (handler.test(layer)) {
                    return;
                }
            }
            if (layer instanceof GroupLayer) {
                canvas.enterGroup(layer.id());
                geometry.layerAt(p.view()).ifPresentOrElse(selection::setAll, selection::clear);
                return;
            }
        }
        if (hit.isEmpty() && canvas.getEnteredGroupId() != null) {
            canvas.enterGroup(null);
            hit = geometry.layerAt(p.view());
        }
        if (hit.isEmpty()) {
            marqueeBase = p.shift() ? List.copyOf(selection) : List.of();
            if (!p.shift()) {
                selection.clear();
            }
            mode = Mode.MARQUEE;
            return;
        }
        String id = hit.get();
        reduceOnRelease = false;
        if (p.shift()) {
            if (selection.contains(id)) {
                selection.remove(id);
                return;
            }
            selection.add(id);
        } else if (!selection.contains(id)) {
            selection.setAll(id);
        } else {
            reduceOnRelease = selection.size() > 1;
        }
        clickedId = id;
        mode = Mode.PENDING;
    }

    /** Starts a scale, rotate or anchor drag when the pointer is on a handle of the layer. */
    private boolean grabHandle(Pointer p, String id) {
        LayerGeometry geometry = canvas.getGeometry();
        Layer layer = geometry.layer(id).orElse(null);
        Quad quad = geometry.quad(id).orElse(null);
        if (layer == null || quad == null || layer.base().locked()) {
            return false;
        }
        Vec2 anchorAt = canvas.getOverlay().anchorPoint(layer);
        Vec2 grabbed;
        if (p.alt() && Handles.distance(anchorAt, p.view()) <= Handles.GRAB) {
            mode = Mode.ANCHOR;
            grabbed = anchorAt;
        } else if (Handles.isRotateHandle(quad, p.view())) {
            mode = Mode.ROTATE;
            grabbed = p.view();
        } else {
            Optional<Handle> scale = Handles.at(quad, p.view());
            if (scale.isEmpty()) {
                return false;
            }
            mode = Mode.SCALE;
            handle = scale.get();
            grabbed = quad.handle(handle);
        }
        viewToParent = Matrices.invert(geometry.parentToView(id));
        if (viewToParent == null) {
            mode = Mode.IDLE;
            return true;
        }
        layerId = id;
        startValues = Values.of(layer.base().transform(), canvas.getTime());
        local = geometry.localBox(layer);
        startQuad = quad;
        grabOffset = grabbed.minus(p.view());
        lastInParent = Matrices.apply(viewToParent, p.view());
        rotation = startValues.rotation();
        positionWritten = false;
        targets = geometry.snapTargets(Set.of(id));
        return true;
    }

    // ------------------------------------------------------------------ drag

    @Override
    public void dragged(Pointer p) {
        if (mode == Mode.PENDING) {
            if (Handles.distance(start.view(), p.view()) < DRAG_THRESHOLD) {
                return;
            }
            beginMove();
        }
        switch (mode) {
            case MOVE -> move(p);
            case SCALE -> scale(p);
            case ROTATE -> rotate(p);
            case ANCHOR -> moveAnchor(p);
            case MARQUEE -> marquee(p);
            default -> {
            }
        }
    }

    /** The selected layers that can move: unlocked, and not inside a group that moves anyway. */
    private List<Layer> movable() {
        Animation animation = doc().getAnimation();
        Set<String> selected = new HashSet<>(selection());
        List<Layer> result = new ArrayList<>();
        for (String id : selection()) {
            Layer layer = Layers.find(animation, id).orElse(null);
            if (layer == null || layer.base().locked()) {
                continue;
            }
            boolean inside = false;
            Optional<GroupLayer> parent = Layers.parentOf(animation, id);
            while (parent.isPresent() && !inside) {
                inside = selected.contains(parent.get().id());
                parent = Layers.parentOf(animation, parent.get().id());
            }
            if (!inside) {
                result.add(layer);
            }
        }
        return result;
    }

    private void beginMove() {
        LayerGeometry geometry = canvas.getGeometry();
        List<Mover> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Box box = null;
        for (Layer layer : movable()) {
            double[] inverse = Matrices.invert(geometry.parentToView(layer.id()));
            if (inverse == null) {
                continue;
            }
            list.add(new Mover(layer.id(), layer.base().transform().position().at(canvas.getTime()), inverse));
            ids.add(layer.id());
            Box bounds = geometry.viewBounds(layer.id()).orElse(null);
            if (bounds != null) {
                box = box == null ? bounds : box.union(bounds);
            }
        }
        movers = list;
        startBox = box;
        targets = geometry.snapTargets(ids);
        mode = list.isEmpty() ? Mode.IDLE : Mode.MOVE;
    }

    private void move(Pointer p) {
        Vec2 delta = p.view().minus(start.view());
        if (p.shift()) {
            delta = TransformMath.constrainAxis(delta);
        }
        boolean snapped = false;
        List<Snapper.Guide> guides = List.of();
        if (!p.snapOff() && startBox != null) {
            Snapper.Result result = Snapper.snap(startBox.translated(delta.x(), delta.y()), targets, SNAP_DISTANCE);
            // a shift-drag stays on its axis: it does not snap across it
            boolean freeX = !p.shift() || delta.y() == 0;
            boolean freeY = !p.shift() || delta.x() == 0;
            delta = delta.plus(new Vec2(freeX ? result.dx() : 0, freeY ? result.dy() : 0));
            guides = result.guides().stream().filter(g -> g.vertical() ? freeX : freeY).toList();
            snapped = !guides.isEmpty();
        }
        canvas.getOverlay().setGuides(guides);
        List<Command> commands = new ArrayList<>();
        for (Mover mover : movers) {
            Vec2 inParent = tidy(Matrices.applyLinear(mover.viewToParent(), delta), snapped);
            commands.add(canvas.valueCommand(mover.id(), POSITION, mover.start().plus(inParent)));
        }
        doc().executeCoalesced(gestureKey, Commands.all("Move", commands.toArray(Command[]::new)));
    }

    /**
     * Rounds a move: to whole pixels when zoomed out, where one view pixel is more than one
     * document pixel anyway, and to hundredths otherwise. A snapped move is not forced onto whole pixels.
     */
    private Vec2 tidy(Vec2 delta, boolean snapped) {
        double unit = canvas.getZoom() < 1 && !snapped ? 1 : 100;
        return new Vec2(Math.rint(delta.x() * unit) / unit, Math.rint(delta.y() * unit) / unit);
    }

    private void scale(Pointer p) {
        Vec2 view = p.view().plus(grabOffset);
        List<Snapper.Guide> guides = List.of();
        if (!p.snapOff() && isAxisAligned(startQuad)) {
            Vec2 direction = startQuad.handle(handle).minus(startQuad.handle(handle.opposite()));
            Snapper.Result result = Snapper.snapPoint(view.x(), view.y(), Math.abs(direction.x()) > 0.5,
                    Math.abs(direction.y()) > 0.5, targets, SNAP_DISTANCE);
            view = view.plus(new Vec2(result.dx(), result.dy()));
            guides = result.guides();
        }
        canvas.getOverlay().setGuides(guides);
        TransformMath.Scaled scaled = TransformMath.scale(startValues, local, handle,
                Matrices.apply(viewToParent, view), p.alt(), p.shift());
        List<Command> commands = new ArrayList<>();
        commands.add(canvas.valueCommand(layerId, SCALE, scaled.scale()));
        // Commands of a gesture apply to the document as the previous drag event left it. Once a
        // position has been written, it has to be written again when it returns to the start
        // (alt pressed during the drag); before that, leaving it out keeps auto-key from keying it.
        if (positionWritten || Handles.distance(scaled.position(), startValues.position()) > 1e-9) {
            commands.add(canvas.valueCommand(layerId, POSITION, scaled.position()));
            positionWritten = true;
        }
        doc().executeCoalesced(gestureKey, Commands.all("Scale", commands.toArray(Command[]::new)));
    }

    private static boolean isAxisAligned(Quad quad) {
        Vec2 top = quad.topRight().minus(quad.topLeft());
        Vec2 side = quad.bottomLeft().minus(quad.topLeft());
        return (Math.abs(top.x()) < 1e-6 || Math.abs(top.y()) < 1e-6)
                && (Math.abs(side.x()) < 1e-6 || Math.abs(side.y()) < 1e-6);
    }

    private void rotate(Pointer p) {
        Vec2 now = Matrices.apply(viewToParent, p.view());
        rotation = TransformMath.rotation(rotation, startValues.position(), lastInParent, now);
        lastInParent = now;
        double value = p.shift() ? TransformMath.snapAngle(rotation, 15) : rotation;
        doc().executeCoalesced(gestureKey, Commands.all("Rotate", canvas.valueCommand(layerId, ROTATION, value)));
    }

    private void moveAnchor(Pointer p) {
        TransformMath.Anchored moved = TransformMath.moveAnchor(startValues,
                Matrices.apply(viewToParent, p.view().plus(grabOffset)));
        doc().executeCoalesced(gestureKey, Commands.all("Move Anchor",
                canvas.valueCommand(layerId, ANCHOR, moved.anchor()),
                canvas.valueCommand(layerId, POSITION, moved.position())));
    }

    private void marquee(Pointer p) {
        LayerGeometry geometry = canvas.getGeometry();
        Box box = Box.between(start.view(), p.view());
        canvas.getOverlay().setMarquee(box);
        List<String> ids = new ArrayList<>(marqueeBase);
        for (Layer layer : geometry.scope()) {
            if (geometry.isSelectable(layer) && !ids.contains(layer.id())
                    && geometry.viewBounds(layer.id()).filter(box::intersects).isPresent()) {
                ids.add(layer.id());
            }
        }
        if (!ids.equals(selection())) {
            selection().setAll(ids);
        }
    }

    // ------------------------------------------------------------------ end

    private boolean isEditing() {
        return mode == Mode.MOVE || mode == Mode.SCALE || mode == Mode.ROTATE || mode == Mode.ANCHOR;
    }

    @Override
    public void released(Pointer p) {
        if (mode == Mode.PENDING && reduceOnRelease && !p.shift()) {
            selection().setAll(clickedId);
        } else if (isEditing()) {
            // back where it started: nothing happened, also no keyframe from auto-key
            if (p.view().equals(start.view())) {
                doc().cancelGesture();
            } else {
                doc().endGesture();
            }
        }
        reset();
    }

    @Override
    public void finish() {
        if (isEditing() && doc() != null) {
            doc().endGesture();
        }
        reset();
    }

    @Override
    public boolean cancel() {
        boolean cancelled = isEditing() || mode == Mode.MARQUEE;
        if (isEditing()) {
            doc().cancelGesture();
        } else if (mode == Mode.MARQUEE) {
            selection().setAll(marqueeBase);
        }
        reset();
        return cancelled;
    }

    private void reset() {
        mode = Mode.IDLE;
        movers = List.of();
        canvas.getOverlay().setGuides(List.of());
        canvas.getOverlay().setMarquee(null);
    }

    // ------------------------------------------------------------------ keys, hover, cursor

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isShortcutDown() || event.isAltDown() || mode != Mode.IDLE) {
            return false;
        }
        double step = event.isShiftDown() ? 10 : 1;
        Vec2 delta = switch (event.getCode()) {
            case LEFT -> new Vec2(-step, 0);
            case RIGHT -> new Vec2(step, 0);
            case UP -> new Vec2(0, -step);
            case DOWN -> new Vec2(0, step);
            default -> null;
        };
        if (delta == null) {
            return false;
        }
        nudge(delta);
        return true;
    }

    /** Moves the selection by a distance in document pixels, without snapping. */
    private void nudge(Vec2 delta) {
        LayerGeometry geometry = canvas.getGeometry();
        Animation animation = doc().getAnimation();
        List<Command> commands = new ArrayList<>();
        for (Layer layer : movable()) {
            String parent = Layers.parentOf(animation, layer.id()).map(Layer::id).orElse(null);
            double[] inverse = Matrices.invert(geometry.groupToDocument(parent));
            if (inverse != null) {
                Vec2 position = layer.base().transform().position().at(canvas.getTime());
                commands.add(canvas.valueCommand(layer.id(), POSITION,
                        position.plus(Matrices.applyLinear(inverse, delta))));
            }
        }
        if (!commands.isEmpty()) {
            // consecutive nudges of the same selection undo as one step
            doc().executeCoalesced(List.of("nudge", List.copyOf(selection())),
                    Commands.all("Nudge", commands.toArray(Command[]::new)));
        }
    }

    @Override
    public void moved(Pointer p) {
        canvas.getOverlay().setHover(handleCursor(p) != null ? null
                : canvas.getGeometry().layerAt(p.view()).orElse(null));
    }

    @Override
    public void exited() {
        canvas.getOverlay().setHover(null);
    }

    @Override
    public Cursor cursor(Pointer p) {
        if (mode == Mode.MOVE) {
            return Cursor.MOVE;
        }
        Cursor onHandle = handleCursor(p);
        if (onHandle != null) {
            return onHandle;
        }
        Optional<String> hit = canvas.getGeometry().layerAt(p.view());
        return hit.isPresent() && selection().contains(hit.get()) ? Cursor.MOVE : Cursor.DEFAULT;
    }

    /** The cursor for the handle under the pointer; {@code null} when there is none. */
    private Cursor handleCursor(Pointer p) {
        if (mode == Mode.SCALE) {
            return resizeCursor(startQuad, handle);
        }
        if (mode == Mode.ROTATE || mode == Mode.ANCHOR) {
            return Cursor.CROSSHAIR;
        }
        if (doc() == null || selection().size() != 1) {
            return null;
        }
        LayerGeometry geometry = canvas.getGeometry();
        Layer layer = geometry.layer(selection().get(0)).orElse(null);
        Quad quad = geometry.quad(selection().get(0)).orElse(null);
        if (layer == null || quad == null || layer.base().locked()) {
            return null;
        }
        if (p.alt() && Handles.distance(canvas.getOverlay().anchorPoint(layer), p.view()) <= Handles.GRAB) {
            return Cursor.CROSSHAIR;
        }
        if (Handles.isRotateHandle(quad, p.view())) {
            return Cursor.CROSSHAIR;
        }
        return Handles.at(quad, p.view()).map(h -> resizeCursor(quad, h)).orElse(null);
    }

    private static final Cursor[] RESIZE = {Cursor.E_RESIZE, Cursor.SE_RESIZE, Cursor.S_RESIZE, Cursor.SW_RESIZE,
            Cursor.W_RESIZE, Cursor.NW_RESIZE, Cursor.N_RESIZE, Cursor.NE_RESIZE};

    /** The resize cursor that points the way the handle points on screen, also for a rotated box. */
    private static Cursor resizeCursor(Quad quad, Handle handle) {
        Vec2 out = quad.handle(handle).minus(quad.center());
        double degrees = Math.toDegrees(Math.atan2(out.y(), out.x()));
        return RESIZE[Math.floorMod((int) Math.rint(degrees / 45), 8)];
    }
}
