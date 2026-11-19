package com.dlsc.animatorfx.player.internal.audio;

import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.io.AnimationFile;
import javafx.animation.PauseTransition;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Sound through JavaFX media: one {@link MediaPlayer} per audio layer, created when the layer
 * first sounds, and one {@link AudioClip} per cue asset. An asset that cannot be played is
 * logged once and skipped.
 */
public final class MediaAudioOutput implements AudioOutput {

    /** A voice is not repositioned again before its last seek had time to take effect. */
    private static final long SEEK_SETTLE_NANOS = 250_000_000L;

    /** The media player of one audio layer. It becomes ready some time after it was created. */
    private final class Voice {

        final String assetId;
        final MediaPlayer player;
        final boolean loop;
        boolean ready;
        boolean started;
        boolean reposition;
        boolean broken;
        long lastSeek;
        /** What the voice should be doing; {@code null} for nothing. */
        VoiceTarget wanted;

        Voice(String assetId, MediaPlayer player, boolean loop) {
            this.assetId = assetId;
            this.player = player;
            this.loop = loop;
            if (loop) {
                player.setCycleCount(MediaPlayer.INDEFINITE);
            }
            player.setOnReady(() -> {
                ready = true;
                sync();
            });
            player.setOnPlaying(() -> played = true);
            player.setOnEndOfMedia(this::ended);
            player.setOnError(() -> {
                broken = true;
                fail(assetId, player.getError());
            });
        }

        /**
         * The player reports the end of the sound. That also happens right after a restart when
         * the voice had been paused on the very end of its sound: the player then stays silent
         * where the voice should be sounding, so it is positioned once more.
         */
        private void ended() {
            if (disposed || broken) {
                return;
            }
            if (loop) {
                // the player wrapped by itself: check its position on the next tick
                lastSeek = System.nanoTime() - SEEK_SETTLE_NANOS - 1;
                return;
            }
            VoiceTarget target = wanted;
            Double duration = durations.get(assetId);
            if (started && target != null
                    && (duration == null || target.mediaTime() < duration - 2 * AudioSchedule.MAX_DRIFT)) {
                seek(target, System.nanoTime());
                player.play();
            }
        }

        void want(VoiceTarget target, boolean fromItsMediaTime) {
            wanted = target;
            reposition |= fromItsMediaTime && target != null;
            sync();
        }

        private void sync() {
            if (!ready || broken || disposed) {
                return;
            }
            VoiceTarget target = wanted;
            if (target == null) {
                if (started) {
                    player.pause();
                    started = false;
                }
                reposition = false;
                return;
            }
            if (player.getVolume() != target.volume()) {
                player.setVolume(target.volume());
            }
            if (player.getBalance() != target.balance()) {
                player.setBalance(target.balance());
            }
            if (player.getRate() != target.rate()) {
                player.setRate(target.rate());
            }
            long now = System.nanoTime();
            if (!started || reposition) {
                seek(target, now);
                if (!started) {
                    player.play();
                    started = true;
                }
                reposition = false;
            } else if (now - lastSeek > SEEK_SETTLE_NANOS && drifted(target)) {
                seek(target, now);
            }
        }

        private void seek(VoiceTarget target, long now) {
            player.seek(Duration.seconds(target.mediaTime()));
            lastSeek = now;
            seeks++;
        }

        private boolean drifted(VoiceTarget target) {
            double actual = player.getCurrentTime().toSeconds();
            Double duration = durations.get(assetId);
            if (loop && duration != null) {
                // either side of the wrap is the same place in the sound
                double off = actual - target.mediaTime();
                actual = target.mediaTime() + off - duration * Math.rint(off / duration);
            }
            return AudioSchedule.needsSeek(actual, target.mediaTime());
        }
    }

    private final System.Logger logger;
    private final AudioFiles files;
    /** Whether a layer loops, by the id of every audio layer of the document. */
    private final Map<String, Boolean> layers = new HashMap<>();
    /** An empty entry is an asset or a layer that cannot be played. */
    private final Map<String, Optional<Media>> media = new HashMap<>();
    private final Map<String, Optional<Voice>> voices = new HashMap<>();
    private final Map<String, Optional<AudioClip>> clips = new HashMap<>();
    private final Set<MediaPlayer> oneShots = new HashSet<>();
    private final Map<String, Double> durations = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private PauseTransition snippetEnd;
    private boolean disposed;
    private boolean played;
    private int seeks;

    public MediaAudioOutput(AnimationFile file, System.Logger logger) {
        this.logger = logger;
        files = new AudioFiles(file, logger);
        collect(file.animation().layers());
    }

    private void collect(List<Layer> layers) {
        for (Layer layer : layers) {
            if (layer instanceof AudioLayer audio) {
                this.layers.put(audio.id(), audio.loop());
            } else if (layer instanceof GroupLayer group) {
                collect(group.children());
            }
        }
    }

    @Override
    public OptionalDouble duration(String assetId) {
        Double duration = durations.get(assetId);
        return duration == null ? OptionalDouble.empty() : OptionalDouble.of(duration);
    }

    @Override
    public void apply(List<VoiceTarget> targets) {
        if (disposed) {
            return;
        }
        cancelSnippet();
        match(targets, false);
    }

    @Override
    public void snippet(List<VoiceTarget> targets, double seconds) {
        if (disposed) {
            return;
        }
        cancelSnippet();
        match(targets, true);
        snippetEnd = new PauseTransition(Duration.seconds(seconds));
        snippetEnd.setOnFinished(event -> {
            snippetEnd = null;
            stopVoices();
        });
        snippetEnd.play();
    }

