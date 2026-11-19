package com.dlsc.animatorfx.editor.audio;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

/**
 * An approximate waveform for sounds that {@code javax.sound.sampled} cannot decode (MP3, AAC):
 * the file is played through a muted JavaFX {@code MediaPlayer} at volume 0, at twice the normal
 * speed where the platform does that, and the audio spectrum it reports is turned into one loudness value per
 * interval. Nothing is audible. The result is normalised: the loudest moment has the peak 1.
 *
 * <p>Runs on the JavaFX application thread through the callbacks of the media player and never
 * blocks it. The analysis ends by itself: when the sound has been played, on a media error, when
 * the file has not been opened after {@link #READY_SECONDS} seconds, when no data arrives for {@link #STALL_SECONDS} seconds, or after a time limit that follows from the
 * length of the sound. Any failure means "no waveform".
 */
public final class SpectrumAnalyser {

    /** One report of the spectrum listener. */
    record Sample(double timestamp, double wallSeconds, double amplitude) {
    }

    /** A running analysis. */
    public interface Analysis {

        /** Stops the analysis; nothing is reported any more. */
        void cancel();
    }

    static final int THRESHOLD = -80;
    static final double STALL_SECONDS = 6;
    /** A file the media stack has not opened by then is taken for no sound: not every stack reports an error. */
    static final double READY_SECONDS = 4;
    private static final double INTERVAL = 0.02;
    private static final int MIN_STUCK_REPORTS = 4;
    private static final int BANDS = 24;
    /**
     * Faster is not better: measured on macOS with the JavaFX 25 media stack, the spectrum of a
     * WAV file is complete at the rates 1 to 3, smeared at 4 and mostly missing at 8. MP3 and
     * AAC play in real time there whatever the rate.
     */
    private static final double[] RATES = {2, 1};

    private SpectrumAnalyser() {
    }

    /**
     * Starts analysing a sound file. Call on the JavaFX application thread; the consumers are
     * called there too.
     *
     * @param onDuration receives the length of the sound in seconds as soon as it is known
     * @param onProgress receives the waveform of the part analysed so far, about once a second
     * @param onDone     receives the waveform, or empty when the sound could not be analysed;
     *                   called exactly once unless the analysis is cancelled
     */
    public static Analysis analyse(URI source, DoubleConsumer onDuration, Consumer<Waveform> onProgress,
                                   Consumer<Optional<Waveform>> onDone) {
        Run run = new Run(onDuration, onProgress, onDone);
        run.start(source);
        return run;
    }

    private static final class Run implements Analysis {

        private final DoubleConsumer onDuration;
        private final Consumer<Waveform> onProgress;
        private final Consumer<Optional<Waveform>> onDone;
        private final List<Sample> samples = new ArrayList<>();
        private final Timeline watchdog = new Timeline(new KeyFrame(Duration.seconds(1), e -> check()));
        private MediaPlayer player;
        private double duration = Double.NaN;
        private long started;
        private long lastData;
        private boolean finished;
        private boolean ready;

        Run(DoubleConsumer onDuration, Consumer<Waveform> onProgress, Consumer<Optional<Waveform>> onDone) {
            this.onDuration = onDuration;
            this.onProgress = onProgress;
            this.onDone = onDone;
        }

        void start(URI source) {
            started = lastData = System.nanoTime();
            watchdog.setCycleCount(Timeline.INDEFINITE);
            watchdog.play();
            try {
                Media media = new Media(source.toString());
                media.setOnError(() -> finish(false));
                player = new MediaPlayer(media);
                // both: neither alone is promised to keep every platform silent
                player.setMute(true);
                player.setVolume(0);
                player.setAudioSpectrumInterval(INTERVAL);
                player.setAudioSpectrumNumBands(BANDS);
                player.setAudioSpectrumThreshold(THRESHOLD);
                player.setAudioSpectrumListener((timestamp, length, magnitudes, phases) -> {
                    lastData = System.nanoTime();
                    samples.add(new Sample(timestamp, (lastData - started) / 1e9, amplitude(magnitudes, THRESHOLD)));
                });
                player.setOnReady(this::ready);
                player.setOnEndOfMedia(() -> finish(true));
                player.setOnError(() -> finish(false));
                player.setOnHalted(() -> finish(false));
            } catch (RuntimeException | LinkageError e) {
                finish(false);
            }
        }

        private void ready() {
            if (finished) {
                return;
            }
            ready = true;
            Duration length = player.getMedia().getDuration();
            if (length != null && !length.isUnknown() && !length.isIndefinite() && length.toSeconds() > 0) {
                duration = length.toSeconds();
                onDuration.accept(duration);
                if (finished) {
                    return;
                }
            }
            for (double rate : RATES) {
                try {
                    player.setRate(rate);
                    break;
                } catch (RuntimeException e) {
                    // the next lower rate
                }
            }
            lastData = System.nanoTime();
            player.play();
        }

