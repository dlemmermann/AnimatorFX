package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.Vec2;
import com.lottie4j.core.model.animation.Animated;
import com.lottie4j.core.model.animation.EasingHandle;
import com.lottie4j.core.model.bezier.AnimatedBezier;
import com.lottie4j.core.model.bezier.Bezier;
import com.lottie4j.core.model.bezier.BezierDefinition;
import com.lottie4j.core.model.bezier.BezierKeyframe;
import com.lottie4j.core.model.bezier.FixedBezier;
import com.lottie4j.core.model.keyframe.NumberKeyframe;
import com.lottie4j.core.model.keyframe.TimedKeyframe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Turns the properties of the Lottie model into animated values of ours: a fixed value stays a
 * value, keyframes become keyframes with their times in seconds and their easing as a cubic
 * Bezier. Reading the file, including keyframes in the old form, is Lottie4J's job.
 */
final class LottieProperties {

    private final Warnings warnings;
    private final double frameRate;

    LottieProperties(Warnings warnings, double frameRate) {
        this.warnings = warnings;
        this.frameRate = frameRate;
    }

    /**
     * Seconds of a frame number.
     *
     * @param frameOffset frames to add: minus the in point of the document, plus the start of
     *                    every precomposition around
     */
    double time(double frame, double frameOffset) {
        return (frame + frameOffset) / frameRate;
    }

    Animatable<Double> number(Animated property, double frameOffset, double fallback, double factor) {
        return read(property, frameOffset, fallback * factor, n -> (n.length == 0 ? fallback : n[0]) * factor);
    }

    Animatable<Vec2> vec2(Animated property, double frameOffset, Vec2 fallback, double factor) {
        return read(property, frameOffset, fallback.times(factor),
                n -> n.length < 2 ? fallback.times(factor) : new Vec2(n[0] * factor, n[1] * factor));
    }

    /** A colour as {@code [r, g, b]} or {@code [r, g, b, a]} with components in 0..1. */
    Animatable<Rgba> color(Animated property, double frameOffset, Rgba fallback) {
        return read(property, frameOffset, fallback, n -> color(n, fallback));
    }

    static Rgba color(double[] n, Rgba fallback) {
        if (n == null || n.length < 3) {
            return fallback;
        }
        return new Rgba(unit(n[0]), unit(n[1]), unit(n[2]), n.length > 3 ? unit(n[3]) : 1);
    }

    private static double unit(double value) {
        return Double.isNaN(value) ? 0 : Math.clamp(value, 0.0, 1.0);
    }

    Animatable<double[]> numbers(Animated property, double frameOffset, double[] fallback) {
        return read(property, frameOffset, fallback, n -> n);
    }

    /**
     * @param fallback the value of a missing property
     * @param convert  turns the numbers of one value into the model value
     */
    <T> Animatable<T> read(Animated property, double frameOffset, T fallback, Function<double[], T> convert) {
        if (property == null || property.keyframes() == null || property.keyframes().isEmpty()) {
            return Animatable.of(fallback);
        }
        List<com.lottie4j.core.model.keyframe.Keyframe> entries = property.keyframes();
        if (!(entries.get(0) instanceof TimedKeyframe)) {
            double[] value = fixed(entries);
            return Limits.usable(value) ? Animatable.of(convert.apply(value)) : unusable(fallback);
        }
        Track<T> track = new Track<>();
        for (com.lottie4j.core.model.keyframe.Keyframe entry : entries) {
            if (!(entry instanceof TimedKeyframe keyframe) || keyframe.values() == null || keyframe.time() == null) {
                continue;
            }
            if (curved(keyframe.tangentOut()) || curved(keyframe.tangentIn())) {
                warnings.add("Curved motion between position keyframes is not supported; the motion is straight",
                        "keyframe", "keyframes");
            }
            double time = time(keyframe.time(), frameOffset);
            double[] value = keyframe.values().stream().mapToDouble(Number::doubleValue).toArray();
            if (!Limits.usable(time) || !Limits.usable(value)) {
                return unusable(fallback);
            }
            boolean hold = keyframe.holdFrame() != null && keyframe.holdFrame() != 0;
            track.add(time, convert.apply(value), easing(hold, keyframe.easingOut(), keyframe.easingIn()));
        }
        return track.finish(fallback);
    }

    /** A Bezier shape; Lottie tangents are relative to their vertex like the model's. */
    Animatable<PathData> path(Bezier bezier, double frameOffset) {
        if (bezier instanceof FixedBezier fixed) {
            if (fixed.bezier() == null) {
                return Animatable.of(PathData.EMPTY);
            }
            return usable(fixed.bezier()) ? Animatable.of(shape(fixed.bezier())) : unusable(PathData.EMPTY);
        }
        if (!(bezier instanceof AnimatedBezier animated) || animated.beziers() == null) {
            return Animatable.of(PathData.EMPTY);
        }
        Track<PathData> track = new Track<>();
        for (BezierKeyframe keyframe : animated.beziers()) {
            if (keyframe == null || keyframe.time() == null || keyframe.beziers() == null
                    || keyframe.beziers().isEmpty()) {
                continue;
            }
            BezierDefinition definition = keyframe.beziers().get(0);
            double time = time(keyframe.time(), frameOffset);
            if (!Limits.usable(time) || !usable(definition)) {
                return unusable(PathData.EMPTY);
            }
            track.add(time, shape(definition), easing(keyframe.isHold(), keyframe.easingOut(), keyframe.easingIn()));
        }
        return track.finish(PathData.EMPTY);
    }

