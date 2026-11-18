package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.canvas.tools.NewLayers;
import com.dlsc.animatorfx.editor.canvas.tools.SlotTool;
import com.dlsc.animatorfx.editor.canvas.tools.TextTool;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.ui.PngEncoder;
import com.dlsc.animatorfx.editor.ui.ToolKind;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The canvas tools, driven by mouse and key events under the headless toolkit.
 */
class CanvasViewTest extends CanvasTestBase {

    private static final String POSITION = "transform.position";

    private static void assertVec(double x, double y, Vec2 actual) {
        assertEquals(x, actual.x(), 1e-6, "x of " + actual);
        assertEquals(y, actual.y(), 1e-6, "y of " + actual);
    }

    private Vec2 size(Layer layer) {
        return ((Geometry.Rect) ((ShapeLayer) layer).geometry()).size().at(0);
    }

    private Vec2 scale(String id) {
        return layer(id).base().transform().scale().at(0);
    }

    private static Layer transformed(Layer layer, Transform transform) {
        return layer.withBase(layer.base().withTransform(transform));
    }

    private Node lookup(String selector) {
        return fx(() -> canvas.lookup(selector));
    }

    // ------------------------------------------------------------------ creating shapes

    @Test
    void draggingWithTheRectangleToolCreatesOneShapeLayerOfTheDraggedSizeAndSelectsIt() {
        tool(ToolKind.RECTANGLE);

        dragFromTo(100, 100, 300, 220);

        assertEquals(1, layers().size());
        ShapeLayer shape = (ShapeLayer) layers().get(0);
        assertVec(200, 120, size(shape));
        assertVec(200, 160, shape.base().transform().position().at(0));
        assertVec(100, 60, shape.base().transform().anchor().at(0));
        assertEquals("Rectangle 1", shape.name());
        assertEquals(0, shape.base().inPoint());
        assertEquals(3, shape.base().outPoint());
        assertEquals(new Paint.Solid(NewLayers.SHAPE_COLOR), shape.fill().at(0));
        assertEquals(List.of(shape.id()), selection());
        assertEquals(ToolKind.SELECT, canvas.getActiveTool());
        assertEquals(ToolKind.SELECT, window.getToolRail().getActiveTool());

        run(doc::undo);
        assertTrue(layers().isEmpty());
        assertFalse(doc.isCanUndo());
    }

    @Test
    void aClickWithAShapeToolCreatesTheDefaultSizeAroundTheClick() {
        tool(ToolKind.ELLIPSE);

        click(400, 300);

        ShapeLayer shape = (ShapeLayer) layers().get(0);
        assertTrue(shape.geometry() instanceof Geometry.Ellipse);
        assertVec(100, 100, ((Geometry.Ellipse) shape.geometry()).size().at(0));
        assertVec(400, 300, shape.base().transform().position().at(0));
        assertEquals("Ellipse 1", shape.name());
    }

    @Test
    void shiftMakesASquareAndNamesCountUp() {
        add(rect("Rectangle 1", 500, 400, 50, 50));
        tool(ToolKind.RECTANGLE);

        dragFromTo(100, 100, 300, 160, Keys.SHIFT);

        ShapeLayer shape = (ShapeLayer) layers().get(1);
        assertVec(200, 200, size(shape));
        assertEquals("Rectangle 2", shape.name());
    }

    @Test
    void theLineToolMakesAStrokedLineAndShiftKeepsItOn45Degrees() {
        tool(ToolKind.LINE);

        dragFromTo(100, 100, 300, 110, Keys.SHIFT);

        ShapeLayer shape = (ShapeLayer) layers().get(0);
        Geometry.Line line = (Geometry.Line) shape.geometry();
        assertVec(0, 0, line.start().at(0));
        assertVec(200, 0, line.end().at(0));
        assertVec(200, 100, shape.base().transform().position().at(0));
        assertTrue(shape.fill().at(0) instanceof Paint.None);
        assertEquals(2, shape.stroke().width().at(0));
        assertEquals("Line 1", shape.name());
    }

