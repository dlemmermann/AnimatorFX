package com.dlsc.animatorfx.editor.inspector.fields;

import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.model.Rgba;
import java.util.Optional;

/**
 * Hex notation and HSV conversion for the colour fields.
 */
public final class HexColors {

    private HexColors() {
    }

    /**
     * Reads {@code #RRGGBB} or {@code #RRGGBBAA}; the hash may be missing, and the short forms
     * {@code #RGB} and {@code #RGBA} are accepted too.
     */
    public static Optional<Rgba> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String hex = text.strip();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() == 3 || hex.length() == 4) {
            StringBuilder wide = new StringBuilder();
            for (char c : hex.toCharArray()) {
                wide.append(c).append(c);
            }
            hex = wide.toString();
        }
        if (hex.length() != 6 && hex.length() != 8) {
            return Optional.empty();
        }
        try {
            return Optional.of(Rgba.parse("#" + hex));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** {@code #RRGGBB}, or {@code #RRGGBBAA} for a colour that is not opaque. */
    public static String format(Rgba color) {
        return PropertyUnits.hex(color);
    }

    /**
     * @return hue in degrees {@code 0..360}, saturation and value in {@code 0..1}
     */
    public static double[] toHsv(Rgba color) {
        double max = Math.max(color.r(), Math.max(color.g(), color.b()));
        double min = Math.min(color.r(), Math.min(color.g(), color.b()));
        double delta = max - min;
        double hue;
        if (delta == 0) {
            hue = 0;
        } else if (max == color.r()) {
            hue = 60 * (((color.g() - color.b()) / delta) % 6);
        } else if (max == color.g()) {
            hue = 60 * ((color.b() - color.r()) / delta + 2);
        } else {
            hue = 60 * ((color.r() - color.g()) / delta + 4);
        }
        if (hue < 0) {
            hue += 360;
        }
        return new double[]{hue, max == 0 ? 0 : delta / max, max};
    }

    public static Rgba fromHsv(double hue, double saturation, double value, double alpha) {
        double h = ((hue % 360) + 360) % 360 / 60;
        double s = Math.clamp(saturation, 0.0, 1.0);
        double v = Math.clamp(value, 0.0, 1.0);
        double c = v * s;
        double x = c * (1 - Math.abs(h % 2 - 1));
        double m = v - c;
        double[] rgb = switch ((int) h) {
            case 0 -> new double[]{c, x, 0};
            case 1 -> new double[]{x, c, 0};
            case 2 -> new double[]{0, c, x};
            case 3 -> new double[]{0, x, c};
            case 4 -> new double[]{x, 0, c};
            default -> new double[]{c, 0, x};
        };
        return new Rgba(rgb[0] + m, rgb[1] + m, rgb[2] + m, Math.clamp(alpha, 0.0, 1.0));
    }
}
