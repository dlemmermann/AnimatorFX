package com.dlsc.animatorfx.model;

/**
 * A point or vector in document pixels (origin top-left, y down).
 */
public record Vec2(double x, double y) {

    public static final Vec2 ZERO = new Vec2(0, 0);
    public static final Vec2 ONE = new Vec2(1, 1);

    public Vec2 plus(Vec2 o) {
        return new Vec2(x + o.x, y + o.y);
    }

    public Vec2 minus(Vec2 o) {
        return new Vec2(x - o.x, y - o.y);
    }

    public Vec2 times(double factor) {
        return new Vec2(x * factor, y * factor);
    }

    public Vec2 lerp(Vec2 o, double u) {
        return new Vec2(x + (o.x - x) * u, y + (o.y - y) * u);
    }
}
