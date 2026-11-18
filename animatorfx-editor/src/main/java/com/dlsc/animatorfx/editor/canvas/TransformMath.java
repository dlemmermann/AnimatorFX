package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.doc.Matrices;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;

/**
 * The maths behind the selection handles. Everything here works in the coordinate space of the
 * layer's parent, which is where {@code transform.position} lives; the canvas converts the
 * pointer into that space first.
 */
public final class TransformMath {

    private static final double EPSILON = 1e-9;

    private TransformMath() {
    }

    /**
     * The values of a layer transform at one time.
     */
    public record Values(Vec2 anchor, Vec2 position, Vec2 scale, double rotation, Vec2 skew) {

        public static Values of(Transform transform, double time) {
            return new Values(transform.anchor().at(time), transform.position().at(time), transform.scale().at(time),
                    transform.rotation().at(time), transform.skew().at(time));
        }

        /** The local-to-parent matrix. */
        public double[] matrix() {
            return matrix(anchor, position, scale);
        }

        private double[] matrix(Vec2 anchor, Vec2 position, Vec2 scale) {
            return new Transform(Animatable.of(anchor), Animatable.of(position), Animatable.of(scale),
                    Animatable.of(rotation), Animatable.of(skew)).matrixAt(0);
        }

        /** Rotation and skew only: what the scale is applied through. */
        double[] frame() {
            return matrix(Vec2.ZERO, Vec2.ZERO, Vec2.ONE);
        }
    }

    public record Scaled(Vec2 scale, Vec2 position) {
    }

    public record Anchored(Vec2 anchor, Vec2 position) {
    }

    /**
     * The scale and position after dragging a scale handle to the pointer.
     *
     * @param start       the transform when the drag began
     * @param local       the selection box in the layer's own coordinates
     * @param pointer     the pointer in parent coordinates
     * @param aboutAnchor scale about the anchor point instead of the opposite handle
     * @param keepAspect  change both axes by the same factor
     */
    public static Scaled scale(Values start, Box local, Handle handle, Vec2 pointer, boolean aboutAnchor,
                               boolean keepAspect) {
        double[] frame = start.frame();
        double[] inverse = Matrices.invert(frame);
        if (inverse == null) {
            return new Scaled(start.scale(), start.position());
        }
        Vec2 dragged = local.point(handle.fx(), handle.fy());
        Vec2 fixedLocal = aboutAnchor ? start.anchor() : local.point(handle.opposite().fx(), handle.opposite().fy());
        Vec2 fixed = Matrices.apply(start.matrix(), fixedLocal);
        Vec2 wanted = Matrices.applyLinear(inverse, pointer.minus(fixed));
        Vec2 span = dragged.minus(fixedLocal);

        boolean activeX = handle.movesX() && Math.abs(span.x()) > EPSILON;
        boolean activeY = handle.movesY() && Math.abs(span.y()) > EPSILON;
        double scaleX = activeX ? wanted.x() / span.x() : start.scale().x();
        double scaleY = activeY ? wanted.y() / span.y() : start.scale().y();
        if (keepAspect && (activeX || activeY)) {
            double factorX = activeX && start.scale().x() != 0 ? scaleX / start.scale().x() : Double.NaN;
            double factorY = activeY && start.scale().y() != 0 ? scaleY / start.scale().y() : Double.NaN;
            double factor = Double.isNaN(factorY) || Math.abs(factorX) >= Math.abs(factorY) ? factorX : factorY;
            if (!Double.isNaN(factor)) {
                scaleX = start.scale().x() * factor;
                scaleY = start.scale().y() * factor;
            }
        }
        Vec2 offset = fixedLocal.minus(start.anchor());
        Vec2 position = fixed.minus(Matrices.applyLinear(frame, new Vec2(offset.x() * scaleX, offset.y() * scaleY)));
        return new Scaled(new Vec2(scaleX, scaleY), position);
    }

    /**
     * The rotation after dragging the rotate handle from one point to another around the
     * position of the layer.
     *
     * @param snap round the result to multiples of 15 degrees
     */
    public static double rotation(Values start, Vec2 startPointer, Vec2 pointer, boolean snap) {
        Vec2 from = startPointer.minus(start.position());
        Vec2 to = pointer.minus(start.position());
        double delta = Math.toDegrees(Math.atan2(to.y(), to.x()) - Math.atan2(from.y(), from.x()));
        delta = delta - 360 * Math.rint(delta / 360);
        double rotation = clean(start.rotation() + delta);
        return snap ? snapAngle(rotation, 15) : rotation;
    }

    public static double snapAngle(double degrees, double step) {
        return Math.rint(degrees / step) * step;
    }

    /**
     * Moves the anchor point to the pointer without moving the layer: the position follows.
     */
    public static Anchored moveAnchor(Values start, Vec2 pointer) {
        double[] inverse = Matrices.invert(start.matrix());
        if (inverse == null) {
            return new Anchored(start.anchor(), start.position());
        }
        return new Anchored(Matrices.apply(inverse, pointer), pointer);
    }

    /** Keeps the larger of the two components: a move along one axis. */
    public static Vec2 constrainAxis(Vec2 delta) {
        return Math.abs(delta.x()) >= Math.abs(delta.y()) ? new Vec2(delta.x(), 0) : new Vec2(0, delta.y());
    }

    /** Makes both components as long as the longer one, keeping their signs: a square drag. */
    public static Vec2 constrainSquare(Vec2 delta) {
        double side = Math.max(Math.abs(delta.x()), Math.abs(delta.y()));
        return new Vec2(Math.copySign(side, delta.x()), Math.copySign(side, delta.y()));
    }

    /** Projects the vector onto the nearest direction that is a multiple of the step in degrees. */
    public static Vec2 constrainAngle(Vec2 delta, double stepDegrees) {
        double angle = Math.toRadians(snapAngle(Math.toDegrees(Math.atan2(delta.y(), delta.x())), stepDegrees));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double length = delta.x() * cos + delta.y() * sin;
        return new Vec2(clean(length * cos), clean(length * sin));
    }

    private static double clean(double value) {
        double rounded = Math.rint(value * 1e9) / 1e9;
        return rounded == 0 ? 0 : rounded;
    }
}
