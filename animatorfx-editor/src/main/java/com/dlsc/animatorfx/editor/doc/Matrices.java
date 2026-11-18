package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.model.Vec2;

/**
 * Maths on 2D affine matrices in the layout of {@link com.dlsc.animatorfx.model.Transform#matrixAt}:
 * {@code {mxx, mxy, tx, myx, myy, ty}}.
 */
public final class Matrices {

    private static final double EPSILON = 1e-9;

    private Matrices() {
    }

    public static double[] identity() {
        return new double[]{1, 0, 0, 0, 1, 0};
    }

    /**
     * The matrix that applies {@code b} first and {@code a} second.
     */
    public static double[] multiply(double[] a, double[] b) {
        return new double[]{
                a[0] * b[0] + a[1] * b[3], a[0] * b[1] + a[1] * b[4], a[0] * b[2] + a[1] * b[5] + a[2],
                a[3] * b[0] + a[4] * b[3], a[3] * b[1] + a[4] * b[4], a[3] * b[2] + a[4] * b[5] + a[5]};
    }

    /**
     * @return the inverse, or {@code null} when the matrix flattens the plane (a scale of zero)
     */
    public static double[] invert(double[] m) {
        double det = m[0] * m[4] - m[1] * m[3];
        if (!(Math.abs(det) > 1e-12)) {
            return null;
        }
        double a = m[4] / det;
        double b = -m[1] / det;
        double c = -m[3] / det;
        double d = m[0] / det;
        return new double[]{a, b, -(a * m[2] + b * m[5]), c, d, -(c * m[2] + d * m[5])};
    }

    public static Vec2 apply(double[] m, Vec2 p) {
        return new Vec2(m[0] * p.x() + m[1] * p.y() + m[2], m[3] * p.x() + m[4] * p.y() + m[5]);
    }

    /**
     * Transforms a direction or a distance: the matrix without its translation.
     */
    public static Vec2 applyLinear(double[] m, Vec2 d) {
        return new Vec2(m[0] * d.x() + m[1] * d.y(), m[3] * d.x() + m[4] * d.y());
    }

    public static boolean isIdentity(double[] m) {
        return Math.abs(m[0] - 1) < EPSILON && Math.abs(m[1]) < EPSILON && Math.abs(m[2]) < EPSILON
                && Math.abs(m[3]) < EPSILON && Math.abs(m[4] - 1) < EPSILON && Math.abs(m[5]) < EPSILON;
    }

    /**
     * Transform values that produce a matrix, see {@link #decompose}.
     */
    public record Decomposed(Vec2 position, Vec2 scale, double rotation, Vec2 skew) {
    }

    /**
     * Splits a matrix into the values of a {@link com.dlsc.animatorfx.model.Transform} with the
     * given anchor. The shear ends up in {@code skew.x}; {@code skew.y} is always 0 and
     * {@code scale.x} is never negative (a mirrored matrix has a negative {@code scale.y}).
     */
    public static Decomposed decompose(double[] m, Vec2 anchor) {
        double scaleX = Math.hypot(m[0], m[3]);
        double radians = scaleX > 0 ? Math.atan2(m[3], m[0]) : 0;
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double u = cos * m[1] + sin * m[4];
        double scaleY = -sin * m[1] + cos * m[4];
        double skewX = Math.abs(scaleY) > 1e-12 ? Math.toDegrees(Math.atan(u / scaleY)) : 0;
        return new Decomposed(apply(m, anchor), new Vec2(scaleX, scaleY), clean(Math.toDegrees(radians)),
                new Vec2(clean(skewX), 0));
    }

    /** Removes the rounding noise of the trigonometry, so 30° does not come back as 29.999999999999996°. */
    private static double clean(double value) {
        double rounded = Math.rint(value * 1e9) / 1e9;
        return rounded == 0 ? 0 : rounded;
    }
}