    @Test
    void polygonAndStarFitIntoTheDraggedBox() {
        tool(ToolKind.POLYGON);
        dragFromTo(100, 100, 300, 180);
        tool(ToolKind.STAR);
        dragFromTo(400, 100, 500, 200);

        ShapeLayer polygon = (ShapeLayer) layers().get(0);
        assertEquals(40, ((Geometry.Polygon) polygon.geometry()).radius().at(0), 1e-6);
        assertVec(140, 140, polygon.base().transform().position().at(0));
        ShapeLayer star = (ShapeLayer) layers().get(1);
        assertEquals(50, ((Geometry.Star) star.geometry()).outerRadius().at(0), 1e-6);
        assertEquals("Star 1", star.name());
    }

    @Test
    void escapeDuringACreationDragRemovesTheLayerAgain() {
        tool(ToolKind.RECTANGLE);
        press(100, 100);
        drag(200, 200);
        assertEquals(1, layers().size());

        key(KeyCode.ESCAPE);
        release(200, 200);

        assertTrue(layers().isEmpty());
        assertFalse(doc.isCanUndo());
    }

    // ------------------------------------------------------------------ selecting and moving

    @Test
    void draggingALayerMovesItsPositionAndOneUndoRevertsTheWholeDrag() {
        add(rect("a", 100, 100, 200, 120));
        Vec2 before = position("a");

        dragFromTo(150, 150, 190, 175);

        assertEquals(List.of("a"), selection());
        assertVec(before.x() + 40, before.y() + 25, position("a"));
        assertEquals("Move", doc.getUndoName());
        run(doc::undo);
        assertVec(before.x(), before.y(), position("a"));
        assertEquals("Add Layer", doc.getUndoName());
    }

    @Test
    void aDragThatEndsWhereItStartedLeavesNoUndoEntry() {
        add(rect("a", 100, 100, 200, 120));

        press(150, 150);
        drag(190, 175);
        drag(150, 150);
        release(150, 150);

        assertEquals("Add Layer", doc.getUndoName());
        assertVec(200, 160, position("a"));
    }

    @Test
    void withAutoKeyAMoveAtOneSecondCreatesAKeyframe() {
        add(rect("a", 100, 100, 200, 120));
        run(() -> {
            doc.setAutoKey(true);
            doc.setPlayhead(1);
        });

        dragFromTo(150, 150, 190, 175);

        Animatable<?> position = layer("a").base().transform().position();
        assertTrue(position.isAnimated());
        assertEquals(1, position.keyframes().size());
        Keyframe<?> keyframe = position.keyframes().get(0);
        assertEquals(1, keyframe.time(), 1e-9);
        assertEquals(new Vec2(240, 185), keyframe.value());
    }

    @Test
    void escapeCancelsADragAndRestoresTheLayer() {
        add(rect("a", 100, 100, 200, 120));

        press(150, 150);
        drag(250, 250);
        key(KeyCode.ESCAPE);
        drag(260, 260);
        release(260, 260);

        assertVec(200, 160, position("a"));
        assertEquals("Add Layer", doc.getUndoName());
        assertEquals(List.of("a"), selection());
    }

    @Test
    void escapeWithNothingInProgressDeselects() {
        add(rect("a", 100, 100, 200, 120));
        select("a");

        key(KeyCode.ESCAPE);

        assertTrue(selection().isEmpty());
    }

    @Test
    void deleteRemovesTheSelection() {
        add(rect("a", 100, 100, 200, 120), rect("b", 400, 100, 100, 100));
        click(150, 150);

        key(KeyCode.DELETE);

        assertEquals(1, layers().size());
        assertEquals("b", layers().get(0).id());
        assertTrue(selection().isEmpty());
    }

    @Test
    void arrowKeysNudgeByOnePixelAndWithShiftByTen() {
        add(rect("a", 100, 100, 200, 120));
        select("a");

        key(KeyCode.RIGHT);
        key(KeyCode.DOWN, true);

        assertVec(201, 170, position("a"));
        run(doc::undo);
        assertVec(200, 160, position("a"));
    }

    @Test
    void clickSelectsTheTopMostLayerAndSkipsLockedAndHiddenOnes() {
        Layer locked = rect("locked", 100, 100, 200, 200);
        locked = locked.withBase(locked.base().withLocked(true));
        Layer hidden = rect("hidden", 100, 100, 200, 200);
        hidden = hidden.withBase(hidden.base().withVisible(false));
        Layer late = rect("late", 100, 100, 200, 200);
        late = late.withBase(late.base().withInPoint(2));
        add(rect("bottom", 100, 100, 200, 200), rect("top", 150, 150, 200, 200), locked, hidden, late);

        click(200, 200);
        assertEquals(List.of("top"), selection());
        click(120, 120);
        assertEquals(List.of("bottom"), selection());
        click(700, 500);
        assertTrue(selection().isEmpty());
    }

