package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.inspector.EasingEditor;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.SectionBase;

/**
 * The easing of the selected keyframes; shown while keyframes are selected.
 */
public final class EasingSection extends SectionBase {

    private final EasingEditor editor = new EasingEditor();

    public EasingSection() {
        super("Easing");
        add(editor);
    }

    public EasingEditor getEditor() {
        return editor;
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return !context.keyframes().isEmpty();
    }

    @Override
    protected void updateFields(InspectorContext context) {
        editor.update(context);
    }
}
