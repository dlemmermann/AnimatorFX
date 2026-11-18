package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.doc.Matrices;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.PathData.Contour;
import com.dlsc.animatorfx.model.PathData.Vertex;
import com.dlsc.animatorfx.model.Vec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Editing operations on Bezier paths, for the pen tool and path editing. Everything is pure: a
 * path goes in, a new path comes out, and an operation that changes nothing returns the path it
 * was given. References to contours, vertices or segments that the path does not have are ignored.
 *
 * <p>Segment {@code i} of a contour runs from vertex {@code i} to vertex {@code i + 1}; the last
 * segment of a closed contour runs from the last vertex back to the first. A segment without
 * handles is a straight line, and its parameter is proportional to distance.
 */
public final class PathOps {

    private static final int SAMPLES = 24;
    private static final double EPSILON = 1e-9;

    private PathOps() {
    }

    /** A vertex by the index of its contour and its index in the contour. */
    public record VertexRef(int contour, int index) {
    }

    /** One of the two handles of a vertex. */
    public enum Tangent {IN, OUT}

    /** What happens to the other handle of a vertex when one is set. */
    public enum TangentMode {
        /** The other handle mirrors the one that is set. */
        SYMMETRIC,
        /** The other handle stays opposite and keeps its length. */
        ALIGNED,
        /** The other handle stays as it is. */
        BROKEN
    }

    /**
     * The point of a path that is nearest to some point.
     *
     * @param segment  the index of the segment in its contour
     * @param t        the parameter on the segment, {@code 0..1}
     * @param distance how far the point is from the path
     */
    public record Hit(int contour, int segment, double t, double distance, Vec2 point) {
    }

    private record Segment(Vec2 p0, Vec2 p1, Vec2 p2, Vec2 p3) {

        static Segment between(Vertex from, Vertex to) {
            return new Segment(from.point(), from.point().plus(from.out()), to.point().plus(to.in()), to.point());
        }

        boolean isLine() {
            return p1.equals(p0) && p2.equals(p3);
        }

        Vec2 at(double t) {
            if (isLine()) {
                return p0.lerp(p3, t);
            }
            double s = 1 - t;
            double a = s * s * s;
            double b = 3 * s * s * t;
            double c = 3 * s * t * t;
            double d = t * t * t;
            return new Vec2(a * p0.x() + b * p1.x() + c * p2.x() + d * p3.x(),
                    a * p0.y() + b * p1.y() + c * p2.y() + d * p3.y());
        }
    }

    // ------------------------------------------------------------------ reading

    public static int segmentCount(Contour contour) {
        int count = contour.vertices().size();
        return count < 2 ? 0 : contour.closed() ? count : count - 1;
    }

    public static Optional<Vertex> vertex(PathData path, VertexRef ref) {
        if (ref.contour() < 0 || ref.contour() >= path.contours().size()) {
            return Optional.empty();
        }
        List<Vertex> vertices = path.contours().get(ref.contour()).vertices();
        return ref.index() < 0 || ref.index() >= vertices.size() ? Optional.empty() : Optional.of(vertices.get(ref.index()));
    }

    private static Segment segment(PathData path, int contour, int segment) {
        if (contour < 0 || contour >= path.contours().size()) {
            return null;
        }
        Contour c = path.contours().get(contour);
        if (segment < 0 || segment >= segmentCount(c)) {
            return null;
        }
        List<Vertex> v = c.vertices();
        return Segment.between(v.get(segment), v.get((segment + 1) % v.size()));
    }

    /**
     * The point at a parameter of a segment.
     *
     * @throws IllegalArgumentException if the path has no such segment
     */
    public static Vec2 pointAt(PathData path, int contour, int segment, double t) {
        Segment s = segment(path, contour, segment);
        if (s == null) {
            throw new IllegalArgumentException("No segment " + segment + " in contour " + contour);
        }
        return s.at(t);
    }

    /** Whether the vertex has no handles. */
    public static boolean isCorner(Vertex vertex) {
        return isZero(vertex.in()) && isZero(vertex.out());
    }

    /** Whether the vertex has two handles that point in opposite directions, so the outline has no kink. */
    public static boolean isSmooth(Vertex vertex) {
        Vec2 in = vertex.in();
        Vec2 out = vertex.out();
        double lengths = length(in) * length(out);
        if (!(lengths > 0)) {
            return false;
        }
        double cross = in.x() * out.y() - in.y() * out.x();
        double dot = in.x() * out.x() + in.y() * out.y();
        return dot < 0 && Math.abs(cross) <= 1e-6 * lengths;
    }

