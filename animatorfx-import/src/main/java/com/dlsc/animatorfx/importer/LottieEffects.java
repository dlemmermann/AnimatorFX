package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Rgba;
import com.lottie4j.core.definition.EffectType;
import com.lottie4j.core.model.effect.EffectValue;
import com.lottie4j.core.model.layer.Layer;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts the layer effects the model knows: Gaussian blur and drop shadow.
 */
final class LottieEffects {

    /**
     * Players blur with a standard deviation of 0.3 × blurriness; a JavaFX blur of radius r has
     * a standard deviation of about r / 3.
     */
    static final double BLURRINESS_TO_RADIUS = 0.9;

    /** Players blur a shadow with a standard deviation of softness / 4. */
    static final double SOFTNESS_TO_RADIUS = 0.75;

    private final LottieProperties properties;
    private final Warnings warnings;

    LottieEffects(LottieProperties properties, Warnings warnings) {
        this.properties = properties;
        this.warnings = warnings;
    }

    List<Effect> read(Layer layer, double frameOffset) {
        List<Effect> effects = new ArrayList<>();
        if (layer.effects() == null) {
            return effects;
        }
        for (com.lottie4j.core.model.effect.Effect effect : layer.effects()) {
            if (effect == null || (effect.enabled() != null && effect.enabled() == 0)) {
                continue;
            }
            List<EffectValue> values = effect.values() == null ? List.of() : effect.values();
            if (effect.type() == EffectType.GAUSSIAN_BLUR && !values.isEmpty()) {
                effects.add(new Effect.Blur(
                        properties.number(values.get(0).value(), frameOffset, 0, BLURRINESS_TO_RADIUS)));
            } else if (effect.type() == EffectType.DROP_SHADOW && values.size() >= 5) {
                effects.add(dropShadow(values, frameOffset));
            } else {
                warnings.add("Layer effects other than Gaussian blur and drop shadow are not supported",
                        "effect ignored", "effects ignored");
            }
        }
        return effects;
    }

    /** Values in order: colour, opacity (0..255), direction (degrees, 0 = up, clockwise), distance, softness. */
    private Effect dropShadow(List<EffectValue> values, double frameOffset) {
        Animatable<Rgba> color = properties.color(values.get(0).value(), frameOffset, Rgba.BLACK);
        Animatable<Double> opacity = properties.number(values.get(1).value(), frameOffset, 255, 1.0 / 255);
        Animatable<Double> direction = properties.number(values.get(2).value(), frameOffset, 0, 1);
        Animatable<Double> distance = properties.number(values.get(3).value(), frameOffset, 0, 1);
        Animatable<Double> softness = properties.number(values.get(4).value(), frameOffset, 0, SOFTNESS_TO_RADIUS);
        Runnable sampled = () -> warnings.add(
                "Properties animated with different timing are combined by sampling at their key times", "value",
                "values");
        Animatable<Rgba> faded = Keyframes.combine(time -> {
            Rgba c = color.at(time);
            return new Rgba(c.r(), c.g(), c.b(), c.a() * Math.clamp(opacity.at(time), 0.0, 1.0));
        }, sampled, color, opacity);
        Animatable<Double> offsetX = Keyframes.combine(
                time -> distance.at(time) * Math.sin(Math.toRadians(direction.at(time))) + 0.0, sampled, direction,
                distance);
        Animatable<Double> offsetY = Keyframes.combine(
                time -> -distance.at(time) * Math.cos(Math.toRadians(direction.at(time))) + 0.0, sampled, direction,
                distance);
        return new Effect.DropShadow(faded, softness, offsetX, offsetY, Animatable.of(0.0));
    }
}
