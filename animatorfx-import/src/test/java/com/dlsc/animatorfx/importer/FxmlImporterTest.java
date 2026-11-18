package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import javafx.scene.image.Image;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static com.dlsc.animatorfx.importer.ImportChecks.assertVec;
import static com.dlsc.animatorfx.importer.ImportChecks.names;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxmlImporterTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    private static URL fixture(String name) {
        return Objects.requireNonNull(FxmlImporterTest.class.getResource("fxml/" + name + ".fxml"), name);
    }

    private static <T> T onFxThread(Callable<T> action) throws Exception {
        try {
            return WaitForAsyncUtils.asyncFx(action).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static FxmlImporter.Result read(String name) throws Exception {
        FxmlImporter.Result result = onFxThread(() -> FxmlImporter.read(fixture(name)));
        ImportChecks.assertSavesAndRenders(result.file());
        return result;
    }

    private static SlotLayer slot(Animation animation, String name) {
        return ImportChecks.named(animation, name, SlotLayer.class);
    }

    @Test
    void nodesWithAnFxIdBecomeSlotsWithTheirLaidOutBounds() throws Exception {
        FxmlImporter.Result result = read("three-slots");
        Animation animation = result.file().animation();
        assertEquals("three-slots", animation.name());
        assertEquals(300, animation.width());
        assertEquals(200, animation.height());
        assertEquals(3, animation.duration());
        assertEquals(60, animation.frameRate());
        assertEquals(Rgba.TRANSPARENT, animation.background());
        assertEquals(List.of(), animation.assets());

        // the node of fx:define is not part of the scene graph
        assertEquals(List.of("title", "okButton", "swatch"), names(animation.layers()));

        SlotLayer title = slot(animation, "title");
        assertEquals("title", title.slotName());
        assertVec(5, 5, title.base().transform().position().at(0));
        assertEquals(120, title.width());
        assertEquals(30, title.height());
        assertEquals(title.base().transform(), title.restTransform());
        assertEquals(Transform.at(5, 5), title.restTransform());
        assertEquals(0, title.base().inPoint());
        assertEquals(3, title.base().outPoint());

        SlotLayer button = slot(animation, "okButton");
        assertVec(5, 45, button.base().transform().position().at(0));
        assertEquals(80, button.width());
        assertEquals(40, button.height());

        SlotLayer swatch = slot(animation, "swatch");
        assertVec(5, 95, swatch.base().transform().position().at(0));
        assertEquals(50, swatch.width());
        assertEquals(20, swatch.height());

        assertEquals(List.of(
                "The controller com.example.app.MissingController was left out: the FXML is loaded without it",
                "Event handlers were left out (2 handlers)",
                "Nodes with an fx:id that are not part of the scene graph are skipped (1 node)"), result.warnings());
    }

    @Test
    void everySlotHasASnapshotOfItsNode() throws Exception {
        FxmlImporter.Result result = read("three-slots");
        assertEquals(List.of("title", "okButton", "swatch"),
                result.previews().stream().map(FxmlImporter.SlotPreview::slotName).toList());

        for (FxmlImporter.SlotPreview preview : result.previews()) {
            byte[] png = preview.pngSnapshot();
            assertEquals((byte) 0x89, png[0]);
            assertEquals('P', png[1]);
        }
        Image swatch = onFxThread(() -> new Image(new ByteArrayInputStream(result.previews().get(2).pngSnapshot())));
        assertEquals(50, swatch.getWidth());
        assertEquals(20, swatch.getHeight());
        // the fill of the rectangle: #3366cc
        int argb = swatch.getPixelReader().getArgb(25, 10);
        assertEquals(0xFF3366CC, argb, Integer.toHexString(argb));

        // the stylesheet next to the FXML is found, and %keys resolve to themselves
        Image title = onFxThread(() -> new Image(new ByteArrayInputStream(result.previews().get(0).pngSnapshot())));
        assertEquals(120, title.getWidth());
        boolean red = false;
        for (int y = 0; y < title.getHeight() && !red; y++) {
            for (int x = 0; x < title.getWidth() && !red; x++) {
                int pixel = title.getPixelReader().getArgb(x, y);
                red = (pixel >>> 24) > 200 && (pixel >> 16 & 0xFF) > 150 && (pixel >> 8 & 0xFF) < 60;
            }
        }
        assertTrue(red, "the label text is painted in the red of the stylesheet");
    }

    @Test
    void nestedNodesBecomeFlatSlotsWithDescendantsAboveTheirAncestors() throws Exception {
        FxmlImporter.Result result = read("nested");
        Animation animation = result.file().animation();
        // flat, in paint order: an ancestor comes before (below) its descendants
        assertEquals(List.of("root", "back", "card", "cardBody", "cardBadge", "front"), names(animation.layers()));
        for (Layer layer : animation.layers()) {
            assertInstanceOf(SlotLayer.class, layer);
        }
        // the pane is as large as its content needs
        assertEquals(170, animation.width());
        assertEquals(120, animation.height());
        SlotLayer root = slot(animation, "root");
        assertVec(0, 0, root.base().transform().position().at(0));
        assertEquals(170, root.width());

        // bounds are in the coordinates of the root, whatever the nesting
        SlotLayer card = slot(animation, "card");
        assertVec(40, 15, card.base().transform().position().at(0));
        assertEquals(90, card.width());
        assertEquals(55, card.height());
        SlotLayer badge = slot(animation, "cardBadge");
        assertVec(100, 15, badge.base().transform().position().at(0));
        assertEquals(30, badge.width());
        assertEquals(15, badge.height());
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void scriptsAreLeftOut() throws Exception {
        FxmlImporter.Result result = read("scripted");
        assertEquals(List.of("button"), names(result.file().animation().layers()));
        assertEquals(100, slot(result.file().animation(), "button").width());
        assertEquals(List.of("Scripts were left out (2 scripts)"), result.warnings());
    }

    @Test
    void fxmlThatNeedsItsControllerFails() {
        IOException failure = assertThrows(IOException.class, () -> read("needs-controller"));
        assertTrue(failure.getMessage().contains("com.example.app.MissingController"), failure.getMessage());
    }

    @Test
    void fxmlThatNeedsAMissingClassFails() {
        IOException failure = assertThrows(IOException.class, () -> read("missing-class"));
        assertTrue(failure.getMessage().contains("FancyGauge"), failure.getMessage());
    }

    @Test
    void unreadableFxmlFails() throws Exception {
        Path directory = Path.of("target", "fxml");
        Files.createDirectories(directory);
        Path broken = directory.resolve("broken.fxml");
        Files.writeString(broken, "<VBox xmlns:fx='http://javafx.com/fxml'><oops>");
        assertThrows(IOException.class, () -> onFxThread(() -> FxmlImporter.read(broken.toUri().toURL())));

        Path notANode = directory.resolve("list.fxml");
        Files.writeString(notANode, "<?import java.util.ArrayList?><ArrayList xmlns:fx='http://javafx.com/fxml'/>");
        IOException failure = assertThrows(IOException.class,
                () -> onFxThread(() -> FxmlImporter.read(notANode.toUri().toURL())));
        assertTrue(failure.getMessage().contains("ArrayList"), failure.getMessage());

        assertThrows(IOException.class,
                () -> onFxThread(() -> FxmlImporter.read(directory.resolve("nowhere.fxml").toUri().toURL())));
    }

    @Test
    void aNodeThatIsTooLargeToDrawHasASlotButNoPreview() throws Exception {
        FxmlImporter.Result result = read("huge");
        assertEquals(List.of("huge", "small"), names(result.file().animation().layers()));
        assertEquals(100000, slot(result.file().animation(), "huge").width());
        assertEquals(List.of("small"), result.previews().stream().map(FxmlImporter.SlotPreview::slotName).toList());
        assertEquals(List.of("Nodes that are too large to be drawn have no preview (1 slot)"), result.warnings());
    }

    @Test
    void mustBeCalledOnTheFxThread() {
        assertThrows(IllegalStateException.class, () -> FxmlImporter.read(fixture("three-slots")));
    }
}