    private void match(List<VoiceTarget> targets, boolean fromTheirMediaTime) {
        Set<String> sounding = new HashSet<>();
        for (VoiceTarget target : targets) {
            if (target.audible()) {
                sounding.add(target.layerId());
                voices.computeIfAbsent(target.layerId(), id -> voice(target))
                        .ifPresent(voice -> voice.want(target, fromTheirMediaTime));
            }
        }
        voices.forEach((id, voice) -> {
            if (!sounding.contains(id)) {
                voice.ifPresent(v -> v.want(null, false));
            }
        });
    }

    @Override
    public void silence() {
        if (!disposed) {
            cancelSnippet();
            stopVoices();
        }
    }

    private void stopVoices() {
        voices.values().forEach(voice -> voice.ifPresent(v -> v.want(null, false)));
    }

    private void cancelSnippet() {
        if (snippetEnd != null) {
            snippetEnd.stop();
            snippetEnd = null;
        }
    }

    @Override
    public void playCue(String assetId, double volume) {
        if (disposed || !(volume > 0)) {
            return;
        }
        Optional<AudioClip> clip = clips.computeIfAbsent(assetId, this::clip);
        if (clip.isPresent()) {
            try {
                clip.get().play(volume);
                return;
            } catch (RuntimeException | LinkageError e) {
                clips.put(assetId, Optional.empty());
            }
        }
        playOnce(assetId, volume);
    }

    private Optional<AudioClip> clip(String assetId) {
        Optional<URI> uri = files.uri(assetId);
        if (uri.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AudioClip(uri.get().toString()));
        } catch (RuntimeException | LinkageError e) {
            // a media player gets its chance
            return Optional.empty();
        }
    }

    /** The cue through a media player of its own, for a sound an audio clip cannot play. */
    private void playOnce(String assetId, double volume) {
        Optional<Media> source = media(assetId);
        if (source.isEmpty()) {
            return;
        }
        try {
            MediaPlayer player = new MediaPlayer(source.get());
            oneShots.add(player);
            player.setVolume(volume);
            player.setOnEndOfMedia(() -> release(player));
            player.setOnError(() -> {
                fail(assetId, player.getError());
                release(player);
            });
            player.play();
        } catch (RuntimeException | LinkageError e) {
            fail(assetId, e);
        }
    }

    private void release(MediaPlayer player) {
        if (oneShots.remove(player)) {
            player.dispose();
        }
    }

    private Optional<Voice> voice(VoiceTarget target) {
        Optional<Media> source = media(target.assetId());
        if (source.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Voice(target.assetId(), new MediaPlayer(source.get()),
                    layers.getOrDefault(target.layerId(), false)));
        } catch (RuntimeException | LinkageError e) {
            fail(target.assetId(), e);
            return Optional.empty();
        }
    }

    private Optional<Media> media(String assetId) {
        Optional<Media> known = media.get(assetId);
        if (known == null) {
            known = load(assetId);
            media.put(assetId, known);
        }
        return known;
    }

    private Optional<Media> load(String assetId) {
        Optional<URI> uri = files.uri(assetId);
        if (uri.isEmpty()) {
            return Optional.empty();
        }
        try {
            Media source = new Media(uri.get().toString());
            source.setOnError(() -> fail(assetId, source.getError()));
            source.durationProperty().addListener(observable -> durationKnown(assetId, source.getDuration()));
            durationKnown(assetId, source.getDuration());
            return Optional.of(source);
        } catch (RuntimeException | LinkageError e) {
            fail(assetId, e);
            return Optional.empty();
        }
    }

    private void durationKnown(String assetId, Duration duration) {
        if (duration != null && !duration.isUnknown() && !duration.isIndefinite() && duration.toSeconds() > 0) {
            durations.put(assetId, duration.toSeconds());
        }
    }

    private void fail(String assetId, Throwable cause) {
        if (!disposed && failed.add(assetId)) {
            logger.log(System.Logger.Level.WARNING,
                    "Audio asset '" + assetId + "' cannot be played and stays silent", cause);
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        try {
            cancelSnippet();
        } catch (RuntimeException e) {
            // nothing left to cancel
        }
        disposed = true;
        voices.values().forEach(voice -> voice.ifPresent(v -> release(() -> v.player.dispose())));
        voices.clear();
        oneShots.forEach(player -> release(player::dispose));
        oneShots.clear();
        clips.values().forEach(clip -> clip.ifPresent(c -> release(c::stop)));
        clips.clear();
        media.clear();
        files.dispose();
    }

    private static void release(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            // what cannot be released is left to the garbage collector
        }
    }

    /** The number of media players created for audio layers so far. */
    int voiceCount() {
        return (int) voices.values().stream().filter(Optional::isPresent).count();
    }

    /** Whether a voice has reached the playing state of JavaFX media at least once. */
    boolean hasPlayed() {
        return played;
    }

    /** How often a voice was positioned so far, starts included. */
    int seekCount() {
        return seeks;
    }

    /** Where the voice of a layer is in its sound, in seconds; NaN without a voice. */
    double mediaTime(String layerId) {
        Optional<Voice> voice = voices.get(layerId);
        return voice == null || voice.isEmpty() ? Double.NaN : voice.get().player.getCurrentTime().toSeconds();
    }

    /** The asset ids that failed and were logged. */
    Set<String> failedAssets() {
        return Set.copyOf(failed);
    }
}
