package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.audio.TestSounds;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Layer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sounds come into a document through File ▸ Import ▸ Audio…, a drop on the window and the Assets tab. */
class AudioImportUiTest extends EditorUiTest {

    private final List<Path> picked = new ArrayList<>();

    @BeforeEach
    void stubThePicker() {
        window.getImportActions().setAudioPicker(owner -> {
            List<Path> files = List.copyOf(picked);
            picked.clear();
            return files;
        });
    }

    private Path sound(String name, double seconds) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, TestSounds.tone(seconds, 0.5));
        return file;
    }

    private void importAudio() {
        run(() -> window.menuItem("menu-file-import-audio").fire());
        WaitForAsyncUtils.waitForFxEvents();
        layout();
    }

    private List<String> notices() {
        return fx(() -> window.getNoticeBar().getMessages());
    }

    @Test
    void importingAWavAddsOneAssetAndOneAudioLayerAtThePlayheadInOneUndoStep() throws Exception {
        picked.add(sound("door chime.wav", 0.75));
        run(() -> doc.setPlayhead(1.0));

        importAudio();

        assertEquals(1, doc.getAnimation().assets().size());
        Asset asset = doc.getAnimation().assets().get(0);
        assertEquals(Asset.Kind.AUDIO, asset.kind());
        assertEquals("door chime.wav", asset.name());
        assertEquals("assets/" + asset.id() + ".wav", asset.path());
        assertEquals(1, doc.getAnimation().layers().size());
        AudioLayer layer = (AudioLayer) doc.getAnimation().layers().get(0);
        assertEquals("door chime", layer.name());
        assertEquals(asset.id(), layer.assetId());
        assertEquals(1.0, layer.base().inPoint(), 1e-9);
        assertEquals(1.75, layer.base().outPoint(), 1e-9);
        assertEquals(0, layer.clipOffset());
        assertEquals(List.of(layer.id()), List.copyOf(doc.getSelection().layerIds()));
        assertEquals(List.of(), notices());
        assertEquals("Add Audio", doc.getUndoName());

        run(doc::undo);
        assertTrue(doc.getAnimation().assets().isEmpty());
        assertTrue(doc.getAnimation().layers().isEmpty());
        assertFalse(doc.isCanUndo());
    }

    @Test
    void aSoundImportedAtTheEndOfTheDocumentStartsAtZeroAndIsCutAtTheEnd() throws Exception {
        picked.add(sound("long.wav", 5));
        run(() -> doc.setPlayhead(3));

        importAudio();

        Layer layer = doc.getAnimation().layers().get(0);
        assertEquals(0, layer.base().inPoint());
        assertEquals(3, layer.base().outPoint(), 1e-9);
    }

    @Test
    void severalSoundsAreImportedOneAboveTheOther() throws Exception {
        picked.add(sound("a.wav", 0.5));
        picked.add(sound("b.aiff", 0.5));
        Files.write(dir.resolve("b.aiff"), TestSounds.wav(TestSounds.sine(0.5, 8000, 440, 0.5), 8000, 1, 16, true));

        importAudio();

        assertEquals(List.of("a", "b"), doc.getAnimation().layers().stream().map(Layer::name).toList());
        assertEquals(2, doc.getAnimation().assets().size());
    }

    @Test
    void aFileThatIsNoSoundIsRefusedWithANotice() throws Exception {
        Path file = dir.resolve("noise.mp3");
        Files.write(file, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
        picked.add(file);

        importAudio();
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> notices().stream().anyMatch(n -> n.startsWith("Could not import")));

        assertEquals(List.of("Could not import noise.mp3: it is not a sound the editor can play."), notices());
        assertTrue(doc.getAnimation().assets().isEmpty());
        assertTrue(doc.getAnimation().layers().isEmpty());
        assertFalse(doc.isCanUndo());
    }

    @Test
    void aFileOfMoreThan50MegabytesIsRefused() throws Exception {
        Path file = dir.resolve("huge.wav");
        try (RandomAccessFile sparse = new RandomAccessFile(file.toFile(), "rw")) {
            sparse.setLength(50L * 1024 * 1024 + 1);
        }
        picked.add(file);

        importAudio();

        assertEquals(List.of("Could not import huge.wav: it is larger than 50 MB."), notices());
        assertTrue(doc.getAnimation().assets().isEmpty());
    }

    @Test
    void aSoundDroppedOnTheWindowBecomesAnAudioLayer() throws Exception {
        Path file = sound("drop.wav", 0.5);

        assertTrue(window.isDroppable(file.toFile()));
        assertTrue(fx(() -> window.openDropped(List.of(file.toFile()))));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, window.getWorkspace().getDocuments().size(), "no new document");
        assertEquals(List.of("drop"), doc.getAnimation().layers().stream().map(Layer::name).toList());
    }

    @Test
    void theAssetsTabAddsASoundWithoutALayerAndPlacesItOnTheTimeline() throws Exception {
        Path file = sound("tick.wav", 0.25);
        var assets = window.getSidePanel().getAssets();

        run(() -> assets.addFiles(List.of(file)));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, doc.getAnimation().assets().size());
        assertTrue(doc.getAnimation().layers().isEmpty());
        String assetId = doc.getAnimation().assets().get(0).id();

        run(() -> doc.setPlayhead(0.5));
        run(() -> assets.placeAudio(assetId));

        AudioLayer layer = (AudioLayer) doc.getAnimation().layers().get(0);
        assertEquals("tick", layer.name());
        assertEquals(0.5, layer.base().inPoint(), 1e-9);
        assertEquals(0.75, layer.base().outPoint(), 1e-9);
        assertEquals(List.of(layer.id()), List.copyOf(doc.getSelection().layerIds()));
    }
}
