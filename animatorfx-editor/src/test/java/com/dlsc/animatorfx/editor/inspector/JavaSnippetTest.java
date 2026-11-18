package com.dlsc.animatorfx.editor.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaSnippetTest {

    private static SlotLayer slot(String name) {
        return new SlotLayer(LayerBase.create(name, 3), name, 100, 40, Transform.IDENTITY);
    }

    private static Animation twoSlots() {
        return Animation.create("checkout success", 400, 240, 3)
                .withLayers(List.of(slot("button"),
                        new GroupLayer(LayerBase.create("Group 1", 3), List.of(slot("card")))))
                .withMarkers(List.of(new Marker("intro", 0, 0.5), new Marker("success", 1.5, 3)));
    }

    @Test
    void aDocumentWithTwoSlotsBindsBothAndNamesTheMarkers() {
        String code = JavaSnippet.generate(twoSlots(), "checkout-success.afx");
        assertTrue(code.contains("import com.dlsc.animatorfx.model.io.AnimationFile;"), code);
        assertTrue(code.contains("import com.dlsc.animatorfx.model.io.AnimationIO;"));
        assertTrue(code.contains("import com.dlsc.animatorfx.player.AnimationView;"));
        assertTrue(code.contains("public final class CheckoutSuccessAnimation {"));
        assertTrue(code.contains("public static AnimationView create(Node button, Node card) {"));
        assertTrue(code.contains(
                "AnimationIO.read(CheckoutSuccessAnimation.class.getResource(\"checkout-success.afx\"))"));
        assertTrue(code.contains("AnimationView view = new AnimationView(file);"));
        assertTrue(code.contains("view.bind(\"button\", button);"));
        assertTrue(code.contains("view.bind(\"card\", card);"));
        assertTrue(code.contains("view.autoBind(root)"));
        assertTrue(code.contains("view.playMarker(\"intro\");"));
        assertTrue(code.contains("view.playMarker(\"success\");"));
        assertTrue(code.contains("return view;"));
        assertEquals(count(code, '{'), count(code, '}'));
        assertEquals(count(code, '('), count(code, ')'));
        assertFalse(code.contains("\t"));
    }

    @Test
    void aDocumentWithoutSlotsOrMarkersHasNoSectionsForThem() {
        String code = JavaSnippet.generate(Animation.create("Untitled", 400, 240, 3), null);
        assertTrue(code.contains("public static AnimationView create() {"), code);
        assertTrue(code.contains("getResource(\"untitled.afx\")"));
        assertFalse(code.contains("view.bind("));
        assertFalse(code.contains("playMarker"));
        assertFalse(code.contains("import javafx.scene.Node;"));
        assertTrue(code.contains("view.play();"));
    }

    @Test
    void namesAreTurnedIntoValidJava() {
        assertEquals("CheckoutSuccessAnimation", JavaSnippet.className("checkout success"));
        assertEquals("My2ndDraftAnimation", JavaSnippet.className("my 2nd-draft"));
        assertEquals("Animation2Go", JavaSnippet.className("2 go"));
        assertEquals("MyAnimation", JavaSnippet.className("  "));
        assertEquals("LoaderAnimation", JavaSnippet.className("Loader Animation"));
        String code = JavaSnippet.generate(Animation.create("x", 1, 1, 1)
                .withMarkers(List.of(new Marker("say \"hi\"", 0, 1))), "a\\b.afx");
        assertTrue(code.contains("view.playMarker(\"say \\\"hi\\\"\");"), code);
        assertTrue(code.contains("getResource(\"a\\\\b.afx\")"));
    }

    @Test
    void slotNamesThatClashWithTheSnippetGetOtherParameterNames() {
        Animation doc = Animation.create("e", 400, 240, 3).withLayers(List.of(slot("e"), slot("file"), slot("view"),
                slot("readFailure"), slot("AnimationIO"), slot("Node"), slot("slot2"), slot("EAnimation"), slot("ok")));
        String code = JavaSnippet.generate(doc, "e.afx");
        assertTrue(code.contains("public final class EAnimation {"), code);
        assertTrue(code.contains(
                "create(Node e, Node slot1, Node slot3, Node slot4, Node slot5, Node slot6, Node slot2, Node slot7, Node ok)"),
                code);
        // the slot keeps its name in the binding; only the parameter differs
        assertTrue(code.contains("view.bind(\"e\", e);"));
        assertTrue(code.contains("view.bind(\"file\", slot1);"));
        assertTrue(code.contains("view.bind(\"AnimationIO\", slot5);"));
        assertTrue(code.contains("view.bind(\"slot2\", slot2);"));
        assertTrue(code.contains("catch (IOException readFailure)"));
        assertTrue(code.contains("throw new UncheckedIOException(readFailure);"));
    }

    @Test
    void parameterNamesAreUniqueIdentifiers() {
        assertEquals(List.of("a", "slot1", "slot2", "slot3", "slot4"),
                JavaSnippet.parameterNames(List.of("a", "a", "class", "2nd", ""), "XAnimation"));
    }

    private static long count(String text, char c) {
        return text.chars().filter(ch -> ch == c).count();
    }
}
