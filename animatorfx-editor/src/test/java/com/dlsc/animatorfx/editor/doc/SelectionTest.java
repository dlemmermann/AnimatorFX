package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.editor.doc.Selection.KeyframeRef;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.SoundCue;
import javafx.beans.InvalidationListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Cues are selected instead of layers and keyframes, never with them; every way from one to the other. */
class SelectionTest {

    private static final KeyframeRef KEY = new KeyframeRef("a", "volume", "k");
    private static final KeyframeRef OTHER_KEY = new KeyframeRef("a", "volume", "k2");

    private final Selection selection = new Selection();

    private void assertSelected(List<String> layers, Set<KeyframeRef> keyframes, Set<String> cues) {
        assertEquals(layers, List.copyOf(selection.layerIds()), "layers");
        assertEquals(keyframes, Set.copyOf(selection.keyframes()), "keyframes");
        assertEquals(cues, Set.copyOf(selection.cueIds()), "cues");
    }

    @Test
    void aCueReplacesASelectedLayer() {
        selection.layerIds().setAll("a", "b");

        selection.cueIds().add("c");

        assertSelected(List.of(), Set.of(), Set.of("c"));
    }

    @Test
    void aCueReplacesASelectedLayerWithItsKeyframes() {
        selection.layerIds().setAll("a");
        selection.keyframes().addAll(List.of(KEY, OTHER_KEY));

        selection.cueIds().add("c");

        assertSelected(List.of(), Set.of(), Set.of("c"));
    }

    @Test
    void aCueReplacesKeyframesWithoutALayer() {
        selection.keyframes().add(KEY);

        selection.cueIds().add("c");

        assertSelected(List.of(), Set.of(), Set.of("c"));
    }

    @Test
    void severalCuesAtOnceReplaceLayersAndKeyframes() {
        selection.layerIds().setAll("a");
        selection.keyframes().add(KEY);

        selection.cueIds().addAll(List.of("c", "d"));

        assertSelected(List.of(), Set.of(), Set.of("c", "d"));
    }

    @Test
    void cuesAreAddedToCuesOneByOne() {
        selection.cueIds().add("c");
        selection.cueIds().add("d");
        selection.cueIds().remove("c");
        selection.cueIds().add("e");

        assertSelected(List.of(), Set.of(), Set.of("d", "e"));
    }

    @Test
    void aLayerReplacesTheSelectedCues() {
        selection.cueIds().addAll(List.of("c", "d"));

        selection.layerIds().add("a");
        assertSelected(List.of("a"), Set.of(), Set.of());

        selection.cueIds().add("c");
        selection.layerIds().setAll("a", "b");
        assertSelected(List.of("a", "b"), Set.of(), Set.of());

        selection.cueIds().add("c");
        selection.layerIds().addAll(List.of("x"));
        assertSelected(List.of("x"), Set.of(), Set.of());
    }

    @Test
    void aKeyframeReplacesTheSelectedCues() {
        selection.cueIds().add("c");

        selection.keyframes().add(KEY);
        assertSelected(List.of(), Set.of(KEY), Set.of());

        selection.cueIds().add("c");
        selection.keyframes().addAll(List.of(KEY, OTHER_KEY));
        assertSelected(List.of(), Set.of(KEY, OTHER_KEY), Set.of());
    }

    @Test
    void removingAndClearingLeaveTheOtherKindAlone() {
        selection.cueIds().addAll(List.of("c", "d"));
        selection.layerIds().clear();
        selection.keyframes().clear();
        selection.layerIds().setAll();
        assertSelected(List.of(), Set.of(), Set.of("c", "d"));

        selection.clear();
        assertSelected(List.of(), Set.of(), Set.of());

        selection.layerIds().setAll("a");
        selection.keyframes().add(KEY);
        selection.cueIds().clear();
        assertSelected(List.of("a"), Set.of(KEY), Set.of());
    }

    @Test
    void whoListensNeverSeesAnEmptySelectionOnTheWayFromALayerToACue() {
        selection.layerIds().setAll("a");
        selection.keyframes().add(KEY);
        List<String> seen = new ArrayList<>();
        InvalidationListener watch = obs -> seen.add(selection.layerIds() + " " + selection.keyframes().size() + " "
                + List.copyOf(selection.cueIds()));
        selection.layerIds().addListener(watch);
        selection.keyframes().addListener(watch);
        selection.cueIds().addListener(watch);

        selection.cueIds().add("c");

        assertFalse(seen.isEmpty());
        assertFalse(seen.contains("[] 0 []"), seen.toString());
        assertEquals("[] 0 [c]", seen.get(seen.size() - 1));

        seen.clear();
        selection.layerIds().setAll("b");
        assertFalse(seen.contains("[] 0 []"), seen.toString());
        assertEquals("[b] 0 []", seen.get(seen.size() - 1));
    }

    @Test
    void undoAndRedoStillPruneWhatIsGone() {
        EditorDocument document = EditorDocument.untitled();
        document.addAsset(new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "chime.wav"), new byte[]{1});
        document.execute(Commands.addAudioLayer("a", "chime", "snd", 0, 1));
        document.execute(Commands.addCue(new SoundCue("c", "hit", 1, "snd", 1, false)));
        Selection sel = document.getSelection();

        sel.layerIds().setAll("a");
        sel.cueIds().add("c");
        assertEquals(Set.of("c"), Set.copyOf(sel.cueIds()));
        document.undo();
        assertEquals(Set.of(), Set.copyOf(sel.cueIds()));

        sel.layerIds().setAll("a");
        document.redo();
        assertEquals(List.of("a"), List.copyOf(sel.layerIds()), "a cue coming back does not touch the layer selection");
        document.undo();
        document.undo();
        assertEquals(List.of(), List.copyOf(sel.layerIds()), "the layer is gone");
    }
}
