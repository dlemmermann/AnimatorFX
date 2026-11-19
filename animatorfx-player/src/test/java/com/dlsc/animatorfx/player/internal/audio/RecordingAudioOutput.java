package com.dlsc.animatorfx.player.internal.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** An audio output that makes no sound and remembers what it was asked to do. */
public final class RecordingAudioOutput implements AudioOutput {

    /** Every call in order: {@code apply}, {@code cue:<asset>:<volume>}, {@code snippet}, {@code silence}, {@code stopCues}, {@code dispose}. */
    public final List<String> calls = new ArrayList<>();
    public final List<List<VoiceTarget>> applied = new ArrayList<>();
    public final List<List<VoiceTarget>> snippets = new ArrayList<>();
    public final List<Double> snippetSeconds = new ArrayList<>();
    public final Map<String, Double> durations = new HashMap<>();
    /** Makes every call but {@code dispose} throw. */
    public boolean failing;

    private void call(String name) {
        calls.add(name);
        if (failing) {
            throw new IllegalStateException("no audio device");
        }
    }

    @Override
    public OptionalDouble duration(String assetId) {
        if (failing) {
            throw new IllegalStateException("no audio device");
        }
        Double duration = durations.get(assetId);
        return duration == null ? OptionalDouble.empty() : OptionalDouble.of(duration);
    }

    @Override
    public void apply(List<VoiceTarget> targets) {
        applied.add(List.copyOf(targets));
        call("apply");
    }

    @Override
    public void playCue(String assetId, double volume) {
        call("cue:" + assetId + ":" + volume);
    }

    @Override
    public void snippet(List<VoiceTarget> targets, double seconds) {
        snippets.add(List.copyOf(targets));
        snippetSeconds.add(seconds);
        call("snippet");
    }

    @Override
    public void silence() {
        call("silence");
    }

    @Override
    public void stopCues() {
        call("stopCues");
    }

    @Override
    public void dispose() {
        calls.add("dispose");
    }

    /** The targets of the latest {@code apply}. */
    public List<VoiceTarget> last() {
        return applied.get(applied.size() - 1);
    }

    public long count(String call) {
        return calls.stream().filter(call::equals).count();
    }

    /** The cue calls so far, in order. */
    public List<String> cues() {
        return calls.stream().filter(call -> call.startsWith("cue:")).toList();
    }
}
