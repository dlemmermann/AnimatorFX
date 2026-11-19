package com.dlsc.animatorfx.player.internal.audio;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.player.Evaluator;
import com.dlsc.animatorfx.player.TestDocs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.dlsc.animatorfx.player.TestDocs.base;
import static com.dlsc.animatorfx.player.TestDocs.doc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What should sound when; pure, needs no toolkit. */
class AudioScheduleTest {

    private static final double EPS = 1e-9;

    /** Sounds from 0.5 to 1.5, starting 0.2 s into the clip. */
    private static AudioLayer music() {
        return new AudioLayer(base("music").withInPoint(0.5).withOutPoint(1.5), "snd", 0.2, Animatable.of(1.0),
                Animatable.of(0.0), false, false);
    }

    private static VoiceTarget voice(Animation animation, double time, double rate, boolean reversing, boolean muted,
                                     double master, Map<String, Double> durations) {
        List<VoiceTarget> voices = AudioSchedule.voices(animation, Evaluator.sample(animation, time), true, rate,
                reversing, muted, master, durations);
        assertEquals(1, voices.size());
        assertEquals("music", voices.get(0).layerId());
        assertEquals("snd", voices.get(0).assetId());
        return voices.get(0);
    }

    private static VoiceTarget voice(Layer layer, double time) {
        return voice(doc(layer), time, 1, false, false, 1, Map.of());
    }

    @Test
    void mediaTimeFollowsTheInPointAndTheClipOffset() {
        VoiceTarget voice = voice(music(), 0.8);

        assertTrue(voice.audible());
        assertEquals(0.5, voice.mediaTime(), EPS);
        assertEquals(1, voice.volume(), EPS);
        assertEquals(0, voice.balance(), EPS);
        assertEquals(1, voice.rate(), EPS);
        assertEquals(0.2, voice(music(), 0.5).mediaTime(), EPS);
    }

    @Test
    void silentOutsideTheInOutRange() {
        assertFalse(voice(music(), 0.4).audible());
        assertFalse(voice(music(), 1.5).audible());
        assertTrue(voice(music(), 1.4999).audible());
    }

    @Test
    void aLayerThatLastsToTheEndIsSilentOnTheLastFrame() {
        AudioLayer toTheEnd = music().withBase(music().base().withOutPoint(TestDocs.DURATION));

        assertTrue(voice(toTheEnd, 1.99).audible());
        assertFalse(voice(toTheEnd, TestDocs.DURATION).audible());
    }

    @Test
    void pastTheClipEndIsSilentWithoutLoopAndWrappedWithLoop() {
        Map<String, Double> durations = Map.of("snd", 0.4);

        assertTrue(voice(doc(music()), 0.6, 1, false, false, 1, durations).audible());
        assertFalse(voice(doc(music()), 0.8, 1, false, false, 1, durations).audible());

        VoiceTarget looped = voice(doc(music().withLoop(true)), 0.8, 1, false, false, 1, durations);
        assertTrue(looped.audible());
        assertEquals(0.1, looped.mediaTime(), EPS);
    }

    @Test
    void anUnknownClipDurationCountsAsEndless() {
        VoiceTarget looped = voice(music().withLoop(true), 1.4);

        assertTrue(looped.audible());
        assertEquals(1.1, looped.mediaTime(), EPS);
        assertTrue(voice(music(), 1.4).audible());
    }

    @Test
    void mutedHiddenOrNotPlayingIsSilent() {
        assertFalse(voice(music().withMuted(true), 0.8).audible());
        assertFalse(voice(music().withBase(music().base().withVisible(false)), 0.8).audible());
        assertFalse(voice(doc(music()), 0.8, 1, false, true, 1, Map.of()).audible());

        Animation animation = doc(music());
        List<VoiceTarget> paused = AudioSchedule.voices(animation, Evaluator.sample(animation, 0.8), false, 1, false,
                false, 1, Map.of());
        assertEquals(1, paused.size());
        assertFalse(paused.get(0).audible());
        assertEquals("music", paused.get(0).layerId());
    }

