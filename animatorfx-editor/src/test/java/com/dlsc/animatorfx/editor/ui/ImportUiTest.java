package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * File ▸ Import in the running window. With {@code -Danimatorfx.screenshots=DIR} every import
 * also writes a picture of the window ({@code task-10-svg.png}, …) into that directory.
 */
class ImportUiTest extends EditorUiTest {

    private final List<Path> picked = new ArrayList<>();
    private final List<List<String>> shownWarnings = new ArrayList<>();
    private ImportActions imports;

    @BeforeEach
    void stubThePickers() {
        imports = window.getImportActions();
        imports.setFilePicker((owner, kind) -> picked.isEmpty() ? Optional.empty() : Optional.of(picked.remove(0)));
        imports.setWarningsViewer((title, lines) -> shownWarnings.add(lines));
    }

    private Path fixture(String name) throws IOException {
        Path file = dir.resolve(name);
        try (InputStream in = Objects.requireNonNull(ImportUiTest.class.getResourceAsStream("import/" + name), name)) {
            Files.copy(in, file);
        }
        return file;
    }

    private void fire(String menuItemId) {
        run(() -> window.menuItem(menuItemId).fire());
        WaitForAsyncUtils.waitForFxEvents();
        layout();
    }

    private EditorDocument active() {
        return fx(() -> window.getWorkspace().getActiveDocument());
    }

    private static List<String> names(List<Layer> layers) {
        return layers.stream().map(Layer::name).toList();
    }

    private void screenshot(String name) throws IOException {
        String directory = System.getProperty("animatorfx.screenshots");
        if (directory == null || directory.isBlank()) {
            return;
        }
        WaitForAsyncUtils.waitForFxEvents();
        layout();
        WritableImage image = fx(() -> window.getScene().snapshot(null));
        Files.createDirectories(Path.of(directory));
        Files.write(Path.of(directory, name), PngEncoder.encode(image));
    }

    @Test
    void theImportItemsAreEnabled() {
        for (String id : List.of("menu-file-import-svg", "menu-file-import-svg-into", "menu-file-import-lottie",
                "menu-file-import-fxml", "menu-file-import-image")) {
            assertFalse(fx(() -> window.menuItem(id).isDisable()), id);
        }
    }

    @Test
    void importingAnSvgOpensANewUntitledDocument() throws IOException {
        picked.add(fixture("badge.svg"));
        fire("menu-file-import-svg");

        EditorDocument imported = active();
        // the untouched untitled document made way, as it does for an opened file
        assertEquals(List.of(imported), window.getWorkspace().getDocuments());
        assertNull(imported.getPath());
        assertTrue(imported.isDirty());
        assertEquals("badge", imported.getTitle());
        assertFalse(imported.isCanUndo());

        Animation animation = imported.getAnimation();
        assertEquals("badge", animation.name());
        assertEquals(480, animation.width());
        assertEquals(320, animation.height());
        assertEquals(List.of("frame", "scene", "border", "banner"), names(animation.layers()));
        GroupLayer scene = assertInstanceOf(GroupLayer.class, animation.layers().get(1));
        assertEquals(animation.layers().get(0).id(), scene.base().mask().layerId());
        assertEquals(List.of("background", "sunDisc", "stars", "hills", "hillsFront", "flag"), names(scene.children()));

        // one notice with the summary; its action lists the warnings
        assertEquals(List.of("Imported badge.svg with 2 warnings."), fx(() -> window.getNoticeBar().getMessages()));
        run(() -> ((Button) window.getNoticeBar().lookup(".notice-action")).fire());
        assertEquals(1, shownWarnings.size());
        assertEquals(List.of("Filters are not supported (1 element ignored)",
                "Text is placed approximately: the baseline and the width are estimated without the font (1 text)"),
                shownWarnings.get(0));
        screenshot("task-10-svg.png");
    }

    @Test
    void importingALottieFileOpensAnAnimatedDocument() throws IOException {
        picked.add(fixture("loader.json"));
        fire("menu-file-import-lottie");

        EditorDocument imported = active();
        assertTrue(imported.isDirty());
        assertEquals("Loader", imported.getTitle());
        Animation animation = imported.getAnimation();
        assertEquals(3, animation.duration());
        assertEquals(30, animation.frameRate());
        assertEquals(List.of("Backdrop", "Badge", "Ring", "Check", "Rig (parent transform)", "Rig"),
                names(animation.layers()));
        assertEquals(List.of("Orbit dot"), names(((GroupLayer) animation.layers().get(4)).children()));
        assertEquals(List.of("intro", "loop"), animation.markers().stream().map(Marker::name).toList());
        assertTrue(Layers.find(animation, animation.layers().get(4).id()).orElseThrow().base().transform()
                .rotation().isAnimated());
        // nothing was left out: no notice
        assertEquals(List.of(), fx(() -> window.getNoticeBar().getMessages()));

        run(() -> imported.setPlayhead(1.5));
        screenshot("task-10-lottie.png");
    }

