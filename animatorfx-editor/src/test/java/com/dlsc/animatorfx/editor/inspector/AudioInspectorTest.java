package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.audio.RecordingAudioOutput;
import com.dlsc.animatorfx.editor.audio.TestSounds;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.inspector.fields.KeyframeToggle;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.editor.inspector.sections.AudioSection;
import com.dlsc.animatorfx.editor.inspector.sections.CueSection;
import com.dlsc.animatorfx.editor.ui.EditorUiTest;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.player.internal.audio.VoiceTarget;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Audio section for audio layers and the Cue section for sound cues. */
class AudioInspectorTest extends EditorUiTest {

    private InspectorView inspector;
    private AudioSection audioSection;
    private List<RecordingAudioOutput> outputs;

    /** Two sounds, a rectangle, the audio layers a (0.5 to 2.5) and b, and the cues c1 and c2. */
    @BeforeEach
    void content() {
        outputs = RecordingAudioOutput.install();
        inspector = window.getSidePanel().getInspector();
        audioSection = inspector.section(AudioSection.class).orElseThrow();
        run(() -> {
            doc.addAsset(new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "chime.wav"), TestSounds.tone(1, 0.5));
            doc.addAsset(new Asset("boom", Asset.Kind.AUDIO, "assets/boom.wav", "boom.wav"), TestSounds.tone(0.5, 0.5));
            doc.addAsset(new Asset("img", Asset.Kind.IMAGE, "assets/img.png", "img.png"), new byte[]{1});
        });
        add(rect("r", 100, 100, 100, 100), audio("a", 0.5, 2.5), audio("b", 0, 3));
        execute(Commands.addCue(new SoundCue("c1", "one", 0.5, "snd", 1, false)));
        execute(Commands.addCue(new SoundCue("c2", "two", 1.5, "snd", 1, false)));
        select("a");
    }

    @AfterEach
    void restore() {
        RecordingAudioOutput.uninstall();
    }

    private static AudioLayer audio(String id, double in, double out) {
        AudioLayer layer = AudioLayer.create(id, "snd", in, out);
        return layer.withBase(layer.base().withId(id));
    }

    private AudioLayer audio(String id) {
        return (AudioLayer) layer(id);
    }

    private SoundCue cue(String id) {
        return doc.getAnimation().cues().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private void selectCues(String... ids) {
        run(() -> {
            doc.getSelection().cueIds().clear();
            doc.getSelection().cueIds().addAll(List.of(ids));
        });
        layout();
    }

    private void type(TextField field, String text) {
        run(() -> {
            field.requestFocus();
            field.setText(text);
            Event.fireEvent(field, new ActionEvent());
        });
        layout();
    }

    private void fire(ButtonBase button) {
        run(button::fire);
        layout();
    }

    private NumberField number(String key) {
        return ((PropertyRows.NumberRow) audioSection.propertyRow(key).orElseThrow()).field();
    }

    private List<String> visibleSections() {
        return fx(() -> inspector.getVisibleSections().stream().map(InspectorSection::title).toList());
    }

    // ------------------------------------------------------------------ audio layers

    @Test
    void anAudioLayerShowsTheLayerAndTheAudioSection() {
        assertEquals(List.of("Layer", "Audio"), visibleSections());
        select("a", "b");
        assertEquals(List.of("Layer", "Audio"), visibleSections());
        select("a", "r");
        assertEquals(List.of("Layer"), visibleSections());
    }

    @Test
    void volumeIsEditedInPercentAndKeyframed() {
        assertEquals("100%", fx(() -> number("volume").getText()));

        type(number("volume"), "50");
        assertEquals(0.5, audio("a").volume().at(0), 1e-9);
        assertFalse(audio("a").volume().isAnimated());

        type(number("volume"), "250");
        assertEquals(1.0, audio("a").volume().at(0), 1e-9, "never above 100 %");

        playhead(1.0);
        KeyframeToggle toggle = audioSection.propertyRow("volume").orElseThrow().toggle();
        fire(toggle);
        assertEquals(1, audio("a").volume().keyframes().size());
        assertEquals(KeyframeToggle.State.KEYED, fx(toggle::getState));

        playhead(2.0);
        type(number("volume"), "20");
        assertEquals(2, audio("a").volume().keyframes().size());
        assertEquals(0.2, audio("a").volume().at(2.0), 1e-9);
    }

    @Test
    void balanceRunsFromLeftToRight() {
        type(number("balance"), "-50");
        assertEquals(-0.5, audio("a").balance().at(0), 1e-9);

        type(number("balance"), "400");
        assertEquals(1.0, audio("a").balance().at(0), 1e-9);
    }

    @Test
    void theClipOffsetIsNeverNegative() {
        NumberField offset = lookup("#" + AudioSection.OFFSET_ID);

        type(offset, "0.25");
        assertEquals(0.25, audio("a").clipOffset(), 1e-9);
        assertEquals("Change Clip Offset", doc.getUndoName());

        type(offset, "-3");
        assertEquals(0, audio("a").clipOffset());
    }

    @Test
    void loopAndMuteAreSwitches() {
        CheckBox loop = lookup("#" + AudioSection.LOOP_ID);
        CheckBox mute = lookup("#" + AudioSection.MUTE_ID);

        fire(loop);
        assertTrue(audio("a").loop());
        assertEquals("Loop Sound", doc.getUndoName());
        fire(mute);
        assertTrue(audio("a").muted());
        assertEquals("Mute Layer", doc.getUndoName());

        run(doc::undo);
        layout();
        assertFalse(fx(mute::isSelected));
        assertTrue(fx(loop::isSelected));
    }

    @Test
    void theAssetIsChosenAmongTheSounds() {
        ComboBox<Asset> asset = lookup("#" + AudioSection.ASSET_ID);
        assertEquals(List.of("chime.wav", "boom.wav"), fx(() -> asset.getItems().stream().map(Asset::name).toList()));
        assertEquals("snd", fx(() -> asset.getValue().id()));

        run(() -> asset.setValue(asset.getItems().get(1)));

        assertEquals("boom", audio("a").assetId());
        assertEquals("Change Sound", doc.getUndoName());
    }

    @Test
    void theLengthAndFormatOfTheClipAreShown() throws Exception {
        Label info = lookup("#" + AudioSection.INFO_ID);

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(() -> info.getText().startsWith("1.000 s")));

        assertEquals("1.000 s\nWAV · 8 kHz · mono", fx(info::getText));
    }

    @Test
    void anEditAppliesToAllSelectedAudioLayers() {
        select("a", "b");

        type(number("volume"), "30");
        fire(lookup("#" + AudioSection.LOOP_ID));

        for (String id : List.of("a", "b")) {
            assertEquals(0.3, audio(id).volume().at(0), 1e-9);
            assertTrue(audio(id).loop());
        }
        run(doc::undo);
        run(doc::undo);
        assertEquals(1.0, audio("b").volume().at(0), 1e-9);
    }

    @Test
    void thePreviewButtonPlaysTheClipFromItsOffsetAndStopsOnTheSecondClick() {
        execute(Commands.setClipOffset("a", 0.25));
        execute(Commands.setValueAt("a", "volume", 0, 0.5, false));
        ButtonBase preview = lookup("#" + AudioSection.PREVIEW_ID);

        fire(preview);

        assertEquals(1, outputs.size());
        RecordingAudioOutput output = outputs.get(0);
        assertEquals(List.of("snippet"), output.calls);
        VoiceTarget target = output.snippets.get(0).get(0);
        assertEquals("snd", target.assetId());
        assertEquals(0.25, target.mediaTime(), 1e-9);
        assertEquals(0.5, target.volume(), 1e-9);
        assertTrue(target.audible());
        assertEquals(0.75, output.snippetSeconds.get(0), 0.01, "to the end of the clip");

        fire(preview);
        assertTrue(output.disposed);
        assertEquals(1, outputs.size());
    }

    @Test
    void aPreviewStopsWhenTheSelectionChanges() {
        fire(lookup("#" + AudioSection.PREVIEW_ID));
        assertFalse(outputs.get(0).disposed);

        select("b");

        assertTrue(outputs.get(0).disposed);
    }

    @Test
    void aLockedAudioLayerCannotBeEdited() {
        execute(Commands.updateLayer("Lock Layer", "a", l -> l.withBase(l.base().withLocked(true))));
        layout();

        assertTrue(fx(() -> audioSection.node().isDisabled()));
    }

    // ------------------------------------------------------------------ cues

    @Test
    void aSelectedCueShowsTheCueSection() {
        selectCues("c1");
        assertEquals(List.of("Cue"), visibleSections());
        assertFalse(fx(() -> inspector.lookup(".inspector-empty").isVisible()));

        select("a");
        assertEquals(List.of("Layer", "Audio"), visibleSections());
    }

    @Test
    void nameTimeVolumeAndMuteOfACueAreEdited() {
        selectCues("c1");

        type(lookup("#" + CueSection.NAME_ID), "bang");
        type(lookup("#" + CueSection.TIME_ID), "1.25");
        type(lookup("#" + CueSection.VOLUME_ID), "40");
        fire(lookup("#" + CueSection.MUTE_ID));

        assertEquals(new SoundCue("c1", "bang", 1.25, "snd", 0.4, true), cue("c1"));

        type(lookup("#" + CueSection.TIME_ID), "99");
        assertEquals(3, cue("c1").time(), "not past the end of the document");
        type(lookup("#" + CueSection.VOLUME_ID), "900");
        assertEquals(1, cue("c1").volume());
    }

    @Test
    void theSoundOfACueIsChosenAmongTheSounds() {
        selectCues("c1", "c2");
        ComboBox<Asset> asset = lookup("#" + CueSection.ASSET_ID);
        assertEquals(2, fx(() -> asset.getItems().size()));

        run(() -> asset.setValue(asset.getItems().get(1)));

        assertEquals("boom", cue("c1").assetId());
        assertEquals("boom", cue("c2").assetId());
        run(doc::undo);
        assertEquals("snd", cue("c2").assetId());
    }

    @Test
    void theCuePreviewPlaysItsSound() {
        execute(Commands.updateCue("Change Cue", "c2", c -> c.withVolume(0.3)));
        selectCues("c2");

        fire(lookup("#" + CueSection.PREVIEW_ID));

        VoiceTarget target = outputs.get(0).snippets.get(0).get(0);
        assertEquals("snd", target.assetId());
        assertEquals(0, target.mediaTime());
        assertEquals(0.3, target.volume(), 1e-9);
    }
}
