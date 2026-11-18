package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.canvas.Handles;
import com.dlsc.animatorfx.editor.canvas.LayerGeometry;
import com.dlsc.animatorfx.editor.canvas.Snapper;
import com.dlsc.animatorfx.editor.canvas.TransformMath;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.ui.ToolKind;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.Cursor;

import java.util.List;
import java.util.Set;

/**
 * Creates a shape layer by dragging: the drag spans the box of a rectangle or an ellipse, the
 * circle around a polygon or a star, or runs from one end of a line to the other. Shift makes
 * the box square, or keeps a line on 45° steps; alt drags from the centre. A click without a
 * drag makes a shape of the default size around the click.
 *
 * <p>The layer exists in the document from the first movement on, so the drag shows the real
 * thing; the whole drag is one undo step and Escape removes the layer again. Afterwards the new
 * layer is selected and the select tool takes over.
 */
public final class ShapeTool implements Tool {

    public enum Kind {RECTANGLE, ELLIPSE, LINE, POLYGON, STAR}

    public static final double DEFAULT_SIZE = 100;

    private static final double DRAG_THRESHOLD = 3;
    private static final double SNAP_DISTANCE = 6;

    private final CanvasView canvas;
    private final Kind kind;
    /** Where the button went down, before snapping: a click is told from a drag by the distance from here. */
    private Vec2 pressedAt;
    private Pointer start;
    private String parentId;
    private Vec2 startInParent;
    private Object gestureKey;
    private String layerId;
    private List<com.dlsc.animatorfx.editor.canvas.Box> targets = List.of();
    private boolean active;

    public ShapeTool(CanvasView canvas, Kind kind) {
        this.canvas = canvas;
        this.kind = kind;
    }

    @Override
    public void pressed(Pointer p) {
        LayerGeometry geometry = canvas.getGeometry();
        canvas.getDocument().endGesture();
        targets = geometry.snapTargets(Set.of());
        pressedAt = p.view();
        start = snapped(p);
        parentId = canvas.getEnteredGroupId();
        startInParent = geometry.viewToGroup(parentId, start.view());
        gestureKey = new Object();
        layerId = null;
        active = true;
    }

    /** The pointer moved onto the artboard edge or a layer edge when one is near. */
    private Pointer snapped(Pointer p) {
        if (p.snapOff()) {
            canvas.getOverlay().setGuides(List.of());
            return p;
        }
        Snapper.Result result = Snapper.snapPoint(p.view().x(), p.view().y(), true, true, targets, SNAP_DISTANCE);
        canvas.getOverlay().setGuides(result.guides());
        return new Pointer(p.view().plus(new Vec2(result.dx(), result.dy())), p.shift(), p.alt(), p.snapOff(),
                p.clicks());
    }

    @Override
    public void dragged(Pointer p) {
        if (!active || (layerId == null && Handles.distance(pressedAt, p.view()) < DRAG_THRESHOLD)) {
            return;
        }
        Pointer at = snapped(p);
        Vec2 delta = canvas.getGeometry().viewToGroup(parentId, at.view()).minus(startInParent);
        put(layer(delta, at.shift(), at.alt()));
    }

    @Override
    public void released(Pointer p) {
        if (!active) {
            return;
        }
        if (layerId == null) {
            Vec2 half = new Vec2(DEFAULT_SIZE / 2, DEFAULT_SIZE / 2);
            startInParent = kind == Kind.LINE ? startInParent.minus(new Vec2(half.x(), 0)) : startInParent.minus(half);
            put(layer(kind == Kind.LINE ? new Vec2(DEFAULT_SIZE, 0) : new Vec2(DEFAULT_SIZE, DEFAULT_SIZE), false, false));
        }
        canvas.getDocument().endGesture();
        active = false;
        canvas.getOverlay().setGuides(List.of());
        canvas.setActiveTool(ToolKind.SELECT);
    }

    /** Adds the layer on top of the current parent, or replaces it while the drag goes on. */
    private void put(ShapeLayer layer) {
        EditorDocument doc = canvas.getDocument();
        if (layerId == null) {
            layerId = layer.id();
            int top = canvas.getGeometry().scope().size();
            doc.executeCoalesced(gestureKey, Commands.addLayer(parentId, top, layer));
            doc.getSelection().layerIds().setAll(layerId);
        } else {
            ShapeLayer same = layer.withBase(layer.base().withId(layerId));
            doc.executeCoalesced(gestureKey, Commands.updateLayer("Add Layer", layerId, old -> same));
        }
    }

    /** The shape for a drag by {@code delta} from the start point, in parent coordinates. */
    private ShapeLayer layer(Vec2 delta, boolean constrain, boolean fromCentre) {
        Animation animation = canvas.getDocument().getAnimation();
        if (layerId != null) {
            // the layer being dragged keeps its name; it must not count itself
            animation = Commands.removeLayers(List.of(layerId)).apply(animation);
        }
        if (kind == Kind.LINE) {
            Vec2 along = constrain ? TransformMath.constrainAngle(delta, 45) : delta;
            Vec2 from = fromCentre ? startInParent.minus(along) : startInParent;
            return NewLayers.line(animation, from, startInParent.plus(along));
        }
        Vec2 span = constrain || kind == Kind.POLYGON || kind == Kind.STAR ? squared(delta, constrain) : delta;
        Vec2 size = new Vec2(Math.abs(span.x()), Math.abs(span.y()));
        Vec2 centre = fromCentre ? startInParent : startInParent.plus(span.times(0.5));
        if (fromCentre) {
            size = size.times(2);
        }
        return switch (kind) {
            case RECTANGLE -> NewLayers.rectangle(animation, centre, size);
            case ELLIPSE -> NewLayers.ellipse(animation, centre, size);
            case POLYGON -> NewLayers.polygon(animation, centre, size.x() / 2);
            default -> NewLayers.star(animation, centre, size.x() / 2);
        };
    }

    /**
     * A square span: with shift the longer side decides; a polygon or star without shift fits
     * inside the dragged box, so the shorter side decides.
     */
    private static Vec2 squared(Vec2 delta, boolean longer) {
        if (longer) {
            return TransformMath.constrainSquare(delta);
        }
        double side = Math.min(Math.abs(delta.x()), Math.abs(delta.y()));
        return new Vec2(Math.copySign(side, delta.x()), Math.copySign(side, delta.y()));
    }

    @Override
    public void finish() {
        if (active && canvas.getDocument() != null) {
            canvas.getDocument().endGesture();
        }
        active = false;
        canvas.getOverlay().setGuides(List.of());
    }

    @Override
    public boolean cancel() {
        if (!active) {
            return false;
        }
        if (layerId != null) {
            canvas.getDocument().cancelGesture();
        }
        active = false;
        canvas.getOverlay().setGuides(List.of());
        return true;
    }

    @Override
    public Cursor cursor(Pointer pointer) {
        return Cursor.CROSSHAIR;
    }
}
