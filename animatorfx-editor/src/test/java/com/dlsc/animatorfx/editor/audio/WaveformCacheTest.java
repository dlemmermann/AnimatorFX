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
        assertEquals("WAV · 8 kHz · mono", info.describe());
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
    void whatIsKnownGoesWithTheCopyADocumentMakes() throws Exception {
        WaveformCache cache = new WaveformCache();
        byte[] wav = TestSounds.tone(0.2, 0.5);
        byte[] copy = wav.clone();

        fx(() -> cache.waveform(wav, "wav"));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(() -> cache.waveform(wav, "wav").isPresent()));
        int revision = fx(cache::getRevision);
        fx(() -> {
            cache.transfer(wav, copy);
            return null;
        });

        assertSame(fx(() -> cache.waveform(wav, "wav").orElseThrow()), fx(() -> cache.waveform(copy, "wav").orElseThrow()));
        assertEquals(revision, fx(cache::getRevision), "nothing was worked out again");
    }

    @Test
    void theLengthOfACompressedSoundDoesNotWaitForTheWaveformOfAnother() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"), "probe");
        java.nio.file.Path longFile = dir.resolve("long.m4a");
        java.nio.file.Path shortFile = dir.resolve("short.m4a");
        org.junit.jupiter.api.Assumptions.assumeTrue(TestSounds.aac(longFile, 8) && TestSounds.aac(shortFile, 0.5),
                "afconvert is not available");
        WaveformCache cache = new WaveformCache();
        byte[] longSound = java.nio.file.Files.readAllBytes(longFile);
        byte[] shortSound = java.nio.file.Files.readAllBytes(shortFile);

        CompletableFuture<AudioInfo> first = fx(() -> cache.info(longSound, "m4a"));
        assertEquals(8, first.get(4, TimeUnit.SECONDS).duration(), 0.12);
        // the long sound is being analysed now, which takes eight seconds
        CompletableFuture<AudioInfo> second = fx(() -> cache.info(shortSound, "m4a"));

        assertEquals(0.5, second.get(4, TimeUnit.SECONDS).duration(), 0.12);
        assertEquals("AAC", second.get().format());
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
