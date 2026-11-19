package com.dlsc.animatorfx.editor.audio;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The check behind the hidden command line switch {@value #SWITCH}: tells whether JavaFX media
 * works in this runtime, without a window and without a sound. It opens a generated sound in a
 * muted player and reports whether that player starts playing. Made for packaged application
 * images, where the media module and its native libraries have to be part of the runtime.
 */
public final class AudioDiagnosis {

    /** The command line argument that runs the check instead of the editor. */
    public static final String SWITCH = "--diagnose-audio";

    private static final long TIMEOUT_MILLIS = 3000;
    private static final int SAMPLE_RATE = 22_050;

    /**
     * @param line what is printed: {@code audio: ok (…)} or {@code audio: failed: …}
     */
    public record Result(boolean ok, String line) {
    }

    private AudioDiagnosis() {
    }

    public static boolean requested(String[] arguments) {
        return List.of(arguments).contains(SWITCH);
    }

    /**
     * Starts the JavaFX toolkit, runs the check, prints its outcome and shuts the toolkit down.
     *
     * @return the exit code: 0 if media works
     */
    public static int run(PrintStream out) {
        Result result;
        try {
            try {
                Platform.startup(() -> {
                });
            } catch (IllegalStateException e) {
                // already running: the Java launcher starts the toolkit before the main method of
                // an Application class
            }
            out.println("javafx: " + System.getProperty("javafx.runtime.version") + ", media module: "
                    + Media.class.getModule().getName() + ", java: " + Runtime.version() + ", os: "
                    + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            result = probe(tone(), "wav", TIMEOUT_MILLIS);
        } catch (RuntimeException | LinkageError e) {
            result = new Result(false, "audio: failed: " + e);
        }
        out.println(result.line());
        try {
            Platform.exit();
        } catch (RuntimeException e) {
            // the toolkit never came up; there is nothing to shut down
        }
        return result.ok() ? 0 : 1;
    }

    /**
     * Plays the sound muted. Needs the running toolkit and must not be called on its thread.
     *
     * @param extension the file extension of the sound, by which the media stack tells its format
     */
    static Result probe(byte[] sound, String extension, long timeoutMillis) {
        Path file;
        try {
            file = Files.createTempFile("animatorfx-diagnosis", "." + extension);
            Files.write(file, sound);
        } catch (IOException e) {
            return new Result(false, "audio: failed: the test sound could not be written: " + e);
        }
        AtomicReference<MediaPlayer> created = new AtomicReference<>();
        AtomicReference<String> outcome = new AtomicReference<>();
        AtomicReference<String> reached = new AtomicReference<>("not opened");
        CountDownLatch done = new CountDownLatch(1);
        try {
            Platform.runLater(() -> {
                try {
                    MediaPlayer player = new MediaPlayer(new Media(file.toUri().toString()));
                    created.set(player);
                    player.setMute(true);
                    player.setVolume(0);
                    player.setOnError(() -> {
                        outcome.compareAndSet(null, "audio: failed: " + player.getError());
                        done.countDown();
                    });
                    player.setOnReady(() -> {
                        reached.set("READY");
                        player.play();
                    });
                    player.setOnPlaying(() -> {
                        outcome.compareAndSet(null, String.format(Locale.ROOT, "audio: ok (PLAYING, %.2f s, %s)",
                                player.getMedia().getDuration().toSeconds(), player.getClass().getModule().getName()));
                        done.countDown();
                    });
                } catch (RuntimeException | LinkageError e) {
                    outcome.compareAndSet(null, "audio: failed: " + e);
                    done.countDown();
                }
            });
            if (!done.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                outcome.compareAndSet(null, "audio: failed: the player did not start within " + timeoutMillis
                        + " ms (" + reached.get() + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome.compareAndSet(null, "audio: failed: interrupted");
        } finally {
            CountDownLatch released = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    MediaPlayer player = created.get();
                    if (player != null) {
                        player.dispose();
                    }
                } finally {
                    released.countDown();
                }
            });
            try {
                released.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                file.toFile().deleteOnExit();
            }
        }
        String line = outcome.get();
        return new Result(line.startsWith("audio: ok"), line);
    }

    /** A quiet tone of 0.3 s as a WAV file: 16 bit, mono. */
    static byte[] tone() {
        int frames = (int) (0.3 * SAMPLE_RATE);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + frames * 2);
        ascii(out, "RIFF");
        number(out, 36 + frames * 2, 4);
        ascii(out, "WAVEfmt ");
        number(out, 16, 4);
        number(out, 1, 2);
        number(out, 1, 2);
        number(out, SAMPLE_RATE, 4);
        number(out, SAMPLE_RATE * 2, 4);
        number(out, 2, 2);
        number(out, 16, 2);
        ascii(out, "data");
        number(out, frames * 2, 4);
        for (int i = 0; i < frames; i++) {
            number(out, (int) (3000 * Math.sin(2 * Math.PI * 440 * i / SAMPLE_RATE)), 2);
        }
        return out.toByteArray();
    }

    private static void ascii(ByteArrayOutputStream out, String text) {
        for (int i = 0; i < text.length(); i++) {
            out.write(text.charAt(i));
        }
    }

    private static void number(ByteArrayOutputStream out, int value, int bytes) {
        for (int i = 0; i < bytes; i++) {
            out.write(value >> 8 * i);
        }
    }
}
