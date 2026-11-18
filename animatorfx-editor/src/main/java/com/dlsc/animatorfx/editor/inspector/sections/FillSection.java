package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.PropertyRows;
import com.dlsc.animatorfx.editor.inspector.SectionBase;

/**
 * The fill of the selected shapes and texts: none, a colour, or a linear or radial gradient.
 */
public final class FillSection extends SectionBase {

    private final PropertyRows.PaintRow fill;

    public FillSection() {
        super("Fill");
        fill = paint("fill", "Paint");
    }

    public PropertyRows.PaintRow fill() {
        return fill;
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.allHave("fill");
    }

    @Override
    protected void updateFields(InspectorContext context) {
        fill.field().setExtent(Extents.of(context.layers().get(0), context.time()));
    }
}
