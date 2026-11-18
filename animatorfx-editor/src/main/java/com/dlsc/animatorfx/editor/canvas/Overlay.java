package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.Matrices;
import com.dlsc.animatorfx.editor.ui.Formats;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.MotionPath;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.PathMeasure;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.player.render.PathConverter;
import javafx.geometry.Bounds;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * What the canvas draws above the artboard, in view pixels, so it keeps its size at any zoom and
 * never becomes part of the document: slot rectangles, the hover outline, the selection with its
 * handles, the motion path of the selected layer (a dashed line with a dot at its start and an
 * arrow at its end), snap guides, the marquee and the feedback of the tools.
 *
 * <p>The nodes are created once and moved on every {@link #refresh()}; the canvas refreshes
 * during its layout. Everything is styled by style class in {@code editor.css}
 * ({@code overlay-selection}, {@code overlay-handle}, …). Other code adds its own drawing with
 * {@link #addDecoration}.
 */
public final class Overlay extends Pane {

    private static final double HANDLE_SIZE = 7;
    private static final double ANCHOR_RADIUS = 4;

    private record Decoration(Node node, Runnable update) {
    }

    private final CanvasView canvas;
    private final Group slots = new Group();
    private final Group outlines = new Group();
    private final Group guides = new Group();
    private final Group decorations = new Group();
    private final List<Polygon> slotShapes = new ArrayList<>();
    private final List<Text> slotLabels = new ArrayList<>();
    private final List<Polygon> memberOutlines = new ArrayList<>();
    private final List<Line> guideLines = new ArrayList<>();
    private final List<Decoration> decorationList = new ArrayList<>();
    private final Polygon groupOutline = polygon("overlay-group");
    private final Polygon hoverOutline = polygon("overlay-hover");
    private final Polygon selectionOutline = polygon("overlay-selection");
    private final Polygon preview = polygon("overlay-preview");
    private final Rectangle marquee = new Rectangle();
    private final Line rotateStem = new Line();
    private final Circle rotateHandle = new Circle(HANDLE_SIZE / 2 + 0.5);
    private final Group anchor = new Group();
    private final Map<Handle, Rectangle> handles = new EnumMap<>(Handle.class);
    private final Label readout = new Label();
    private final Path motionPath = new Path();
    private final Circle motionStart = new Circle(3);
    private final Polygon motionArrow = new Polygon(0.0, 0.0, -9.0, -4.0, -9.0, 4.0);
    private final Rotate motionArrowAngle = new Rotate();
    private final Group motion = new Group(motionPath, motionStart, motionArrow);

    private String hoverId;
    private Box marqueeBox;
    private Quad previewQuad;
    private List<Snapper.Guide> guideList = List.of();
    private boolean handlesHidden;

    Overlay(CanvasView canvas) {
        this.canvas = canvas;
        getStyleClass().add("canvas-overlay");
        setMouseTransparent(true);
        setManaged(false);

        marquee.getStyleClass().add("overlay-marquee");
        rotateStem.getStyleClass().add("overlay-selection");
        rotateHandle.getStyleClass().addAll("overlay-handle", "overlay-rotate-handle");
        Circle ring = new Circle(ANCHOR_RADIUS);
        ring.getStyleClass().add("overlay-anchor");
        Line horizontal = new Line(-ANCHOR_RADIUS - 3, 0, ANCHOR_RADIUS + 3, 0);
        Line vertical = new Line(0, -ANCHOR_RADIUS - 3, 0, ANCHOR_RADIUS + 3);
        horizontal.getStyleClass().add("overlay-anchor-cross");
        vertical.getStyleClass().add("overlay-anchor-cross");
        anchor.getChildren().addAll(horizontal, vertical, ring);
        Group handleNodes = new Group();
        for (Handle handle : Handle.values()) {
            Rectangle node = new Rectangle(HANDLE_SIZE, HANDLE_SIZE);
            node.getStyleClass().add("overlay-handle");
            handles.put(handle, node);
            handleNodes.getChildren().add(node);
        }
        readout.getStyleClass().addAll("overlay-readout", "mono");
        readout.setVisible(false);
        motionPath.getStyleClass().add("overlay-motion-path");
        motionStart.getStyleClass().add("overlay-motion-start");
        motionArrow.getStyleClass().add("overlay-motion-arrow");
        motionArrow.getTransforms().add(motionArrowAngle);
        motion.setVisible(false);
        getChildren().addAll(slots, groupOutline, hoverOutline, outlines, motion, selectionOutline, rotateStem,
                handleNodes, rotateHandle, anchor, preview, guides, marquee, readout, decorations);
        refresh();
    }

    // ------------------------------------------------------------------ feedback set by tools

    /** The layer under the pointer, drawn with an outline; {@code null} for none. */
    public void setHover(String layerId) {
        if (!Objects.equals(hoverId, layerId)) {
            hoverId = layerId;
            canvas.requestLayout();
        }
    }

    public String getHover() {
        return hoverId;
    }

    /** The selection rectangle being dragged, in view pixels; {@code null} for none. */
    public void setMarquee(Box box) {
        marqueeBox = box;
        canvas.requestLayout();
    }

    /** The lines where the dragged thing snapped, in view pixels. */
    public void setGuides(List<Snapper.Guide> guides) {
        guideList = List.copyOf(guides);
        canvas.requestLayout();
    }

    public List<Snapper.Guide> getGuides() {
        return guideList;
    }

    /**
     * An outline for something that does not exist yet, in view pixels; {@code null} for none.
     *
     * @param styleClass an extra style class for the outline, such as {@code overlay-slot}
     */
    public void setPreview(Quad quad, String styleClass) {
        previewQuad = quad;
        preview.getStyleClass().setAll("overlay-preview");
        if (styleClass != null) {
            preview.getStyleClass().add(styleClass);
        }
        canvas.requestLayout();
    }

    /** Hides the handles while a handle is being dragged, so they do not cover what is going on. */
    public void setHandlesHidden(boolean hidden) {
        handlesHidden = hidden;
        canvas.requestLayout();
    }

    /** Removes hover, marquee, guides and preview. */
    public void clearFeedback() {
        hoverId = null;
        marqueeBox = null;
        previewQuad = null;
        guideList = List.of();
        handlesHidden = false;
        canvas.requestLayout();
    }

    /**
     * Adds a node in view pixels, above everything else, with a function that positions it;
     * the function runs on every refresh.
     */
    public void addDecoration(Node node, Runnable update) {
        decorationList.add(new Decoration(node, update));
        decorations.getChildren().add(node);
        canvas.requestLayout();
    }

    public void removeDecoration(Node node) {
        decorationList.removeIf(d -> d.node() == node);
        decorations.getChildren().remove(node);
    }

    // ------------------------------------------------------------------ drawing

    /** Moves every node to where the frame on screen puts it. */
    void refresh() {
        EditorDocument doc = canvas.getDocument();
        LayerGeometry geometry = canvas.getGeometry();
        List<String> selected = doc == null ? List.of() : doc.getSelection().layerIds();

        refreshSlots(doc, geometry);
        show(groupOutline, geometry.quad(canvas.getEnteredGroupId()).orElse(null));
        boolean hoverShown = hoverId != null && !selected.contains(hoverId);
        show(hoverOutline, hoverShown ? geometry.quad(hoverId).orElse(null) : null);

        List<Quad> quads = new ArrayList<>();
        for (String id : selected) {
            geometry.quad(id).ifPresent(quads::add);
        }
        Quad single = selected.size() == 1 && quads.size() == 1 ? quads.get(0) : null;
        Quad enclosing = single;
        if (single == null && !quads.isEmpty()) {
            Box box = null;
            for (Quad quad : quads) {
                box = box == null ? quad.bounds() : box.union(quad.bounds());
            }
            enclosing = Quad.of(box);
        }
        pool(memberOutlines, outlines, single == null ? quads.size() : 0, () -> polygon("overlay-member"));
        if (single == null) {
            for (int i = 0; i < quads.size(); i++) {
                show(memberOutlines.get(i), quads.get(i));
            }
        }
        show(selectionOutline, enclosing);
        Layer only = selected.size() == 1 ? geometry.layer(selected.get(0)).orElse(null) : null;
        refreshHandles(single, single == null ? null : only);
        refreshReadout(enclosing);
        refreshMotionPath(only);

        show(preview, previewQuad);
        marquee.setVisible(marqueeBox != null);
        if (marqueeBox != null) {
            marquee.setX(crisp(marqueeBox.minX()));
            marquee.setY(crisp(marqueeBox.minY()));
            marquee.setWidth(Math.rint(marqueeBox.width()));
            marquee.setHeight(Math.rint(marqueeBox.height()));
        }
        pool(guideLines, guides, guideList.size(), () -> {
            Line line = new Line();
            line.getStyleClass().add("overlay-guide");
            return line;
        });
        for (int i = 0; i < guideList.size(); i++) {
            Snapper.Guide guide = guideList.get(i);
            Line line = guideLines.get(i);
            double at = crisp(guide.position());
            line.setStartX(guide.vertical() ? at : guide.from());
            line.setEndX(guide.vertical() ? at : guide.to());
            line.setStartY(guide.vertical() ? guide.from() : at);
            line.setEndY(guide.vertical() ? guide.to() : at);
        }
        for (Decoration decoration : decorationList) {
            decoration.update().run();
        }
    }

    private void refreshHandles(Quad quad, Layer layer) {
        boolean shown = quad != null && layer != null && !handlesHidden;
        List<Handle> visible = shown ? Handles.visible(quad) : List.of();
        double angle = 0;
        if (shown) {
            Vec2 top = quad.topRight().minus(quad.topLeft());
            angle = Math.toDegrees(Math.atan2(top.y(), top.x()));
        }
        for (Map.Entry<Handle, Rectangle> entry : handles.entrySet()) {
            Rectangle node = entry.getValue();
            node.setVisible(visible.contains(entry.getKey()));
            if (node.isVisible()) {
                Vec2 at = quad.handle(entry.getKey());
                node.setX(Math.rint(at.x() - HANDLE_SIZE / 2) + 0.5);
                node.setY(Math.rint(at.y() - HANDLE_SIZE / 2) + 0.5);
                node.setRotate(angle);
            }
        }
        rotateStem.setVisible(shown);
        rotateHandle.setVisible(shown);
        anchor.setVisible(shown);
        if (!shown) {
            return;
        }
        Vec2 top = quad.point(0.5, 0);
        Vec2 rotate = quad.rotateHandle(Handles.ROTATE_DISTANCE);
        rotateStem.setStartX(crisp(top.x()));
        rotateStem.setStartY(crisp(top.y()));
        rotateStem.setEndX(crisp(rotate.x()));
        rotateStem.setEndY(crisp(rotate.y()));
        rotateHandle.setCenterX(crisp(rotate.x()));
        rotateHandle.setCenterY(crisp(rotate.y()));
        Vec2 anchorAt = anchorPoint(layer);
        anchor.setLayoutX(crisp(anchorAt.x()));
        anchor.setLayoutY(crisp(anchorAt.y()));
    }

    /** The motion path of the one selected layer, which lives in the coordinates of the layer's parent. */
    private void refreshMotionPath(Layer layer) {
        MotionPath path = layer == null ? null : layer.base().motionPath();
        boolean shown = path != null && path.path().contours().stream().anyMatch(c -> c.vertices().size() >= 2);
        motion.setVisible(shown);
        if (!shown) {
            return;
        }
        PathData view = PathOps.transform(path.path(), canvas.getGeometry().parentToView(layer.id()));
        PathConverter.write(view, motionPath.getElements());
        PathMeasure measure = new PathMeasure(view);
        Vec2 from = measure.pointAt(0);
        Vec2 to = measure.pointAt(1);
        motionStart.setCenterX(from.x());
        motionStart.setCenterY(from.y());
        motionArrow.setLayoutX(to.x());
        motionArrow.setLayoutY(to.y());
        motionArrowAngle.setAngle(measure.angleAt(1));
    }

    /** Whether the motion path of the selected layer is showing. */
    public boolean isMotionPathShown() {
        return motion.isVisible();
    }

    /** The size of the selection in document pixels, and its angle when it is turned, below its box. */
    private void refreshReadout(Quad quad) {
        readout.setVisible(quad != null);
        if (quad == null) {
            return;
        }
        double zoom = canvas.getZoom();
        Vec2 top = quad.topRight().minus(quad.topLeft());
        double angle = Math.toDegrees(Math.atan2(top.y(), top.x()));
        String text = Formats.size(Math.rint(quad.width() / zoom * 10) / 10, Math.rint(quad.height() / zoom * 10) / 10);
        if (Math.abs(angle) >= 0.05) {
            text += "   " + Formats.number(Math.rint(angle * 10) / 10) + "\u00b0";
        }
        readout.setText(text);
        if (getScene() != null) {
            readout.applyCss();
        }
        Box box = quad.bounds();
        double width = readout.prefWidth(-1);
        readout.resizeRelocate(Math.rint(box.centerX() - width / 2), Math.rint(box.maxY() + 9), width,
                readout.prefHeight(-1));
    }

    /** Where the anchor point of the layer is in view pixels. */
    public Vec2 anchorPoint(Layer layer) {
        LayerGeometry geometry = canvas.getGeometry();
        Vec2 local = layer.base().transform().anchor().at(canvas.getTime());
        return Matrices.apply(geometry.localToView(layer), local);
    }

    private void refreshSlots(EditorDocument doc, LayerGeometry geometry) {
        List<SlotLayer> shown = new ArrayList<>();
        List<Quad> quads = new ArrayList<>();
        if (doc != null) {
            for (SlotLayer slot : Layers.slots(doc.getAnimation())) {
                geometry.quad(slot.id()).ifPresent(quad -> {
                    shown.add(slot);
                    quads.add(quad);
                });
            }
        }
        boolean created = slotShapes.size() < shown.size();
        pool(slotShapes, slots, shown.size(), () -> polygon("overlay-slot"));
        while (slotLabels.size() < shown.size()) {
            Text label = new Text();
            label.getStyleClass().addAll("overlay-slot-label", "mono");
            label.setTextOrigin(VPos.CENTER);
            slotLabels.add(label);
            slots.getChildren().add(label);
        }
        if (created && getScene() != null) {
            slots.applyCss();
        }
        for (int i = 0; i < slotLabels.size(); i++) {
            Text label = slotLabels.get(i);
            label.setVisible(i < shown.size());
            if (i >= shown.size()) {
                continue;
            }
            Quad quad = quads.get(i);
            show(slotShapes.get(i), quad);
            label.setText("slot: " + shown.get(i).slotName());
            Bounds text = label.getLayoutBounds();
            Box box = quad.bounds();
            boolean inside = box.width() >= text.getWidth() + 12 && box.height() >= text.getHeight() + 6;
            Vec2 centre = quad.center();
            label.setX(Math.rint(inside ? centre.x() - text.getWidth() / 2 : box.minX()));
            label.setY(Math.rint(inside ? centre.y() : box.minY() - text.getHeight() / 2 - 3));
        }
    }

    private static Polygon polygon(String styleClass) {
        Polygon polygon = new Polygon();
        polygon.getStyleClass().add(styleClass);
        polygon.setVisible(false);
        return polygon;
    }

    private static void show(Polygon polygon, Quad quad) {
        polygon.setVisible(quad != null);
        if (quad == null) {
            return;
        }
        Double[] points = new Double[8];
        int i = 0;
        for (Vec2 corner : quad.corners()) {
            points[i++] = crisp(corner.x());
            points[i++] = crisp(corner.y());
        }
        polygon.getPoints().setAll(points);
    }

    /** Keeps exactly {@code count} nodes of the pool visible, creating nodes as needed. */
    private static <T extends Node> void pool(List<T> pool, Group parent, int count, Supplier<T> factory) {
        while (pool.size() < count) {
            T node = factory.get();
            pool.add(node);
            parent.getChildren().add(node);
        }
        for (int i = 0; i < pool.size(); i++) {
            pool.get(i).setVisible(i < count);
        }
    }

    /** The centre of the pixel, so a one-pixel line is not smeared over two. */
    private static double crisp(double value) {
        return Math.floor(value) + 0.5;
    }
}
