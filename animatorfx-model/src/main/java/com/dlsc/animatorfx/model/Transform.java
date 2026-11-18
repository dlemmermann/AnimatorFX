package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * Places a layer in its parent. The anchor is the point of the layer (in layer coordinates) that
 * lands on {@code position}; rotation, skew and scale happen around it.
 */
public record Transform(Animatable<Vec2> anchor, Animatable<Vec2> position, Animatable<Vec2> scale,
                        Animatable<Double> rotation, Animatable<Vec2> skew) {

    public static final Transform IDENTITY = new Transform(Animatable.of(Vec2.ZERO), Animatable.of(Vec2.ZERO),
            Animatable.of(Vec2.ONE), Animatable.of(0.0), Animatable.of(Vec2.ZERO));

    public Transform {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(skew, "skew");
    }

    /**
     * The identity transform moved to the given position.
     */
    public static Transform at(double x, double y) {
        return IDENTITY.withPosition(Animatable.of(new Vec2(x, y)));
    }

    /**
     * The 2D affine matrix at the given time as {@code {mxx, mxy, tx, myx, myy, ty}}, mapping
     * {@code (x, y)} to {@code (mxx*x + mxy*y + tx, myx*x + myy*y + ty)}. It is composed as
     * translate(position) · rotate(rotation) · skew · scale · translate(-anchor); the skew angles
     * are in degrees, {@code skew.x} shearing along x and {@code skew.y} along y.
     */
    public double[] matrixAt(double time) {
        Vec2 a = anchor.at(time);
        Vec2 p = position.at(time);
        Vec2 s = scale.at(time);
        Vec2 k = skew.at(time);
        double radians = Math.toRadians(rotation.at(time));
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double shearX = Math.tan(Math.toRadians(k.x()));
        double shearY = Math.tan(Math.toRadians(k.y()));

        double mxx = (cos - sin * shearY) * s.x();
        double mxy = (cos * shearX - sin) * s.y();
        double myx = (sin + cos * shearY) * s.x();
        double myy = (sin * shearX + cos) * s.y();
        return new double[]{
                mxx, mxy, p.x() - mxx * a.x() - mxy * a.y(),
                myx, myy, p.y() - myx * a.x() - myy * a.y()};
    }

    public Transform withAnchor(Animatable<Vec2> anchor) {
        return new Transform(anchor, position, scale, rotation, skew);
    }

    public Transform withPosition(Animatable<Vec2> position) {
        return new Transform(anchor, position, scale, rotation, skew);
    }

    public Transform withScale(Animatable<Vec2> scale) {
        return new Transform(anchor, position, scale, rotation, skew);
    }

    public Transform withRotation(Animatable<Double> rotation) {
        return new Transform(anchor, position, scale, rotation, skew);
    }

    public Transform withSkew(Animatable<Vec2> skew) {
        return new Transform(anchor, position, scale, rotation, skew);
    }
}
