package com.dlsc.animatorfx.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Arc-length measurement of a path, based on flattening its segments. Fractions run from 0 to 1
 * along the whole path, contour after contour.
 */
public final class PathMeasure {

    /** A straight piece of a flattened segment, covering the segment parameters {@code t0..t1}. */
    private record Step(int contour, int segment, double t0, double t1, double start, double length) {
        double end() {
            return start + length;
        }
    }

    private record Location(Step step, double t) {
    }

    private record Piece(int contour, boolean whole, List<PathData.Vertex> vertices) {
    }

    private final PathData path;
    private final List<List<Cubic>> segments = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final double[] contourStart;
    private final double[] contourEnd;
    private final double length;

    public PathMeasure(PathData path) {
        this.path = path;
        int contours = path.contours().size();
        contourStart = new double[contours];
        contourEnd = new double[contours];
        double total = 0;
        for (int c = 0; c < contours; c++) {
            List<Cubic> contourSegments = Cubic.segments(path.contours().get(c));
            segments.add(contourSegments);
            contourStart[c] = total;
            for (int s = 0; s < contourSegments.size(); s++) {
                Cubic segment = contourSegments.get(s);
                int count = segment.steps();
                Vec2 previous = segment.p0();
                for (int i = 1; i <= count; i++) {
                    double t1 = (double) i / count;
                    Vec2 next = segment.pointAt(t1);
                    Step step = new Step(c, s, (double) (i - 1) / count, t1, total, Cubic.distance(previous, next));
                    steps.add(step);
                    total = step.end();
                    previous = next;
                }
            }
            contourEnd[c] = total;
        }
        length = total;
    }

    public double length() {
        return length;
    }

    /**
     * The point at the given fraction (clamped to {@code 0..1}) of the path length.
     */
    public Vec2 pointAt(double fraction) {
        if (steps.isEmpty()) {
            for (PathData.Contour contour : path.contours()) {
                if (!contour.vertices().isEmpty()) {
                    return contour.vertices().get(0).point();
                }
            }
            return Vec2.ZERO;
        }
        Location location = endingAt(distance(fraction));
        return segment(location.step()).pointAt(location.t());
    }

    /**
     * The direction of travel at the given fraction (clamped to {@code 0..1}) in degrees,
     * clockwise positive with 0 pointing along the positive x axis.
     */
    public double angleAt(double fraction) {
        if (steps.isEmpty()) {
            return 0;
        }
        Location location = endingAt(distance(fraction));
        Cubic segment = segment(location.step());
        Vec2 direction = segment.derivativeAt(location.t());
        if (Math.hypot(direction.x(), direction.y()) < 1e-9) {
            // the derivative vanishes where a handle is zero: use the flattened piece instead
            direction = segment.pointAt(location.step().t1()).minus(segment.pointAt(location.step().t0()));
        }
        return Math.toDegrees(Math.atan2(direction.y(), direction.x()));
    }

    /**
     * The part of the path between the fractions {@code start} and {@code end} (each clamped to
     * {@code 0..1}, in either order), shifted along the path by {@code offset}. A shifted range
     * that runs past the end of the path continues at its beginning; where that happens within a
     * single closed contour the result is one continuous open contour, otherwise two. Contours
     * that are covered completely are returned unchanged, partially covered ones become open.
     * Curves stay curves.
     */
    public PathData trim(double start, double end, double offset) {
        double low = Math.clamp(Math.min(start, end), 0.0, 1.0);
        double high = Math.clamp(Math.max(start, end), 0.0, 1.0);
        double epsilon = length * 1e-12;
        if (length <= 0 || (high - low) * length <= epsilon) {
            return PathData.EMPTY;
        }
        if (high - low >= 1) {
            return path;
        }
        double shift = offset - Math.floor(offset);
        double from = low + shift;
        double to = high + shift;
        if (from >= 1) {
            from -= 1;
            to -= 1;
        }
        List<Piece> pieces = new ArrayList<>();
        if (to <= 1) {
            collect(from * length, to * length, epsilon, pieces);
        } else {
            collect(from * length, length, epsilon, pieces);
            int wrapAt = pieces.size();
            collect(0, (to - 1) * length, epsilon, pieces);
            joinAcrossStart(pieces, wrapAt);
        }
        if (pieces.isEmpty()) {
            return PathData.EMPTY;
        }
        List<PathData.Contour> contours = new ArrayList<>(pieces.size());
        for (Piece piece : pieces) {
            contours.add(piece.whole() ? path.contours().get(piece.contour())
                    : new PathData.Contour(piece.vertices(), false));
        }
        return new PathData(contours);
    }

