package com.dlsc.animatorfx.player;

import com.dlsc.animatorfx.player.internal.Clock;

import java.util.function.DoubleConsumer;

/** A clock that only advances when a test says so. */
final class ManualClock implements Clock {

    private DoubleConsumer tick;
    boolean running;

    @Override
    public void start(DoubleConsumer tick) {
        this.tick = tick;
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    /** Lets the given number of seconds pass; ignored while the clock is stopped. */
    void tick(double seconds) {
        if (running) {
            tick.accept(seconds);
        }
    }
}
