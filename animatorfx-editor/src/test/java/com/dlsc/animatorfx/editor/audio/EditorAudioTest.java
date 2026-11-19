package com.dlsc.animatorfx.editor.audio;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.AnimationPlayer;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the sound of the editor comes from, and that edits do not make a player start over. */
class EditorAudioTest {

    private static final byte[] SOUND = TestSounds.tone(1, 0.5);
    private List<RecordingAudioOutput> outputs;
    private boolean playing;

    @BeforeAll
    static void toolkit() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    @BeforeEach
    void record() {
        outputs = RecordingAudioOutput.install();
        playing = false;
    }

    @AfterEach
    void restore() {
        RecordingAudioOutput.uninstall();
    }

    private static AnimationFile file(AudioLayer layer) {
        Animation animation = Animation.create("Test", 100, 100, 2)
                .withAssets(List.of(new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "snd.wav")))
                .withLayers(List.<Layer>of(layer));
        return new AnimationFile(animation, Map.of("snd", SOUND));
    }

    private static AudioLayer layer() {
        AudioLayer layer = AudioLayer.create("a", "snd", 0, 2);
        return layer.withBase(layer.base().withId("a"));
    }

    private static void fx(Runnable action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                done.complete(null);
            } catch (Throwable e) {
                done.completeExceptionally(e);
            }
        });
        done.get(10, TimeUnit.SECONDS);
    }

    @Test
    void withoutAFactoryTheTestsOfTheEditorHaveNoSound() {
        RecordingAudioOutput.uninstall();

        assertEquals("off", System.getProperty(EditorAudio.PROPERTY));
        assertNull(EditorAudio.createOutput(file(layer())));
    }

    @Test
    void anEditThatKeepsTheSoundsKeepsTheOutput() throws Exception {
        fx(() -> {
            AnimationPlayer player = new AnimationPlayer();
            EditorAudio.install(player, () -> playing);
            player.setAnimation(file(layer()));
            player.scrub(0.5);
            player.setAnimation(file(layer().withVolume(Animatable.of(0.3)).withClipOffset(0.2)));
            player.scrub(0.6);
        });

        assertEquals(1, outputs.size());
        RecordingAudioOutput output = outputs.get(0);
        assertEquals(List.of("snippet", "silence", "snippet"), output.calls);
        assertEquals(0.3, output.snippets.get(1).get(0).volume(), 1e-9);
        assertFalse(output.disposed);
    }

    @Test
    void whilePlayingAnEditDoesNotSilenceTheOutput() throws Exception {
        playing = true;
        fx(() -> {
            AnimationPlayer player = new AnimationPlayer();
            EditorAudio.install(player, () -> playing);
            player.setAnimation(file(layer()));
            playing = false;
            player.scrub(0.5);
            playing = true;
            player.setAnimation(file(layer().withMuted(true)));
        });

        assertEquals(List.of("snippet"), outputs.get(0).calls);

        // nobody took the output over: it falls silent by itself
        Thread.sleep(600);
        fx(() -> {
        });
        assertEquals(List.of("snippet", "silence"), outputs.get(0).calls);
    }

    @Test
    void otherSoundsOrLayersGetANewOutput() throws Exception {
        Runnable[] release = new Runnable[1];
        fx(() -> {
            AnimationPlayer player = new AnimationPlayer();
            release[0] = EditorAudio.install(player, () -> playing);
            player.setAnimation(file(layer()));
            player.scrub(0.5);
            player.setAnimation(file(layer().withLoop(true)));
            player.scrub(0.5);
        });

        assertEquals(2, outputs.size());
        assertTrue(outputs.get(0).disposed);
        assertFalse(outputs.get(1).disposed);

        fx(release[0]);
        assertTrue(outputs.get(1).disposed);
    }
}
