package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * A vector shape. {@code stroke} and {@code trim} may be {@code null}; an unfilled shape has the
 * fill {@link Paint.None}.
 */
public record ShapeLayer(LayerBase base, Geometry geometry, Animatable<Paint> fill, Stroke stroke, Trim trim)
        implements Layer {

    public ShapeLayer {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(fill, "fill");
    }

    @Override
    public ShapeLayer withBase(LayerBase base) {
        return new ShapeLayer(base, geometry, fill, stroke, trim);
    }

    public ShapeLayer withGeometry(Geometry geometry) {
        return new ShapeLayer(base, geometry, fill, stroke, trim);
    }

    public ShapeLayer withFill(Animatable<Paint> fill) {
        return new ShapeLayer(base, geometry, fill, stroke, trim);
    }

    /**
     * @param stroke the stroke, or {@code null} for none
     */
    public ShapeLayer withStroke(Stroke stroke) {
        return new ShapeLayer(base, geometry, fill, stroke, trim);
    }

    /**
     * @param trim the trim, or {@code null} for none
     */
    public ShapeLayer withTrim(Trim trim) {
        return new ShapeLayer(base, geometry, fill, stroke, trim);
    }
}
