package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.audio.TestSounds;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.ui.EditorUiTest;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.SoundCue;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Audio assets in the Assets tab. */
class AudioAssetsTest extends EditorUiTest {

    private AssetsView assets;

    @BeforeEach
    void content() {
        assets = window.getSidePanel().getAssets();
        run(() -> doc.addAsset(new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "chime.wav"), TestSounds.chimes(0.5)));
        layout();
    }

    private Node row() {
        return fx(() -> assets.lookup(".asset-row"));
    }

    private String detail() {
        return fx(() -> ((Label) row().lookup(".asset-detail")).getText());
    }

    private static AudioLayer audio(String id) {
        AudioLayer layer = AudioLayer.create(id, "snd", 0, 1);
        return layer.withBase(layer.base().withId(id));
    }

    @Test
    void anAudioAssetShowsItsKindSizeLengthAndWaveform() throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(() -> row().lookup(".asset-waveform") != null));
        layout();

        assertEquals("Audio · 0.500 s\n21.6 KB · unused", detail());
        assertNotNull(row().lookup(".asset-waveform"));
        assertNull(row().lookup(".asset-sound-icon"), "the waveform replaced the speaker");
    }

    @Test
    void theUsageCountsLayersAndCues() {
        add(audio("a"));
        execute(Commands.addCue(new SoundCue("c1", "one", 0.5, "snd", 1, false)));
        execute(Commands.addCue(new SoundCue("c2", "two", 1.5, "snd", 1, false)));
        layout();

        assertTrue(detail().endsWith("used 3×"), detail());
    }

    @Test
    void removingASoundInUseIsRefusedAndSaysWhatUsesIt() {
        add(audio("a"));
        execute(Commands.addCue(new SoundCue("c1", "one", 0.5, "snd", 1, false)));
        execute(Commands.addCue(new SoundCue("c2", "two", 1.5, "snd", 1, false)));

        run(() -> assets.remove("snd"));
        assertEquals("“chime.wav” is used by 1 layer and 2 cues. Delete or reassign them first.", assets.getMessage());
        assertEquals(1, doc.getAnimation().assets().size());

        execute(Commands.removeCues(java.util.List.of("c1", "c2")));
        run(() -> assets.remove("snd"));
        assertEquals("“chime.wav” is used by 1 layer. Delete or reassign it first.", assets.getMessage());

        execute(Commands.removeLayers(java.util.List.of("a")));
        execute(Commands.addCue(new SoundCue("c3", "three", 0.5, "snd", 1, false)));
        run(() -> assets.remove("snd"));
        assertEquals("“chime.wav” is used by 1 cue. Delete or reassign it first.", assets.getMessage());

        execute(Commands.removeCues(java.util.List.of("c3")));
        run(() -> assets.remove("snd"));
        assertTrue(doc.getAnimation().assets().isEmpty());
    }

    @Test
    void thePlaceButtonPutsTheSoundOnTheTimelineAtThePlayhead() throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(() -> row().lookup(".asset-waveform") != null));
        playhead(1.0);

        run(() -> ((ButtonBase) row().lookup(".asset-place")).fire());

        AudioLayer layer = (AudioLayer) doc.getAnimation().layers().get(0);
        assertEquals("chime", layer.name());
        assertEquals(1.0, layer.base().inPoint(), 1e-9);
        assertEquals(1.5, layer.base().outPoint(), 1e-9);
    }
}