    private void collect(double from, double to, double epsilon, List<Piece> pieces) {
        for (int c = 0; c < contourStart.length; c++) {
            double s = Math.max(from, contourStart[c]);
            double e = Math.min(to, contourEnd[c]);
            if (e - s <= epsilon) {
                continue;
            }
            if (s <= contourStart[c] && e >= contourEnd[c]) {
                pieces.add(new Piece(c, true, List.of()));
            } else {
                pieces.add(new Piece(c, false, Cubic.vertices(chain(c, startingAt(s), endingAt(e)))));
            }
        }
    }

    private List<Cubic> chain(int contour, Location from, Location to) {
        List<Cubic> contourSegments = segments.get(contour);
        int first = from.step().segment();
        int last = to.step().segment();
        List<Cubic> chain = new ArrayList<>(last - first + 1);
        if (first == last) {
            chain.add(contourSegments.get(first).sub(from.t(), to.t()));
            return chain;
        }
        chain.add(contourSegments.get(first).sub(from.t(), 1));
        chain.addAll(contourSegments.subList(first + 1, last));
        chain.add(contourSegments.get(last).sub(0, to.t()));
        return chain;
    }

    /**
     * Merges the piece that ends at the end of a closed contour with the piece that continues at
     * its start.
     */
    private void joinAcrossStart(List<Piece> pieces, int wrapAt) {
        if (wrapAt == 0 || wrapAt == pieces.size()) {
            return;
        }
        Piece tail = pieces.get(wrapAt - 1);
        Piece head = pieces.get(wrapAt);
        if (tail.contour() != head.contour() || tail.whole() || head.whole()
                || !path.contours().get(tail.contour()).closed()) {
            return;
        }
        List<PathData.Vertex> joined = new ArrayList<>(tail.vertices());
        PathData.Vertex seamIn = joined.remove(joined.size() - 1);
        PathData.Vertex seamOut = head.vertices().get(0);
        joined.add(new PathData.Vertex(seamIn.point(), seamIn.in(), seamOut.out()));
        joined.addAll(head.vertices().subList(1, head.vertices().size()));
        pieces.set(wrapAt - 1, new Piece(tail.contour(), false, joined));
        pieces.remove(wrapAt);
    }

    private double distance(double fraction) {
        return Double.isNaN(fraction) ? 0 : Math.clamp(fraction, 0.0, 1.0) * length;
    }

    private Cubic segment(Step step) {
        return segments.get(step.contour()).get(step.segment());
    }

    /** The first step that reaches {@code distance}; at a joint this is the step ending there. */
    private Location endingAt(double distance) {
        int low = 0;
        int high = steps.size() - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (steps.get(mid).end() >= distance) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return location(steps.get(low), distance);
    }

    /** The last step that begins at or before {@code distance}; at a joint this is the step starting there. */
    private Location startingAt(double distance) {
        int low = 0;
        int high = steps.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (steps.get(mid).start() <= distance) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return location(steps.get(low), distance);
    }

    private static Location location(Step step, double distance) {
        if (step.length() <= 0) {
            return new Location(step, step.t0());
        }
        double u = Math.clamp((distance - step.start()) / step.length(), 0.0, 1.0);
        return new Location(step, step.t0() + (step.t1() - step.t0()) * u);
    }
}
