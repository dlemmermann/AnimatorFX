package com.dlsc.animatorfx.editor.audio;

import com.dlsc.animatorfx.editor.audio.SpectrumAnalyser.Sample;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The approximate waveform from the audio spectrum. The pure part is tested with made-up
 * reports; the part that runs a real, muted media player needs the media stack and is skipped
 * where it is missing. MP3 and AAC files are made with the tools of the machine when it has them.
 */
class SpectrumAnalyserTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void toolkit() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    // ------------------------------------------------------------------ pure

    @Test
    void silenceHasNoAmplitudeAndLouderBandsHaveMore() {
        float[] silent = {-80, -80, -80};
        assertEquals(0, SpectrumAnalyser.amplitude(silent, -80));
        double quiet = SpectrumAnalyser.amplitude(new float[]{-40, -80, -80}, -80);
        double loud = SpectrumAnalyser.amplitude(new float[]{-20, -80, -80}, -80);
        assertEquals(0.01, quiet, 1e-9);
        assertEquals(0.1, loud, 1e-9);
        assertTrue(SpectrumAnalyser.amplitude(new float[]{-20, -20, -80}, -80) > loud);
    }

    @Test
    void reportsBecomeNormalisedPeaksAtTheirTimestamps() {
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            double time = i * 0.02;
            samples.add(new Sample(time, time / 8, time >= 0.5 && time < 0.75 ? 0.2 : time >= 0.75 ? 0.05 : 0));
        }

        Waveform waveform = SpectrumAnalyser.build(samples, 1);

        assertEquals(1, waveform.duration());
        assertEquals(Waveform.PEAKS_PER_SECOND, waveform.peaks().length);
        assertEquals(0, waveform.peak(0, 0.47));
        assertEquals(1, waveform.peak(0.52, 0.72), 1e-6);
        assertEquals(0.25, waveform.peak(0.78, 0.97), 1e-6);
    }

    @Test
    void soundDeliveredBeforeTheClockStartsShiftsTheLaterTimestamps() {
        // as macOS does for AAC: 0.6 s of reports at timestamp 0, in real time, then the clock runs
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            samples.add(new Sample(0, 0.1 + i * 0.04, 0));
        }
        for (int i = 1; i <= 85; i++) {
            double timestamp = i * 0.04;
            double media = timestamp + 0.6;
            samples.add(new Sample(timestamp, 0.7 + timestamp, media >= 1 && media < 2 ? 0.5 : 0));
        }

        Waveform waveform = SpectrumAnalyser.build(samples, 4);

        assertEquals(0, waveform.peak(0, 0.9));
        assertEquals(1, waveform.peak(1.05, 1.95), 1e-6);
        assertEquals(0, waveform.peak(2.1, 4));
    }

    @Test
    void noReportsGiveAnEmptyWaveform() {
        Waveform waveform = SpectrumAnalyser.build(List.of(), 0.5);

        assertEquals(100, waveform.peaks().length);
        assertEquals(0, waveform.peak(0, 0.5));
    }

    // ------------------------------------------------------------------ real media, muted

    private record Outcome(Optional<Waveform> waveform, double duration) {
    }

    private Outcome analyse(Path file) throws Exception {
        CompletableFuture<Optional<Waveform>> done = new CompletableFuture<>();
        double[] duration = {Double.NaN};
        Platform.runLater(() -> SpectrumAnalyser.analyse(file.toUri(), d -> duration[0] = d, partial -> {
        }, done::complete));
        return new Outcome(done.get(40, TimeUnit.SECONDS), duration[0]);
    }

    /** One second of silence, one of a tone, two of silence. */
    private Path toneFile() throws Exception {
        double[] samples = new double[22050 * 4];
        double[] tone = TestSounds.sine(1, 22050, 440, 0.8);
        System.arraycopy(tone, 0, samples, 22050, tone.length);
        Path file = dir.resolve("tone.wav");
        Files.write(file, TestSounds.wav(samples, 22050, 1, 16, false));
        return file;
    }

    private static void assertToneInTheSecondSecond(Outcome outcome, double slack) {
        assumeTrue(outcome.waveform().isPresent(), "the media stack gave no spectrum");
        Waveform waveform = outcome.waveform().get();
        assertEquals(4, outcome.duration(), 0.15);
        assertEquals(4, waveform.duration(), 0.15);
        String peaks = String.format("before %.2f, tone %.2f, after %.2f", waveform.peak(0, 1 - slack),
                waveform.peak(1 + slack, 2 - slack), waveform.peak(2 + slack, 4));
        assertTrue(waveform.peak(0, 1 - slack) < 0.1, "silent before the tone: " + peaks);
        assertTrue(waveform.peak(1 + slack, 2 - slack) > 0.6, "the tone: " + peaks);
        assertTrue(waveform.peak(2 + slack, 4) < 0.1, "silent after the tone: " + peaks);
    }

    @Test
    void aMutedPlayerStillReportsTheSpectrumOfAWavFile() throws Exception {
        assertToneInTheSecondSecond(analyse(toneFile()), 0.1);
    }

    @Test
    void anAacFileGetsAnApproximateWaveform() throws Exception {
        Path m4a = dir.resolve("tone.m4a");
        assumeTrue(convert("afconvert", "-f", "m4af", "-d", "aac", toneFile().toString(), m4a.toString()),
                "afconvert is not available");
        assertToneInTheSecondSecond(analyse(m4a), 0.2);
    }

    @Test
    void anMp3FileGetsAnApproximateWaveform() throws Exception {
        Path mp3 = dir.resolve("tone.mp3");
        assumeTrue(convert("lame", "--quiet", toneFile().toString(), mp3.toString()), "lame is not available");
        assertToneInTheSecondSecond(analyse(mp3), 0.2);
    }

    @Test
    void aFileThatIsNoSoundGivesNoWaveform() throws Exception {
        Path file = dir.resolve("broken.mp3");
        Files.write(file, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertFalse(analyse(file).waveform().isPresent());
    }

    @Test
    void aCancelledAnalysisReportsNothing() throws Exception {
        Path file = toneFile();
        CompletableFuture<Optional<Waveform>> done = new CompletableFuture<>();
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        Platform.runLater(() -> {
            SpectrumAnalyser.analyse(file.toUri(), d -> {
            }, partial -> {
            }, done::complete).cancel();
            cancelled.complete(null);
        });
        cancelled.get(5, TimeUnit.SECONDS);
        Thread.sleep(1500);

        assertFalse(done.isDone());
    }

    private static boolean convert(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
