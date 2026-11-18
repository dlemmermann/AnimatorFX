package com.dlsc.animatorfx.player;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.AnimationPlayer.Status;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The production clock: a player without a view, driven by the toolkit's pulses. */
@ExtendWith(FxThreadExtension.class)
class AnimationTimerPlaybackTest {

    @Test
    void aPlayerOnItsOwnPlaysToTheEndInRealTime() {
        AnimationPlayer player = new AnimationPlayer(
                new AnimationFile(Animation.create("short", 10, 10, 0.2), Map.of()));
        Object key = new Object();
        player.setOnFinished(event -> Platform.exitNestedEventLoop(key, null));
        long started = System.nanoTime();

        player.play();
        Platform.enterNestedEventLoop(key);

        double seconds = (System.nanoTime() - started) / 1e9;
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(0.2, player.getCurrentTime(), 1e-9);
        assertTrue(seconds > 0.15 && seconds < 5, "took " + seconds + " s");
    }
}
