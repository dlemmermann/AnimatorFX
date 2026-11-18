package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.model.Vec2;

import java.util.Collection;

/**
 * An axis-aligned rectangle given by two corners.
 */
public record Box(double minX, double minY, double maxX, double maxY) {

    /**
     * The smallest box around the points; {@code null} for no points.
     */
    public static Box around(Collection<Vec2> points) {
        Box box = null;
        for (Vec2 p : points) {
            Box point = new Box(p.x(), p.y(), p.x(), p.y());
            box = box == null ? point : box.union(point);
        }
        return box;
    }

    /**
     * The box between two corners in any order.
     */
    public static Box between(Vec2 a, Vec2 b) {
        return new Box(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.max(a.x(), b.x()), Math.max(a.y(), b.y()));
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public double centerX() {
        return (minX + maxX) / 2;
    }

    public double centerY() {
        return (minY + maxY) / 2;
    }

    /**
     * The point at the given fractions of the width and the height.
     */
    public Vec2 point(double fx, double fy) {
        return new Vec2(minX + width() * fx, minY + height() * fy);
    }

    public boolean contains(double x, double y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public boolean intersects(Box other) {
        return other.maxX >= minX && other.minX <= maxX && other.maxY >= minY && other.minY <= maxY;
    }

    public Box union(Box other) {
        return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY));
    }

    public Box translated(double dx, double dy) {
        return new Box(minX + dx, minY + dy, maxX + dx, maxY + dy);
    }

    public Box grown(double amount) {
        return new Box(minX - amount, minY - amount, maxX + amount, maxY + amount);
    }
}
