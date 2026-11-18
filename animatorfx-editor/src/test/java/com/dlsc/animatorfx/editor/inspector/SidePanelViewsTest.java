package com.dlsc.animatorfx.editor.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dlsc.animatorfx.editor.canvas.tools.NewLayers;
import com.dlsc.animatorfx.editor.inspector.fields.ColorField;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.editor.inspector.fields.TextValueField;
import com.dlsc.animatorfx.editor.timeline.Markers;
import com.dlsc.animatorfx.editor.ui.EditorUiTest;
import com.dlsc.animatorfx.editor.ui.PngEncoder;
import com.dlsc.animatorfx.editor.ui.SidePanel;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.Vec2;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Assets and Document tabs. */
class SidePanelViewsTest extends EditorUiTest {

    private AssetsView assets;
    private DocumentView documentView;

    @BeforeEach
    void tabs() {
        assets = window.getSidePanel().getAssets();
        documentView = window.getSidePanel().getDocumentView();
    }

    private void showTab(String id) {
        run(() -> window.getSidePanel().setActiveTab(id));
        layout();
    }

    private Path png(String name, int width, int height) throws IOException {
        WritableImage image = fx(() -> {
            WritableImage out = new WritableImage(width, height);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    out.getPixelWriter().setColor(x, y, Color.CORAL);
                }
            }
            return out;
        });
        Path file = dir.resolve(name);
        Files.write(file, PngEncoder.encode(image));
        return file;
    }

    private void type(TextField field, String text) {
        run(() -> {
            field.requestFocus();
            field.setText(text);
            Event.fireEvent(field, new ActionEvent());
        });
        layout();
    }

    private List<Node> assetRows() {
        return fx(() -> List.copyOf(((Pane) assets.lookup("#" + AssetsView.LIST_ID)).getChildren()));
    }

    // ------------------------------------------------------------------ assets

    @Test
    void assetsAreAddedListedPlacedAndRemovedWithUndo() throws IOException {
        showTab(SidePanel.ASSETS);
        Path file = png("logo.png", 40, 20);
        run(() -> assets.setFilePicker(owner -> List.of(file)));
        run(() -> ((ButtonBase) assets.lookup("#" + AssetsView.ADD_ID)).fire());
        layout();
        assertEquals(1, doc.getAnimation().assets().size());
        Asset asset = doc.getAnimation().assets().get(0);
        assertEquals(Asset.Kind.IMAGE, asset.kind());
        assertEquals("logo.png", asset.name());
        assertNotNull(doc.getFileContent().assets().get(asset.id()));
        assertEquals("Add Asset", doc.getUndoName());
        assertEquals(1, assetRows().size());
        Label detail = (Label) assetRows().get(0).lookup(".asset-detail");
        assertTrue(detail.getText().startsWith("Image · "), detail.getText());
        assertTrue(detail.getText().endsWith("KB · unused"), detail.getText());

        // place it on the canvas: an image layer of the natural size in the middle
        run(() -> ((ButtonBase) assetRows().get(0).lookup(".asset-place")).fire());
        layout();
        ImageLayer layer = assertInstanceOf(ImageLayer.class, doc.getAnimation().layers().get(0));
        assertEquals(asset.id(), layer.assetId());
        assertEquals(40, layer.width(), 1e-9);
        assertEquals(new Vec2(400, 300), layer.base().transform().position().at(0));
        assertEquals(List.of(layer.id()), selection());
        assertTrue(fx(detail::getText).endsWith("used 1×"));

        // in use: removing is refused with a message
        run(() -> ((ButtonBase) assetRows().get(0).lookup(".asset-remove")).fire());
        assertEquals(1, doc.getAnimation().assets().size());
        assertTrue(fx(assets::getMessage).contains("logo.png"));
        assertTrue(fx(assets::getMessage).contains("1 layer"));
        com.dlsc.animatorfx.editor.doc.RoundTrip.of(doc.getFileContent());

        run(() -> doc.undo());
        layout();
        run(() -> ((ButtonBase) assetRows().get(0).lookup(".asset-remove")).fire());
        layout();
        assertEquals(0, doc.getAnimation().assets().size());
        assertTrue(doc.getFileContent().assets().isEmpty());
        assertNull(fx(assets::getMessage));
        assertEquals("Remove Asset", doc.getUndoName());
        com.dlsc.animatorfx.editor.doc.RoundTrip.of(doc.getFileContent());
        assertEquals(0, assetRows().size());
        run(() -> doc.undo());
        layout();
        assertEquals(1, doc.getAnimation().assets().size());
        assertNotNull(doc.getFileContent().assets().get(asset.id()), "undo brings the bytes back");
        assertEquals(1, assetRows().size());
    }

    @Test
    void filesThatAreNoImagesOrFontsAreRefusedWithAMessage() throws IOException {
        Path text = dir.resolve("notes.txt");
        Files.writeString(text, "hello");
        Path fake = dir.resolve("broken.png");
        Files.writeString(fake, "not a png");
        Path font = dir.resolve("broken.ttf");
        Files.writeString(font, "not a font");
        run(() -> assets.addFiles(List.of(text, fake, font, dir.resolve("missing.png"))));
        assertEquals(0, doc.getAnimation().assets().size());
        String message = fx(assets::getMessage);
        assertTrue(message.contains("notes.txt"), message);
        assertTrue(message.contains("broken.png cannot be read as an image"), message);
        assertTrue(message.contains("broken.ttf cannot be read as a font"), message);
        assertTrue(message.contains("missing.png"), message);
        assertFalse(doc.isCanUndo());
    }

    // ------------------------------------------------------------------ document

    @Test
    void theDocumentTabEditsTheSettings() {
        showTab(SidePanel.DOCUMENT);
        type(lookup("#" + DocumentView.NAME_ID), "Checkout");
        assertEquals("Checkout", doc.getAnimation().name());
        assertEquals("Rename Document", doc.getUndoName());
        NumberField width = lookup("#" + DocumentView.WIDTH_ID);
        assertEquals("800", fx(width::getText));
        type(width, "400");
        type(lookup("#" + DocumentView.HEIGHT_ID), "240");
        assertEquals(400, doc.getAnimation().width(), 1e-9);
        assertEquals(240, doc.getAnimation().height(), 1e-9);
        NumberField frameRate = lookup("#" + DocumentView.FRAME_RATE_ID);
        assertEquals("60 fps", fx(frameRate::getText));
        type(frameRate, "30");
        assertEquals(30, doc.getAnimation().frameRate(), 1e-9);
        type(frameRate, "24fps");
        assertEquals(24, doc.getAnimation().frameRate(), 1e-9);

        ColorField background = lookup("#" + DocumentView.BACKGROUND_ID);
        assertEquals("#00000000", fx(() -> background.getHexField().getText()), "transparent");
        type(background.getHexField(), "#101820");
        assertEquals(Rgba.parse("#101820"), doc.getAnimation().background());
        run(doc::undo);
        layout();
        assertEquals("#00000000", fx(() -> background.getHexField().getText()));
    }

    @Test
    void shorteningTheDurationDeletesNothing() {
        add(rect("a", 0, 0, 10, 10));
        execute(com.dlsc.animatorfx.editor.doc.Commands.setValueAt("a", "opacity", 2.5, 0.5, true));
        showTab(SidePanel.DOCUMENT);
        NumberField duration = lookup("#" + DocumentView.DURATION_ID);
        assertEquals("3s", fx(duration::getText));
        type(duration, "1.5");
        assertEquals(1.5, doc.getAnimation().duration(), 1e-9);
        assertEquals(3.0, layer("a").base().outPoint(), 1e-9);
        assertEquals(2.5, keyframes("a", "opacity").get(0).time(), 1e-9);
        type(duration, "0");
        assertEquals(1 / 60.0, doc.getAnimation().duration(), 1e-9, "at least one frame");
    }

    @Test
    void theMarkerListStaysInSyncWithTheTimeline() {
        showTab(SidePanel.DOCUMENT);
        playhead(1.0);
        run(() -> ((ButtonBase) documentView.lookup("#" + DocumentView.ADD_MARKER_ID)).fire());
        layout();
        assertEquals(List.of(new Marker("marker1", 1.0, 1.5)), doc.getAnimation().markers());
        Pane list = (Pane) fx(() -> documentView.lookup("#" + DocumentView.MARKERS_ID));
        assertEquals(1, fx(() -> list.getChildren().size()));
        Node row = fx(() -> list.getChildren().get(0));
        TextValueField name = (TextValueField) row.lookup(".marker-name");
        List<Node> times = List.copyOf(row.lookupAll(".marker-time"));
        assertEquals("marker1", fx(name::getText));
        assertEquals("1s", fx(() -> ((NumberField) times.get(0)).getText()));

        // a change made in the timeline shows here, in the same row
        execute(Markers.setRange("Move Marker", "marker1", 0.25, 0.75));
        assertEquals("0.25s", fx(() -> ((NumberField) times.get(0)).getText()));
        assertEquals("0.75s", fx(() -> ((NumberField) times.get(1)).getText()));
        assertEquals(row, fx(() -> list.getChildren().get(0)));

        type((NumberField) times.get(1), "2");
        assertEquals(new Marker("marker1", 0.25, 2.0), doc.getAnimation().markers().get(0));
        type(name, "intro");
        assertEquals("intro", doc.getAnimation().markers().get(0).name());

        // names are unique
        execute(Markers.add(2.5));
        Node second = fx(() -> list.getChildren().get(1));
        TextValueField secondName = (TextValueField) second.lookup(".marker-name");
        run(() -> {
            secondName.requestFocus();
            secondName.setText("intro");
        });
        assertTrue(fx(() -> secondName.getError().isPresent()));
        run(() -> Event.fireEvent(secondName, new ActionEvent()));
        assertEquals("marker1", doc.getAnimation().markers().get(1).name());
        run(() -> canvas.requestFocus());

        run(() -> ((ButtonBase) second.lookup(".marker-remove")).fire());
        layout();
        assertEquals(1, doc.getAnimation().markers().size());
        assertEquals(1, fx(() -> list.getChildren().size()));
    }

    @Test
    void copyJavaSnippetPutsTheCodeForThisDocumentOnTheClipboard() {
        add(NewLayers.slot(doc.getAnimation(), "button", new Vec2(50, 50), new Vec2(80, 40)));
        execute(Markers.add(0));
        List<String> copied = new ArrayList<>();
        run(() -> documentView.setClipboard(copied::add));
        run(() -> ((ButtonBase) documentView.lookup("#" + DocumentView.COPY_SNIPPET_ID)).fire());
        assertEquals(1, copied.size());
        String code = copied.get(0);
        assertTrue(code.contains("public final class UntitledAnimation {"), code);
        assertTrue(code.contains("view.bind(\"button\", button);"));
        assertTrue(code.contains("view.playMarker(\"marker1\");"));
        assertTrue(code.contains("getResource(\"untitled.afx\")"));
        assertEquals(code, fx(documentView::getSnippet));
    }
}
