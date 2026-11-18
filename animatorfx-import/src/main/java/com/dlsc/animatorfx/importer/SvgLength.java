package com.dlsc.animatorfx.importer;

import java.util.Locale;

/**
 * Parses SVG lengths to pixels at 96 dpi.
 */
final class SvgLength {

    private SvgLength() {
    }

    /**
     * @param percentBase what 100 % stands for
     * @param warnings    told when the length is not finite or absurdly large; may be {@code null}
     * @return the length in pixels, or {@code fallback} if the text is missing or not a usable length
     */
    static double parse(String text, double percentBase, double fallback, Warnings warnings) {
        if (text == null) {
            return fallback;
        }
        String trimmed = text.trim();
        int end = SvgTransforms.numberEnd(trimmed, 0);
        if (end == 0) {
            return fallback;
        }
        double value = Double.parseDouble(trimmed.substring(0, end));
        double pixels = switch (trimmed.substring(end).trim().toLowerCase(Locale.ROOT)) {
            case "", "px" -> value;
            case "pt" -> value * 96 / 72;
            case "pc" -> value * 16;
            case "mm" -> value * 96 / 25.4;
            case "cm" -> value * 96 / 2.54;
            case "in" -> value * 96;
            case "em", "rem" -> value * 16;
            case "ex" -> value * 8;
            case "%" -> value / 100 * percentBase;
            default -> Double.NaN;
        };
        return Double.isNaN(pixels) ? fallback : usable(pixels, fallback, warnings);
    }

    private static double usable(double value, double fallback, Warnings warnings) {
        if (Limits.usable(value)) {
            return value;
        }
        if (warnings != null) {
            warnings.add("Lengths that are not finite or too large use their default", "length", "lengths");
        }
        return fallback;
    }

    static boolean isPercentage(String text) {
        return text != null && text.trim().endsWith("%");
    }

    /** A plain number or a percentage as a fraction; {@code fallback} for anything else. */
    static double fraction(String text, double fallback, Warnings warnings) {
        if (text == null) {
            return fallback;
        }
        String trimmed = text.trim();
        int end = SvgTransforms.numberEnd(trimmed, 0);
        if (end == 0) {
            return fallback;
        }
        double value = Double.parseDouble(trimmed.substring(0, end));
        String unit = trimmed.substring(end).trim();
        return unit.equals("%") ? usable(value / 100, fallback, warnings)
                : unit.isEmpty() ? usable(value, fallback, warnings) : fallback;
    }
}