    @Test
    void importingFxmlMakesSlotsWithPreviews() throws IOException {
        picked.add(fixture("login.fxml"));
        fire("menu-file-import-fxml");

        EditorDocument imported = active();
        assertTrue(imported.isDirty());
        assertEquals("login", imported.getTitle());
        Animation animation = imported.getAnimation();
        assertEquals(420, animation.width());
        assertEquals(300, animation.height());
        List<String> slots = List.of("heading", "hint", "userField", "passwordField", "remember", "buttonRow",
                "cancelButton", "loginButton");
        assertEquals(slots, names(animation.layers()));
        animation.layers().forEach(layer -> assertInstanceOf(SlotLayer.class, layer));

        // the pictures of the nodes are session data of the document, shown inside the slot rectangles
        assertEquals(slots.size(), imported.getSlotPreviews().size());
        assertTrue(imported.getSlotPreviews().keySet().containsAll(slots));
        List<Node> pictures = fx(() -> List.copyOf(canvas.lookupAll(".overlay-slot-preview")));
        assertEquals(slots.size(), pictures.stream().filter(Node::isVisible).count());
        ImageView first = (ImageView) pictures.get(0);
        assertSame(imported.getSlotPreviews().get("heading"), first.getImage());
        assertEquals(0.7, first.getOpacity(), 1e-9);

        // they survive edits, undo and redo
        run(() -> {
            imported.execute(Commands.updateLayer("Rename", animation.layers().get(0).id(),
                    layer -> layer.withBase(layer.base().withName("Title"))));
            imported.undo();
            imported.redo();
        });
        assertEquals(slots.size(), imported.getSlotPreviews().size());

        assertEquals(List.of("Imported login.fxml with 2 warnings."), fx(() -> window.getNoticeBar().getMessages()));

        // imported slots stay unique when they are duplicated, so the document can be saved and opened again
        run(() -> imported.execute(Commands.duplicateLayers(animation.layers().stream().map(Layer::id).toList())));
        assertEquals(2 * slots.size(), Layers.slots(imported.getAnimation()).stream().map(SlotLayer::slotName).distinct().count());
        com.dlsc.animatorfx.editor.doc.RoundTrip.of(imported.getFileContent());
        screenshot("task-10-fxml.png");
    }

    @Test
    void anSvgCanBeImportedIntoTheCurrentDocumentAsOneGroup() throws IOException {
        ShapeLayer existing = new ShapeLayer(LayerBase.create("Existing", 3),
                new Geometry.Rect(com.dlsc.animatorfx.model.Animatable.of(new Vec2(10, 10)),
                        com.dlsc.animatorfx.model.Animatable.of(0.0)),
                com.dlsc.animatorfx.model.Animatable.of(new com.dlsc.animatorfx.model.Paint.None()), null, null);
        run(() -> {
            doc.execute(Commands.setDocument("Duration", a -> a.withDuration(5)));
            doc.execute(Commands.addLayer(null, 0, existing));
        });
        String png = Base64.getEncoder().encodeToString(PngEncoder.solid(2, 2, 0xFF336699));
        Path file = dir.resolve("picture.svg");
        Files.writeString(file, "<svg xmlns='http://www.w3.org/2000/svg' width='40' height='20'>"
                + "<rect id='box' width='40' height='20' fill='teal'/>"
                + "<g id='inner'><image id='photo' width='2' height='2' href='data:image/png;base64," + png + "'/></g>"
                + "<filter id='f'/></svg>");
        picked.add(file);
        fire("menu-file-import-svg-into");

        // the same document, with one group on top
        assertSame(doc, active());
        Animation animation = doc.getAnimation();
        assertEquals(List.of("Existing", "picture"), names(animation.layers()));
        GroupLayer group = assertInstanceOf(GroupLayer.class, animation.layers().get(1));
        assertEquals(List.of("box", "inner"), names(group.children()));
        assertEquals(List.of(group.id()), List.copyOf(doc.getSelection().layerIds()));
        // the imported layers live as long as this document
        for (Layer layer : List.of(group, group.children().get(0), ((GroupLayer) group.children().get(1)).children().get(0))) {
            assertEquals(0, layer.base().inPoint());
            assertEquals(5, layer.base().outPoint());
        }
        // the image came along
        assertEquals(1, animation.assets().size());
        Asset asset = animation.assets().get(0);
        assertNotNull(doc.getFileContent().assets().get(asset.id()));
        assertEquals(List.of("Imported picture.svg with 1 warning."), fx(() -> window.getNoticeBar().getMessages()));

        // layers and asset are one undo step
        assertEquals("Import SVG", doc.getUndoName());
        run(doc::undo);
        assertEquals(List.of("Existing"), names(doc.getAnimation().layers()));
        assertEquals(List.of(), doc.getAnimation().assets());
        run(doc::redo);
        assertEquals(2, doc.getAnimation().layers().size());
        assertEquals(1, doc.getAnimation().assets().size());

        // the same file a second time: new layer and asset ids, and a document that can be opened again
        picked.add(file);
        fire("menu-file-import-svg-into");
        assertEquals(3, doc.getAnimation().layers().size());
        assertEquals(2, doc.getAnimation().assets().size());
        com.dlsc.animatorfx.editor.doc.RoundTrip.of(doc.getFileContent());
    }

