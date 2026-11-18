package com.dlsc.animatorfx.model;

import java.util.Map;

/**
 * How a property travels from one keyframe to the next.
 */
public sealed interface Easing permits Easing.Linear, Easing.Hold, Easing.CubicBezier {

    Easing LINEAR = new Linear();
    Easing HOLD = new Hold();

    /**
     * Maps linear progress {@code u} in {@code 0..1} to eased progress. The result may leave
     * {@code 0..1} for overshooting curves.
     */
    double apply(double u);

    /**
     * Named cubic-bezier presets in display order. The map is unmodifiable.
     */
    static Map<String, Easing> presets() {
        return EasingPresets.ALL;
    }

    record Linear() implements Easing {
        @Override
        public double apply(double u) {
            return u;
        }
    }

    /**
     * Keeps the value of the left keyframe until the next keyframe is reached.
     */
    record Hold() implements Easing {
        @Override
        public double apply(double u) {
            return u < 1 ? 0 : 1;
        }
    }

    /**
     * A CSS style timing curve from (0,0) to (1,1) with the control points (x1,y1) and (x2,y2).
     * {@code x1} and {@code x2} are expected in {@code 0..1}.
     */
    record CubicBezier(double x1, double y1, double x2, double y2) implements Easing {

        @Override
        public double apply(double u) {
            if (u <= 0) {
                return 0;
            }
            if (u >= 1) {
                return 1;
            }
            double t = solveX(u);
            return bezier(y1, y2, t);
        }

        private double solveX(double x) {
            double t = x;
            for (int i = 0; i < 8; i++) {
                double error = bezier(x1, x2, t) - x;
                if (Math.abs(error) < 1e-12) {
                    return t;
                }
                double slope = slope(x1, x2, t);
                if (Math.abs(slope) < 1e-6) {
                    break;
                }
                t -= error / slope;
            }
            if (t >= 0 && t <= 1 && Math.abs(bezier(x1, x2, t) - x) < 1e-9) {
                return t;
            }
            // Newton did not converge (flat slope): bisect.
            double low = 0;
            double high = 1;
            for (int i = 0; i < 60; i++) {
                t = (low + high) / 2;
                if (bezier(x1, x2, t) < x) {
                    low = t;
                } else {
                    high = t;
                }
            }
            return (low + high) / 2;
        }

        private static double bezier(double c1, double c2, double t) {
            double s = 1 - t;
            return 3 * s * s * t * c1 + 3 * s * t * t * c2 + t * t * t;
        }

        private static double slope(double c1, double c2, double t) {
            double s = 1 - t;
            return 3 * s * s * c1 + 6 * s * t * (c2 - c1) + 3 * t * t * (1 - c2);
        }
    }
}
