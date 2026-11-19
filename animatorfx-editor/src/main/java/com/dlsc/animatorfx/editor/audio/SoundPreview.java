package com.dlsc.animatorfx.editor.audio;

import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.internal.audio.AudioOutput;
import com.dlsc.animatorfx.player.internal.audio.VoiceTarget;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.util.Duration;

/**
 * Plays one sound of a document by itself, for the preview buttons of the inspector: a voice of
 * its own, apart from the playback of the document. One preview at a time; starting another
 * one, {@link #stop()} or the end of the sound ends it.
 */
public final class SoundPreview {

    private static final SoundPreview SHARED = new SoundPreview();
    /** How long a sound of unknown length is previewed. */
    private static final double UNKNOWN_LENGTH = 30;

    private final ReadOnlyObjectWrapper<Object> owner = new ReadOnlyObjectWrapper<>(this, "owner");
    private final PauseTransition end = new PauseTransition();
    private AudioOutput output;

    public SoundPreview() {
        end.setOnFinished(e -> stop());
    }

    /** The preview voice of the editor. */
    public static SoundPreview shared() {
        return SHARED;
    }

    /**
     * Who started the preview that is sounding; {@code null} while there is none.
     *
     * @return the owner property
     */
    public final ReadOnlyObjectProperty<Object> ownerProperty() {
        return owner.getReadOnlyProperty();
    }

    public final Object getOwner() {
        return owner.get();
    }

    /**
     * Plays an audio asset of the document from a position to its end.
     *
     * @param who          identifies the caller, see {@link #ownerProperty()}
     * @param from         the position in the sound in seconds
     * @param clipDuration the length of the sound; not a positive number when it is not known
     */
    public void play(Object who, AnimationFile file, String assetId, double from, double volume, double balance,
                     double clipDuration) {
        stop();
        double start = Double.isFinite(from) ? Math.max(0, from) : 0;
        double seconds = Double.isFinite(clipDuration) && clipDuration > 0 ? clipDuration - start : UNKNOWN_LENGTH;
        if (!(seconds > 0)) {
            return;
        }
        try {
            output = EditorAudio.createOutput(file);
            if (output == null) {
                return;
            }
            output.snippet(List.of(new VoiceTarget("preview", assetId, true, start,
                    Math.clamp(Double.isNaN(volume) ? 1 : volume, 0, 1), Math.clamp(Double.isNaN(balance) ? 0 : balance, -1, 1), 1)),
                    seconds);
        } catch (RuntimeException | LinkageError e) {
            stop();
            return;
        }
        owner.set(who);
        end.setDuration(Duration.seconds(seconds + 0.1));
        end.playFromStart();
    }

    /** Ends the preview, if one is sounding. */
    public void stop() {
        end.stop();
        AudioOutput old = output;
        output = null;
        owner.set(null);
        if (old != null) {
            try {
                old.dispose();
            } catch (RuntimeException | LinkageError e) {
                // nothing left to release
            }
        }
    }
}
