package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Easing.CubicBezier;
import com.dlsc.animatorfx.model.Vec2;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * The geometry of the easing curve view and the conversions around cubic-bezier easings. The
 * view shows time from 0 to 1 across its width and progress from {@link #Y_MIN} to
 * {@link #Y_MAX} bottom to top, so handles can overshoot the unit square.
 *
 * @param width   the width of the view in pixels
 * @param height  the height of the view in pixels
 * @param padding the room around the plotted range
 */
public record EasingCurve(double width, double height, double padding) {

    public static final double Y_MIN = -0.5;
    public static final double Y_MAX = 1.5;
    /** How close, in pixels, the pointer has to be to grab a handle. */
    public static final double HANDLE_REACH = 9;

    private static final CubicBezier LINEAR = new CubicBezier(0, 0, 1, 1);

    public double x(double time) {
        return padding + time * (width - 2 * padding);
    }

    public double y(double progress) {
        return padding + (Y_MAX - progress) / (Y_MAX - Y_MIN) * (height - 2 * padding);
    }

    /** The handle value under a pixel, with x limited to {@code 0..1} and y to the overshoot range. */
    public Vec2 toValue(double px, double py) {
        double time = (px - padding) / (width - 2 * padding);
        double progress = Y_MAX - (py - padding) / (height - 2 * padding) * (Y_MAX - Y_MIN);
        return new Vec2(Math.clamp(time, 0.0, 1.0), Math.clamp(progress, Y_MIN, Y_MAX));
    }

    /**
     * @return 0 or 1 for the first or second control handle under the pixel, the nearer one when
     * both are in reach; -1 for none
     */
    public int handleAt(CubicBezier bezier, double px, double py) {
        double first = Math.hypot(x(bezier.x1()) - px, y(bezier.y1()) - py);
        double second = Math.hypot(x(bezier.x2()) - px, y(bezier.y2()) - py);
        if (Math.min(first, second) > HANDLE_REACH) {
            return -1;
        }
        return first <= second ? 0 : 1;
    }

    /** The easing with one control handle moved; the value is limited and rounded to three decimals. */
    public static CubicBezier withHandle(CubicBezier bezier, int handle, Vec2 value) {
        double x = round(Math.clamp(value.x(), 0.0, 1.0));
        double y = round(Math.clamp(value.y(), Y_MIN, Y_MAX));
        return handle == 0 ? new CubicBezier(x, y, bezier.x2(), bezier.y2())
                : new CubicBezier(bezier.x1(), bezier.y1(), x, y);
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    /** The cubic bezier that draws the easing; {@code null} for a hold, which has no curve. */
    public static CubicBezier asBezier(Easing easing) {
        return switch (easing) {
            case Easing.Linear linear -> LINEAR;
            case Easing.Hold hold -> null;
            case CubicBezier bezier -> bezier;
        };
    }

    /** "Linear", "Hold", the name of the matching preset, or "Custom". */
    public static String name(Easing easing) {
        if (easing instanceof Easing.Hold) {
            return "Hold";
        }
        CubicBezier bezier = asBezier(easing);
        if (same(bezier, LINEAR)) {
            return "Linear";
        }
        for (Map.Entry<String, Easing> preset : Easing.presets().entrySet()) {
            if (preset.getValue() instanceof CubicBezier candidate && same(bezier, candidate)) {
                return preset.getKey();
            }
        }
        return "Custom";
    }

    private static boolean same(CubicBezier a, CubicBezier b) {
        return Math.abs(a.x1() - b.x1()) < 1e-9 && Math.abs(a.y1() - b.y1()) < 1e-9
                && Math.abs(a.x2() - b.x2()) < 1e-9 && Math.abs(a.y2() - b.y2()) < 1e-9;
    }

    /** {@code cubic-bezier(x1, y1, x2, y2)} with at most three decimals. */
    public static String format(CubicBezier bezier) {
        return "cubic-bezier(" + PropertyUnits.number(bezier.x1(), 3) + ", " + PropertyUnits.number(bezier.y1(), 3)
                + ", " + PropertyUnits.number(bezier.x2(), 3) + ", " + PropertyUnits.number(bezier.y2(), 3) + ")";
    }

    /**
     * Reads four numbers, with or without the {@code cubic-bezier(...)} around them, separated
     * by commas or spaces. The x values are limited to {@code 0..1}, the y values to the
     * overshoot range.
     */
    public static Optional<CubicBezier> parse(String text) {
        String body = text.strip();
        if (body.startsWith("cubic-bezier(") && body.endsWith(")")) {
            body = body.substring("cubic-bezier(".length(), body.length() - 1).strip();
        }
        String[] parts = body.split("[\\s,]+");
        if (parts.length != 4) {
            return Optional.empty();
        }
        double[] v = new double[4];
        try {
            for (int i = 0; i < 4; i++) {
                v[i] = Double.parseDouble(parts[i]);
                if (!Double.isFinite(v[i])) {
                    return Optional.empty();
                }
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(new CubicBezier(Math.clamp(v[0], 0.0, 1.0), Math.clamp(v[1], Y_MIN, Y_MAX),
                Math.clamp(v[2], 0.0, 1.0), Math.clamp(v[3], Y_MIN, Y_MAX)));
    }

    /** The easing that all of them share; empty for none or for a mix. */
    public static Optional<Easing> common(Collection<Easing> easings) {
        Easing first = null;
        for (Easing easing : easings) {
            if (first == null) {
                first = easing;
            } else if (!first.equals(easing)) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(first);
    }
}
