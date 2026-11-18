package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.editor.doc.Selection.KeyframeRef;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.dlsc.animatorfx.editor.doc.CommandsTest.rect;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorDocumentTest {

    private static final String POSITION = "transform.position";

    private static Command add(String id) {
        return Commands.addLayer(null, 0, rect(id));
    }

    @Test
    void anUntitledDocumentHasTheDefaultArtboardAndIsNotDirty() {
        EditorDocument document = EditorDocument.untitled();

        Animation animation = document.getAnimation();
        assertEquals(800, animation.width());
        assertEquals(600, animation.height());
        assertEquals(3, animation.duration());
        assertEquals(60, animation.frameRate());
        assertFalse(document.isDirty());
        assertNull(document.getPath());
        assertEquals("Untitled", document.getTitle());
        assertFalse(document.canUndoProperty().get());
        assertNotEquals(document.getId(), EditorDocument.untitled().getId());
    }

    @Test
    void executeAppliesTheCommandMarksDirtyAndEnablesUndo() {
        EditorDocument document = EditorDocument.untitled();

        document.execute(add("a"));

        assertEquals(1, document.getAnimation().layers().size());
        assertSame(document.getAnimation(), document.getFileContent().animation());
        assertTrue(document.isDirty());
        assertTrue(document.canUndoProperty().get());
        assertEquals("Add Layer", document.getUndoName());
    }

    @Test
    void undoAndRedoSwapWholeDocuments() {
        EditorDocument document = EditorDocument.untitled();
        Animation empty = document.getAnimation();
        document.execute(add("a"));
        Animation one = document.getAnimation();

        document.undo();
        assertSame(empty, document.getAnimation());
        assertFalse(document.canUndoProperty().get());
        assertTrue(document.canRedoProperty().get());
        assertEquals("Add Layer", document.getRedoName());

        document.redo();
        assertSame(one, document.getAnimation());
        assertFalse(document.canRedoProperty().get());
    }

    @Test
    void executeClearsTheRedoStack() {
        EditorDocument document = EditorDocument.untitled();
        document.execute(add("a"));
        document.undo();

        document.execute(add("b"));

        assertFalse(document.canRedoProperty().get());
    }

    @Test
    void aCommandThatChangesNothingCreatesNoUndoEntryAndDoesNotMarkDirty() {
        EditorDocument document = EditorDocument.untitled();

        document.execute(Commands.removeLayers(List.of("nope")));
        document.executeCoalesced("drag", Commands.removeLayers(List.of("nope")));

        assertFalse(document.isDirty());
        assertFalse(document.canUndoProperty().get());
    }

    @Test
    void coalescingCollapsesTenDragUpdatesIntoOneUndoStep() {
        EditorDocument document = EditorDocument.untitled();
        document.execute(add("a"));
        Animation beforeDrag = document.getAnimation();
        Object gesture = new Object();

        for (int i = 1; i <= 10; i++) {
            document.executeCoalesced(gesture,
                    Commands.setValueAt("a", POSITION, 0, new com.dlsc.animatorfx.model.Vec2(i, i), false));
        }
        document.endGesture();

        assertEquals(new com.dlsc.animatorfx.model.Vec2(10, 10), document.getAnimation().layers().get(0)
                .base().transform().position().at(0));
        document.undo();
        assertSame(beforeDrag, document.getAnimation());
        document.undo();
        assertFalse(document.canUndoProperty().get());
    }

    @Test
    void endGestureStartsANewUndoStepForTheSameKey() {
        EditorDocument document = EditorDocument.untitled();
        document.execute(add("a"));
        Object gesture = "drag";
        document.executeCoalesced(gesture, Commands.setValueAt("a", "opacity", 0, 0.5, false));
        Animation afterFirst = document.getAnimation();
        document.endGesture();

        document.executeCoalesced(gesture, Commands.setValueAt("a", "opacity", 0, 0.2, false));
        document.undo();

        assertSame(afterFirst, document.getAnimation());
    }

    @Test
    void anOrdinaryExecuteBetweenCoalescedCallsBreaksTheGesture() {
        EditorDocument document = EditorDocument.untitled();
        document.execute(add("a"));
        document.executeCoalesced("drag", Commands.setValueAt("a", "opacity", 0, 0.5, false));
        document.execute(add("b"));
        Animation afterAdd = document.getAnimation();

        document.executeCoalesced("drag", Commands.setValueAt("a", "opacity", 0, 0.2, false));
        document.undo();

        assertSame(afterAdd, document.getAnimation());
    }

    @Test
    void saveClearsDirtyAndUndoPastTheSavePointSetsItAgain(@TempDir Path dir) throws IOException {
        EditorDocument document = EditorDocument.untitled();
        document.execute(add("a"));
        document.saveAs(dir.resolve("one.afx"));

        assertFalse(document.isDirty());
        assertEquals("one.afx", document.getTitle());
        assertEquals(dir.resolve("one.afx"), document.getPath());

        document.execute(add("b"));
        assertTrue(document.isDirty());
        document.undo();
        assertFalse(document.isDirty());
        document.undo();
        assertTrue(document.isDirty());
        document.redo();
        assertFalse(document.isDirty());
    }

    @Test
    void saveWithoutAPathIsRejected() {
        EditorDocument document = EditorDocument.untitled();
        assertThrows(IllegalStateException.class, document::save);
    }

    @Test
    void openReadsTheFileAndIsNotDirty(@TempDir Path dir) throws IOException {
        EditorDocument first = EditorDocument.untitled();
        first.execute(add("a"));
        first.saveAs(dir.resolve("doc.afx"));

        EditorDocument opened = EditorDocument.open(dir.resolve("doc.afx"));

        assertEquals(first.getAnimation(), opened.getAnimation());
        assertFalse(opened.isDirty());
        assertEquals("doc.afx", opened.getTitle());
    }

    @Test
    void addAssetStoresTheBytesIsUndoableAndIsSaved(@TempDir Path dir) throws IOException {
        EditorDocument document = EditorDocument.untitled();
        byte[] bytes = {1, 2, 3};

        document.addAsset(new Asset("img", Asset.Kind.IMAGE, "assets/img.png", "img.png"), bytes);

        assertEquals(1, document.getAnimation().assets().size());
        assertArrayEquals(bytes, document.getFileContent().assets().get("img"));
        assertTrue(document.isDirty());

        document.saveAs(dir.resolve("with-asset.afx"));
        assertArrayEquals(bytes, AnimationIO.read(dir.resolve("with-asset.afx")).assets().get("img"));

        document.undo();
        assertTrue(document.getAnimation().assets().isEmpty());
        assertTrue(document.getFileContent().assets().isEmpty());
        assertTrue(document.isDirty());
    }

    @Test
    void revertReloadsTheFileAndDropsTheUndoHistory(@TempDir Path dir) throws IOException {
        EditorDocument document = EditorDocument.untitled();
        document.execute(add("a"));
        document.saveAs(dir.resolve("doc.afx"));
        document.execute(add("b"));

        document.revert();

        assertEquals(1, document.getAnimation().layers().size());
        assertFalse(document.isDirty());
        assertFalse(document.canUndoProperty().get());
    }

    @Test
    void aRecoveredDocumentKeepsItsIdAndPathAndIsDirtyUntilSaved(@TempDir Path dir) throws IOException {
        Animation animation = Animation.create("Recovered", 100, 50, 1);
        Path path = dir.resolve("r.afx");

        EditorDocument document = EditorDocument.recovered("doc-1", new AnimationFile(animation, Map.of()), path);

        assertEquals("doc-1", document.getId());
        assertEquals(path, document.getPath());
        assertTrue(document.isDirty());
        document.save();
        assertFalse(document.isDirty());
    }

    @Test
    void theSelectionDropsLayersAndKeyframesThatNoLongerExist() {
        EditorDocument document = EditorDocument.untitled();
        document.execute(add("a"));
        document.execute(add("b"));
        document.execute(Commands.setValueAt("a", "opacity", 1, 0.5, true));
        String keyId = document.getAnimation().layers().get(1).base().opacity().keyframes().get(0).id();
        document.getSelection().layerIds().setAll("a", "b");
        document.getSelection().keyframes().add(new KeyframeRef("a", "opacity", keyId));

        document.undo();
        assertEquals(List.of("a", "b"), document.getSelection().layerIds());
        assertTrue(document.getSelection().keyframes().isEmpty());

        document.execute(Commands.removeLayers(List.of("a")));
        assertEquals(List.of("b"), document.getSelection().layerIds());
    }

    @Test
    void viewStateStartsWithNeutralDefaults() {
        ViewState view = EditorDocument.untitled().getViewState();

        assertEquals(1.0, view.getCanvasZoom());
        assertEquals(0.0, view.getCanvasPanX());
        assertEquals(1.0, view.getTimelineZoom());
        assertTrue(view.getExpandedLayerIds().isEmpty());
        assertFalse(view.isShowAllProperties());
    }

    @Test
    void aGestureThatEndsWhereItStartedLeavesNoUndoStepAndKeepsTheRedoHistory() {
        EditorDocument document = EditorDocument.untitled();
        document.execute(add("a"));
        document.execute(add("b"));
        document.undo();
        Animation beforeDrag = document.getAnimation();
        Vec2 start = beforeDrag.layers().get(0).base().transform().position().at(0);

        document.executeCoalesced("drag", Commands.setValueAt("a", POSITION, 0, new Vec2(40, 40), false));
        assertFalse(document.canRedoProperty().get());
        document.executeCoalesced("drag", Commands.setValueAt("a", POSITION, 0, start, false));
        document.endGesture();

        assertSame(beforeDrag, document.getAnimation());
        assertEquals("Add Layer", document.getUndoName());
        assertTrue(document.canRedoProperty().get());
        document.undo();
        assertFalse(document.canUndoProperty().get());
    }

    @Test
    void cancelGestureRestoresTheStateBeforeTheGesture() {
        EditorDocument document = EditorDocument.untitled();
        document.execute(add("a"));
        Animation beforeDrag = document.getAnimation();
        document.executeCoalesced("drag", Commands.setValueAt("a", POSITION, 0, new Vec2(40, 40), false));
        document.executeCoalesced("drag", Commands.setValueAt("a", POSITION, 0, new Vec2(80, 80), false));

        assertTrue(document.cancelGesture());

        assertSame(beforeDrag, document.getAnimation());
        assertEquals("Add Layer", document.getUndoName());
        assertFalse(document.canRedoProperty().get());
        assertFalse(document.cancelGesture());

        // a finished gesture is not cancelled any more
        document.executeCoalesced("drag", Commands.setValueAt("a", POSITION, 0, new Vec2(40, 40), false));
        document.endGesture();
        assertFalse(document.cancelGesture());
        assertEquals(new Vec2(40, 40), document.getAnimation().layers().get(0).base().transform().position().at(0));
    }

    @Test
    void executeWithAssetsAddsAssetsAndLayersAsOneUndoStep() {
        EditorDocument document = EditorDocument.untitled();
        Asset asset = new Asset("img", Asset.Kind.IMAGE, "assets/img.png", "img.png");

        document.execute(add("a"), Map.of(asset, new byte[]{1, 2, 3}));

        assertEquals(List.of(asset), document.getAnimation().assets());
        assertArrayEquals(new byte[]{1, 2, 3}, document.getFileContent().assets().get("img"));
        assertEquals(1, document.getAnimation().layers().size());
        assertEquals("Add Layer", document.getUndoName());

        // an asset the document already has is not added again
        document.execute(add("b"), Map.of(asset, new byte[]{9}));
        assertEquals(1, document.getAnimation().assets().size());
        assertArrayEquals(new byte[]{1, 2, 3}, document.getFileContent().assets().get("img"));

        document.undo();
        document.undo();
        assertTrue(document.getAnimation().assets().isEmpty());
        assertTrue(document.getFileContent().assets().isEmpty());
        assertFalse(document.canUndoProperty().get());
    }
}
