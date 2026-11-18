package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Keyframe;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.DoubleFunction;
import java.util.function.Function;

/**
 * Derives animated values from other animated values.
 */
final class Keyframes {

    private Keyframes() {
    }

    static <T> Keyframe<T> key(double time, T value, Easing easing) {
        return new Keyframe<>(UUID.randomUUID().toString(), time, value, easing);
    }

    /** A single keyframe does not move: it becomes a static value. */
    static <T> Animatable<T> of(List<Keyframe<T>> keyframes) {
        return keyframes.size() == 1 ? Animatable.of(keyframes.get(0).value()) : Animatable.keyframed(keyframes);
    }

    /**
     * Converts every value, keeping times and easings. Exact for conversions that are linear,
     * such as a change of unit.
     */
    static <T, R> Animatable<R> map(Animatable<T> source, Function<T, R> conversion) {
        if (!source.isAnimated()) {
            return Animatable.of(conversion.apply(source.at(0)));
        }
        List<Keyframe<R>> keyframes = new ArrayList<>();
        for (Keyframe<T> keyframe : source.keyframes()) {
            keyframes.add(key(keyframe.time(), conversion.apply(keyframe.value()), keyframe.easing()));
        }
        return Animatable.keyframed(keyframes);
    }

    /**
     * A value computed from several animated inputs. When the animated inputs share their key
     * times and easings, the result has the same keyframes, which is exact for values that
     * depend linearly on the inputs. Otherwise it is sampled at every key time of any input and
     * interpolated linearly in between, which is reported through {@code onSampled}.
     *
     * @param sampler the value at a time in seconds
     */
    static <R> Animatable<R> combine(DoubleFunction<R> sampler, Runnable onSampled, Animatable<?>... inputs) {
        List<Animatable<?>> animated = new ArrayList<>();
        for (Animatable<?> input : inputs) {
            if (input.isAnimated()) {
                animated.add(input);
            }
        }
        if (animated.isEmpty()) {
            return Animatable.of(sampler.apply(0));
        }
        List<? extends Keyframe<?>> first = animated.get(0).keyframes();
        List<Keyframe<R>> keyframes = new ArrayList<>();
        if (animated.stream().allMatch(input -> aligned(first, input.keyframes()))) {
            for (Keyframe<?> keyframe : first) {
                keyframes.add(key(keyframe.time(), sampler.apply(keyframe.time()), keyframe.easing()));
            }
            return Animatable.keyframed(keyframes);
        }
        TreeSet<Double> times = new TreeSet<>();
        for (Animatable<?> input : animated) {
            for (Keyframe<?> keyframe : input.keyframes()) {
                times.add(keyframe.time());
            }
        }
        for (double time : times) {
            keyframes.add(key(time, sampler.apply(time), Easing.LINEAR));
        }
        onSampled.run();
        return of(keyframes);
    }

    private static boolean aligned(List<? extends Keyframe<?>> a, List<? extends Keyframe<?>> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (Math.abs(a.get(i).time() - b.get(i).time()) > Animatable.TIME_EPSILON) {
                return false;
            }
            // the easing of the last keyframe leads nowhere
            if (i < a.size() - 1 && !a.get(i).easing().equals(b.get(i).easing())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cuts off what happens before time 0, where the model allows no keyframes. The value at 0
     * becomes the first keyframe, with the rest of the easing curve that was under way, so the
     * animation from 0 on is unchanged.
     */
    static <T> Animatable<T> fromZero(Animatable<T> source) {
        List<Keyframe<T>> keyframes = source.keyframes();
        if (keyframes.isEmpty() || keyframes.get(0).time() >= 0) {
            return source;
        }
        int next = 0;
        while (next < keyframes.size() && keyframes.get(next).time() < 0) {
            next++;
        }
        if (next == keyframes.size()) {
            return Animatable.of(keyframes.get(next - 1).value());
        }
        List<Keyframe<T>> result = new ArrayList<>();
        Keyframe<T> before = keyframes.get(next - 1);
        Keyframe<T> after = keyframes.get(next);
        if (after.time() > 0) {
            double progress = -before.time() / (after.time() - before.time());
            result.add(new Keyframe<>(before.id(), 0, source.at(0), rest(before.easing(), progress)));
        }
        result.addAll(keyframes.subList(next, keyframes.size()));
        return of(result);
    }

    /**
     * The easing that continues {@code easing} from {@code progress} (0..1) on: the remaining
     * part of the curve, stretched to run from (0,0) to (1,1) again.
     */
    static Easing rest(Easing easing, double progress) {
        if (!(easing instanceof Easing.CubicBezier curve)) {
            return easing;
        }
        // the curve parameter at which x reaches the progress; x is monotone
        double low = 0;
        double high = 1;
        for (int i = 0; i < 60; i++) {
            double mid = (low + high) / 2;
            if (bezier(curve.x1(), curve.x2(), mid) < progress) {
                low = mid;
            } else {
                high = mid;
            }
        }
        double t = (low + high) / 2;
        double startX = bezier(curve.x1(), curve.x2(), t);
        double startY = bezier(curve.y1(), curve.y2(), t);
        if (1 - startX < 1e-9 || Math.abs(1 - startY) < 1e-9) {
            return Easing.LINEAR;
        }
        // de Casteljau: the control points of the part from t to 1
        double[] x = split(curve.x1(), curve.x2(), t);
        double[] y = split(curve.y1(), curve.y2(), t);
        return new Easing.CubicBezier(
                Math.clamp((x[0] - startX) / (1 - startX), 0.0, 1.0), (y[0] - startY) / (1 - startY),
                Math.clamp((x[1] - startX) / (1 - startX), 0.0, 1.0), (y[1] - startY) / (1 - startY));
    }

    private static double bezier(double c1, double c2, double t) {
        double s = 1 - t;
        return 3 * s * s * t * c1 + 3 * s * t * t * c2 + t * t * t;
    }

    /** The two inner control points of the part from {@code t} to 1 of the curve 0, c1, c2, 1. */
    private static double[] split(double c1, double c2, double t) {
        double p12 = c1 + (c2 - c1) * t;
        double p23 = c2 + (1 - c2) * t;
        double p123 = p12 + (p23 - p12) * t;
        return new double[]{p123, p23};
    }
}