    /**
     * How a drag of one handle of the vertex treats the other handle: mirrored when the two
     * mirror each other now, kept opposite when the vertex is smooth, left alone otherwise.
     */
    public static TangentMode dragMode(Vertex vertex) {
        if (!isSmooth(vertex)) {
            return TangentMode.BROKEN;
        }
        return Math.abs(length(vertex.in()) - length(vertex.out())) <= 1e-6 * length(vertex.out())
                ? TangentMode.SYMMETRIC : TangentMode.ALIGNED;
    }

    /**
     * The box around the outline itself, which can be smaller than the box around its handles;
     * {@code null} for a path without vertices.
     */
    public static Box bounds(PathData path) {
        List<Vec2> points = new ArrayList<>();
        for (int c = 0; c < path.contours().size(); c++) {
            Contour contour = path.contours().get(c);
            for (Vertex vertex : contour.vertices()) {
                points.add(vertex.point());
            }
            for (int s = 0; s < segmentCount(contour); s++) {
                Segment segment = segment(path, c, s);
                if (!segment.isLine()) {
                    extremes(segment, true, points);
                    extremes(segment, false, points);
                }
            }
        }
        return Box.around(points);
    }

    /** Adds the points where the segment turns around along one axis. */
    private static void extremes(Segment s, boolean x, List<Vec2> out) {
        double p0 = x ? s.p0().x() : s.p0().y();
        double p1 = x ? s.p1().x() : s.p1().y();
        double p2 = x ? s.p2().x() : s.p2().y();
        double p3 = x ? s.p3().x() : s.p3().y();
        double a = -p0 + 3 * p1 - 3 * p2 + p3;
        double b = 2 * (p0 - 2 * p1 + p2);
        double c = p1 - p0;
        if (Math.abs(a) < 1e-12) {
            if (Math.abs(b) > 1e-12) {
                addInside(s, -c / b, out);
            }
            return;
        }
        double discriminant = b * b - 4 * a * c;
        if (discriminant >= 0) {
            double root = Math.sqrt(discriminant);
            addInside(s, (-b + root) / (2 * a), out);
            addInside(s, (-b - root) / (2 * a), out);
        }
    }

    private static void addInside(Segment s, double t, List<Vec2> out) {
        if (t > 0 && t < 1) {
            out.add(s.at(t));
        }
    }

