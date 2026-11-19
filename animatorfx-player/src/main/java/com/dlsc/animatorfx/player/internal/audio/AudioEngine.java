package com.dlsc.animatorfx.player.internal.audio;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.Frame;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;

/**
 * The sound of one document for one player: asks the {@link AudioSchedule} what should sound
 * and passes it to an {@link AudioOutput}, which it creates when sound is first needed. A
 * failing output never fails the caller: the failure is logged once and the document stays
 * silent from then on.
 */
public final class AudioEngine {

    /** How long a scrubbing snippet sounds, in seconds. */
    public static final double SNIPPET_SECONDS = 0.1;

    private final AnimationFile file;
    private final Function<AnimationFile, AudioOutput> factory;
    private final System.Logger logger;
    /** The assets of the audio layers: the ones whose length matters. */
    private final Set<String> clipAssets = new LinkedHashSet<>();
    private final Map<String, Double> durations = new HashMap<>();

    private AudioOutput output;
    /** Set once the output failed or the engine was disposed: silent for good. */
    private boolean closed;

    /**
     * @param factory creates the output; may return {@code null} for none
     */
    public AudioEngine(AnimationFile file, Function<AnimationFile, AudioOutput> factory, System.Logger logger) {
        this.file = file;
        this.factory = factory;
        this.logger = logger;
        collectAssets(file.animation().layers());
    }

    /**
     * Whether the document has anything to sound: an audio layer or a cue.
     */
    public static boolean hasAudio(Animation animation) {
        return !animation.cues().isEmpty() || hasAudioLayer(animation.layers());
    }

    private static boolean hasAudioLayer(List<Layer> layers) {
        for (Layer layer : layers) {
            if (layer instanceof AudioLayer || (layer instanceof GroupLayer group && hasAudioLayer(group.children()))) {
                return true;
            }
        }
        return false;
    }

    private void collectAssets(List<Layer> layers) {
        for (Layer layer : layers) {
            if (layer instanceof AudioLayer audio) {
                clipAssets.add(audio.assetId());
            } else if (layer instanceof GroupLayer group) {
                collectAssets(group.children());
            }
        }
    }

    /**
     * Makes the voices follow a playing player; call after every tick.
     */
    public void playing(Frame frame, double rate, boolean reversing, boolean muted, double volume) {
        run(() -> {
            if (frame != null && open()) {
                output.apply(AudioSchedule.voices(file.animation(), frame, true, rate, reversing, muted, volume,
                        clipDurations()));
            }
        });
    }

    /**
     * Plays the cues playback crossed.
     */
    public void cues(List<SoundCue> crossed, boolean muted, double volume) {
        if (muted || crossed.isEmpty()) {
            return;
        }
        run(() -> {
            if (open()) {
                double master = AudioSchedule.limit(volume, 0, 1);
                for (SoundCue cue : crossed) {
                    output.playCue(cue.assetId(), AudioSchedule.limit(cue.volume(), 0, 1) * master);
                }
            }
        });
    }

    /**
     * Plays a short snippet of what sounds at the time of the frame.
     */
    public void scrub(Frame frame, double volume) {
        run(() -> {
            if (frame != null && open()) {
                output.snippet(AudioSchedule.voices(file.animation(), frame, true, 1, false, false, volume,
                        clipDurations()), SNIPPET_SECONDS);
            }
        });
    }

    /**
     * Stops the voices; does nothing as long as nothing has sounded yet.
     */
    public void silence() {
        run(() -> {
            if (output != null) {
                output.silence();
            }
        });
    }

    /**
     * Cuts the cues that are sounding.
     */
    public void stopCues() {
        run(() -> {
            if (output != null) {
                output.stopCues();
            }
        });
    }

    /**
     * Releases the output. The engine is silent afterwards.
     */
    public void dispose() {
        closed = true;
        AudioOutput old = output;
        output = null;
        if (old != null) {
            try {
                old.dispose();
            } catch (RuntimeException | LinkageError e) {
                logger.log(System.Logger.Level.WARNING, "Audio output could not be released", e);
            }
        }
    }

    private boolean open() {
        if (output == null && !closed) {
            output = factory.apply(file);
            closed = output == null;
        }
        return output != null;
    }

    private Map<String, Double> clipDurations() {
        if (durations.size() < clipAssets.size()) {
            for (String asset : clipAssets) {
                if (!durations.containsKey(asset)) {
                    OptionalDouble duration = output.duration(asset);
                    if (duration.isPresent()) {
                        durations.put(asset, duration.getAsDouble());
                    }
                }
            }
        }
        return durations;
    }

    private void run(Runnable action) {
        if (closed) {
            return;
        }
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            logger.log(System.Logger.Level.WARNING, "Audio is not available; the animation plays silently", e);
            dispose();
        }
    }
}
