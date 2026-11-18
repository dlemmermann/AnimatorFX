package com.dlsc.animatorfx.player;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.AnimationPlayer.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.dlsc.animatorfx.player.TestDocs.doc;
import static com.dlsc.animatorfx.player.TestDocs.keys;
import static com.dlsc.animatorfx.player.TestDocs.rect;
import static com.dlsc.animatorfx.player.TestDocs.transformed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Playback with a manual clock; needs no JavaFX toolkit. */
class AnimationPlayerTest {

    private static final double EPS = 1e-9;

    private final ManualClock clock = new ManualClock();
    private final AnimationPlayer player = new AnimationPlayer(clock);
    private final List<String> reached = new ArrayList<>();
    private int finished;

    AnimationPlayerTest() {
        player.setAnimation(file(doc(rect("r", 10, 10)).withMarkers(List.of(
                new Marker("a", 0.5, 1.5), new Marker("b", 1.5, 2)))));
        player.setOnFinished(event -> finished++);
        player.setOnMarkerReached(marker -> reached.add(marker.name()));
    }

    private static AnimationFile file(Animation animation) {
        return new AnimationFile(animation, Map.of());
    }

    @Test
    void startsStoppedAtZero() {
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(0, player.getCurrentTime(), EPS);
        assertEquals(1, player.getRate(), EPS);
        assertEquals(1, player.getCycleCount());
        assertFalse(player.isAutoReverse());
        assertNull(player.getActiveMarker());
        assertFalse(clock.running);
    }

    @Test
    void timeAdvancesByRateTimesElapsed() {
        player.setRate(2);
        player.play();
        clock.tick(0.25);

        assertEquals(Status.PLAYING, player.getStatus());
        assertEquals(0.5, player.getCurrentTime(), EPS);
    }

    @Test
    void stopsAtDurationAndFiresOnFinishedOnce() {
        player.play();
        clock.tick(1.5);
        clock.tick(1);

        assertEquals(2, player.getCurrentTime(), EPS);
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(1, finished);
        assertFalse(clock.running);

        clock.tick(1);
        assertEquals(1, finished);
    }

    @Test
    void cycleCountTwoWrapsOnce() {
        player.setCycleCount(2);
        player.play();
        clock.tick(2.5);

        assertEquals(0.5, player.getCurrentTime(), EPS);
        assertEquals(Status.PLAYING, player.getStatus());
        assertEquals(0, finished);

        clock.tick(1.5);
        assertEquals(2, player.getCurrentTime(), EPS);
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(1, finished);
    }

    @Test
    void autoReverseReturnsToZero() {
        player.setCycleCount(2);
        player.setAutoReverse(true);
        player.play();
        clock.tick(2.5);

        assertEquals(1.5, player.getCurrentTime(), EPS);

        clock.tick(2);
        assertEquals(0, player.getCurrentTime(), EPS);
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(1, finished);
    }

    @Test
    void indefiniteNeverFinishes() {
        player.setCycleCount(AnimationPlayer.INDEFINITE);
        player.play();
        for (int i = 0; i < 10; i++) {
            clock.tick(2.5);
        }

        assertEquals(Status.PLAYING, player.getStatus());
        assertEquals(0, finished);
        assertEquals(1, player.getCurrentTime(), 1e-6);
    }

    @Test
    void negativeRatePlaysBackwardsFromTheEnd() {
        player.setRate(-1);
        player.play();
        assertEquals(2, player.getCurrentTime(), EPS);

        clock.tick(0.75);
        assertEquals(1.25, player.getCurrentTime(), EPS);

        clock.tick(5);
        assertEquals(0, player.getCurrentTime(), EPS);
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(1, finished);
    }

    @Test
    void rateZeroHoldsTimeWithoutChangingStatus() {
        player.play();
        clock.tick(0.5);
        player.setRate(0);
        clock.tick(3);

        assertEquals(0.5, player.getCurrentTime(), EPS);
        assertEquals(Status.PLAYING, player.getStatus());
    }

