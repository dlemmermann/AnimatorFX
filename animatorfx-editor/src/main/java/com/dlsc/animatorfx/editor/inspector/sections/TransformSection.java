package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.PropertyRows;
import com.dlsc.animatorfx.editor.inspector.SectionBase;

/**
 * Position, anchor, scale (in percent), rotation and skew (in degrees) and opacity (in percent).
 */
public final class TransformSection extends SectionBase {

    private final PropertyRows.Vec2Row position;
    private final PropertyRows.Vec2Row anchor;
    private final PropertyRows.Vec2Row scale;
    private final PropertyRows.NumberRow rotation;
    private final PropertyRows.Vec2Row skew;
    private final PropertyRows.NumberRow opacity;

    public TransformSection() {
        super("Transform");
        position = vec2("transform.position", "Position", "X", "Y", false);
        anchor = vec2("transform.anchor", "Anchor", "X", "Y", false);
        scale = vec2("transform.scale", "Scale", "Horizontal", "Vertical", true);
        rotation = number("transform.rotation", "Rotation");
        skew = vec2("transform.skew", "Skew", "Horizontal", "Vertical", false);
        opacity = number("opacity", "Opacity");
        opacity.field().setRange(0, 1);
        skew.field().setRange(-89, 89);
    }

    public PropertyRows.Vec2Row position() {
        return position;
    }

    public PropertyRows.Vec2Row anchor() {
        return anchor;
    }

    public PropertyRows.Vec2Row scale() {
        return scale;
    }

    public PropertyRows.NumberRow rotation() {
        return rotation;
    }

    public PropertyRows.Vec2Row skew() {
        return skew;
    }

    public PropertyRows.NumberRow opacity() {
        return opacity;
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.allVisual();
    }
}
