package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.player.AnimationPlayer;
import com.dlsc.animatorfx.player.AnimationView;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Plays the document in the canvas: the player of the canvas view runs and drives the playhead
 * of the document, so everything that follows the playhead follows playback. It is also the one
 * player of the editor that makes sound: while playing, and for a moment when scrubbing.
 */
final class Playback {

    private static final long SCRUB_INTERVAL_NANOS = 50_000_000L;

    private final AnimationView view;
    private final ReadOnlyBooleanWrapper playing = new ReadOnlyBooleanWrapper(this, "playing");
    private final BooleanProperty loop = new SimpleBooleanProperty(this, "loop");
    private EditorDocument document;
    private boolean driving;
    private long lastScrub;

    Playback(AnimationView view) {
        this.view = view;
        view.statusProperty().addListener((obs, oldStatus, status) -> {
            playing.set(status == AnimationPlayer.Status.PLAYING);
            pushTime();
        });
        view.currentTimeProperty().addListener((obs, oldTime, time) -> {
            if (playing.get()) {
                pushTime();
            }
        });
        loop.addListener((obs, was, now) -> view.setCycleCount(now ? AnimationPlayer.INDEFINITE : 1));
    }

    /**
     * Whether the document is playing.
     *
     * @return the playing property
     */
    final ReadOnlyBooleanProperty playingProperty() {
        return playing.getReadOnlyProperty();
    }

    final boolean isPlaying() {
        return playing.get();
    }

    /**
     * Whether playback starts over at the end.
     *
     * @return the loop property
     */
    final BooleanProperty loopProperty() {
        return loop;
    }

    final boolean isLoop() {
        return loop.get();
    }

    final void setLoop(boolean value) {
        loop.set(value);
    }

    void setDocument(EditorDocument document) {
        view.pause();
        this.document = document;
    }

    void toggle() {
        if (document == null) {
            return;
        }
        if (isPlaying()) {
            view.pause();
        } else {
            view.seek(document.getPlayhead());
            view.play();
        }
    }

    void pause() {
        view.pause();
    }

    /**
     * Lets the sound at a time be heard for a moment, as the user drags the playhead. Not while
     * playing, and not more often than every {@link #SCRUB_INTERVAL_NANOS}: a fast drag would
     * otherwise ask for a snippet on every pixel.
     */
    void scrub(double seconds) {
        if (document == null || isPlaying()) {
            return;
        }
        long now = System.nanoTime();
        if (lastScrub != 0 && now - lastScrub < SCRUB_INTERVAL_NANOS) {
            return;
        }
        lastScrub = now;
        view.scrub(seconds);
    }

    /** The playhead was moved: by playback itself, or by the user, which the view then follows. */
    void playheadChanged() {
        if (!driving && document != null) {
            view.seek(document.getPlayhead());
        }
    }

    private void pushTime() {
        if (document == null || driving) {
            return;
        }
        driving = true;
        try {
            document.setPlayhead(view.getCurrentTime());
        } finally {
            driving = false;
        }
    }
}
