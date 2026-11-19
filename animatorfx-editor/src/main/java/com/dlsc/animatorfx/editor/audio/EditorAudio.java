package com.dlsc.animatorfx.editor.audio;

import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.AnimationPlayer;
import com.dlsc.animatorfx.player.internal.audio.AudioOutput;
import com.dlsc.animatorfx.player.internal.audio.MediaAudioOutput;
import com.dlsc.animatorfx.player.internal.audio.VoiceTarget;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * The one place where the editor makes sound: every player that plays a document and every
 * preview gets its audio output from here.
 *
 * <p>The output comes from a factory that can be replaced ({@link #setOutputFactory}): tests
 * install a recording one. With the system property {@code animatorfx.audio=off} the editor has
 * no sound at all unless a factory is installed; the tests of the editor run that way.
 */
public final class EditorAudio {

    /** The system property that switches the sound of the editor off with the value {@code off}. */
    public static final String PROPERTY = "animatorfx.audio";

    private static final System.Logger LOG = System.getLogger(EditorAudio.class.getName());
    private static Function<AnimationFile, AudioOutput> outputFactory;

    private EditorAudio() {
    }

    /**
     * Replaces what makes the sound of the editor audible; {@code null} restores JavaFX media
     * (or silence, with {@code animatorfx.audio=off}). The factory may return {@code null} for
     * silence. Players that are already making sound keep their output until their document changes.
     */
    public static void setOutputFactory(Function<AnimationFile, AudioOutput> factory) {
        outputFactory = factory;
    }

    /** A new output for a document, or {@code null} for silence. */
    public static AudioOutput createOutput(AnimationFile file) {
        if (outputFactory != null) {
            return outputFactory.apply(file);
        }
        if ("off".equalsIgnoreCase(System.getProperty(PROPERTY))) {
            return null;
        }
        return new MediaAudioOutput(file, LOG);
    }

    /**
     * Makes a player that plays documents in the editor take its sound from here.
     *
     * <p>A player disposes its output whenever its animation is replaced, which in the editor
     * happens with every edit. So that an edit during playback does not stop and restart every
     * sound, the output is kept across edits that leave the sounds and their layers as they
     * are, and only then really released when it is no longer wanted.
     *
     * @param playing whether the player is playing right now
     * @return releases the kept output; to be called when the player is no longer used
     */
    public static Runnable install(AnimationPlayer player, BooleanSupplier playing) {
        Shared shared = new Shared(playing);
        player.setAudioOutputFactory(shared::acquire);
        return shared::close;
    }

    /** What decides whether an output made for one document can go on for another. */
    private record Signature(Map<String, byte[]> sounds, Map<String, String> layers) {

        static Signature of(AnimationFile file) {
            Map<String, byte[]> sounds = new HashMap<>();
            file.animation().assets().forEach(asset -> {
                if (asset.kind() == com.dlsc.animatorfx.model.Asset.Kind.AUDIO) {
                    sounds.put(asset.id(), file.assets().get(asset.id()));
                }
            });
            Map<String, String> layers = new HashMap<>();
            collect(file.animation().layers(), layers);
            return new Signature(sounds, layers);
        }

        private static void collect(List<Layer> list, Map<String, String> out) {
            for (Layer layer : list) {
                if (layer instanceof AudioLayer audio) {
                    out.put(audio.id(), audio.assetId() + (audio.loop() ? "\u0000loop" : ""));
                } else if (layer instanceof GroupLayer group) {
                    collect(group.children(), out);
                }
            }
        }

        boolean sameAs(Signature other) {
            if (!layers.equals(other.layers) || !sounds.keySet().equals(other.sounds.keySet())) {
                return false;
            }
            // the same content, not equal content: comparing megabytes on every edit is too slow
            return other.sounds.entrySet().stream().allMatch(e -> sounds.get(e.getKey()) == e.getValue());
        }
    }

    private static final class Shared {

        /** How long an output that nobody holds may go on sounding while the player plays. */
        private static final Duration GRACE = Duration.millis(200);

        private final BooleanSupplier playing;
        private final PauseTransition orphaned = new PauseTransition(GRACE);
        private AudioOutput real;
        private Signature signature;
        private Lease lease;

        Shared(BooleanSupplier playing) {
            this.playing = playing;
            orphaned.setOnFinished(e -> {
                if (lease == null && real != null) {
                    real.silence();
                }
            });
        }

        AudioOutput acquire(AnimationFile file) {
            orphaned.stop();
            Signature wanted = Signature.of(file);
            if (real == null || !wanted.sameAs(signature)) {
                close();
                real = createOutput(file);
                signature = wanted;
            }
            lease = real == null ? null : new Lease();
            return lease;
        }

        void close() {
            orphaned.stop();
            lease = null;
            if (real != null) {
                try {
                    real.dispose();
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING, "Audio output could not be released", e);
                }
                real = null;
                signature = null;
            }
        }

        /** What a player holds for one animation: the kept output, which outlives it. */
        private final class Lease implements AudioOutput {

            private boolean current() {
                return lease == this && real != null;
            }

            @Override
            public OptionalDouble duration(String assetId) {
                return current() ? real.duration(assetId) : OptionalDouble.empty();
            }

            @Override
            public void apply(List<VoiceTarget> targets) {
                if (current()) {
                    real.apply(targets);
                }
            }

            @Override
            public void playCue(String assetId, double volume) {
                if (current()) {
                    real.playCue(assetId, volume);
                }
            }

            @Override
            public void snippet(List<VoiceTarget> targets, double seconds) {
                if (current()) {
                    real.snippet(targets, seconds);
                }
            }

            @Override
            public void silence() {
                if (current()) {
                    real.silence();
                }
            }

            @Override
            public void dispose() {
                if (!current()) {
                    return;
                }
                lease = null;
                if (playing.getAsBoolean()) {
                    // the next tick takes the output over; if it does not, the sound stops
                    orphaned.playFromStart();
                } else {
                    real.silence();
                }
            }
        }
    }
}
