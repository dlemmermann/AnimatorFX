package com.dlsc.animatorfx.editor.ui;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalTime;
import java.util.Locale;

/**
 * The text formats of the toolbar and the status bar.
 */
public final class Formats {

    private Formats() {
    }

    /**
     * Seconds as {@code mm:ss.mmm}, rounded to the millisecond; negative and non-finite times show as zero.
     */
    public static String timecode(double seconds) {
        long millis = Double.isFinite(seconds) && seconds > 0 ? Math.round(seconds * 1000) : 0;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", millis / 60_000, millis / 1000 % 60, millis % 1000);
    }

    public static String zoom(double factor) {
        return Math.round(factor * 100) + "%";
    }

    public static String frameRate(double framesPerSecond) {
        return number(framesPerSecond) + " fps";
    }

    public static String size(double width, double height) {
        return number(width) + " × " + number(height);
    }

    public static String count(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    public static String selected(int count) {
        return count + " selected";
    }

    /**
     * "Saved" for a clean document, otherwise "Autosaved HH:mm" once a recovery copy has been
     * written and "Unsaved changes" before that.
     */
    public static String autosaveState(boolean dirty, LocalTime autosavedAt) {
        if (!dirty) {
            return "Saved";
        }
        return autosavedAt == null ? "Unsaved changes"
                : String.format(Locale.ROOT, "Autosaved %02d:%02d", autosavedAt.getHour(), autosavedAt.getMinute());
    }

    /** At most two decimals, none when the number is whole. */
    public static String number(double value) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
    }
}
