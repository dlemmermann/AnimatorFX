package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;

/**
 * 2D affine matrices as {@code {mxx, mxy, tx, myx, myy, ty}}, the layout of
 * {@link Transform#matrixAt}.
 */
final class Matrix {

    private Matrix() {
    }

    static double[] identity() {
        return new double[]{1, 0, 0, 0, 1, 0};
    }

    static double[] translate(double x, double y) {
        return new double[]{1, 0, x, 0, 1, y};
    }

    static double[] scale(double x, double y) {
        return new double[]{x, 0, 0, 0, y, 0};
    }

    /** Clockwise (y down) by the given degrees around the origin. */
    static double[] rotate(double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new double[]{cos, -sin, 0, sin, cos, 0};
    }

    /** The matrix that applies {@code b} first and {@code a} second. */
    static double[] multiply(double[] a, double[] b) {
        return new double[]{
                a[0] * b[0] + a[1] * b[3], a[0] * b[1] + a[1] * b[4], a[0] * b[2] + a[1] * b[5] + a[2],
                a[3] * b[0] + a[4] * b[3], a[3] * b[1] + a[4] * b[4], a[3] * b[2] + a[4] * b[5] + a[5]};
    }

    static Vec2 apply(double[] m, double x, double y) {
        return new Vec2(m[0] * x + m[1] * y + m[2], m[3] * x + m[4] * y + m[5]);
    }

    static Vec2 apply(double[] m, Vec2 point) {
        return apply(m, point.x(), point.y());
    }

    /** Transforms a direction: the translation is left out. */
    static Vec2 applyLinear(double[] m, double x, double y) {
        return new Vec2(m[0] * x + m[1] * y, m[3] * x + m[4] * y);
    }

    static double determinant(double[] m) {
        return m[0] * m[4] - m[1] * m[3];
    }

    static boolean isIdentity(double[] m) {
        return m[0] == 1 && m[1] == 0 && m[2] == 0 && m[3] == 0 && m[4] == 1 && m[5] == 0;
    }

    /** Whether the matrix scales by the same factor in every direction (rotation and reflection allowed). */
    static boolean isUniform(double[] m) {
        double lengthX = Math.hypot(m[0], m[3]);
        double lengthY = Math.hypot(m[1], m[4]);
        double dot = m[0] * m[1] + m[3] * m[4];
        double tolerance = 1e-6 * Math.max(1, Math.max(lengthX, lengthY));
        return Math.abs(lengthX - lengthY) <= tolerance && Math.abs(dot) <= tolerance * Math.max(lengthX, lengthY);
    }

    /** The factor by which the matrix scales lengths on average. */
    static double meanScale(double[] m) {
        return Math.sqrt(Math.abs(determinant(m)));
    }

    /**
     * The transform with anchor (0,0) whose matrix is {@code m}: translate · rotate · skew along
     * x · scale. Every matrix that does not collapse the plane is reproduced exactly; a
     * reflection comes out as a negative y scale.
     */
    static Transform decompose(double[] m) {
        double scaleX = Math.hypot(m[0], m[3]);
        double radians = scaleX == 0 ? 0 : Math.atan2(m[3], m[0]);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        // rotate back: what is left is {{scaleX, shear * scaleY}, {0, scaleY}}
        double sheared = cos * m[1] + sin * m[4];
        double scaleY = -sin * m[1] + cos * m[4];
        double skew = scaleY == 0 ? 0 : Math.toDegrees(Math.atan(sheared / scaleY));
        return new Transform(Animatable.of(Vec2.ZERO), Animatable.of(new Vec2(m[2], m[5])),
                Animatable.of(new Vec2(clean(scaleX), clean(scaleY))), Animatable.of(clean(Math.toDegrees(radians))),
                Animatable.of(new Vec2(clean(skew), 0)));
    }

    /** Removes the noise of the trigonometry from values that are whole numbers. */
    private static double clean(double value) {
        double rounded = Math.rint(value);
        return Math.abs(value - rounded) < 1e-12 ? rounded + 0.0 : value;
    }
}
