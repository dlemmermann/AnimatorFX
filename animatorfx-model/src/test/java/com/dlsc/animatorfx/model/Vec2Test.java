package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Vec2Test {

    @Test
    void arithmetic() {
        Vec2 a = new Vec2(1, 2);
        Vec2 b = new Vec2(3, 5);
        assertEquals(new Vec2(4, 7), a.plus(b));
        assertEquals(new Vec2(2, 3), b.minus(a));
        assertEquals(new Vec2(2, 4), a.times(2));
        assertEquals(new Vec2(2, 3.5), a.lerp(b, 0.5));
        assertEquals(new Vec2(0, 0), Vec2.ZERO);
        assertEquals(new Vec2(1, 1), Vec2.ONE);
    }
}
