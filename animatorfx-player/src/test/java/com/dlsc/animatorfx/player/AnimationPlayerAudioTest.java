package com.dlsc.animatorfx.player;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.AnimationPlayer.Status;
import com.dlsc.animatorfx.player.internal.audio.RecordingAudioOutput;
import com.dlsc.animatorfx.player.internal.audio.VoiceTarget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static com.dlsc.animatorfx.player.TestDocs.base;
import static com.dlsc.animatorfx.player.TestDocs.doc;
import static com.dlsc.animatorfx.player.TestDocs.rect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sound during playback, with a manual clock and an output that only records; needs no toolkit. */
class AnimationPlayerAudioTest {

    private static final double EPS = 1e-9;

    private static final class RecordingLogger implements System.Logger {

        final List<String> warnings = new ArrayList<>();

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
            record(level, message);
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            record(level, format);
        }

        private void record(Level level, String message) {
            if (level == Level.WARNING) {
                warnings.add(message);
            }
        }
    }

    private final ManualClock clock = new ManualClock();
    private final RecordingLogger logger = new RecordingLogger();
    private final RecordingAudioOutput output = new RecordingAudioOutput();
    private final AnimationPlayer player = new AnimationPlayer(clock, logger, output);

    AnimationPlayerAudioTest() {
        player.setAnimation(sounding());
    }

    /**
     * Two seconds; the layer "music" sounds from 0.5 to 1.5 starting 0.2 s into its clip; a cue
     * at 0 and one at 1 with volume 0.5; the marker "a" spans 0.5 to 1.5.
     */
    private static AnimationFile sounding() {
        AudioLayer music = new AudioLayer(base("music").withInPoint(0.5).withOutPoint(1.5), "snd", 0.2,
                Animatable.of(1.0), Animatable.of(0.0), false, false);
        Animation animation = doc(music)
                .withAssets(List.of(new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "snd.wav"),
                        new Asset("click", Asset.Kind.AUDIO, "assets/click.wav", "click.wav")))
                .withMarkers(List.of(new Marker("a", 0.5, 1.5)))
                .withCues(List.of(new SoundCue("c0", "", 0, "click", 1, false),
                        new SoundCue("c1", "", 1, "snd", 0.5, false)));
        return new AnimationFile(animation, Map.of("snd", new byte[0], "click", new byte[0]));
    }

    private static AnimationFile silent() {
        return new AnimationFile(doc(rect("r", 10, 10)), Map.of());
    }

    private VoiceTarget voice() {
        assertEquals(1, output.last().size());
        return output.last().get(0);
    }

    @Test
    void mutedAndVolumeHaveDefaults() {
        assertFalse(player.isMuted());
        assertEquals(1, player.getVolume(), EPS);
        assertEquals(List.of(), output.calls, "nothing sounds before playback");
    }

    @Test
    void playingAppliesTheTargetsOnEveryTick() {
        player.play();
        assertEquals(0, output.applied.size());

        clock.tick(0.25);
        assertFalse(voice().audible());
        clock.tick(0.25);
        clock.tick(0.25);

        assertEquals(3, output.applied.size());
        assertTrue(voice().audible());
        assertEquals("music", voice().layerId());
        assertEquals(0.45, voice().mediaTime(), EPS);
        assertEquals(1, voice().rate(), EPS);
    }

    @Test
    void pauseSilences() {
        player.play();
        clock.tick(0.6);
        player.pause();

        assertEquals("silence", output.calls.get(output.calls.size() - 1));
    }

    @Test
    void stopSilencesAndCutsTheCues() {
        player.play();
        clock.tick(0.6);
        player.stop();

        assertEquals(1, output.count("silence"));
        assertEquals(1, output.count("stopCues"));
    }

    @Test
    void mutingCutsTheCuesAtOnce() {
        player.play();
        clock.tick(0.6);
        player.setMuted(true);
        assertEquals(1, output.count("stopCues"));

        player.setMuted(false);
        assertEquals(1, output.count("stopCues"));
    }

    @Test
    void pauseFinishAndSuspensionLetCuesRingOut() {
        player.play();
        clock.tick(0.6);
        player.pause();
        player.play();
        player.setSuspended(true);
        player.setSuspended(false);
        clock.tick(5);

        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(3, output.count("silence"));
        assertEquals(0, output.count("stopCues"));
    }

    @Test
    void aCueAtTheRangeStartFiresOnEveryForwardLegOfAutoReverse() {
        player.setAutoReverse(true);
        player.setCycleCount(4);
        player.play();
        for (int i = 0; i < 8; i++) {
            clock.tick(1);
        }

        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(List.of("cue:click:1.0", "cue:snd:0.5", "cue:click:1.0", "cue:snd:0.5"), output.cues());
    }

    @Test
    void aCueAtAMarkerStartFiresOnEveryForwardLegOfAutoReverse() {
        player.setAnimation(new AnimationFile(sounding().animation().withCues(List.of(
                new SoundCue("c", "", 0.5, "click", 1, false))), Map.of()));
        player.setAutoReverse(true);
        player.setCycleCount(AnimationPlayer.INDEFINITE);
        player.playMarker("a");
        for (int i = 0; i < 12; i++) {
            clock.tick(0.5);
        }

        assertEquals(List.of("cue:click:1.0", "cue:click:1.0", "cue:click:1.0"), output.cues());
    }

    @Test
    void aTickThatSpansSeveralCyclesFiresEachCueOnce() {
        player.setCycleCount(3);
        player.play();
        clock.tick(5.5);

        assertEquals(1.5, player.getCurrentTime(), EPS);
        assertEquals(List.of("cue:click:1.0", "cue:snd:0.5"), output.cues());

        clock.tick(5);
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(List.of("cue:click:1.0", "cue:snd:0.5"), output.cues());
    }

    @Test
    void aCueExactlyAtTheSeekTargetFiresOnTheNextTickWhilePlaying() {
        player.play();
        clock.tick(0.1);
        player.seek(1);
        assertEquals(List.of("cue:click:1.0"), output.cues());

        clock.tick(0.1);
        assertEquals(List.of("cue:click:1.0", "cue:snd:0.5"), output.cues());
    }

    @Test
    void disposeWhilePlayingStaysSilentUntilPlayIsCalledAgain() {
        AnimationPlayer counted = new AnimationPlayer(clock, logger);
        List<RecordingAudioOutput> created = new ArrayList<>();
        counted.setAudioOutputFactory(file -> {
            created.add(new RecordingAudioOutput());
            return created.get(created.size() - 1);
        });
        counted.setAnimation(sounding());
        counted.setCycleCount(AnimationPlayer.INDEFINITE);
        counted.play();
        clock.tick(0.1);
        assertEquals(1, created.size());

        counted.dispose();
        clock.tick(0.1);
        clock.tick(0.1);
        counted.scrub(0.8);
        clock.tick(0.1);
        assertEquals(Status.PLAYING, counted.getStatus());
        assertEquals(1, created.size());
        assertEquals("dispose", created.get(0).calls.get(created.get(0).calls.size() - 1));

        counted.play();
        clock.tick(0.1);
        assertEquals(2, created.size());
        assertEquals(1, created.get(1).applied.size());
    }

    @Test
    void aNewAnimationSoundsAgainAfterDispose() {
        player.play();
        clock.tick(0.1);
        player.dispose();
        player.setAnimation(sounding());
        clock.tick(0.1);

        assertEquals(1, output.count("dispose"));
        assertEquals(2, output.applied.size());
    }

    @Test
    void installingAnOutputFactoryDoesNotSwitchSoundOff() {
        player.setAudioOutputFactory(file -> output);
        player.scrub(0.8);

        assertEquals(List.of("snippet"), output.calls);
    }

    @Test
    void finishingSilencesAndAppliesNothingAfterwards() {
        player.play();
        clock.tick(0.6);
        clock.tick(5);

        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals("silence", output.calls.get(output.calls.size() - 1));
        assertEquals(1, output.applied.size());
    }

    @Test
    void suspendingSilences() {
        player.play();
        clock.tick(0.6);
        player.setSuspended(true);

        assertEquals("silence", output.calls.get(output.calls.size() - 1));
    }

    @Test
    void aSeekWhilePlayingRepositionsWithoutFiringTheCuesItJumpedOver() {
        player.play();
        clock.tick(0.6);
        player.seek(1.2);
        clock.tick(0.1);

        assertEquals(1.0, voice().mediaTime(), EPS);
        assertEquals(List.of("cue:click:1.0"), output.cues());
    }

    @Test
    void aCycleWrapRepositionsAndFiresTheCuesAgain() {
        player.setCycleCount(2);
        player.play();
        clock.tick(1.4);
        assertEquals(1.1, voice().mediaTime(), EPS);
        assertEquals(List.of("cue:click:1.0", "cue:snd:0.5"), output.cues());

        clock.tick(0.8);
        assertEquals(0.2, player.getCurrentTime(), EPS);
        assertFalse(voice().audible());
        assertEquals(List.of("cue:click:1.0", "cue:snd:0.5", "cue:click:1.0"), output.cues());

        clock.tick(0.5);
        assertEquals(0.4, voice().mediaTime(), EPS);
        clock.tick(0.5);
        assertEquals(List.of("cue:click:1.0", "cue:snd:0.5", "cue:click:1.0", "cue:snd:0.5"), output.cues());
    }

    @Test
    void markerPlaybackSoundsWithinItsRange() {
        player.setCycleCount(2);
        player.playMarker("a");
        clock.tick(0.25);
        assertEquals(0.45, voice().mediaTime(), EPS);

        clock.tick(0.5);
        assertEquals(List.of("cue:snd:0.5"), output.cues());

        clock.tick(0.5);
        assertEquals(0.75, player.getCurrentTime(), EPS);
        assertEquals(0.45, voice().mediaTime(), EPS);
        clock.tick(0.5);
        assertEquals(List.of("cue:snd:0.5", "cue:snd:0.5"), output.cues());
    }

    @Test
    void theReverseLegOfAutoReverseIsSilent() {
        player.setAutoReverse(true);
        player.setCycleCount(2);
        player.play();
        clock.tick(1.2);
        assertTrue(voice().audible());

        clock.tick(1.6);
        assertEquals(1.2, player.getCurrentTime(), EPS);
        assertFalse(voice().audible());
        clock.tick(0.5);

        assertEquals(List.of("cue:click:1.0", "cue:snd:0.5"), output.cues());
    }

    @Test
    void aNegativeRateIsSilent() {
        player.seek(1.4);
        player.setRate(-1);
        player.play();
        clock.tick(0.6);

        assertEquals(0.8, player.getCurrentTime(), EPS);
        assertFalse(voice().audible());
        assertEquals(List.of(), output.cues());
    }

    @Test
    void aRateChangeTakesEffectOnTheNextTick() {
        player.play();
        clock.tick(0.6);
        player.setRate(2);
        clock.tick(0.1);

        assertEquals(2, voice().rate(), EPS);
        assertEquals(0.5, voice().mediaTime(), EPS);

        player.setRate(0);
        clock.tick(0.1);
        assertFalse(voice().audible());
    }

    @Test
    void resumingDoesNotFireTheCueItPausedOnAgain() {
        player.play();
        clock.tick(1);
        player.pause();
        player.play();
        clock.tick(0.1);

        assertEquals(List.of("cue:click:1.0", "cue:snd:0.5"), output.cues());
    }

    @Test
    void theRuntimeVolumeScalesLayersAndCues() {
        player.setVolume(0.5);
        player.play();
        clock.tick(1);

        assertEquals(0.5, voice().volume(), EPS);
        assertEquals(List.of("cue:click:0.5", "cue:snd:0.25"), output.cues());
    }

    @Test
    void theRuntimeVolumeIsLimitedWhenApplied() {
        player.setVolume(5);
        player.play();
        clock.tick(0.6);
        assertEquals(1, voice().volume(), EPS);

        player.setVolume(Double.NaN);
        clock.tick(0.1);
        assertEquals(1, voice().volume(), EPS);

        player.setVolume(-3);
        clock.tick(0.1);
        assertEquals(0, voice().volume(), EPS);
    }

    @Test
    void mutingSilencesLayersAndSkipsCuesFromTheNextTick() {
        player.play();
        clock.tick(0.6);
        assertTrue(voice().audible());

        player.setMuted(true);
        clock.tick(0.6);
        assertFalse(voice().audible());
        assertEquals(List.of("cue:click:1.0"), output.cues());
    }

    @Test
    void scrubbingWhileNotPlayingPlaysAShortSnippet() {
        player.scrub(0.8);

        assertEquals(0.8, player.getCurrentTime(), EPS);
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(List.of("snippet"), output.calls);
        assertEquals(0.1, output.snippetSeconds.get(0), EPS);
        VoiceTarget voice = output.snippets.get(0).get(0);
        assertTrue(voice.audible());
        assertEquals(0.5, voice.mediaTime(), EPS);
        assertEquals(1, voice.rate(), EPS);

        player.scrub(99);
        assertEquals(2, player.getCurrentTime(), EPS);
    }

    @Test
    void scrubbingWhileNotPlayingFiresNoCues() {
        player.scrub(0.2);
        player.scrub(1.2);

        assertEquals(List.of(), output.cues());
    }

    @Test
    void scrubbingWhilePlayingIsASeek() {
        player.play();
        clock.tick(0.6);
        player.scrub(1.2);
        clock.tick(0.1);

        assertEquals(0, output.snippets.size());
        assertEquals(1.0, voice().mediaTime(), EPS);
    }

    @Test
    void scrubbingWhileMutedIsSilent() {
        player.setMuted(true);
        player.scrub(0.8);

        assertEquals(0.8, player.getCurrentTime(), EPS);
        assertEquals(List.of(), output.calls);
    }

    @Test
    void replacingTheAnimationDisposesTheOutput() {
        player.play();
        clock.tick(0.6);
        player.setAnimation(sounding());
        assertEquals(1, output.count("dispose"));

        clock.tick(0.1);
        player.setAnimation(null);
        assertEquals(2, output.count("dispose"));
    }

    @Test
    void disposeReleasesTheOutputOnce() {
        player.play();
        clock.tick(0.6);
        player.dispose();
        player.dispose();

        assertEquals(1, output.count("dispose"));
        assertEquals("dispose", output.calls.get(output.calls.size() - 1));
    }

    @Test
    void theOutputIsCreatedWhenSoundIsFirstNeeded() {
        AnimationPlayer lazy = new AnimationPlayer(clock, logger);
        List<AnimationFile> created = new ArrayList<>();
        lazy.setAudioOutputFactory(file -> {
            created.add(file);
            return output;
        });
        AnimationFile file = sounding();
        lazy.setAnimation(file);
        lazy.seek(0.5);
        lazy.play();
        assertEquals(List.of(), created);

        clock.tick(0.1);
        clock.tick(0.1);
        assertEquals(List.of(file), created);
        assertEquals(2, output.applied.size());
    }

    @Test
    void aDocumentWithoutAudioNeverAsksForAnOutput() {
        AnimationPlayer quiet = new AnimationPlayer(clock, logger);
        quiet.setAudioOutputFactory(file -> {
            throw new AssertionError("no output is needed");
        });
        quiet.setAnimation(silent());
        quiet.play();
        clock.tick(0.5);
        quiet.scrub(1);
        quiet.pause();
        quiet.play();
        clock.tick(5);
        quiet.dispose();

        assertEquals(Status.STOPPED, quiet.getStatus());
        assertEquals(List.of(), logger.warnings);
    }

    @Test
    void aFailingOutputIsLoggedOnceAndTheAnimationPlaysOnSilently() {
        output.failing = true;
        player.play();
        clock.tick(0.6);
        clock.tick(0.6);
        player.pause();
        player.scrub(0.8);
        player.play();
        clock.tick(5);

        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(2, player.getCurrentTime(), EPS);
        assertEquals(1, logger.warnings.size(), logger.warnings.toString());
        assertEquals(1, output.count("dispose"));
    }

    @Test
    void aFactoryThatFailsOrGivesNothingMeansSilence() {
        AnimationPlayer broken = new AnimationPlayer(clock, logger);
        broken.setAudioOutputFactory(file -> {
            throw new UnsupportedOperationException("no media");
        });
        broken.setAnimation(sounding());
        broken.play();
        clock.tick(0.6);
        clock.tick(0.6);
        assertEquals(1.2, broken.getCurrentTime(), EPS);
        assertEquals(1, logger.warnings.size());

        AnimationPlayer none = new AnimationPlayer(clock, logger);
        none.setAudioOutputFactory(file -> null);
        none.setAnimation(sounding());
        none.play();
        clock.tick(0.6);
        assertEquals(0.6, none.getCurrentTime(), EPS);
    }
}
