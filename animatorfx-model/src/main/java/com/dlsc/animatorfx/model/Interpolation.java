package com.dlsc.animatorfx.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Interpolation between two keyframe values.
 */
public final class Interpolation {

    private Interpolation() {
    }

    /**
     * Interpolates from {@code a} to {@code b}. {@code Double}, {@link Vec2} and {@link Rgba}
     * interpolate linearly, {@link Paint} component-wise when both paints have the same form and
     * stop count, {@link PathData} by morphing. Everything else holds: {@code a} for
     * {@code u < 1}, otherwise {@code b}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T lerp(T a, T b, double u) {
        Object result;
        if (a instanceof Double x && b instanceof Double y) {
            result = x + (y - x) * u;
        } else if (a instanceof Vec2 x && b instanceof Vec2 y) {
            result = x.lerp(y, u);
        } else if (a instanceof Rgba x && b instanceof Rgba y) {
            result = x.lerp(y, u);
        } else if (a instanceof Paint x && b instanceof Paint y) {
            result = paint(x, y, u);
        } else if (a instanceof PathData x && b instanceof PathData y) {
            result = PathMorph.morph(x, y, u);
        } else {
            result = hold(a, b, u);
        }
        return (T) result;
    }

    private static <T> T hold(T a, T b, double u) {
        return u < 1 ? a : b;
    }

    private static Paint paint(Paint a, Paint b, double u) {
        if (a instanceof Paint.Solid x && b instanceof Paint.Solid y) {
            return new Paint.Solid(x.color().lerp(y.color(), u));
        }
        if (a instanceof Paint.LinearGradient x && b instanceof Paint.LinearGradient y
                && x.stops().size() == y.stops().size()) {
            return new Paint.LinearGradient(x.start().lerp(y.start(), u), x.end().lerp(y.end(), u),
                    stops(x.stops(), y.stops(), u));
        }
        if (a instanceof Paint.RadialGradient x && b instanceof Paint.RadialGradient y
                && x.stops().size() == y.stops().size()) {
            return new Paint.RadialGradient(x.center().lerp(y.center(), u),
                    x.radius() + (y.radius() - x.radius()) * u, stops(x.stops(), y.stops(), u));
        }
        return hold(a, b, u);
    }

    private static List<Paint.Stop> stops(List<Paint.Stop> a, List<Paint.Stop> b, double u) {
        List<Paint.Stop> result = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            Paint.Stop x = a.get(i);
            Paint.Stop y = b.get(i);
            result.add(new Paint.Stop(x.offset() + (y.offset() - x.offset()) * u, x.color().lerp(y.color(), u)));
        }
        return result;
    }
}
