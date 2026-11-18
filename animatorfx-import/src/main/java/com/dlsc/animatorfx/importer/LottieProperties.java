package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.dlsc.animatorfx.importer.LottieJson.get;
import static com.dlsc.animatorfx.importer.LottieJson.list;

/**
 * Reads Lottie properties ({@code {a, k}}): a static value, or keyframes in the old form (with
 * the end value {@code e}) or the new one (the end value is the next keyframe's start value).
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

    Animatable<Double> number(Object property, double frameOffset, double fallback, double factor) {
        return read(property, frameOffset, fallback * factor,
                value -> LottieJson.number(value, fallback) * factor);
    }

    Animatable<Vec2> vec2(Object property, double frameOffset, Vec2 fallback, double factor) {
        return read(property, frameOffset, fallback.times(factor), value -> {
            double[] n = LottieJson.numbers(value);
            return n == null || n.length < 2 ? fallback.times(factor) : new Vec2(n[0] * factor, n[1] * factor);
        });
    }

    /** A colour as {@code [r, g, b]} or {@code [r, g, b, a]} with components in 0..1. */
    Animatable<Rgba> color(Object property, double frameOffset, Rgba fallback) {
        return read(property, frameOffset, fallback, value -> color(LottieJson.numbers(value), fallback));
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

    Animatable<double[]> numbers(Object property, double frameOffset, double[] fallback) {
        return read(property, frameOffset, fallback, value -> {
            double[] n = LottieJson.numbers(value);
            return n == null ? fallback : n;
        });
    }

    /** A Bezier shape {@code {v, i, o, c}}; Lottie tangents are relative to their vertex like the model's. */
    Animatable<PathData> path(Object property, double frameOffset) {
        return read(property, frameOffset, PathData.EMPTY, LottieProperties::shape);
    }

    private static PathData shape(Object value) {
        Object shape = value instanceof List<?> wrapped && !wrapped.isEmpty() ? wrapped.get(0) : value;
        List<Object> points = list(shape, "v");
        List<Object> in = list(shape, "i");
        List<Object> out = list(shape, "o");
        List<PathData.Vertex> vertices = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            vertices.add(new PathData.Vertex(point(points.get(i)), i < in.size() ? point(in.get(i)) : Vec2.ZERO,
                    i < out.size() ? point(out.get(i)) : Vec2.ZERO));
        }
        if (vertices.isEmpty()) {
            return PathData.EMPTY;
        }
        return new PathData(List.of(new PathData.Contour(vertices, LottieJson.flag(shape, "c"))));
    }

    private static Vec2 point(Object value) {
        double[] n = LottieJson.numbers(value);
        return n == null || n.length < 2 ? Vec2.ZERO : new Vec2(n[0], n[1]);
    }

    /**
     * @param fallback the value of a missing property
     * @param convert  turns the JSON of one value into the model value
     */
    <T> Animatable<T> read(Object property, double frameOffset, T fallback, Function<Object, T> convert) {
        if (property == null) {
            return Animatable.of(fallback);
        }
        Object k = LottieJson.has(property, "k") ? get(property, "k") : property;
        List<Object> entries = list(k);
        boolean keyframed = !entries.isEmpty() && LottieJson.has(entries.get(0), "t");
        if (!keyframed) {
            return Animatable.of(convert.apply(k));
        }
        List<Keyframe<T>> keyframes = new ArrayList<>();
        Object previousEnd = null;
        Object previousStart = null;
        for (Object entry : entries) {
            Object start = get(entry, "s");
            // the last keyframe of an old file has only its time: its value is where the previous one ends
            Object value = start != null ? start : previousEnd != null ? previousEnd : previousStart;
            previousEnd = get(entry, "e");
            previousStart = value;
            if (value == null || !(get(entry, "t") instanceof Number frame)) {
                continue;
            }
            if (curved(get(entry, "to")) || curved(get(entry, "ti"))) {
                warnings.add("Curved motion between position keyframes is not supported; the motion is straight",
                        "keyframe", "keyframes");
            }
            double time = time(frame.doubleValue(), frameOffset);
            Keyframe<T> keyframe = Keyframes.key(time, convert.apply(value), easing(entry));
            if (!keyframes.isEmpty()
                    && Math.abs(keyframes.get(keyframes.size() - 1).time() - time) < Animatable.TIME_EPSILON) {
                keyframes.set(keyframes.size() - 1, keyframe);
            } else {
                keyframes.add(keyframe);
            }
        }
        if (keyframes.isEmpty()) {
            return Animatable.of(fallback);
        }
        return Keyframes.fromZero(Keyframes.of(keyframes));
    }

    private static boolean curved(Object tangent) {
        double[] n = LottieJson.numbers(tangent);
        if (n != null) {
            for (double component : n) {
                if (component != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The easing towards the next keyframe: the out handle {@code o} of this keyframe and the in
     * handle {@code i} towards the next, as the control points of a cubic Bezier.
     */
    private Easing easing(Object keyframe) {
        if (LottieJson.flag(keyframe, "h")) {
            return Easing.HOLD;
        }
        Object out = get(keyframe, "o");
        Object in = get(keyframe, "i");
        double[] outX = LottieJson.numbers(get(out, "x"));
        double[] outY = LottieJson.numbers(get(out, "y"));
        double[] inX = LottieJson.numbers(get(in, "x"));
        double[] inY = LottieJson.numbers(get(in, "y"));
        if (empty(outX) || empty(outY) || empty(inX) || empty(inY)) {
            return Easing.LINEAR;
        }
        if (differ(outX) || differ(outY) || differ(inX) || differ(inY)) {
            warnings.add("Easing that differs per dimension is not supported; the first dimension is used",
                    "keyframe", "keyframes");
        }
        double x1 = Math.clamp(outX[0], 0.0, 1.0);
        double x2 = Math.clamp(inX[0], 0.0, 1.0);
        if (x1 == outY[0] && x2 == inY[0]) {
            return Easing.LINEAR;
        }
        return new Easing.CubicBezier(x1, outY[0], x2, inY[0]);
    }

    private static boolean empty(double[] values) {
        return values == null || values.length == 0 || Double.isNaN(values[0]);
    }

    private static boolean differ(double[] values) {
        for (double value : values) {
            if (value != values[0]) {
                return true;
            }
        }
        return false;
    }
}
