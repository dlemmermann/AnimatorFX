package com.dlsc.animatorfx.editor.audio;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveformCacheTest {

    @BeforeAll
    static void toolkit() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    @Test
    void aWavFileHasItsInfoAtOnceAndItsWaveformSoonAfter() throws Exception {
        WaveformCache cache = new WaveformCache();
        byte[] wav = TestSounds.tone(0.5, 0.5);
        int before = fx(cache::getRevision);

        AudioInfo info = fx(() -> cache.info(wav, "wav").getNow(null));
        assertEquals(0.5, info.duration(), 1e-9);
        assertEquals(8000, info.sampleRate());
        assertEquals(1, info.channels());
        assertEquals("WAVE · 8 kHz · mono", info.describe());
        assertEquals("0.500 s", info.durationText());

        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(() -> cache.waveform(wav, "wav").isPresent()));
        Waveform waveform = fx(() -> cache.waveform(wav, "wav").orElseThrow());
        assertEquals(0.5, waveform.peak(0, 0.5), 0.02);
        assertTrue(fx(cache::getRevision) > before, "the arrival is announced");
        assertSame(waveform, fx(() -> cache.waveform(wav, "wav").orElseThrow()), "decoded once");
    }

    @Test
    void sameContentInAnotherArrayIsAnotherEntry() throws Exception {
        WaveformCache cache = new WaveformCache();
        byte[] wav = TestSounds.tone(0.2, 0.5);
        byte[] copy = wav.clone();

        fx(() -> cache.waveform(wav, "wav"));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(() -> cache.waveform(wav, "wav").isPresent()));

        assertEquals(Optional.empty(), fx(() -> cache.waveform(copy, "wav")));
    }

    @Test
    void whatIsNoSoundHasNoInfoAndNoWaveform() throws Exception {
        WaveformCache cache = new WaveformCache();
        byte[] junk = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        CompletableFuture<AudioInfo> info = fx(() -> cache.info(junk, "mp3"));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, info::isDone);

        assertTrue(info.isCompletedExceptionally());
        assertFalse(fx(() -> cache.waveform(junk, "mp3")).isPresent());
        assertFalse(fx(() -> cache.knownInfo(junk, "mp3")).isPresent());
    }
}