    @Test
    void shiftClickAddsAndRemovesAndTheMarqueeSelectsWhatItTouches() {
        add(rect("a", 100, 100, 100, 100), rect("b", 300, 100, 100, 100), rect("c", 500, 400, 100, 100));

        click(150, 150);
        click(350, 150, Keys.SHIFT);
        assertEquals(List.of("a", "b"), selection());
        click(150, 150, Keys.SHIFT);
        assertEquals(List.of("b"), selection());

        dragFromTo(50, 50, 320, 250);
        assertEquals(List.of("a", "b"), selection());
        assertFalse(lookup(".overlay-marquee").isVisible());
    }

    @Test
    void severalSelectedLayersMoveTogether() {
        add(rect("a", 100, 100, 100, 100), rect("b", 300, 100, 100, 100));
        select("a", "b");

        dragFromTo(150, 150, 170, 190, Keys.CONTROL);

        assertVec(170, 190, position("a"));
        assertVec(370, 190, position("b"));
        assertEquals(List.of("a", "b"), selection());
    }

    @Test
    void aShapeIsHitOnItsOutlineNotOnItsBox() {
        ShapeLayer circle = NewLayers.ellipse(doc.getAnimation(), new Vec2(200, 200), new Vec2(200, 200));
        add(circle);

        click(108, 108);
        assertTrue(selection().isEmpty(), "the corner of the box is outside the circle");
        click(200, 200);
        assertEquals(List.of(circle.id()), selection());
    }

    @Test
    void aThinLineIsHitWithATolerance() {
        ShapeLayer line = NewLayers.line(doc.getAnimation(), new Vec2(100, 100), new Vec2(300, 100));
        add(line);

        click(200, 103);
        assertEquals(List.of(line.id()), selection());
        click(200, 112);
        assertTrue(selection().isEmpty());
    }

    @Test
    void aShapeWithoutFillAndStrokeIsSelectableByItsBounds() {
        ShapeLayer ghost = rect("ghost", 100, 100, 200, 120).withFill(Animatable.of(new Paint.None()));
        add(ghost);

        click(200, 160);

        assertEquals(List.of("ghost"), selection());
        Box bounds = fx(() -> canvas.getGeometry().viewBounds("ghost").orElseThrow());
        Vec2 corner = view(100, 100);
        assertEquals(corner.x(), bounds.minX(), 1e-6);
        assertEquals(200, bounds.width(), 1e-6);
        assertEquals(120, bounds.height(), 1e-6);
    }

    // ------------------------------------------------------------------ handles

    @Test
    void aCornerHandleScalesAboutTheOppositeCornerAndIsOneUndoStep() {
        add(rect("a", 100, 100, 200, 120));
        select("a");

        dragFromTo(300, 220, 400, 280, Keys.CONTROL);

        assertVec(1.5, 1.5, scale("a"));
        // the top-left corner has not moved
        Vec2 corner = fx(() -> canvas.getGeometry().quad("a").orElseThrow().topLeft());
        assertVec(view(100, 100).x(), view(100, 100).y(), corner);
        assertVec(200, 120, size(layer("a")));
        assertEquals("Scale", doc.getUndoName());
        run(doc::undo);
        assertVec(1, 1, scale("a"));
        assertVec(200, 160, position("a"));
    }

    @Test
    void altScalesAboutTheAnchorAndShiftKeepsTheAspectRatio() {
        add(rect("a", 100, 100, 200, 120));
        select("a");

        dragFromTo(300, 160, 400, 160, new Keys(true, true, true));

        assertVec(2, 2, scale("a"));
        assertVec(200, 160, position("a"));
    }

    @Test
    void pressingAltDuringAScaleDragPutsThePositionBack() {
        add(rect("a", 100, 100, 200, 120));
        select("a");

        pressView(view(300, 220), Keys.CONTROL);
        dragView(view(400, 280), Keys.CONTROL);
        assertVec(1.5, 1.5, scale("a"));
        assertVec(250, 190, position("a"));
        dragView(view(400, 280), new Keys(false, true, true));
        releaseView(view(400, 280), new Keys(false, true, true));

        assertVec(2, 2, scale("a"));
        assertVec(200, 160, position("a"));
        run(doc::undo);
        assertVec(1, 1, scale("a"));
        assertVec(200, 160, position("a"));
    }