    /**
     * The point of the path nearest to the given point; empty for a path without segments.
     */
    public static Optional<Hit> nearest(PathData path, Vec2 point) {
        Hit best = null;
        for (int c = 0; c < path.contours().size(); c++) {
            for (int s = 0; s < segmentCount(path.contours().get(c)); s++) {
                Segment segment = segment(path, c, s);
                double t = nearestParameter(segment, point);
                Vec2 at = segment.at(t);
                double distance = distance(at, point);
                if (best == null || distance < best.distance()) {
                    best = new Hit(c, s, t, distance, at);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static double nearestParameter(Segment segment, Vec2 point) {
        if (segment.isLine()) {
            Vec2 along = segment.p3().minus(segment.p0());
            double lengthSquared = along.x() * along.x() + along.y() * along.y();
            if (!(lengthSquared > 0)) {
                return 0;
            }
            Vec2 to = point.minus(segment.p0());
            return Math.clamp((to.x() * along.x() + to.y() * along.y()) / lengthSquared, 0, 1);
        }
        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i <= SAMPLES; i++) {
            double distance = distance(segment.at((double) i / SAMPLES), point);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        double low = Math.max(0, (bestIndex - 1.0) / SAMPLES);
        double high = Math.min(1, (bestIndex + 1.0) / SAMPLES);
        for (int i = 0; i < 40; i++) {
            double a = low + (high - low) / 3;
            double b = high - (high - low) / 3;
            if (distance(segment.at(a), point) <= distance(segment.at(b), point)) {
                high = b;
            } else {
                low = a;
            }
        }
        return (low + high) / 2;
    }

    // ------------------------------------------------------------------ vertices

    /**
     * Splits a segment at a parameter with a new vertex. The outline stays exactly as it was;
     * the handles of the two neighbours that face the new vertex get shorter.
     */
    public static PathData insertVertex(PathData path, int contour, int segment, double t) {
        Segment s = segment(path, contour, segment);
        if (s == null) {
            return path;
        }
        List<Vertex> vertices = new ArrayList<>(path.contours().get(contour).vertices());
        int next = (segment + 1) % vertices.size();
        Vertex from = vertices.get(segment);
        Vertex to = vertices.get(next);
        Vertex inserted;
        if (s.isLine()) {
            inserted = new Vertex(s.p0().lerp(s.p3(), t), Vec2.ZERO, Vec2.ZERO);
        } else {
            Vec2 q0 = s.p0().lerp(s.p1(), t);
            Vec2 q1 = s.p1().lerp(s.p2(), t);
            Vec2 q2 = s.p2().lerp(s.p3(), t);
            Vec2 r0 = q0.lerp(q1, t);
            Vec2 r1 = q1.lerp(q2, t);
            Vec2 middle = r0.lerp(r1, t);
            inserted = new Vertex(middle, r0.minus(middle), r1.minus(middle));
            vertices.set(segment, new Vertex(from.point(), from.in(), q0.minus(from.point())));
            vertices.set(next, new Vertex(to.point(), q2.minus(to.point()), to.out()));
        }
        vertices.add(segment + 1, inserted);
        return withContour(path, contour, new Contour(vertices, path.contours().get(contour).closed()));
    }

    /**
     * Removes vertices. A contour that is left with fewer than two vertices goes away; a closed
     * contour that is left with two becomes an open one.
     */
    public static PathData deleteVertices(PathData path, Collection<VertexRef> refs) {
        List<Contour> contours = new ArrayList<>();
        boolean changed = false;
        for (int c = 0; c < path.contours().size(); c++) {
            Contour contour = path.contours().get(c);
            List<Vertex> kept = new ArrayList<>();
            for (int i = 0; i < contour.vertices().size(); i++) {
                if (!refs.contains(new VertexRef(c, i))) {
                    kept.add(contour.vertices().get(i));
                }
            }
            if (kept.size() == contour.vertices().size()) {
                contours.add(contour);
                continue;
            }
            changed = true;
            if (kept.size() >= 2) {
                contours.add(new Contour(kept, contour.closed() && kept.size() >= 3));
            }
        }
        return changed ? new PathData(contours) : path;
    }

    public static PathData moveVertices(PathData path, Collection<VertexRef> refs, Vec2 delta) {
        if (isZero(delta)) {
            return path;
        }
        PathData result = path;
        for (VertexRef ref : refs) {
            result = mapVertex(result, ref, v -> new Vertex(v.point().plus(delta), v.in(), v.out()));
        }
        return result;
    }

    /** Replaces a vertex. */
    public static PathData withVertex(PathData path, VertexRef ref, Vertex vertex) {
        return mapVertex(path, ref, old -> vertex);
    }

    /**
     * Sets one handle of a vertex, relative to its point, and adjusts the other one as the mode says.
     */
    public static PathData setTangent(PathData path, VertexRef ref, Tangent which, Vec2 relative, TangentMode mode) {
        return mapVertex(path, ref, v -> {
            Vec2 other = which == Tangent.IN ? v.out() : v.in();
            double length = length(relative);
            if (mode == TangentMode.SYMMETRIC) {
                other = scaled(relative, -1);
            } else if (mode == TangentMode.ALIGNED && length > 0) {
                other = scaled(relative, -length(other) / length);
            }
            return which == Tangent.IN ? new Vertex(v.point(), relative, other) : new Vertex(v.point(), other, relative);
        });
    }

    /**
     * Turns a corner into a smooth vertex and any other vertex into a corner. The handles of a
     * smooth vertex run along the line between its two neighbours, each a third as long as the
     * distance to the neighbour on its side; an end vertex of an open contour points at its only
     * neighbour.
     */
    public static PathData toggleSmooth(PathData path, VertexRef ref) {
        Vertex vertex = vertex(path, ref).orElse(null);
        if (vertex == null) {
            return path;
        }
        if (!isCorner(vertex)) {
            return withVertex(path, ref, new Vertex(vertex.point(), Vec2.ZERO, Vec2.ZERO));
        }
        Contour contour = path.contours().get(ref.contour());
        List<Vertex> v = contour.vertices();
        int count = v.size();
        Vec2 point = vertex.point();
        Vec2 previous = ref.index() > 0 ? v.get(ref.index() - 1).point()
                : contour.closed() && count > 2 ? v.get(count - 1).point() : null;
        Vec2 next = ref.index() < count - 1 ? v.get(ref.index() + 1).point()
                : contour.closed() && count > 2 ? v.get(0).point() : null;
        if (previous == null && next == null) {
            return path;
        }
        Vec2 chord = previous == null ? next.minus(point) : next == null ? point.minus(previous) : next.minus(previous);
        double chordLength = length(chord);
        if (!(chordLength > 0)) {
            return path;
        }
        Vec2 direction = chord.times(1 / chordLength);
        double outLength = distance(point, next == null ? previous : next) / 3;
        double inLength = distance(point, previous == null ? next : previous) / 3;
        return withVertex(path, ref, new Vertex(point, scaled(direction, -inLength), scaled(direction, outLength)));
    }

    // ------------------------------------------------------------------ contours, whole paths

    public static PathData setClosed(PathData path, int contour, boolean closed) {
        if (contour < 0 || contour >= path.contours().size() || path.contours().get(contour).closed() == closed) {
            return path;
        }
        return withContour(path, contour, new Contour(path.contours().get(contour).vertices(), closed));
    }

    /** Adds a vertex at the end of the last contour; an empty path gets an open contour. */
    public static PathData append(PathData path, Vertex vertex) {
        List<Contour> contours = new ArrayList<>(path.contours());
        if (contours.isEmpty()) {
            contours.add(new Contour(List.of(vertex), false));
        } else {
            Contour last = contours.remove(contours.size() - 1);
            List<Vertex> vertices = new ArrayList<>(last.vertices());
            vertices.add(vertex);
            contours.add(new Contour(vertices, last.closed()));
        }
        return new PathData(contours);
    }

    /** Removes the last vertex of the last contour, and the contour with its last vertex. */
    public static PathData removeLast(PathData path) {
        if (path.contours().isEmpty()) {
            return path;
        }
        List<Contour> contours = new ArrayList<>(path.contours());
        Contour last = contours.remove(contours.size() - 1);
        if (last.vertices().size() > 1) {
            contours.add(new Contour(last.vertices().subList(0, last.vertices().size() - 1), last.closed()));
        }
        return new PathData(contours);
    }

    public static PathData translate(PathData path, Vec2 delta) {
        return isZero(delta) ? path : mapAll(path, v -> new Vertex(v.point().plus(delta), v.in(), v.out()));
    }

    /**
     * The path in another coordinate space: points go through the matrix, handles through its
     * linear part. With a layer's local-to-view matrix this gives the path in view pixels, and
     * with the inverse the way back.
     */
    public static PathData transform(PathData path, double[] matrix) {
        return mapAll(path, v -> new Vertex(Matrices.apply(matrix, v.point()), Matrices.applyLinear(matrix, v.in()),
                Matrices.applyLinear(matrix, v.out())));
    }

    // ------------------------------------------------------------------ helpers

    private static PathData mapAll(PathData path, UnaryOperator<Vertex> fn) {
        List<Contour> contours = new ArrayList<>();
        for (Contour contour : path.contours()) {
            contours.add(new Contour(contour.vertices().stream().map(fn).toList(), contour.closed()));
        }
        return new PathData(contours);
    }

    private static PathData mapVertex(PathData path, VertexRef ref, UnaryOperator<Vertex> fn) {
        Vertex vertex = vertex(path, ref).orElse(null);
        if (vertex == null) {
            return path;
        }
        Vertex mapped = fn.apply(vertex);
        if (mapped.equals(vertex)) {
            return path;
        }
        Contour contour = path.contours().get(ref.contour());
        List<Vertex> vertices = new ArrayList<>(contour.vertices());
        vertices.set(ref.index(), mapped);
        return withContour(path, ref.contour(), new Contour(vertices, contour.closed()));
    }

    private static PathData withContour(PathData path, int index, Contour contour) {
        List<Contour> contours = new ArrayList<>(path.contours());
        contours.set(index, contour);
        return new PathData(contours);
    }

    /** The vector times a factor, without negative zeros, which would not equal a plain zero. */
    public static Vec2 scaled(Vec2 v, double factor) {
        return new Vec2(v.x() * factor + 0.0, v.y() * factor + 0.0);
    }

    private static boolean isZero(Vec2 v) {
        return Math.abs(v.x()) < EPSILON && Math.abs(v.y()) < EPSILON;
    }

    private static double length(Vec2 v) {
        return Math.hypot(v.x(), v.y());
    }

    private static double distance(Vec2 a, Vec2 b) {
        return Math.hypot(b.x() - a.x(), b.y() - a.y());
    }
}
