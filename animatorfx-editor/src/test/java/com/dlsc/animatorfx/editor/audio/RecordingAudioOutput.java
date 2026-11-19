package com.dlsc.animatorfx.editor.audio;

import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.internal.audio.AudioOutput;
import com.dlsc.animatorfx.player.internal.audio.VoiceTarget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** A silent audio output that writes down what it is asked to do. */
public final class RecordingAudioOutput implements AudioOutput {

    /** Every call, in order: {@code apply}, {@code cue:<asset>}, {@code snippet}, {@code silence}, {@code dispose}. */
    public final List<String> calls = new ArrayList<>();
    public final List<List<VoiceTarget>> applied = new ArrayList<>();
    public final List<List<VoiceTarget>> snippets = new ArrayList<>();
    public final List<Double> snippetSeconds = new ArrayList<>();
    public final Map<String, Double> durations = new HashMap<>();
    public final AnimationFile file;
    public boolean disposed;
    /** How often the sounding cues were cut; not part of {@link #calls}. */
    public int cuesStopped;

    public RecordingAudioOutput(AnimationFile file) {
        this.file = file;
    }

    /** Makes the editor record instead of sound; returns the outputs as they are created. */
    public static List<RecordingAudioOutput> install() {
        List<RecordingAudioOutput> outputs = new ArrayList<>();
        EditorAudio.setOutputFactory(file -> {
            RecordingAudioOutput output = new RecordingAudioOutput(file);
            outputs.add(output);
            return output;
        });
        return outputs;
    }

    public static void uninstall() {
        EditorAudio.setOutputFactory(null);
    }

    public long count(String call) {
        return calls.stream().filter(call::equals).count();
    }

    @Override
    public OptionalDouble duration(String assetId) {
        Double duration = durations.get(assetId);
        return duration == null ? OptionalDouble.empty() : OptionalDouble.of(duration);
    }

    @Override
    public void apply(List<VoiceTarget> targets) {
        calls.add("apply");
        applied.add(List.copyOf(targets));
    }

    @Override
    public void playCue(String assetId, double volume) {
        calls.add("cue:" + assetId);
    }

    @Override
    public void snippet(List<VoiceTarget> targets, double seconds) {
        calls.add("snippet");
        snippets.add(List.copyOf(targets));
        snippetSeconds.add(seconds);
    }

    @Override
    public void silence() {
        calls.add("silence");
    }

    @Override
    public void stopCues() {
        cuesStopped++;
    }

    @Override
    public void dispose() {
        calls.add("dispose");
        disposed = true;
    }
}