    @Test
    void aLayerScaledToNothingIsNotHitAndDoesNotBlockWhatIsBeneath() {
        SlotLayer slot = NewLayers.slot(doc.getAnimation(), "button", new Vec2(200, 160), new Vec2(80, 40));
        Layer collapsed = transformed(slot, slot.base().transform().withScale(Animatable.of(Vec2.ZERO)));
        TextLayer text = NewLayers.text(doc.getAnimation(), new Vec2(200, 160), "Title");
        Layer noText = transformed(text, text.base().transform().withScale(Animatable.of(Vec2.ZERO)));
        add(rect("a", 100, 100, 200, 120), collapsed, noText);

        click(150, 130);
        assertEquals(List.of("a"), selection());
        click(200, 160);
        assertEquals(List.of("a"), selection(), "not even on the point the collapsed layers sit on");
        click(600, 500);
        assertTrue(selection().isEmpty());
        move(600, 500);
        assertNull(canvas.getOverlay().getHover());
    }

    @Test
    void theRotateHandleCanTurnTheLayerPastHalfATurn() {
        add(rect("a", 100, 100, 200, 120));
        select("a");
        Vec2 handle = fx(() -> canvas.getGeometry().quad("a").orElseThrow().rotateHandle(Handles.ROTATE_DISTANCE));
        Vec2 centre = view(200, 160);

        pressView(handle, Keys.NONE);
        for (int turned = 30; turned <= 270; turned += 30) {
            double angle = Math.toRadians(-90 + turned);
            dragView(new Vec2(centre.x() + 150 * Math.cos(angle), centre.y() + 150 * Math.sin(angle)), Keys.NONE);
        }
        Vec2 end = new Vec2(centre.x() - 150, centre.y());
        releaseView(end, Keys.NONE);

        assertEquals(270, layer("a").base().transform().rotation().at(0), 1e-3);
    }

    @Test
    void aClickWithOnePixelOfJitterNextToASnapLineStillMakesTheDefaultShape() {
        tool(ToolKind.RECTANGLE);

        press(3, 300);
        drag(4, 300);
        release(4, 300);

        assertVec(100, 100, size(layers().get(0)));
        assertVec(0, 300, position(layers().get(0).id()));
    }

    @Test
    void theTextEditorOpenedFromTheSelectToolFollowsPanAndZoom() {
        TextLayer text = NewLayers.text(doc.getAnimation(), new Vec2(200, 150), "Title");
        add(text);
        run(() -> ((TextTool) canvas.getTool(ToolKind.TEXT)).edit(text.id()));
        layout();
        TextField editor = (TextField) lookup("#" + TextTool.EDITOR_ID);
        assertEquals(ToolKind.SELECT, canvas.getActiveTool());
        double x = editor.getLayoutX();
        double y = editor.getLayoutY();

        run(() -> canvas.panBy(30, -20));
        layout();

        assertEquals(x + 30, editor.getLayoutX(), 1e-6);
        assertEquals(y - 20, editor.getLayoutY(), 1e-6);
    }

    @Test
    void theRotateHandleTurnsTheLayerAndShiftSnapsTo15Degrees() {
        add(rect("a", 100, 100, 200, 120));
        select("a");
        Vec2 handle = fx(() -> canvas.getGeometry().quad("a").orElseThrow().rotateHandle(Handles.ROTATE_DISTANCE));
        Vec2 centre = view(200, 160);
        // 40 degrees clockwise from straight up
        double angle = Math.toRadians(-90 + 40);
        Vec2 target = new Vec2(centre.x() + 150 * Math.cos(angle), centre.y() + 150 * Math.sin(angle));

        pressView(handle, Keys.NONE);
        dragView(target, Keys.NONE);
        assertEquals(40, layer("a").base().transform().rotation().at(0), 1e-3);
        dragView(target, Keys.SHIFT);
        releaseView(target, Keys.SHIFT);

        assertEquals(45, layer("a").base().transform().rotation().at(0), 1e-6);
        assertVec(200, 160, position("a"));
        assertEquals("Rotate", doc.getUndoName());
        assertEquals("200 × 120   45°", ((Label) lookup(".overlay-readout")).getText());
    }

