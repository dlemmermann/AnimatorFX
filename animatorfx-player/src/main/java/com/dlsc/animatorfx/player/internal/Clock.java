package com.dlsc.animatorfx.player.internal;

import java.util.function.DoubleConsumer;

/**
 * The time source of a player. Production code runs on a JavaFX {@code AnimationTimer}; tests
 * advance time by hand.
 */
public interface Clock {

    /**
     * Starts the clock. From now on {@code tick} receives the seconds that passed since the call
     * before (since the start for the first one), until {@link #stop()}.
     */
    void start(DoubleConsumer tick);

    void stop();
}
