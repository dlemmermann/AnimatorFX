package com.dlsc.animatorfx.importer;

/**
 * What an import may produce. A small file can ask for an enormous document (references that
 * multiply, chains without end, numbers beyond any drawing); one instance keeps one import
 * within bounds and reports, once, when it had to stop.
 */
final class Limits {

    /** The most layers one import produces. */
    static final int MAX_LAYERS = 20_000;

    /** The deepest nesting of layers, and of the references that are expanded into them. */
    static final int MAX_DEPTH = 64;

    /** Coordinates, sizes and other values beyond this magnitude are treated as broken. */
    static final double MAX_MAGNITUDE = 1e9;

    /** The most points of a star and sides of a polygon. */
    static final int MAX_POINTS = 1000;

    private final Warnings warnings;
    private int layers;
    private int depth;
    private boolean stopped;

    Limits(Warnings warnings) {
        this.warnings = warnings;
    }

    /** Whether the value is a finite number of a magnitude a document can have. */
    static boolean usable(double value) {
        return Math.abs(value) <= MAX_MAGNITUDE;
    }

    static boolean usable(double[] values) {
        for (double value : values) {
            if (!usable(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Asks for one more layer. Every layer that ends up in the document must be asked for,
     * whatever it is there for: content, a group that carries a parent's transform, a mask.
     *
     * @return {@code false} once the import has produced all the layers it may; nothing more
     *         must be converted then
     */
    boolean layer() {
        if (layers >= MAX_LAYERS) {
            stopped = true;
            stopped(warnings);
            return false;
        }
        layers++;
        return true;
    }

    /** Whether a layer has been refused: what was being built at that moment is incomplete. */
    boolean stopped() {
        return stopped;
    }

    static void stopped(Warnings warnings) {
        warnings.once("Import stopped at " + MAX_LAYERS + " layers; the rest of the file was skipped");
    }

    /**
     * Goes one level deeper, into the content of a layer; pair with {@link #leave()} when it
     * returns {@code true}.
     *
     * @return {@code false} when the content would lie deeper than the limit: it must be skipped
     */
    boolean enter() {
        if (depth >= MAX_DEPTH - 1) {
            tooDeep();
            return false;
        }
        depth++;
        return true;
    }

    void leave() {
        depth--;
    }

    void tooDeep() {
        tooDeep(warnings);
    }

    static void tooDeep(Warnings warnings) {
        warnings.once("Nesting deeper than " + MAX_DEPTH + " levels is not supported; the deeper content was"
                + " skipped");
    }
}