    @Test
    void altDragsTheAnchorWithoutMovingTheLayer() {
        add(rect("a", 100, 100, 200, 120));
        select("a");

        dragFromTo(200, 160, 100, 100, Keys.ALT);

        assertVec(0, 0, layer("a").base().transform().anchor().at(0));
        assertVec(100, 100, position("a"));
        assertVec(view(100, 100).x(), view(100, 100).y(),
                fx(() -> canvas.getGeometry().quad("a").orElseThrow().topLeft()));
    }

    @Test
    void theCursorShowsWhatThePointerWouldDo() {
        add(rect("a", 100, 100, 200, 120));
        select("a");

        move(300, 220);
        assertEquals(Cursor.SE_RESIZE, canvas.getCursor());
        move(300, 160);
        assertEquals(Cursor.E_RESIZE, canvas.getCursor());
        move(200, 140);
        assertEquals(Cursor.MOVE, canvas.getCursor());
        move(600, 500);
        assertEquals(Cursor.DEFAULT, canvas.getCursor());
        tool(ToolKind.RECTANGLE);
        move(600, 500);
        assertEquals(Cursor.CROSSHAIR, canvas.getCursor());
    }

    // ------------------------------------------------------------------ groups

    private GroupLayer rotatedGroup() {
        Transform quarterTurn = Transform.at(400, 300).withRotation(Animatable.of(90.0));
        return (GroupLayer) transformed(new GroupLayer(LayerBase.create("g", 3).withId("g"),
                List.of(rect("child", 0, 0, 100, 60), rect("other", 0, 100, 40, 40))), quarterTurn);
    }

    @Test
    void aClickSelectsTheGroupAndADoubleClickEntersIt() {
        add(rotatedGroup());
        // the child covers 0..100 x 0..60 in the group, which is turned by 90 degrees around (400, 300)
        Vec2 onChild = new Vec2(400 - 30, 300 + 50);

        click(onChild.x(), onChild.y());
        assertEquals(List.of("g"), selection());
        assertNull(canvas.getEnteredGroupId());

        doubleClick(onChild.x(), onChild.y());
        assertEquals("g", canvas.getEnteredGroupId());
        assertEquals(List.of("child"), selection());

        click(700, 100);
        assertNull(canvas.getEnteredGroupId());
        assertTrue(selection().isEmpty());
    }

    @Test
    void movingAChildOfATransformedGroupConvertsThePointerDelta() {
        add(rotatedGroup());
        Vec2 onChild = new Vec2(400 - 30, 300 + 50);
        doubleClick(onChild.x(), onChild.y());
        Vec2 before = position("child");

        dragFromTo(onChild.x(), onChild.y(), onChild.x() + 50, onChild.y(), Keys.CONTROL);

        // 50 px to the right on screen is 50 px up in the group that is turned by 90 degrees
        assertVec(before.x(), before.y() - 50, position("child"));
        key(KeyCode.ESCAPE);
        assertNull(canvas.getEnteredGroupId());
        assertEquals(List.of("g"), selection());
    }

    // ------------------------------------------------------------------ snapping

    @Test
    void aMoveSnapsToTheArtboardEdgeAndShowsAGuide() {
        add(rect("a", 100, 100, 200, 120));

        press(150, 150);
        drag(54, 150);
        layout();

        assertVec(100, 160, position("a"));
        List<Snapper.Guide> guides = canvas.getOverlay().getGuides();
        assertEquals(1, guides.size());
        assertTrue(guides.get(0).vertical());
        assertEquals(view(0, 0).x(), guides.get(0).position(), 1e-6);
        assertTrue(lookup(".overlay-guide").isVisible());

        release(54, 150);
        assertTrue(canvas.getOverlay().getGuides().isEmpty());
    }

    @Test
    void controlSwitchesSnappingOff() {
        add(rect("a", 100, 100, 200, 120));

        dragFromTo(150, 150, 54, 150, Keys.CONTROL);

        assertVec(104, 160, position("a"));
    }

    @Test
    void aMoveSnapsToAnotherLayer() {
        add(rect("a", 100, 100, 100, 100), rect("b", 400, 300, 100, 100));

        // the right edge of a ends 3 px before the left edge of b
        dragFromTo(150, 150, 347, 150);

        assertVec(350, 150, position("a"));
    }

    // ------------------------------------------------------------------ text

