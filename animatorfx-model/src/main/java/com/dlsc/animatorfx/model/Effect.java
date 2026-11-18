package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * A visual effect on a layer. Effects are applied in list order.
 */
public sealed interface Effect permits Effect.Blur, Effect.DropShadow, Effect.Glow {

    /** A Gaussian blur. */
    record Blur(Animatable<Double> radius) implements Effect {
        public Blur {
            Objects.requireNonNull(radius, "radius");
        }
    }

    record DropShadow(Animatable<Rgba> color, Animatable<Double> radius, Animatable<Double> offsetX,
                      Animatable<Double> offsetY, Animatable<Double> spread) implements Effect {
        public DropShadow {
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(radius, "radius");
            Objects.requireNonNull(offsetX, "offsetX");
            Objects.requireNonNull(offsetY, "offsetY");
            Objects.requireNonNull(spread, "spread");
        }
    }

    record Glow(Animatable<Double> level) implements Effect {
        public Glow {
            Objects.requireNonNull(level, "level");
        }
    }
}
