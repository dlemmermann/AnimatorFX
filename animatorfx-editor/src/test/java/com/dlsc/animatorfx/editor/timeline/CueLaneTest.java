package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.editor.audio.TestSounds;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.ui.EditorUiTest;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.SoundCue;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sound cues in the timeline: adding, selecting, moving and deleting them. */
class CueLaneTest extends EditorUiTest {

    private static final double Y = 9;
    private CueLane lane;

    @BeforeEach
    void content() {
        lane = timeline.getCueLane();
        add(rect("r", 100, 100, 100, 100));
    }

    private void addSound(String id, String name) {
        run(() -> doc.addAsset(new Asset(id, Asset.Kind.AUDIO, "assets/" + id + ".wav", name), TestSounds.tone(0.2, 0.5)));
    }

    private void addCues(SoundCue... cues) {
        for (SoundCue cue : cues) {
            execute(Commands.addCue(cue));
        }
        layout();
    }

    private double x(double time) {
        return fx(() -> timeline.getScale().toX(time));
    }

    private List<SoundCue> cues() {
        return doc.getAnimation().cues();
    }

    private Set<String> selectedCues() {
        return Set.copyOf(doc.getSelection().cueIds());
    }

    private List<String> notices() {
        return fx(() -> window.getNoticeBar().getMessages());
    }

    private Button addButton() {
        return lookup("#" + LayerTreeColumn.ADD_CUE_ID);
    }

    @Test
    void theLaneLiesBetweenTheMarkerLaneAndTheTracks() {
        double markerBottom = fx(() -> timeline.getMarkerLane().getBoundsInParent().getMaxY());
        assertEquals(markerBottom, fx(() -> lane.getBoundsInParent().getMinY()), 0.01);
        assertEquals(TimelineView.CUE_HEIGHT, fx(lane::getHeight), 0.01);
        assertEquals(fx(() -> lane.getBoundsInParent().getMaxY()),
                fx(() -> timeline.getTrackPane().getBoundsInParent().getMinY()), 0.01);
    }

    @Test
    void theHeaderButtonAddsACueAtThePlayheadForTheOnlySound() {
        addSound("snd", "door chime.wav");
        playhead(1.0);

        run(() -> addButton().fire());

        assertEquals(1, cues().size());
        SoundCue cue = cues().get(0);
        assertEquals(new SoundCue(cue.id(), "door chime", 1.0, "snd", 1, false), cue);
        assertEquals(Set.of(cue.id()), selectedCues());
        assertEquals("Add Cue", doc.getUndoName());
    }

    @Test
    void withSeveralSoundsTheHeaderButtonOffersThem() {
        addSound("snd", "chime.wav");
        addSound("boom", "boom.wav");
        playhead(0.5);

        List<MenuItem> items = fx(() -> List.copyOf(timeline.getLayerColumn().getCueMenu().getItems()));
        assertEquals(List.of("chime.wav", "boom.wav"), items.subList(0, 2).stream().map(MenuItem::getText).toList());
        assertEquals("Import Sound…", items.get(3).getText());

        run(() -> items.get(1).fire());

        assertEquals("boom", cues().get(0).assetId());
        assertEquals(0.5, cues().get(0).time(), 1e-9);
    }

    @Test
    void importSoundAddsTheSoundAndACueInOneStep() throws Exception {
        Path file = dir.resolve("snap.wav");
        Files.write(file, TestSounds.tone(0.2, 0.5));
        window.getImportActions().setAudioPicker(owner -> List.of(file));
        playhead(2.0);

        run(() -> timeline.getLayerColumn().getCueMenu().getItems().get(0).fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, doc.getAnimation().assets().size());
        assertEquals("snap", cues().get(0).name());
        assertEquals(2.0, cues().get(0).time(), 1e-9);
        assertEquals(Set.of(cues().get(0).id()), selectedCues());
        run(doc::undo);
        assertTrue(doc.getAnimation().assets().isEmpty());
        assertTrue(cues().isEmpty());
    }

    @Test
    void aDoubleClickOnTheLaneAddsACueThereWithTheSoundUsedLast() {
        addSound("snd", "chime.wav");
        addSound("boom", "boom.wav");
        run(() -> timeline.addCue(0.5, "snd"));

        doubleClick(lane, x(2.0), Y);

        assertEquals(2, cues().size());
        assertEquals("snd", cues().get(1).assetId());
        assertEquals(2.0, cues().get(1).time(), 1e-9);
        assertEquals(Set.of(cues().get(1).id()), selectedCues());
    }

