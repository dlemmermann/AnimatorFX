package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.model.io.AnimationFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The audio and cue commands: what they change, and that the result can always be saved and read back. */
class AudioCommandsTest {

    private static final Asset SOUND = new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "chime.wav");
    private static final Asset OTHER = new Asset("snd2", Asset.Kind.AUDIO, "assets/snd2.wav", "boom.wav");
    private static final Asset PICTURE = new Asset("img", Asset.Kind.IMAGE, "assets/img.png", "img.png");

    private static Animation doc(Layer... layers) {
        return Animation.create("Test", 800, 600, 3).withFrameRate(10)
                .withAssets(List.of(SOUND, OTHER, PICTURE)).withLayers(List.of(layers));
    }

    private static AudioLayer audio(String id) {
        AudioLayer layer = AudioLayer.create(id, "snd", 0, 3);
        return layer.withBase(layer.base().withId(id));
    }

    private static AudioLayer audio(Animation animation, String id) {
        return (AudioLayer) Layers.find(animation, id).orElseThrow();
    }

    private static Animation saved(Animation animation) {
        byte[] bytes = {1, 2, 3};
        return RoundTrip.of(new AnimationFile(animation, Map.of("snd", bytes, "snd2", bytes, "img", bytes))).animation();
    }

    // ------------------------------------------------------------------ audio layers

    @Test
    void anAudioLayerIsAddedOnTopAtTheFrameOfTheTimeAndLastsAsLongAsItsClip() {
        Animation after = Commands.addAudioLayer("a", "chime", "snd", 1.04, 0.75).apply(doc(CommandsTest.rect("r")));

        assertEquals(List.of("r", "a"), after.layers().stream().map(Layer::id).toList());
        AudioLayer layer = audio(after, "a");
        assertEquals("chime", layer.name());
        assertEquals(1.0, layer.base().inPoint(), 1e-9);
        assertEquals(1.75, layer.base().outPoint(), 1e-9);
        assertEquals(0, layer.clipOffset());
        assertEquals(1.0, layer.volume().at(0));
        saved(after);
    }

    @Test
    void anAudioLayerEndsWithTheDocumentAndStartsAtZeroWhenTheTimeIsTheEnd() {
        AudioLayer late = audio(Commands.addAudioLayer("a", "chime", "snd", 2.5, 2).apply(doc()), "a");
        assertEquals(2.5, late.base().inPoint(), 1e-9);
        assertEquals(3, late.base().outPoint(), 1e-9);

        AudioLayer atEnd = audio(Commands.addAudioLayer("a", "chime", "snd", 3, 2).apply(doc()), "a");
        assertEquals(0, atEnd.base().inPoint(), 1e-9);
        assertEquals(2, atEnd.base().outPoint(), 1e-9);
    }

    @Test
    void anAudioLayerOfUnknownLengthLastsToTheEndOfTheDocument() {
        for (double unknown : new double[]{Double.NaN, 0, -1, Double.POSITIVE_INFINITY}) {
            AudioLayer layer = audio(Commands.addAudioLayer("a", "chime", "snd", 1, unknown).apply(doc()), "a");
            assertEquals(3, layer.base().outPoint(), 1e-9);
        }
    }

    @Test
    void anAudioLayerNeedsAnAudioAsset() {
        Animation before = doc();
        assertSame(before, Commands.addAudioLayer("a", "x", "img", 0, 1).apply(before));
        assertSame(before, Commands.addAudioLayer("a", "x", "missing", 0, 1).apply(before));
    }

    @Test
    void theClipOffsetIsNeverNegativeOrNotANumber() {
        Animation before = doc(audio("a"));

        assertEquals(0.4, audio(Commands.setClipOffset("a", 0.4).apply(before), "a").clipOffset());
        Animation shifted = Commands.setClipOffset("a", 0.4).apply(before);
        assertEquals(0, audio(Commands.setClipOffset("a", -2).apply(shifted), "a").clipOffset());
        assertSame(shifted, Commands.setClipOffset("a", Double.NaN).apply(shifted));
        assertSame(shifted, Commands.setClipOffset("a", Double.POSITIVE_INFINITY).apply(shifted));
        assertSame(before, Commands.setClipOffset("r", 1).apply(before));
        saved(shifted);
    }

    @Test
    void volumeAndBalanceWrittenThroughTheGenericCommandsStayInRange() {
        Animation before = doc(audio("a"));

        Animation loud = Commands.setValueAt("a", "volume", 0, 1.7, false).apply(before);
        assertEquals(1.0, audio(loud, "a").volume().at(0));
        Animation keyed = Commands.setValueAt("a", "volume", 1, -0.5, true).apply(before);
        assertEquals(0.0, audio(keyed, "a").volume().at(1));
        Animation left = Commands.setValueAt("a", "balance", 0, -3.0, false).apply(before);
        assertEquals(-1.0, audio(left, "a").balance().at(0));
        Animation set = Commands.setProperty("a", "balance", Animatable.keyframed(List.of(
                new Keyframe<>("k1", 0, -4.0, Easing.LINEAR), new Keyframe<>("k2", 1, 9.0, Easing.LINEAR)))).apply(before);
        assertEquals(-1.0, audio(set, "a").balance().at(0));
        assertEquals(1.0, audio(set, "a").balance().at(1));
        Animation updated = Commands.updateLayer("Edit", "a",
                l -> ((AudioLayer) l).withVolume(Animatable.of(Double.NaN)).withClipOffset(-1)).apply(before);
        assertEquals(1.0, audio(updated, "a").volume().at(0));
        assertEquals(0.0, audio(updated, "a").clipOffset());
        for (Animation animation : List.of(loud, keyed, left, set, updated)) {
            saved(animation);
        }
    }

    // ------------------------------------------------------------------ cues

    @Test
    void aCueIsAddedWithItsTimeInsideTheDocumentAndItsVolumeInRange() {
        Animation after = Commands.addCue(new SoundCue("c", "hit", 7, "snd", 4, false)).apply(doc());

        assertEquals(List.of(new SoundCue("c", "hit", 3, "snd", 1, false)), after.cues());
        Animation early = Commands.addCue(new SoundCue("d", "hit", -1, "snd", -1, true)).apply(after);
        assertEquals(new SoundCue("d", "hit", 0, "snd", 0, true), early.cues().get(1));
        saved(early);
    }

    @Test
    void aCueNeedsAnAudioAssetAFiniteTimeAndAnIdOfItsOwn() {
        Animation before = doc().withCues(List.of(new SoundCue("c", "hit", 1, "snd", 1, false)));

        assertSame(before, Commands.addCue(new SoundCue("x", "hit", 1, "img", 1, false)).apply(before));
        assertSame(before, Commands.addCue(new SoundCue("x", "hit", 1, "missing", 1, false)).apply(before));
        assertSame(before, Commands.addCue(new SoundCue("x", "hit", Double.NaN, "snd", 1, false)).apply(before));
        assertSame(before, Commands.addCue(new SoundCue("c", "again", 2, "snd", 1, false)).apply(before));
    }

    @Test
    void aCueIsUpdatedWithinTheSameLimits() {
        Animation before = doc().withCues(List.of(new SoundCue("c", "hit", 1, "snd", 1, false),
                new SoundCue("d", "tick", 2, "snd", 1, false)));

        Animation after = Commands.updateCue("Change Cue", "c",
                cue -> cue.withName("bang").withVolume(2).withTime(-4).withAssetId("snd2").withMuted(true)).apply(before);

        assertEquals(new SoundCue("c", "bang", 0, "snd2", 1, true), after.cues().get(0));
        assertEquals(before.cues().get(1), after.cues().get(1));
        assertSame(before, Commands.updateCue("Change Cue", "c", cue -> cue.withAssetId("img")).apply(before));
        assertSame(before, Commands.updateCue("Change Cue", "c", cue -> cue.withVolume(Double.NaN)).apply(before));
        assertSame(before, Commands.updateCue("Change Cue", "c", cue -> cue.withId("d")).apply(before));
        assertSame(before, Commands.updateCue("Change Cue", "zzz", cue -> cue.withName("x")).apply(before));
        assertSame(before, Commands.updateCue("Change Cue", "c", cue -> cue).apply(before));
        saved(after);
    }

    @Test
    void aCueMovesWithinTheDuration() {
        Animation before = doc().withCues(List.of(new SoundCue("c", "hit", 1, "snd", 1, false)));

        assertEquals(2.25, Commands.moveCue("c", 2.25).apply(before).cues().get(0).time());
        assertEquals(3, Commands.moveCue("c", 99).apply(before).cues().get(0).time());
        assertEquals(0, Commands.moveCue("c", -1).apply(before).cues().get(0).time());
        assertSame(before, Commands.moveCue("c", Double.NaN).apply(before));
        assertSame(before, Commands.moveCue("c", 1).apply(before));
        saved(Commands.moveCue("c", 99).apply(before));
    }

    @Test
    void cuesAreRemovedById() {
        Animation before = doc().withCues(List.of(new SoundCue("c", "hit", 1, "snd", 1, false),
                new SoundCue("d", "tick", 2, "snd", 1, false), new SoundCue("e", "tock", 2.5, "snd", 1, false)));

        Animation after = Commands.removeCues(List.of("c", "e", "nope")).apply(before);

        assertEquals(List.of("d"), after.cues().stream().map(SoundCue::id).toList());
        assertEquals("Delete Cues", Commands.removeCues(List.of("c", "e")).name());
        assertEquals("Delete Cue", Commands.removeCues(List.of("c")).name());
        assertSame(before, Commands.removeCues(List.of("nope")).apply(before));
        saved(after);
    }

    // ------------------------------------------------------------------ selection

    @Test
    void cuesAndLayersAreNotSelectedTogether() {
        EditorDocument document = EditorDocument.untitled();
        document.addAsset(SOUND, new byte[]{1});
        document.execute(Commands.addAudioLayer("a", "chime", "snd", 0, 1));
        document.execute(Commands.addCue(new SoundCue("c", "hit", 1, "snd", 1, false)));
        Selection selection = document.getSelection();

        selection.layerIds().setAll("a");
        selection.cueIds().add("c");
        assertTrue(selection.layerIds().isEmpty());
        assertTrue(selection.keyframes().isEmpty());

        selection.layerIds().setAll("a");
        assertTrue(selection.cueIds().isEmpty());

        // keyframes belong to layers: they do not go with cues either
        selection.cueIds().add("c");
        selection.keyframes().add(new Selection.KeyframeRef("a", "volume", "k"));
        assertTrue(selection.cueIds().isEmpty());
        selection.cueIds().add("c");
        assertTrue(selection.keyframes().isEmpty());

        selection.clear();
        assertTrue(selection.cueIds().isEmpty());
    }

    @Test
    void theCueSelectionFollowsUndoAndRedo() {
        EditorDocument document = EditorDocument.untitled();
        document.addAsset(SOUND, new byte[]{1});
        document.execute(Commands.addCue(new SoundCue("c", "hit", 1, "snd", 1, false)));
        document.getSelection().cueIds().add("c");

        document.undo();
        assertFalse(document.getSelection().cueIds().contains("c"));

        document.redo();
        document.getSelection().cueIds().add("c");
        document.execute(Commands.removeCues(List.of("c")));
        assertTrue(document.getSelection().cueIds().isEmpty());
    }
}
