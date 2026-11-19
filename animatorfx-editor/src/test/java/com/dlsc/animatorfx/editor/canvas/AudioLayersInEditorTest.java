package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.RoundTrip;
import com.dlsc.animatorfx.editor.inspector.AssetsView;
import com.dlsc.animatorfx.editor.inspector.InspectorSection;
import com.dlsc.animatorfx.editor.inspector.sections.LayerSection;
import com.dlsc.animatorfx.editor.timeline.TimelineRow;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audio layers and sound cues in the editor before it has any audio features: they are kept,
 * copied and counted, and stay out of everything that works on what is drawn.
 */
class AudioLayersInEditorTest extends CanvasTestBase {

    private static final byte[] WAV = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E'};
    private static final Asset SOUND = new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "chime.wav");

    private static AudioLayer audio(String id) {
        AudioLayer layer = AudioLayer.create(id, "snd", 0, 3);
        return layer.withBase(layer.base().withId(id));
    }

    private void addSound() {
        run(() -> doc.addAsset(SOUND, WAV));
    }

    private void fire(String menuItemId) {
        run(() -> window.menuItem(menuItemId).fire());
        layout();
    }

    private boolean selectionOutlineShown() {
        return fx(() -> canvas.getOverlay().lookup(".overlay-selection").isVisible());
    }

    @Test
    void anAudioLayerHasNoCanvasPresence() {
        addSound();
        add(audio("music"), rect("a", 100, 100, 100, 100));

        // the audio layer's node is at the origin; nothing there can be hit
        click(0, 0);
        assertEquals(List.of(), selection());
        dragFromTo(-20, -20, 250, 250);
        assertEquals(List.of("a"), selection());
        fire("menu-edit-select-all");
        assertEquals(List.of("a"), selection());

        select("music");
        assertFalse(selectionOutlineShown());
        assertTrue(fx(() -> canvas.getGeometry().quad("music")).isEmpty());
        assertFalse(fx(() -> canvas.getGeometry().isSelectable(layer("music"))));
        assertEquals(1, fx(() -> canvas.getGeometry().snapTargets(List.of("a"))).size(), "only the artboard");

        Layer before = layer("music");
        key(KeyCode.RIGHT);
        assertEquals(before, layer("music"));

        select("a");
        assertTrue(selectionOutlineShown());
    }

    @Test
    void duplicateAndPasteKeepTheAssetReferenceAndDoNotMoveTheLayer() {
        addSound();
        AudioLayer music = audio("music").withVolume(
                Animatable.of(0.0).withKeyframe(0, 0.0, Easing.LINEAR).withKeyframe(1, 1.0, Easing.LINEAR));
        add(music);
        select("music");

        fire("menu-edit-duplicate");
        AudioLayer copy = (AudioLayer) layers().get(1);
        assertNotEquals("music", copy.id());
        assertEquals("snd", copy.assetId());
        assertNotEquals(music.volume().keyframes().get(0).id(), copy.volume().keyframes().get(0).id());
        assertEquals(List.of(0.0, 1.0), copy.volume().keyframes().stream().map(k -> k.value()).toList());

        select("music");
        fire("menu-edit-copy");
        fire("menu-edit-paste");
        AudioLayer pasted = (AudioLayer) layers().get(2);
        assertEquals(Transform.IDENTITY, pasted.base().transform());
        assertEquals("snd", pasted.assetId());

        EditorDocument other = fx(() -> window.getFileActions().newDocument());
        layout();
        SoundCue kept = SoundCue.create("kept", 0.5, "own");
        run(() -> {
            other.addAsset(new Asset("own", Asset.Kind.AUDIO, "assets/own.wav", "own.wav"), WAV);
            other.execute(Commands.setDocument("Add Cue", a -> a.withCues(List.of(kept))));
        });
        fire("menu-edit-paste");

        assertEquals(1, other.getAnimation().layers().size());
        assertEquals(List.of("own", "snd"), other.getAnimation().assets().stream().map(Asset::id).toList());
        assertArrayEquals(WAV, other.getFileContent().assets().get("snd"));
        assertEquals(List.of(kept), other.getAnimation().cues());
        RoundTrip.of(other.getFileContent());
    }

    @Test
    void cuesSurviveEditsUndoAndRedo() {
        addSound();
        SoundCue cue = new SoundCue("cue", "Tick", 1, "snd", 0.5, false);
        run(() -> doc.execute(Commands.setDocument("Add Cue", a -> a.withCues(List.of(cue)))));
        add(rect("a", 100, 100, 100, 100), audio("music"));

        dragFromTo(150, 150, 300, 300);
        select("a");
        fire("menu-edit-duplicate");
        fire("menu-edit-group");
        run(() -> doc.execute(Commands.removeLayers(List.of("music"))));
        assertEquals(List.of(cue), doc.getAnimation().cues());

        run(doc::undo);
        run(doc::undo);
        run(doc::redo);
        assertEquals(List.of(cue), doc.getAnimation().cues());
        RoundTrip.of(doc.getFileContent());
    }

    @Test
    void anAudioAssetInUseByALayerOrACueCannotBeRemoved() {
        addSound();
        AssetsView assets = window.getSidePanel().getAssets();
        add(audio("music"), audio("music2"));
        assertEquals(2, AssetsView.usage(doc.getAnimation(), "snd"));

        run(() -> assets.remove("snd"));
        assertEquals(1, doc.getAnimation().assets().size());
        assertTrue(assets.getMessage().contains("used by 2 layers"), assets.getMessage());

        run(() -> {
            doc.execute(Commands.removeLayers(List.of("music", "music2")));
            doc.execute(Commands.setDocument("Add Cue", a -> a.withCues(List.of(SoundCue.create("c", 1, "snd")))));
        });
        assertEquals(1, AssetsView.usage(doc.getAnimation(), "snd"));
        run(() -> assets.remove("snd"));
        assertEquals(1, doc.getAnimation().assets().size());
        assertTrue(assets.getMessage().contains("used by 1 cue"), assets.getMessage());

        run(() -> {
            doc.execute(Commands.setDocument("Remove Cue", a -> a.withCues(List.of())));
            assets.remove("snd");
        });
        assertEquals(List.of(), doc.getAnimation().assets());
        assertNull(assets.getMessage());
    }

    @Test
    void theInspectorShowsOnlyTheLayerSectionForAnAudioLayer() {
        addSound();
        add(audio("music"), rect("a", 100, 100, 100, 100));

        select("music");
        List<InspectorSection> visible = fx(() -> window.getSidePanel().getInspector().getVisibleSections());
        assertEquals(List.of(LayerSection.class), visible.stream().map(Object::getClass).toList());

        select("music", "a");
        visible = fx(() -> window.getSidePanel().getInspector().getVisibleSections());
        assertEquals(List.of(LayerSection.class), visible.stream().map(Object::getClass).toList());
    }

    @Test
    void anAudioLayerNeitherMasksNorIsMasked() {
        addSound();
        add(audio("music"), rect("a", 100, 100, 100, 100));

        assertFalse(Commands.canMask(doc.getAnimation(), "a", "music"));
        assertFalse(Commands.canMask(doc.getAnimation(), "music", "a"));
        run(() -> doc.execute(Commands.setMask("a", new Mask("music", Mask.Mode.CLIP))));
        assertNull(layer("a").base().mask());
    }

    @Test
    void theTimelineListsAnAudioLayerWithItsVolumeAndBalance() {
        addSound();
        add(audio("music"));
        run(() -> {
            doc.getViewState().getExpandedLayerIds().add("music");
            doc.getViewState().setShowAllProperties(true);
        });
        layout();

        List<TimelineRow> rows = fx(() -> window.getTimelineView().getRows());
        assertEquals(3, rows.size());
        assertEquals("music", ((TimelineRow.LayerRow) rows.get(0)).layer().id());
        assertEquals(List.of("volume", "balance"), rows.subList(1, 3).stream()
                .map(row -> ((TimelineRow.PropertyRow) row).property().key()).toList());

        run(() -> doc.execute(Commands.setValueAt("music", "volume", 0, 0.25, false)));
        assertEquals(Animatable.of(0.25), ((AudioLayer) layer("music")).volume());
        assertEquals(new Vec2(0, 0), position("music"));
    }
}