    @Test
    void theTextToolPlacesATextLayerAndEditsItInPlaceAsOneUndoStep() {
        tool(ToolKind.TEXT);
        click(200, 150);

        TextLayer placed = (TextLayer) layers().get(0);
        assertEquals("Text", placed.text());
        assertEquals("Text 1", placed.name());
        assertEquals(24, placed.fontSize().at(0));
        assertEquals(new Paint.Solid(NewLayers.TEXT_COLOR), placed.fill().at(0));
        assertVec(200, 150, placed.base().transform().position().at(0));
        TextField editor = (TextField) lookup("#" + TextTool.EDITOR_ID);
        assertNotNull(editor);

        run(() -> {
            editor.setText("Hello");
            Event.fireEvent(editor, new ActionEvent());
        });

        assertEquals("Hello", ((TextLayer) layers().get(0)).text());
        assertNull(lookup("#" + TextTool.EDITOR_ID));
        assertEquals(ToolKind.SELECT, canvas.getActiveTool());
        assertEquals(List.of(placed.id()), selection());
        run(doc::undo);
        assertTrue(layers().isEmpty());
        assertFalse(doc.isCanUndo());
    }

    @Test
    void escapeOnANewTextThatWasEmptiedRemovesTheLayer() {
        tool(ToolKind.TEXT);
        click(200, 150);
        TextField editor = (TextField) lookup("#" + TextTool.EDITOR_ID);

        run(() -> editor.setText(""));
        key(KeyCode.ESCAPE);

        assertTrue(layers().isEmpty());
        assertFalse(doc.isCanUndo());
        assertNull(lookup("#" + TextTool.EDITOR_ID));
    }

    @Test
    void aDoubleClickEditsAnExistingTextAndEscapeKeepsIt() {
        TextLayer text = NewLayers.text(doc.getAnimation(), new Vec2(200, 150), "Title");
        add(text);
        Box box = fx(() -> canvas.getGeometry().viewBounds(text.id()).orElseThrow());
        Vec2 inside = fx(() -> canvas.viewToDocument(new Vec2(box.centerX(), box.centerY())));

        doubleClick(inside.x(), inside.y());
        TextField editor = (TextField) lookup("#" + TextTool.EDITOR_ID);
        assertNotNull(editor);
        assertEquals("Title", editor.getText());
        run(() -> editor.setText("Changed"));
        key(KeyCode.ESCAPE);
        assertEquals("Title", ((TextLayer) layer(text.id())).text());

        doubleClick(inside.x(), inside.y());
        TextField again = (TextField) lookup("#" + TextTool.EDITOR_ID);
        run(() -> {
            again.setText("Changed");
            Event.fireEvent(again, new ActionEvent());
        });
        assertEquals("Changed", ((TextLayer) layer(text.id())).text());
        assertEquals("Edit Text", doc.getUndoName());
    }

    @Test
    void keysDoNotReachTheDocumentWhileATextInputHasTheFocus() {
        add(rect("a", 100, 100, 200, 120));
        TextLayer text = NewLayers.text(doc.getAnimation(), new Vec2(400, 400), "Title");
        add(text);
        select("a");
        run(() -> ((TextTool) canvas.getTool(ToolKind.TEXT)).edit(text.id()));
        TextField editor = (TextField) lookup("#" + TextTool.EDITOR_ID);
        assertSame(editor, fx(() -> window.getScene().getFocusOwner()));
        // with its skin in place the field handles keys itself, as it does on screen: Delete
        // removes the selected text; without this the outcome depends on when the next pulse runs
        layout();

        key(KeyCode.DELETE);
        key(KeyCode.RIGHT);
        key(KeyCode.SPACE);
        key(KeyCode.R);
        run(() -> window.menuItem("menu-edit-delete").fire());
        run(() -> window.menuItem("menu-edit-duplicate").fire());

        assertEquals(2, layers().size());
        assertVec(200, 160, position("a"));
        assertFalse(canvas.isPlaying());
        assertEquals(ToolKind.SELECT, canvas.getActiveTool());

        // select all goes to the text
        assertEquals("", fx(editor::getText), "Delete went to the text");
        run(() -> {
            editor.setText("Title");
            editor.deselect();
            window.menuItem("menu-edit-select-all").fire();
        });
        assertEquals("Title", editor.getSelectedText());
        assertEquals(List.of("a"), selection());
    }

    // ------------------------------------------------------------------ slots

