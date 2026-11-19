package com.dlsc.animatorfx.player.internal.audio;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.player.Frame;
import com.dlsc.animatorfx.player.LayerState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Decides what should sound when. Pure functions of their arguments: no media, no toolkit.
 */
public final class AudioSchedule {

    /** A voice further than this from where it should be is repositioned (seconds). */
    static final double MAX_DRIFT = 0.08;

    /** The fastest rate JavaFX media plays at. */
    static final double MAX_RATE = 8;

    private AudioSchedule() {
    }

    /**
     * One target per audio layer of the document, in document order, nested layers included.
     *
     * @param frame         the document sampled at the current time
     * @param playing       whether the player is playing; nothing sounds otherwise
     * @param playbackRate  the rate of the player; only rates within {@code (0, 8]} sound
     * @param reversing     whether the player is on the backward leg of auto reverse
     * @param muted         the runtime mute switch
     * @param masterVolume  the runtime volume
     * @param clipDurations the length in seconds of the audio assets whose length is known, by
     *                      asset id; a clip of unknown length counts as endless
     */
    public static List<VoiceTarget> voices(Animation a, Frame frame, boolean playing, double playbackRate,
                                           boolean reversing, boolean muted, double masterVolume,
                                           Map<String, Double> clipDurations) {
        List<VoiceTarget> out = new ArrayList<>();
        boolean sounding = playing && !muted && !reversing && playbackRate > 0 && playbackRate <= MAX_RATE;
        collect(a.layers(), frame, sounding, playbackRate, limit(masterVolume, 0, 1), clipDurations, out);
        return out;
    }

    private static void collect(List<Layer> layers, Frame frame, boolean sounding, double rate, double master,
                                Map<String, Double> durations, List<VoiceTarget> out) {
        for (Layer layer : layers) {
            if (layer instanceof AudioLayer audio) {
                out.add(sounding ? voice(audio, frame, rate, master, durations)
                        : VoiceTarget.silent(audio.id(), audio.assetId()));
            } else if (layer instanceof GroupLayer group) {
                // the state of a layer is local: a group that is hidden or out of range silences its children
                LayerState state = frame.get(group.id());
                collect(group.children(), frame, sounding && state != null && state.visible(), rate, master,
                        durations, out);
            }
        }
    }

    private static VoiceTarget voice(AudioLayer layer, Frame frame, double rate, double master,
                                     Map<String, Double> durations) {
        LayerState state = frame.get(layer.id());
        double time = frame.time();
        // unlike a visual layer, sound does not hold on the last frame
        if (state == null || !state.visible() || layer.muted() || !(time < layer.base().outPoint())) {
            return VoiceTarget.silent(layer.id(), layer.assetId());
        }
        double mediaTime = Math.max(0, layer.clipOffset() + time - layer.base().inPoint());
        Double duration = durations.get(layer.assetId());
        if (duration != null && mediaTime >= duration) {
            if (!layer.loop() || !(duration > 0)) {
                return VoiceTarget.silent(layer.id(), layer.assetId());
            }
            mediaTime %= duration;
        }
        return new VoiceTarget(layer.id(), layer.assetId(), true, mediaTime, limit(state.volume(), 0, 1) * master,
                Double.isNaN(state.balance()) ? 0 : limit(state.balance(), -1, 1), rate);
    }

    /**
     * The cues playback crosses on the way from one time to a later one, in time order: those
     * after {@code from} up to and including {@code to}, and those exactly at {@code from} on
     * the first tick after playback started there. Muted cues are left out. Moving backwards
     * crosses nothing.
     */
    public static List<SoundCue> crossed(Animation a, double from, double to, boolean firstTickAfterPlay) {
        List<SoundCue> out = new ArrayList<>();
        if (!(to > from)) {
            return out;
        }
        for (SoundCue cue : a.cues()) {
            double time = cue.time();
            if (!cue.muted() && ((time > from && time <= to) || (time == from && firstTickAfterPlay))) {
                out.add(cue);
            }
        }
        out.sort(Comparator.comparingDouble(SoundCue::time));
        return out;
    }

    /**
     * Whether a voice is too far from where it should be, see {@link #MAX_DRIFT}.
     */
    public static boolean needsSeek(double actualMediaTime, double targetMediaTime) {
        return !(Math.abs(actualMediaTime - targetMediaTime) <= MAX_DRIFT);
    }

    /** Also maps NaN to the lower limit. */
    static double limit(double value, double min, double max) {
        return value >= max ? max : value > min ? value : min;
    }
}