    @Test
    void volumeIsTheLayerVolumeTimesTheMasterVolume() {
        AudioLayer fading = music().withVolume(TestDocs.keys(0, 0.0, 2, 1.0)).withBalance(Animatable.of(-0.5));

        VoiceTarget voice = voice(doc(fading), 1, 1, false, false, 0.5, Map.of());
        assertEquals(0.25, voice.volume(), EPS);
        assertEquals(-0.5, voice.balance(), EPS);

        assertEquals(1, voice(doc(music()), 1, 1, false, false, 3, Map.of()).volume(), EPS);
        assertEquals(0, voice(doc(music()), 1, 1, false, false, -1, Map.of()).volume(), EPS);
        assertEquals(1, voice(doc(music().withVolume(Animatable.of(4.0))), 1, 1, false, false, 1, Map.of()).volume(),
                EPS);
        assertEquals(1, voice(music().withBalance(Animatable.of(7.0)), 1).balance(), EPS);
    }

    @Test
    void theRateIsPassedOnWithinTheLimitsOfJavaFxMedia() {
        Animation animation = doc(music());

        assertEquals(2, voice(animation, 0.8, 2, false, false, 1, Map.of()).rate(), EPS);
        assertEquals(0.25, voice(animation, 0.8, 0.25, false, false, 1, Map.of()).rate(), EPS);
        assertTrue(voice(animation, 0.8, 8, false, false, 1, Map.of()).audible());
        assertFalse(voice(animation, 0.8, 8.5, false, false, 1, Map.of()).audible());
        assertFalse(voice(animation, 0.8, 0, false, false, 1, Map.of()).audible());
        assertFalse(voice(animation, 0.8, -1, false, false, 1, Map.of()).audible());
        assertFalse(voice(animation, 0.8, Double.NaN, false, false, 1, Map.of()).audible());
        assertFalse(voice(animation, 0.8, 1, true, false, 1, Map.of()).audible());
    }

    @Test
    void aNestedLayerSoundsOnlyWhileItsGroupsAreVisibleAndInRange() {
        AudioLayer inside = music().withBase(music().base().withInPoint(0).withOutPoint(2));
        GroupLayer late = new GroupLayer(base("g").withInPoint(1), List.of(inside));
        Animation animation = doc(new GroupLayer(base("outer"), List.of(late)));

        assertFalse(voice(animation, 0.5, 1, false, false, 1, Map.of()).audible());
        assertTrue(voice(animation, 1.2, 1, false, false, 1, Map.of()).audible());

        Animation hidden = doc(new GroupLayer(base("outer").withVisible(false), List.of(late)));
        assertFalse(voice(hidden, 1.2, 1, false, false, 1, Map.of()).audible());
    }

    @Test
    void aVoiceIsRepositionedWhenItDriftsMoreThan80Milliseconds() {
        assertFalse(AudioSchedule.needsSeek(1.07, 1));
        assertFalse(AudioSchedule.needsSeek(0.93, 1));
        assertTrue(AudioSchedule.needsSeek(1.09, 1));
        assertTrue(AudioSchedule.needsSeek(0.91, 1));
        assertTrue(AudioSchedule.needsSeek(Double.NaN, 1));
    }

    private static Animation withCues() {
        return doc().withCues(List.of(
                new SoundCue("late", "late", 1, "snd", 1, false),
                new SoundCue("start", "start", 0, "snd", 1, false),
                new SoundCue("early", "early", 0.5, "snd", 0.5, false),
                new SoundCue("off", "off", 0.5, "snd", 1, true)));
    }

    private static List<String> crossed(double from, double to, boolean first) {
        return AudioSchedule.crossed(withCues(), from, to, first).stream().map(SoundCue::id).toList();
    }

    @Test
    void cuesFireOnceWhenTheirTimeIsCrossedForward() {
        assertEquals(List.of(), crossed(0, 0.4, false));
        assertEquals(List.of("early"), crossed(0.4, 0.5, false));
        assertEquals(List.of(), crossed(0.5, 0.6, false));
        assertEquals(List.of("early", "late"), crossed(0.1, 1.5, false));
    }

    @Test
    void aCueExactlyAtTheStartFiresOnTheFirstTickOnly() {
        assertEquals(List.of("start"), crossed(0, 0.1, true));
        assertEquals(List.of(), crossed(0, 0.1, false));
        assertEquals(List.of("early", "late"), crossed(0.5, 1, true));
    }

    @Test
    void noCueFiresOnBackwardMovement() {
        assertEquals(List.of(), crossed(1.5, 0, false));
        assertEquals(List.of(), crossed(1.5, 0, true));
        assertEquals(List.of(), crossed(0.5, 0.5, false));
    }
}