    @Test
    void aFileThatCannotBeImportedGivesANotice() throws IOException {
        Path broken = dir.resolve("broken.svg");
        Files.writeString(broken, "this is not xml");
        picked.add(broken);
        fire("menu-file-import-svg");
        Path notLottie = dir.resolve("data.json");
        Files.writeString(notLottie, "{\"hello\": 1}");
        picked.add(notLottie);
        fire("menu-file-import-lottie");
        Path needsController = dir.resolve("bound.fxml");
        Files.writeString(needsController, "<?import javafx.scene.control.Label?>"
                + "<Label xmlns:fx='http://javafx.com/fxml' fx:controller='com.example.Gone' text='${controller.title}'/>");
        picked.add(needsController);
        fire("menu-file-import-fxml");
        picked.add(dir.resolve("missing.svg"));
        fire("menu-file-import-svg-into");

        assertSame(doc, active());
        assertEquals(1, window.getWorkspace().getDocuments().size());
        assertFalse(doc.isCanUndo());
        List<String> messages = fx(() -> window.getNoticeBar().getMessages());
        assertEquals(4, messages.size());
        assertTrue(messages.get(0).startsWith("Could not import broken.svg: "), messages.get(0));
        assertTrue(messages.get(1).startsWith("Could not import data.json: "), messages.get(1));
        assertTrue(messages.get(2).startsWith("Could not import bound.fxml: "), messages.get(2));
        assertTrue(messages.get(2).contains("com.example.Gone"), messages.get(2));
        assertTrue(messages.get(3).startsWith("Could not import missing.svg: "), messages.get(3));
    }

    @Test
    void cancellingThePickerDoesNothing() {
        fire("menu-file-import-svg");
        fire("menu-file-import-svg-into");
        fire("menu-file-import-lottie");
        fire("menu-file-import-fxml");
        assertSame(doc, active());
        assertEquals(List.of(), fx(() -> window.getNoticeBar().getMessages()));
    }

    @Test
    void droppedFilesAreOpenedOrImportedByTheirExtension() throws IOException {
        Path saved = dir.resolve("saved.afx");
        run(() -> {
            try {
                doc.saveAs(saved);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });
        List<File> files = List.of(fixture("badge.svg").toFile(), fixture("loader.json").toFile(),
                fixture("login.fxml").toFile(), saved.toFile(), dir.resolve("notes.txt").toFile());
        assertTrue(files.subList(0, 4).stream().allMatch(MainWindow::isDroppable));
        assertFalse(MainWindow.isDroppable(files.get(4)));
        // a file with an upper-case extension counts too
        Path shouting = dir.resolve("SHOUT.SVG");
        Files.copy(files.get(0).toPath(), shouting);
        assertTrue(MainWindow.isDroppable(shouting.toFile()));

        assertTrue(fx(() -> window.openDropped(files)));
        WaitForAsyncUtils.waitForFxEvents();
        List<EditorDocument> documents = List.copyOf(window.getWorkspace().getDocuments());
        assertEquals(List.of("saved.afx", "badge", "Loader", "login"),
                documents.stream().map(EditorDocument::getTitle).toList());
        assertFalse(fx(() -> window.openDropped(List.of(files.get(4)))));
    }
}