    @Test
    void playAfterTheNaturalEndRestarts() {
        player.play();
        clock.tick(3);
        player.play();

        assertEquals(0, player.getCurrentTime(), EPS);
        assertEquals(Status.PLAYING, player.getStatus());
        clock.tick(0.5);
        assertEquals(0.5, player.getCurrentTime(), EPS);
    }

    @Test
    void pauseAndResumeKeepTheTime() {
        player.play();
        clock.tick(0.5);
        player.pause();

        assertEquals(Status.PAUSED, player.getStatus());
        assertFalse(clock.running);
        clock.tick(0.5);
        assertEquals(0.5, player.getCurrentTime(), EPS);

        player.play();
        clock.tick(0.25);
        assertEquals(0.75, player.getCurrentTime(), EPS);
        assertEquals(Status.PLAYING, player.getStatus());
    }

    @Test
    void stopSeeksToZeroAndResetsTheCycles() {
        player.setCycleCount(2);
        player.play();
        clock.tick(2.5);
        player.stop();

        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(0, player.getCurrentTime(), EPS);
        assertFalse(clock.running);
        assertEquals(0, finished);

        player.play();
        clock.tick(2.5);
        assertEquals(Status.PLAYING, player.getStatus(), "both cycles are played again");
    }

    @Test
    void seekClampsToTheDuration() {
        player.seek(5);
        assertEquals(2, player.getCurrentTime(), EPS);
        player.seek(-1);
        assertEquals(0, player.getCurrentTime(), EPS);
        player.seek(1.25);
        assertEquals(1.25, player.getCurrentTime(), EPS);
    }

    @Test
    void settingCurrentTimeBehavesLikeSeek() {
        List<Double> seen = new ArrayList<>();
        player.currentTimeProperty().addListener((o, was, is) -> seen.add(is.doubleValue()));

        player.setCurrentTime(9);

        assertEquals(2, player.getCurrentTime(), EPS);
        assertEquals(2, player.getFrame().time(), EPS);
        assertEquals(2, seen.get(seen.size() - 1), EPS);
    }

    @Test
    void playMarkerPlaysExactlyTheMarkerRange() {
        player.playMarker("a");

        assertEquals("a", player.getActiveMarker());
        assertEquals(0.5, player.getCurrentTime(), EPS);
        assertEquals(Status.PLAYING, player.getStatus());

        clock.tick(0.25);
        assertEquals(0.75, player.getCurrentTime(), EPS);

        clock.tick(5);
        assertEquals(1.5, player.getCurrentTime(), EPS);
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(1, finished);
        assertNull(player.getActiveMarker());
    }

    @Test
    void playMarkerRespectsCycleCountAndAutoReverse() {
        player.setCycleCount(2);
        player.setAutoReverse(true);
        player.playMarker("a");
        clock.tick(1.25);

        assertEquals(1.25, player.getCurrentTime(), EPS);
        assertEquals("a", player.getActiveMarker());

        clock.tick(5);
        assertEquals(0.5, player.getCurrentTime(), EPS);
        assertEquals(1, finished);
    }

    @Test
    void seekClampsToTheMarkerRangeWhileAMarkerPlays() {
        player.playMarker("a");
        player.seek(1.9);
        assertEquals(1.5, player.getCurrentTime(), EPS);
        player.seek(0);
        assertEquals(0.5, player.getCurrentTime(), EPS);
    }

    @Test
    void stopDuringAMarkerSeeksToItsStartAndClearsIt() {
        player.playMarker("a");
        clock.tick(0.5);
        player.stop();

        assertEquals(0.5, player.getCurrentTime(), EPS);
        assertNull(player.getActiveMarker());
        assertEquals(Status.STOPPED, player.getStatus());

        player.seek(1.9);
        assertEquals(1.9, player.getCurrentTime(), EPS, "the whole document is the range again");
    }