        /** Once a second: reports progress and gives up on a player that stalls or takes too long. */
        private void check() {
            long now = System.nanoTime();
            double silentFor = (now - lastData) / 1e9;
            double running = (now - started) / 1e9;
            // a platform that ignores the rate plays in real time
            double limit = Double.isNaN(duration) ? 60 : duration * 1.5 + 20;
            if (!ready && running > READY_SECONDS) {
                finish(false);
            } else if (silentFor > STALL_SECONDS || running > limit) {
                // what has been heard so far is still worth showing if it is most of the sound
                finish(covered() > 0.9);
            } else if (!samples.isEmpty() && !Double.isNaN(duration)) {
                onProgress.accept(build(samples, duration));
            }
        }

        private double covered() {
            if (samples.isEmpty() || Double.isNaN(duration)) {
                return 0;
            }
            return samples.get(samples.size() - 1).timestamp() / duration;
        }

        private void finish(boolean success) {
            if (finished) {
                return;
            }
            release();
            double length = Double.isNaN(duration) && !samples.isEmpty()
                    ? samples.get(samples.size() - 1).timestamp() + INTERVAL : duration;
            Waveform result = success && !samples.isEmpty() && length > 0 ? build(samples, length) : null;
            onDone.accept(Optional.ofNullable(result));
        }

        @Override
        public void cancel() {
            release();
        }

        private void release() {
            finished = true;
            watchdog.stop();
            if (player != null) {
                try {
                    player.setAudioSpectrumListener(null);
                    player.dispose();
                } catch (RuntimeException | LinkageError e) {
                    // left to the garbage collector
                }
                player = null;
            }
        }
    }

    /**
     * The loudness of one spectrum report, on a linear scale that is only meaningful relative to
     * other reports of the same sound: the magnitudes are decibels per frequency band, never
     * below the threshold, which stands for silence. Their powers are added up.
     */
    static double amplitude(float[] magnitudes, int threshold) {
        double power = 0;
        for (float magnitude : magnitudes) {
            if (magnitude > threshold + 0.5 && !Float.isNaN(magnitude)) {
                power += Math.pow(10, magnitude / 10.0);
            }
        }
        return Math.sqrt(power);
    }

    /**
     * Turns the reports into a waveform of the given length, normalised to a loudest peak of 1.
     *
     * <p>The time of a report is its timestamp, with one correction. Some media stacks (macOS
     * for MP3 and AAC) deliver the first stretch of sound while the clock of the player still
     * stands at 0, so all later timestamps are early by that stretch. It is measured on the wall
     * clock, scaled by the speed the player really ran at, and added back.
     */
    static Waveform build(List<Sample> samples, double duration) {
        int count = (int) Math.max(1, Math.ceil(duration * Waveform.PEAKS_PER_SECOND - 1e-9));
        float[] peaks = new float[count];
        if (samples.isEmpty()) {
            return new Waveform(duration, peaks);
        }
        Sample first = samples.get(0);
        Sample last = samples.get(samples.size() - 1);
        int moving = 0;
        while (moving < samples.size() && samples.get(moving).timestamp() <= 1e-6) {
            moving++;
        }
        double offset = 0;
        double speed = 1;
        // one report at 0 is how every sound starts; a run of them is a clock that has not started
        if (moving >= MIN_STUCK_REPORTS && moving < samples.size()) {
            Sample clockStart = samples.get(moving);
            if (last.wallSeconds() > clockStart.wallSeconds() && last.timestamp() > clockStart.timestamp()) {
                speed = (last.timestamp() - clockStart.timestamp()) / (last.wallSeconds() - clockStart.wallSeconds());
            }
            offset = (clockStart.wallSeconds() - first.wallSeconds()) * speed - clockStart.timestamp();
            // within the jitter of the reports there is nothing to correct
            if (offset < 0.1) {
                offset = 0;
            }
        }
        double max = 0;
        double previous = 0;
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            double time = offset == 0 ? sample.timestamp()
                    : i < moving ? (sample.wallSeconds() - first.wallSeconds()) * speed : sample.timestamp() + offset;
            // a report stands for the time since the one before it
            int from = (int) Math.floor(Math.min(previous, time) * Waveform.PEAKS_PER_SECOND);
            int to = (int) Math.floor(time * Waveform.PEAKS_PER_SECOND);
            if (time - previous > 0.25) {
                from = to;
            }
            for (int b = Math.max(0, from); b <= to && b < count; b++) {
                peaks[b] = (float) Math.max(peaks[b], sample.amplitude());
            }
            previous = time;
            max = Math.max(max, sample.amplitude());
        }
        if (max > 0) {
            for (int i = 0; i < count; i++) {
                peaks[i] = (float) Math.min(1, peaks[i] / max);
            }
        }
        return new Waveform(duration, peaks);
    }
}