    @Test
    void withoutASoundACueCannotBeAdded() {
        doubleClick(lane, x(1.0), Y);

        assertTrue(cues().isEmpty());
        assertEquals(1, notices().size());
        assertTrue(notices().get(0).startsWith("Add a sound first"), notices().toString());
    }

    @Test
    void aClickSelectsACueAndDeselectsTheLayers() {
        addSound("snd", "chime.wav");
        addCues(new SoundCue("c1", "one", 0.5, "snd", 1, false), new SoundCue("c2", "two", 1.5, "snd", 1, false));
        select("r");

        click(lane, x(0.5), Y);
        assertEquals(Set.of("c1"), selectedCues());
        assertEquals(List.of(), selection());

        // on the label too
        click(lane, x(1.5) + 20, Y);
        assertEquals(Set.of("c2"), selectedCues());

        click(lane, x(0.5), Y, Keys.SHIFT);
        assertEquals(Set.of("c1", "c2"), selectedCues());

        click(lane, x(2.5), Y);
        assertEquals(Set.of(), selectedCues());

        click(lane, x(0.5), Y);
        select("r");
        assertEquals(Set.of(), selectedCues(), "selecting a layer deselects the cues");
    }

    @Test
    void draggingACueMovesItToAFrameInOneUndoStep() {
        addSound("snd", "chime.wav");
        addCues(new SoundCue("c1", "one", 0.5, "snd", 1, false));

        press(lane, x(0.5), Y, Keys.NONE);
        dragTo(lane, x(1.0), Y, Keys.NONE);
        dragTo(lane, x(1.2504), Y, Keys.NONE);
        release(lane, x(1.2504), Y, Keys.NONE);

        assertEquals(1.25, cues().get(0).time(), 1e-9);
        assertEquals("Move Cue", doc.getUndoName());
        run(doc::undo);
        assertEquals(0.5, cues().get(0).time(), 1e-9);

        drag(lane, x(0.5), Y, x(9), Y);
        assertEquals(3, cues().get(0).time(), 1e-9, "not past the end of the document");
    }

    @Test
    void escapeCancelsACueDrag() {
        addSound("snd", "chime.wav");
        addCues(new SoundCue("c1", "one", 0.5, "snd", 1, false));

        press(lane, x(0.5), Y, Keys.NONE);
        dragTo(lane, x(1.0), Y, Keys.NONE);
        assertEquals(1.0, cues().get(0).time(), 1e-9);
        key(KeyCode.ESCAPE);
        release(lane, x(1.0), Y, Keys.NONE);

        assertEquals(0.5, cues().get(0).time(), 1e-9);
        assertEquals("Add Cue", doc.getUndoName());
    }

    @Test
    void deleteRemovesTheSelectedCues() {
        addSound("snd", "chime.wav");
        addCues(new SoundCue("c1", "one", 0.5, "snd", 1, false), new SoundCue("c2", "two", 1.5, "snd", 1, false),
                new SoundCue("c3", "three", 2.5, "snd", 1, false));

        click(lane, x(0.5), Y);
        click(lane, x(2.5), Y, Keys.SHIFT);
        key(KeyCode.DELETE);

        assertEquals(List.of("c2"), cues().stream().map(SoundCue::id).toList());
        assertEquals("Delete Cues", doc.getUndoName());
        assertEquals(1, doc.getAnimation().layers().size(), "the layer stays");

        // also through the menu, wherever the focus is
        click(lane, x(1.5), Y);
        run(() -> window.getCanvasView().requestFocus());
        assertFalse(fx(() -> window.menuItem("menu-edit-delete").isDisable()));
        run(() -> window.menuItem("menu-edit-delete").fire());
        assertTrue(cues().isEmpty());
    }

    @Test
    void theMarkerMenuAddsACueAtTheStartOfTheMarker() {
        addSound("snd", "chime.wav");
        execute(Markers.add(1.0));
        layout();
        String marker = doc.getAnimation().markers().get(0).name();

        run(() -> {
            timeline.setSelectedMarker(marker);
            timeline.getMarkerLane().getMarkerMenu().getItems().stream()
                    .filter(item -> MarkerLane.MENU_ADD_CUE_ID.equals(item.getId())).findFirst().orElseThrow().fire();
        });

        assertEquals(1, cues().size());
        assertEquals(1.0, cues().get(0).time(), 1e-9);
        assertEquals("chime", cues().get(0).name());
    }
}
