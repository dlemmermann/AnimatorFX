package com.dlsc.animatorfx.editor.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

/**
 * Finds out whether bytes are a sound the editor can play, and how long it is. WAV and AIFF are
 * answered from their header. Everything else is asked from the media stack: the bytes go into
 * a temporary file, a muted media player opens it, reports the length or an error, and is
 * disposed at once; the file is deleted. Every probe has its own player, so probes do not wait
 * for each other or for the analysis of waveforms. Nothing is played.
 */
public final class AudioProbe {

    /**
     * A file the media stack has not opened by then is no sound: not every stack reports an
     * error for a file it cannot make sense of.
     */
    static final Duration READY_LIMIT = Duration.seconds(4);

    /** Reads files and writes temporary ones, so that the JavaFX thread does not. */
    static final ExecutorService IO = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "animatorfx-audio-io");
        thread.setDaemon(true);
        return thread;
    });

    private AudioProbe() {
    }

    /**
     * @param extension the extension of the sound's file, by which the media stack tells the format
     * @return completes on the JavaFX application thread, or at once on the calling thread for a
     *         sound whose header says it all; exceptionally when the bytes are no playable sound
     */
    public static CompletableFuture<AudioInfo> probe(byte[] bytes, String extension) {
        CompletableFuture<AudioInfo> result = new CompletableFuture<>();
        Optional<AudioInfo> header = AudioInfo.read(bytes);
        if (header.isPresent()) {
            result.complete(header.get());
            return result;
        }
        IO.execute(() -> {
            Path file = null;
            try {
                file = Files.createTempFile("animatorfx-probe", "." + (extension == null || extension.isBlank() ? "bin" : extension));
                Files.write(file, bytes);
            } catch (IOException | RuntimeException e) {
                delete(file);
                Platform.runLater(() -> result.completeExceptionally(e));
                return;
            }
            Path written = file;
            Platform.runLater(() -> open(written, AudioInfo.formatOf(extension == null ? "" : extension), result));
        });
        return result;
    }

    private static void open(Path file, String format, CompletableFuture<AudioInfo> result) {
        MediaPlayer[] player = new MediaPlayer[1];
        PauseTransition limit = new PauseTransition(READY_LIMIT);
        Runnable release = () -> {
            limit.stop();
            if (player[0] != null) {
                try {
                    player[0].dispose();
                } catch (RuntimeException | LinkageError e) {
                    // left to the garbage collector
                }
                player[0] = null;
            }
            IO.execute(() -> delete(file));
        };
        Runnable failed = () -> {
            if (!result.isDone()) {
                release.run();
                result.completeExceptionally(new IOException("The sound cannot be played"));
            }
        };
        limit.setOnFinished(e -> failed.run());
        limit.play();
        try {
            Media media = new Media(file.toUri().toString());
            media.setOnError(failed);
            player[0] = new MediaPlayer(media);
            player[0].setMute(true);
            player[0].setVolume(0);
            player[0].setOnError(failed);
            player[0].setOnHalted(failed);
            player[0].setOnReady(() -> {
                if (result.isDone()) {
                    return;
                }
                Duration length = media.getDuration();
                if (length == null || length.isUnknown() || length.isIndefinite() || !(length.toSeconds() > 0)) {
                    failed.run();
                    return;
                }
                release.run();
                result.complete(new AudioInfo(length.toSeconds(), 0, 0, format));
            });
        } catch (RuntimeException | LinkageError e) {
            failed.run();
        }
    }

    private static void delete(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException | RuntimeException e) {
                file.toFile().deleteOnExit();
            }
        }
    }
}