    /** The keyframes of one property on their way to the model. */
    private static final class Track<T> {

        private final List<Keyframe<T>> keyframes = new ArrayList<>();

        /** Of two keyframes at the same time the later one counts. */
        void add(double time, T value, Easing easing) {
            Keyframe<T> keyframe = Keyframes.key(time, value, easing);
            int last = keyframes.size() - 1;
            if (last >= 0 && Math.abs(keyframes.get(last).time() - time) < Animatable.TIME_EPSILON) {
                keyframes.set(last, keyframe);
            } else {
                keyframes.add(keyframe);
            }
        }

        Animatable<T> finish(T fallback) {
            return keyframes.isEmpty() ? Animatable.of(fallback) : Keyframes.fromZero(Keyframes.of(keyframes));
        }
    }

    /** The numbers of a value that is not animated. */
    private static double[] fixed(List<com.lottie4j.core.model.keyframe.Keyframe> entries) {
        return entries.stream().filter(NumberKeyframe.class::isInstance)
                .mapToDouble(entry -> ((NumberKeyframe) entry).doubleValue()).toArray();
    }

    private static PathData shape(BezierDefinition shape) {
        List<List<Double>> points = orEmpty(shape.vertices());
        List<List<Double>> in = orEmpty(shape.tangentsIn());
        List<List<Double>> out = orEmpty(shape.tangentsOut());
        List<PathData.Vertex> vertices = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            vertices.add(new PathData.Vertex(point(points.get(i)), i < in.size() ? point(in.get(i)) : Vec2.ZERO,
                    i < out.size() ? point(out.get(i)) : Vec2.ZERO));
        }
        if (vertices.isEmpty()) {
            return PathData.EMPTY;
        }
        return new PathData(List.of(new PathData.Contour(vertices, Boolean.TRUE.equals(shape.closed()))));
    }

    private static <E> List<E> orEmpty(List<E> list) {
        return list == null ? List.of() : list;
    }

    private static Vec2 point(List<Double> value) {
        return value == null || value.size() < 2 || value.get(0) == null || value.get(1) == null ? Vec2.ZERO
                : new Vec2(value.get(0), value.get(1));
    }

    /** A property with a value or a time that is not finite or absurdly large has its default value. */
    private <T> Animatable<T> unusable(T fallback) {
        warnings.add("Properties with values that are not finite or too large use their default", "property",
                "properties");
        return Animatable.of(fallback);
    }

    private static boolean usable(BezierDefinition shape) {
        for (List<List<Double>> points : List.of(orEmpty(shape.vertices()), orEmpty(shape.tangentsIn()),
                orEmpty(shape.tangentsOut()))) {
            for (List<Double> point : points) {
                for (Double coordinate : orEmpty(point)) {
                    if (coordinate != null && !Limits.usable(coordinate)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean curved(List<? extends Number> tangent) {
        return tangent != null && tangent.stream().anyMatch(component -> component.doubleValue() != 0);
    }

    /**
     * The easing towards the next keyframe: the out handle {@code o} of this keyframe and the in
     * handle {@code i} towards the next, as the control points of a cubic Bezier.
     */
    private Easing easing(boolean hold, EasingHandle out, EasingHandle in) {
        if (hold) {
            return Easing.HOLD;
        }
        if (out == null || in == null || empty(out.x()) || empty(out.y()) || empty(in.x()) || empty(in.y())) {
            return Easing.LINEAR;
        }
        if (differ(out.x()) || differ(out.y()) || differ(in.x()) || differ(in.y())) {
            warnings.add("Easing that differs per dimension is not supported; the first dimension is used",
                    "keyframe", "keyframes");
        }
        double x1 = Math.clamp(out.x().get(0), 0.0, 1.0);
        double x2 = Math.clamp(in.x().get(0), 0.0, 1.0);
        double y1 = out.y().get(0);
        double y2 = in.y().get(0);
        if (x1 == y1 && x2 == y2) {
            return Easing.LINEAR;
        }
        return new Easing.CubicBezier(x1, y1, x2, y2);
    }

    private static boolean empty(List<Double> values) {
        return values == null || values.isEmpty() || values.get(0) == null || Double.isNaN(values.get(0));
    }

    private static boolean differ(List<Double> values) {
        return values.stream().anyMatch(value -> !values.get(0).equals(value));
    }
}