    @Test
    void theSlotToolAsksForAUniqueJavaIdentifierAndCreatesTheSlot() {
        add(NewLayers.slot(doc.getAnimation(), "slot1", new Vec2(600, 500), new Vec2(50, 50)));
        tool(ToolKind.SLOT);

        dragFromTo(100, 100, 260, 148);

        assertEquals(1, layers().size(), "nothing is added before the name is given");
        TextField name = (TextField) lookup("#" + SlotTool.NAME_FIELD_ID);
        Label error = (Label) lookup("#" + SlotTool.NAME_ERROR_ID);
        assertEquals("slot2", name.getText());
        assertTrue(lookup(".overlay-preview").isVisible());

        run(() -> {
            name.setText("slot1");
            Event.fireEvent(name, new ActionEvent());
        });
        assertTrue(error.isVisible());
        assertTrue(error.getText().contains("already"));
        run(() -> {
            name.setText("2nd");
            Event.fireEvent(name, new ActionEvent());
        });
        assertTrue(error.getText().contains("identifier"));
        assertEquals(1, layers().size());

        run(() -> {
            name.setText("button");
            Event.fireEvent(name, new ActionEvent());
        });
        layout();

        SlotLayer slot = (SlotLayer) layers().get(1);
        assertEquals("button", slot.slotName());
        assertEquals(160, slot.width(), 1e-6);
        assertEquals(48, slot.height(), 1e-6);
        assertVec(180, 124, slot.base().transform().position().at(0));
        assertEquals(slot.base().transform(), slot.restTransform());
        assertEquals(List.of(slot.id()), selection());
        assertEquals(ToolKind.SELECT, canvas.getActiveTool());
        assertNull(lookup("#" + SlotTool.NAME_FIELD_ID));
        assertFalse(lookup(".overlay-preview").isVisible());
        assertEquals("Add Slot", doc.getUndoName());
    }

    @Test
    void escapeInTheSlotPromptGivesTheSlotUp() {
        tool(ToolKind.SLOT);
        dragFromTo(100, 100, 260, 148);
        assertNotNull(lookup("#" + SlotTool.NAME_FIELD_ID));

        key(KeyCode.ESCAPE);

        assertTrue(layers().isEmpty());
        assertNull(lookup("#" + SlotTool.NAME_FIELD_ID));
        assertFalse(doc.isCanUndo());
    }

    @Test
    void slotsAreDrawnInTheOverlayWithTheirName() {
        add(NewLayers.slot(doc.getAnimation(), "button", new Vec2(300, 200), new Vec2(200, 80)));

        List<Node> labels = fx(() -> List.copyOf(canvas.getOverlay().lookupAll(".overlay-slot-label")));
        List<Node> shapes = fx(() -> List.copyOf(canvas.getOverlay().lookupAll(".overlay-slot")));
        assertEquals(1, shapes.stream().filter(Node::isVisible).count());
        assertEquals("slot: button", ((javafx.scene.text.Text) labels.get(0)).getText());
        assertTrue(labels.get(0).isVisible());

        click(300, 200);
        assertEquals(1, selection().size(), "a slot is hit on its rectangle");
    }

    // ------------------------------------------------------------------ images

    private Path png(String name, int width, int height) throws IOException {
        Path file = dir.resolve(name);
        return java.nio.file.Files.write(file, PngEncoder.solid(width, height, 0xFF4C9AFF));
    }

    @Test
    void theImageToolAddsAssetAndLayerAsOneUndoStep() throws IOException {
        Path file = png("logo.png", 40, 20);
        run(() -> canvas.getActions().setImagePicker(owner -> Optional.of(file)));

        tool(ToolKind.IMAGE);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, doc.getAnimation().assets().size());
        assertEquals("logo.png", doc.getAnimation().assets().get(0).name());
        ImageLayer image = (ImageLayer) layers().get(0);
        assertEquals(doc.getAnimation().assets().get(0).id(), image.assetId());
        assertEquals(40, image.width());
        assertEquals(20, image.height());
        assertVec(400, 300, image.base().transform().position().at(0));
        assertNotNull(doc.getFileContent().assets().get(image.assetId()));
        assertEquals(List.of(image.id()), selection());
        assertEquals(ToolKind.SELECT, canvas.getActiveTool());

