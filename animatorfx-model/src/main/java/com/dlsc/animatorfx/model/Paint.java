package com.dlsc.animatorfx.model;

import java.util.List;
import java.util.Objects;

/**
 * A fill or stroke paint. Gradient geometry is in layer-local coordinates.
 */
public sealed interface Paint permits Paint.None, Paint.Solid, Paint.LinearGradient, Paint.RadialGradient {

    record None() implements Paint {
    }

    record Solid(Rgba color) implements Paint {
        public Solid {
            Objects.requireNonNull(color, "color");
        }
    }

    record Stop(double offset, Rgba color) {
        public Stop {
            Objects.requireNonNull(color, "color");
        }
    }

    record LinearGradient(Vec2 start, Vec2 end, List<Stop> stops) implements Paint {
        public LinearGradient {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            stops = List.copyOf(stops);
        }
    }

    record RadialGradient(Vec2 center, double radius, List<Stop> stops) implements Paint {
        public RadialGradient {
            Objects.requireNonNull(center, "center");
            stops = List.copyOf(stops);
        }
    }
}