    @Test
    void unknownMarkerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> player.playMarker("nope"));
        assertEquals(Status.STOPPED, player.getStatus());
    }

    @Test
    void markerReachedFiresWhenItsStartIsCrossed() {
        player.play();
        clock.tick(0.4);
        assertEquals(List.of(), reached);

        clock.tick(0.2);
        assertEquals(List.of("a"), reached);

        clock.tick(0.2);
        assertEquals(List.of("a"), reached);

        clock.tick(0.7);
        assertEquals(List.of("a", "b"), reached);
    }

    @Test
    void markersCrossedInOneTickFireInPlaybackOrder() {
        player.play();
        clock.tick(1.9);
        assertEquals(List.of("a", "b"), reached);

        reached.clear();
        player.stop();
        player.setRate(-1);
        player.play();
        clock.tick(1.9);
        assertEquals(List.of("b", "a"), reached);
    }

    @Test
    void markerAtTheStartTimeFiresOnTheFirstTickOnly() {
        player.setAnimation(file(doc(rect("r", 10, 10)).withMarkers(List.of(new Marker("intro", 0, 1)))));
        player.play();
        assertEquals(List.of(), reached);

        clock.tick(0.1);
        assertEquals(List.of("intro"), reached);

        clock.tick(0.1);
        assertEquals(List.of("intro"), reached);
    }

    @Test
    void markerFiresAgainOnEveryCycle() {
        player.setCycleCount(2);
        player.play();
        clock.tick(2.75);

        assertEquals(List.of("a", "b", "a"), reached);
    }

    @Test
    void seekingAcrossAMarkerDoesNotFire() {
        player.play();
        player.seek(1);
        clock.tick(0.1);
        player.seek(0.2);

        assertEquals(List.of(), reached);
    }

    @Test
    void aHandlerMayStopThePlayer() {
        player.setOnMarkerReached(marker -> {
            reached.add(marker.name());
            player.stop();
        });
        player.play();
        clock.tick(1.9);

        assertEquals(List.of("a"), reached);
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(0, player.getCurrentTime(), EPS);
    }

    @Test
    void zeroDurationFinishesImmediately() {
        player.setAnimation(file(Animation.create("empty", 10, 10, 0)));
        player.setCycleCount(AnimationPlayer.INDEFINITE);
        player.play();

        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(1, finished);
        assertFalse(clock.running);
    }

    @Test
    void playWithoutAnAnimationDoesNothing() {
        AnimationPlayer empty = new AnimationPlayer(new ManualClock());
        empty.play();

        assertEquals(Status.STOPPED, empty.getStatus());
        assertNull(empty.getFrame());
    }

    @Test
    void frameFollowsTimeAndAnimation() {
        player.setAnimation(file(doc(transformed(rect("r", 10, 10), Transform.IDENTITY
                .withPosition(keys(0, new Vec2(0, 0), 2, new Vec2(100, 0)))))));
        assertEquals(0, player.getFrame().get("r").position().x(), EPS);

        player.seek(1);
        assertEquals(1, player.getFrame().time(), EPS);
        assertEquals(50, player.getFrame().get("r").position().x(), EPS);

        player.play();
        clock.tick(0.5);
        assertEquals(75, player.getFrame().get("r").position().x(), EPS);

        player.setAnimation(null);
        assertNull(player.getFrame());
        assertEquals(Status.STOPPED, player.getStatus());
        assertEquals(0, player.getCurrentTime(), EPS);
    }

    @Test
    void aShorterAnimationClampsTheTime() {
        player.seek(1.8);
        player.setAnimation(file(Animation.create("short", 10, 10, 1)));

        assertEquals(1, player.getCurrentTime(), EPS);
    }

    @Test
    void theClockRunsOnlyWhilePlaying() {
        assertFalse(clock.running);
        player.play();
        assertTrue(clock.running);
        player.pause();
        assertFalse(clock.running);
        player.play();
        assertTrue(clock.running);
        player.stop();
        assertFalse(clock.running);
    }
}
