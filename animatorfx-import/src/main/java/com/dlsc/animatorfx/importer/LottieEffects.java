package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Rgba;

import java.util.ArrayList;
import java.util.List;

import static com.dlsc.animatorfx.importer.LottieJson.get;
import static com.dlsc.animatorfx.importer.LottieJson.list;

/**
 * Converts the layer effects the model knows: Gaussian blur and drop shadow.
 */
final class LottieEffects {

    private static final int DROP_SHADOW = 25;
    private static final int GAUSSIAN_BLUR = 29;

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

    List<Effect> read(Object layer, double frameOffset) {
        List<Effect> effects = new ArrayList<>();
        for (Object effect : list(layer, "ef")) {
            if (LottieJson.has(effect, "en") && !LottieJson.flag(effect, "en")) {
                continue;
            }
            List<Object> values = list(effect, "ef");
            int type = (int) LottieJson.number(effect, "ty", -1);
            if (type == GAUSSIAN_BLUR && !values.isEmpty()) {
                effects.add(new Effect.Blur(
                        properties.number(get(values.get(0), "v"), frameOffset, 0, BLURRINESS_TO_RADIUS)));
            } else if (type == DROP_SHADOW && values.size() >= 5) {
                effects.add(dropShadow(values, frameOffset));
            } else {
                warnings.add("Layer effects other than Gaussian blur and drop shadow are not supported",
                        "effect ignored", "effects ignored");
            }
        }
        return effects;
    }

    /** Values in order: colour, opacity (0..255), direction (degrees, 0 = up, clockwise), distance, softness. */
    private Effect dropShadow(List<Object> values, double frameOffset) {
        Animatable<Rgba> color = properties.color(get(values.get(0), "v"), frameOffset, Rgba.BLACK);
        Animatable<Double> opacity = properties.number(get(values.get(1), "v"), frameOffset, 255, 1.0 / 255);
        Animatable<Double> direction = properties.number(get(values.get(2), "v"), frameOffset, 0, 1);
        Animatable<Double> distance = properties.number(get(values.get(3), "v"), frameOffset, 0, 1);
        Animatable<Double> softness = properties.number(get(values.get(4), "v"), frameOffset, 0, SOFTNESS_TO_RADIUS);
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
