package com.dlsc.animatorfx.model;

/**
 * A colour with components in {@code 0..1}.
 */
public record Rgba(double r, double g, double b, double a) {

    public static final Rgba BLACK = new Rgba(0, 0, 0, 1);
    public static final Rgba WHITE = new Rgba(1, 1, 1, 1);
    public static final Rgba TRANSPARENT = new Rgba(0, 0, 0, 0);

    /**
     * Parses {@code #RRGGBB} (opaque) or {@code #RRGGBBAA}.
     *
     * @throws IllegalArgumentException if the text has any other form
     */
    public static Rgba parse(String hex) {
        if ((hex.length() != 7 && hex.length() != 9) || hex.charAt(0) != '#') {
            throw new IllegalArgumentException("Expected #RRGGBB or #RRGGBBAA but got: " + hex);
        }
        double[] c = {0, 0, 0, 1};
        for (int i = 0; i < (hex.length() - 1) / 2; i++) {
            int high = Character.digit(hex.charAt(1 + 2 * i), 16);
            int low = Character.digit(hex.charAt(2 + 2 * i), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Expected #RRGGBB or #RRGGBBAA but got: " + hex);
            }
            c[i] = (high * 16 + low) / 255.0;
        }
        return new Rgba(c[0], c[1], c[2], c[3]);
    }

    /**
     * Returns the colour as upper-case {@code #RRGGBBAA}; components outside {@code 0..1} are clamped.
     */
    public String toHex() {
        return String.format("#%02X%02X%02X%02X", channel(r), channel(g), channel(b), channel(a));
    }

    /**
     * Interpolates linearly in RGBA. The result is clamped to {@code 0..1}, so overshooting
     * easings do not produce invalid colours.
     */
    public Rgba lerp(Rgba o, double u) {
        return new Rgba(mix(r, o.r, u), mix(g, o.g, u), mix(b, o.b, u), mix(a, o.a, u));
    }

    private static double mix(double from, double to, double u) {
        return Math.clamp(from + (to - from) * u, 0.0, 1.0);
    }

    private static int channel(double value) {
        return (int) Math.round(Math.clamp(value, 0.0, 1.0) * 255);
    }
}
