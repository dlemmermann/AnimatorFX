package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Vec2;

/**
 * The approximate size of the content of a layer in its own coordinates, for laying a new
 * gradient across it.
 */
final class Extents {

    private static final Vec2 FALLBACK = new Vec2(100, 100);

    private Extents() {
    }

    static Vec2 of(Layer layer, double time) {
        Vec2 size = switch (layer) {
            case ShapeLayer shape -> switch (shape.geometry()) {
                case Geometry.Rect g -> g.size().at(time);
                case Geometry.Ellipse g -> g.size().at(time);
                case Geometry.Line g -> g.end().at(time).minus(g.start().at(time));
                case Geometry.Polygon g -> new Vec2(2 * g.radius().at(time), 2 * g.radius().at(time));
                case Geometry.Star g -> new Vec2(2 * g.outerRadius().at(time), 2 * g.outerRadius().at(time));
                case Geometry.PathGeometry g -> FALLBACK;
            };
            case TextLayer text -> new Vec2(text.fontSize().at(time) * Math.max(1, text.text().length()) * 0.5,
                    text.fontSize().at(time));
            default -> FALLBACK;
        };
        double width = Math.abs(size.x());
        double height = Math.abs(size.y());
        return new Vec2(width < 1 ? FALLBACK.x() : width, height < 1 ? FALLBACK.y() : height);
    }
}
