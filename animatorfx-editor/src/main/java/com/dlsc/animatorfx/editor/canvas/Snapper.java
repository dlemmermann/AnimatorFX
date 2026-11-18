package com.dlsc.animatorfx.editor.canvas;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapping of a moving box or point to the edges and centres of other boxes. All values are in
 * the same coordinate space; the canvas uses view pixels, so the tolerance is a distance on screen.
 */
public final class Snapper {

    private static final double COINCIDE = 0.01;

    private Snapper() {
    }

    /**
     * A line to draw where something snapped.
     *
     * @param vertical whether the line runs along y at {@code x = position}
     * @param from     where the line starts along its direction
     * @param to       where it ends
     */
    public record Guide(boolean vertical, double position, double from, double to) {
    }

    /**
     * @param dx     how far to shift the moving thing along x to snap; 0 when nothing is near
     * @param guides the lines that the snapped thing lines up with
     */
    public record Result(double dx, double dy, List<Guide> guides) {

        public static final Result NONE = new Result(0, 0, List.of());

        public Result {
            guides = List.copyOf(guides);
        }
    }

    /**
     * Snaps the edges and the centre of a box to the edges and centres of the targets. Per axis
     * the nearest pair of lines within the tolerance wins.
     */
    public static Result snap(Box moving, List<Box> targets, double tolerance) {
        double dx = nearest(new double[]{moving.minX(), moving.centerX(), moving.maxX()}, targets, true, tolerance);
        double dy = nearest(new double[]{moving.minY(), moving.centerY(), moving.maxY()}, targets, false, tolerance);
        if (Double.isNaN(dx) && Double.isNaN(dy)) {
            return Result.NONE;
        }
        Box snapped = moving.translated(Double.isNaN(dx) ? 0 : dx, Double.isNaN(dy) ? 0 : dy);
        List<Guide> guides = new ArrayList<>();
        if (!Double.isNaN(dx)) {
            guides(new double[]{snapped.minX(), snapped.centerX(), snapped.maxX()}, snapped, targets, true, guides);
        }
        if (!Double.isNaN(dy)) {
            guides(new double[]{snapped.minY(), snapped.centerY(), snapped.maxY()}, snapped, targets, false, guides);
        }
        return new Result(Double.isNaN(dx) ? 0 : dx, Double.isNaN(dy) ? 0 : dy, guides);
    }

    /**
     * Snaps a single point, such as a dragged handle, on the axes asked for.
     */
    public static Result snapPoint(double x, double y, boolean snapX, boolean snapY, List<Box> targets,
                                   double tolerance) {
        double dx = snapX ? nearest(new double[]{x}, targets, true, tolerance) : Double.NaN;
        double dy = snapY ? nearest(new double[]{y}, targets, false, tolerance) : Double.NaN;
        if (Double.isNaN(dx) && Double.isNaN(dy)) {
            return Result.NONE;
        }
        double sx = x + (Double.isNaN(dx) ? 0 : dx);
        double sy = y + (Double.isNaN(dy) ? 0 : dy);
        Box point = new Box(sx, sy, sx, sy);
        List<Guide> guides = new ArrayList<>();
        if (!Double.isNaN(dx)) {
            guides(new double[]{sx}, point, targets, true, guides);
        }
        if (!Double.isNaN(dy)) {
            guides(new double[]{sy}, point, targets, false, guides);
        }
        return new Result(Double.isNaN(dx) ? 0 : dx, Double.isNaN(dy) ? 0 : dy, guides);
    }

    private static double[] lines(Box box, boolean x) {
        return x ? new double[]{box.minX(), box.centerX(), box.maxX()}
                : new double[]{box.minY(), box.centerY(), box.maxY()};
    }

    /** The smallest shift that puts one of the lines onto a target line; NaN when none is within the tolerance. */
    private static double nearest(double[] moving, List<Box> targets, boolean x, double tolerance) {
        double best = Double.NaN;
        for (Box target : targets) {
            for (double line : lines(target, x)) {
                for (double own : moving) {
                    double delta = line - own;
                    if (Math.abs(delta) <= tolerance && (Double.isNaN(best) || Math.abs(delta) < Math.abs(best))) {
                        best = delta;
                    }
                }
            }
        }
        return best;
    }

    private static void guides(double[] snapped, Box box, List<Box> targets, boolean x, List<Guide> out) {
        for (double own : snapped) {
            double from = Double.NaN;
            double to = Double.NaN;
            for (Box target : targets) {
                for (double line : lines(target, x)) {
                    if (Math.abs(line - own) <= COINCIDE) {
                        double min = Math.min(x ? box.minY() : box.minX(), x ? target.minY() : target.minX());
                        double max = Math.max(x ? box.maxY() : box.maxX(), x ? target.maxY() : target.maxX());
                        from = Double.isNaN(from) ? min : Math.min(from, min);
                        to = Double.isNaN(to) ? max : Math.max(to, max);
                    }
                }
            }
            if (!Double.isNaN(from) && out.stream().noneMatch(g -> g.vertical() == x && Math.abs(g.position() - own) <= COINCIDE)) {
                out.add(new Guide(x, own, from, to));
            }
        }
    }
}
