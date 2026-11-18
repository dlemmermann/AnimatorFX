package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.Vec2;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How the numbers of a property are shown: the stored value times a factor, with a unit suffix.
 * Scale, opacity and other fractions are stored as {@code 0..1} and shown in percent.
 *
 * @param factor the displayed number divided by the stored one
 * @param suffix {@code px}, {@code %} or {@code °}
 */
public record PropertyUnits(double factor, String suffix) {

    public static final PropertyUnits PIXELS = new PropertyUnits(1, "px");
    public static final PropertyUnits PERCENT = new PropertyUnits(100, "%");
    public static final PropertyUnits DEGREES = new PropertyUnits(1, "°");
    public static final PropertyUnits SECONDS = new PropertyUnits(1, "s");
    public static final PropertyUnits NONE = new PropertyUnits(1, "");

    /** The units of an animatable property, by its {@code LayerProperties} key. */
    public static PropertyUnits of(String propertyKey) {
        String member = propertyKey.substring(propertyKey.lastIndexOf('.') + 1);
        return switch (propertyKey) {
            case "transform.scale", "opacity", "motionPath.progress" -> PERCENT;
            case "transform.rotation", "transform.skew" -> DEGREES;
            default -> propertyKey.startsWith("trim.") || member.equals("level") || member.equals("spread")
                    ? PERCENT : PIXELS;
        };
    }

    /**
     * A short read-out of a property value for the timeline: numbers in display units (pixels
     * without their suffix), colours as hex, gradients as their kind and stop count.
     */
    public static String text(String propertyKey, Object value) {
        PropertyUnits units = of(propertyKey);
        String suffix = units == PIXELS ? "" : units.suffix();
        return switch (value) {
            case Double number -> number(number * units.factor(), 1) + suffix;
            case Vec2 vec -> number(vec.x() * units.factor(), 1) + ", " + number(vec.y() * units.factor(), 1) + suffix;
            case Rgba color -> hex(color);
            case Paint.None none -> "none";
            case Paint.Solid solid -> hex(solid.color());
            case Paint.LinearGradient linear -> "linear " + linear.stops().size();
            case Paint.RadialGradient radial -> "radial " + radial.stops().size();
            case PathData path -> path.contours().stream().mapToInt(c -> c.vertices().size()).sum() + " pts";
            default -> String.valueOf(value);
        };
    }

    /** {@code #RRGGBB}, or {@code #RRGGBBAA} for a colour that is not opaque. */
    public static String hex(Rgba color) {
        String hex = color.toHex();
        return hex.endsWith("FF") ? hex.substring(0, 7) : hex;
    }

    /** The number rounded to at most that many decimals, without trailing zeros or a negative zero. */
    public static String number(double value, int maxDecimals) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.signum() == 0) {
            return "0";
        }
        return rounded.toPlainString();
    }
}
