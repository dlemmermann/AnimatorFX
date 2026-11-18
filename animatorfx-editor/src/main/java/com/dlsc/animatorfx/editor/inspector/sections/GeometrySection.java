package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.editor.inspector.FieldRow;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.PropertyRows;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.ShapeLayer;
import java.util.List;

/**
 * The geometry of the selected shapes, when they are all of the same kind: size and corner
 * radius of a rectangle, size of an ellipse, start and end of a line, radius and sides of a
 * polygon, radii and points of a star. Paths are edited on the canvas.
 */
public final class GeometrySection extends SectionBase {

    public static final String SIDES_ID = "inspector-polygon-sides";
    public static final String POINTS_ID = "inspector-star-points";

    private final PropertyRows.Vec2Row size;
    private final PropertyRows.NumberRow cornerRadius;
    private final NumberField sides = new NumberField(PropertyUnits.NONE);
    private final NumberField points = new NumberField(PropertyUnits.NONE);
    private final FieldRow sidesRow;
    private final FieldRow pointsRow;

    public GeometrySection() {
        super("Geometry");
        size = vec2("geometry.size", "Size", "Width", "Height", false);
        size.field().setRange(0, Double.MAX_VALUE);
        cornerRadius = number("geometry.cornerRadius", "Corners");
        cornerRadius.field().setRange(0, Double.MAX_VALUE);
        vec2("geometry.start", "Start", "X", "Y", false);
        vec2("geometry.end", "End", "X", "Y", false);
        number("geometry.radius", "Radius").field().setRange(0, Double.MAX_VALUE);
        number("geometry.outerRadius", "Outer").field().setRange(0, Double.MAX_VALUE);
        number("geometry.innerRadius", "Inner").field().setRange(0, Double.MAX_VALUE);

        sides.setId(SIDES_ID);
        sides.setDecimals(0);
        sides.setRange(3, 360);
        sides.setOnChange((count, adjusting) -> context().edit("Change Sides", ShapeLayer.class,
                shape -> shape.geometry() instanceof Geometry.Polygon p
                        ? shape.withGeometry(new Geometry.Polygon((int) Math.round(count), p.radius())) : shape,
                adjusting ? sides : null));
        sides.setOnAdjustEnd(() -> context().endGesture());
        sidesRow = row("Sides", sides);
        sides.installScrubbing(sidesRow.getLabel());

        points.setId(POINTS_ID);
        points.setDecimals(0);
        points.setRange(2, 360);
        points.setOnChange((count, adjusting) -> context().edit("Change Points", ShapeLayer.class,
                shape -> shape.geometry() instanceof Geometry.Star s
                        ? shape.withGeometry(new Geometry.Star((int) Math.round(count), s.outerRadius(), s.innerRadius()))
                        : shape,
                adjusting ? points : null));
        points.setOnAdjustEnd(() -> context().endGesture());
        pointsRow = row("Points", points);
        points.installScrubbing(pointsRow.getLabel());
    }

    public PropertyRows.Vec2Row size() {
        return size;
    }

    public PropertyRows.NumberRow cornerRadius() {
        return cornerRadius;
    }

    /** The geometry class that all selected layers share; {@code null} when they are not all shapes of one kind. */
    private static Class<?> kind(List<Layer> layers) {
        Class<?> kind = null;
        for (Layer layer : layers) {
            if (!(layer instanceof ShapeLayer shape)) {
                return null;
            }
            if (kind != null && kind != shape.geometry().getClass()) {
                return null;
            }
            kind = shape.geometry().getClass();
        }
        return kind;
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        Class<?> kind = kind(context.layers());
        return kind != null && kind != Geometry.PathGeometry.class;
    }

    @Override
    protected void updateFields(InspectorContext context) {
        Class<?> kind = kind(context.layers());
        sidesRow.show(kind == Geometry.Polygon.class);
        pointsRow.show(kind == Geometry.Star.class);
        if (kind == Geometry.Polygon.class) {
            Integer count = context.common(ShapeLayer.class, s -> ((Geometry.Polygon) s.geometry()).sides());
            sides.setValue(count == null ? null : count.doubleValue());
        } else if (kind == Geometry.Star.class) {
            Integer count = context.common(ShapeLayer.class, s -> ((Geometry.Star) s.geometry()).points());
            points.setValue(count == null ? null : count.doubleValue());
        }
    }
}