        run(doc::undo);
        assertTrue(layers().isEmpty());
        assertTrue(doc.getAnimation().assets().isEmpty());
        assertFalse(doc.isCanUndo());
    }

    @Test
    void aLargeImageIsScaledDownToTheArtboardAndACancelledDialogAddsNothing() throws IOException {
        Path file = png("big.png", 1600, 600);

        Optional<String> id = fx(() -> canvas.addImage(file));

        ImageLayer image = (ImageLayer) layer(id.orElseThrow());
        assertEquals(800, image.width(), 1e-6);
        assertEquals(300, image.height(), 1e-6);

        run(() -> canvas.getActions().setImagePicker(owner -> Optional.empty()));
        tool(ToolKind.IMAGE);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, layers().size());
        assertEquals(ToolKind.SELECT, canvas.getActiveTool());
    }

    @Test
    void aFileThatIsNoImageGivesANotice() throws IOException {
        Path file = dir.resolve("broken.png");
        java.nio.file.Files.writeString(file, "not an image");

        Optional<String> id = fx(() -> canvas.addImage(file));

        assertTrue(id.isEmpty());
        assertTrue(layers().isEmpty());
        assertTrue(window.getNoticeBar().getMessages().get(0).contains("broken.png"));
    }

    // ------------------------------------------------------------------ overlay

    @Test
    void aSingleSelectionShowsHandlesAndSeveralLayersShowOneBox() {
        add(rect("a", 100, 100, 200, 120), rect("b", 400, 300, 100, 100));
        select("a");

        List<Node> handles = fx(() -> List.copyOf(canvas.getOverlay().lookupAll(".overlay-handle")));
        assertEquals(9, handles.stream().filter(Node::isVisible).count(), "8 scale handles and the rotate handle");
        assertTrue(lookup(".overlay-selection").isVisible());
        assertTrue(lookup(".overlay-anchor").getParent().isVisible());
        assertEquals("200 × 120", ((Label) lookup(".overlay-readout")).getText());

        select("a", "b");
        handles = fx(() -> List.copyOf(canvas.getOverlay().lookupAll(".overlay-handle")));
        assertEquals(0, handles.stream().filter(Node::isVisible).count());
        javafx.scene.shape.Polygon box = (javafx.scene.shape.Polygon) lookup(".overlay-selection");
        assertTrue(box.isVisible());
        assertEquals(400, box.getBoundsInLocal().getWidth(), 2);
        assertEquals(300, box.getBoundsInLocal().getHeight(), 2);
    }

    @Test
    void theLayerUnderThePointerGetsAHoverOutlineUnlessItIsSelected() {
        add(rect("a", 100, 100, 200, 120));

        move(150, 150);
        assertEquals("a", canvas.getOverlay().getHover());
        assertTrue(lookup(".overlay-hover").isVisible());

        select("a");
        assertFalse(lookup(".overlay-hover").isVisible());
        move(600, 500);
        assertNull(canvas.getOverlay().getHover());
    }

    @Test
    void theOverlayFollowsTheZoom() {
        add(rect("a", 100, 100, 200, 120));
        select("a");
        javafx.scene.shape.Polygon box = (javafx.scene.shape.Polygon) lookup(".overlay-selection");
        javafx.scene.shape.Rectangle handle = (javafx.scene.shape.Rectangle) fx(() ->
                canvas.getOverlay().lookupAll(".overlay-handle").iterator().next());
        double handleSize = handle.getWidth();

        run(() -> canvas.zoomTo(2, new Vec2(0, 0)));
        layout();

        assertEquals(400, box.getBoundsInLocal().getWidth(), 2);
        assertEquals(handleSize, handle.getWidth());
    }

    @Test
    void editsOfALockedLayerAreNotPossibleOnTheCanvas() {
        Layer locked = rect("a", 100, 100, 200, 120);
        add(locked.withBase(locked.base().withLocked(true)));
        select("a");

        dragFromTo(300, 220, 400, 280);
        dragFromTo(150, 150, 190, 175);

        assertVec(1, 1, scale("a"));
        assertVec(200, 160, position("a"));
    }

    @Test
    void commandsFromOutsideDoNotBreakTheCanvas() {
        add(rect("a", 100, 100, 200, 120));
        select("a");

        run(() -> doc.execute(Commands.removeLayers(List.of("a"))));
        layout();

        assertFalse(lookup(".overlay-selection").isVisible());
        assertTrue(selection().isEmpty());
    }
}
