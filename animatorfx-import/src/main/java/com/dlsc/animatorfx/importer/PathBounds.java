package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Vec2;

import java.util.List;

/**
 * The exact bounding box of a Bezier path.
 */
final class PathBounds {

    private PathBounds() {
    }

    /**
     * @return {@code {x, y, width, height}}, or {@code null} for a path without vertices
     */
    static double[] of(PathData path) {
        double[] box = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY};
        for (PathData.Contour contour : path.contours()) {
            List<PathData.Vertex> vertices = contour.vertices();
            int count = vertices.size();
            for (PathData.Vertex vertex : vertices) {
                include(box, vertex.point().x(), vertex.point().y());
            }
            int segments = contour.closed() ? count : count - 1;
            for (int i = 0; i < segments; i++) {
                PathData.Vertex from = vertices.get(i);
                PathData.Vertex to = vertices.get((i + 1) % count);
                Vec2 c1 = from.point().plus(from.out());
                Vec2 c2 = to.point().plus(to.in());
                extremes(box, from.point().x(), c1.x(), c2.x(), to.point().x(), from.point().y(), c1.y(), c2.y(),
                        to.point().y(), true);
                extremes(box, from.point().y(), c1.y(), c2.y(), to.point().y(), from.point().x(), c1.x(), c2.x(),
                        to.point().x(), false);
            }
        }
        if (box[0] > box[2]) {
            return null;
        }
        return new double[]{box[0], box[1], box[2] - box[0], box[3] - box[1]};
    }

    /** Includes the points where the first coordinate of the cubic turns around. */
    private static void extremes(double[] box, double p0, double p1, double p2, double p3, double q0, double q1,
                                 double q2, double q3, boolean firstIsX) {
        // derivative / 3 = a t² + b t + c
        double a = -p0 + 3 * p1 - 3 * p2 + p3;
        double b = 2 * (p0 - 2 * p1 + p2);
        double c = p1 - p0;
        double[] roots;
        if (Math.abs(a) < 1e-12) {
            roots = Math.abs(b) < 1e-12 ? new double[0] : new double[]{-c / b};
        } else {
            double discriminant = b * b - 4 * a * c;
            if (discriminant < 0) {
                return;
            }
            double root = Math.sqrt(discriminant);
            roots = new double[]{(-b + root) / (2 * a), (-b - root) / (2 * a)};
        }
        for (double t : roots) {
            if (t > 0 && t < 1) {
                double p = bezier(p0, p1, p2, p3, t);
                double q = bezier(q0, q1, q2, q3, t);
                include(box, firstIsX ? p : q, firstIsX ? q : p);
            }
        }
    }

    private static double bezier(double p0, double p1, double p2, double p3, double t) {
        double s = 1 - t;
        return s * s * s * p0 + 3 * s * s * t * p1 + 3 * s * t * t * p2 + t * t * t * p3;
    }

    private static void include(double[] box, double x, double y) {
        box[0] = Math.min(box[0], x);
        box[1] = Math.min(box[1], y);
        box[2] = Math.max(box[2], x);
        box[3] = Math.max(box[3], y);
    }
}
