package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import com.lottie4j.core.model.animation.Animated;

/**
 * Reads Lottie transforms ({@code ks} of a layer, {@code tr} of a shape group).
 * <p>
 * Lottie composes translate(position) · rotate · skew · scale · translate(-anchor) like the
 * model. Rotation runs clockwise in both. A positive Lottie skew leans the other way than a
 * positive {@code skew.x} of the model, so the angle is negated.
 */
final class LottieTransforms {

    record Placement(Transform transform, Animatable<Double> opacity) {
    }

    private final LottieProperties properties;
    private final Warnings warnings;

    LottieTransforms(LottieProperties properties, Warnings warnings) {
        this.properties = properties;
        this.warnings = warnings;
    }

    /** The transform of a layer; that of a layer without one changes nothing. */
    Placement read(com.lottie4j.core.model.transform.Transform ks, double frameOffset) {
        if (ks == null) {
            return read(null, null, null, null, null, null, null, frameOffset);
        }
        return read(ks.anchor(), ks.position(), ks.scale(), ks.rotation() != null ? ks.rotation() : ks.rz(),
                ks.opacity(), ks.skew(), ks.skewAxis(), frameOffset);
    }

    /** The transform of a shape group; that of a group without one changes nothing. */
    Placement read(com.lottie4j.core.model.shape.grouping.Transform tr, double frameOffset) {
        if (tr == null) {
            return read(null, null, null, null, null, null, null, frameOffset);
        }
        return read(tr.anchor(), tr.position(), tr.scale(), tr.rotation() != null ? tr.rotation() : tr.rz(),
                tr.opacity(), tr.skew(), tr.skewAxis(), frameOffset);
    }

    private Placement read(Animated a, Animated p, Animated s, Animated r, Animated o, Animated sk, Animated sa,
                           double frameOffset) {
        Animatable<Vec2> anchor = properties.vec2(a, frameOffset, Vec2.ZERO, 1);
        Animatable<Vec2> scale = properties.vec2(s, frameOffset, new Vec2(100, 100), 0.01);
        Animatable<Double> rotation = properties.number(r, frameOffset, 0, 1);
        Animatable<Double> skewAngle = properties.number(sk, frameOffset, 0, -1);
        Animatable<Vec2> skew = Keyframes.map(skewAngle, angle -> new Vec2(angle + 0.0, 0));
        Animatable<Double> skewAxis = properties.number(sa, frameOffset, 0, 1);
        boolean skewed = skewAngle.isAnimated() || skewAngle.at(0) != 0;
        if (skewed && (skewAxis.isAnimated() || skewAxis.at(0) % 180 != 0)) {
            warnings.add("Skew axes are not supported; the layers are skewed along x", "transform", "transforms");
        }
        return new Placement(new Transform(anchor, position(p, frameOffset), scale, rotation, skew),
                opacity(o, frameOffset));
    }

    /** An opacity in percent as 0..1; values outside that range are limited to it. */
    Animatable<Double> opacity(Animated property, double frameOffset) {
        return Keyframes.map(properties.number(property, frameOffset, 100, 0.01),
                value -> Math.clamp(value, 0.0, 1.0));
    }

    /** A position, also in its split form with separate x and y properties. */
    private Animatable<Vec2> position(Animated p, double frameOffset) {
        if (p == null || !Boolean.TRUE.equals(p.s()) || (p.x() == null && p.y() == null)) {
            return properties.vec2(p, frameOffset, Vec2.ZERO, 1);
        }
        Animatable<Double> x = properties.number(p.x(), frameOffset, 0, 1);
        Animatable<Double> y = properties.number(p.y(), frameOffset, 0, 1);
        return Keyframes.combine(time -> new Vec2(x.at(time), y.at(time)),
                () -> warnings.add("Separate x and y position keyframes with different timing are sampled at their"
                        + " key times", "layer", "layers"), x, y);
    }
}
