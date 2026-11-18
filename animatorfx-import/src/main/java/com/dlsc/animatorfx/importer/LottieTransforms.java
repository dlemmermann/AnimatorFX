package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;

import static com.dlsc.animatorfx.importer.LottieJson.get;

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

    Placement read(Object ks, double frameOffset) {
        Animatable<Vec2> anchor = properties.vec2(get(ks, "a"), frameOffset, Vec2.ZERO, 1);
        Animatable<Vec2> scale = properties.vec2(get(ks, "s"), frameOffset, new Vec2(100, 100), 0.01);
        Object rotationSource = LottieJson.has(ks, "r") ? get(ks, "r") : get(ks, "rz");
        Animatable<Double> rotation = properties.number(rotationSource, frameOffset, 0, 1);
        Animatable<Double> opacity = opacity(get(ks, "o"), frameOffset);
        Animatable<Double> skewAngle = properties.number(get(ks, "sk"), frameOffset, 0, -1);
        Animatable<Vec2> skew = Keyframes.map(skewAngle, angle -> new Vec2(angle + 0.0, 0));
        Animatable<Double> skewAxis = properties.number(get(ks, "sa"), frameOffset, 0, 1);
        boolean skewed = skewAngle.isAnimated() || skewAngle.at(0) != 0;
        if (skewed && (skewAxis.isAnimated() || skewAxis.at(0) % 180 != 0)) {
            warnings.add("Skew axes are not supported; the layers are skewed along x", "transform", "transforms");
        }
        return new Placement(new Transform(anchor, position(get(ks, "p"), frameOffset), scale, rotation, skew),
                opacity);
    }

    /** An opacity in percent as 0..1; values outside that range are limited to it. */
    Animatable<Double> opacity(Object property, double frameOffset) {
        return Keyframes.map(properties.number(property, frameOffset, 100, 0.01),
                value -> Math.clamp(value, 0.0, 1.0));
    }

    /** A position, also in its split form with separate x and y properties. */
    private Animatable<Vec2> position(Object p, double frameOffset) {
        if (!LottieJson.flag(p, "s") || !(LottieJson.has(p, "x") || LottieJson.has(p, "y"))) {
            return properties.vec2(p, frameOffset, Vec2.ZERO, 1);
        }
        Animatable<Double> x = properties.number(get(p, "x"), frameOffset, 0, 1);
        Animatable<Double> y = properties.number(get(p, "y"), frameOffset, 0, 1);
        return Keyframes.combine(time -> new Vec2(x.at(time), y.at(time)),
                () -> warnings.add("Separate x and y position keyframes with different timing are sampled at their"
                        + " key times", "layer", "layers"), x, y);
    }
}
