package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.doc.Command;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.PropertyRows;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.ShapeLayer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.ArrayList;
import java.util.List;

/**
 * Trim paths of the selected shapes: the part of the stroke that is drawn. "Enable trim" gives
 * the shapes a trim over the whole stroke (start 0 %, end 100 %, offset 0 %) and takes it away
 * again, keyframes included. Start, end and offset are fractions of the path length, shown in
 * percent; an offset of 100 % is once around. All three can be animated.
 *
 * <p>Trim only shows on a stroke: while a selected shape has none, a hint says so and offers to
 * add one.
 */
public final class TrimSection extends SectionBase {

    public static final String ENABLED_ID = "inspector-trim-enabled";
    public static final String ADD_STROKE_ID = "inspector-trim-add-stroke";
    public static final String HINT_ID = "inspector-trim-hint";

    private final CheckBox enabled = new CheckBox("Enable trim");
    private final PropertyRows.NumberRow start;
    private final PropertyRows.NumberRow end;
    private final PropertyRows.NumberRow offset;
    private final Label hint = new Label("Trim affects the stroke \u2014 add a stroke");
    private final Button addStroke = new Button("Add Stroke");
    private final HBox strokeHint = new HBox(hint, addStroke);
    private boolean updating;

    public TrimSection() {
        super("Trim");
        enabled.setId(ENABLED_ID);
        enabled.getStyleClass().add("section-check");
        enabled.setOnAction(e -> {
            if (!updating) {
                setEnabled(enabled.isSelected());
            }
        });
        add(enabled);
        start = number("trim.start", "Start");
        start.field().setRange(0, 1);
        end = number("trim.end", "End");
        end.field().setRange(0, 1);
        offset = number("trim.offset", "Offset");

        hint.setId(HINT_ID);
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(hint, Priority.ALWAYS);
        addStroke.setId(ADD_STROKE_ID);
        addStroke.getStyleClass().add("section-button");
        addStroke.setMinWidth(Button.USE_PREF_SIZE);
        addStroke.setOnAction(e -> context().edit("Add Stroke", ShapeLayer.class,
                shape -> shape.stroke() == null ? shape.withStroke(StrokeSection.defaultStroke()) : shape, null));
        strokeHint.getStyleClass().add("hint-row");
        strokeHint.setAlignment(Pos.CENTER_LEFT);
        add(strokeHint);
    }

    public PropertyRows.NumberRow start() {
        return start;
    }

    public PropertyRows.NumberRow end() {
        return end;
    }

    public PropertyRows.NumberRow offset() {
        return offset;
    }

    private void setEnabled(boolean on) {
        List<Command> commands = new ArrayList<>();
        for (Layer layer : context().layers()) {
            commands.add(Commands.setTrimEnabled(layer.id(), on));
        }
        context().document().execute(Commands.batch(on ? "Enable Trim" : "Remove Trim", commands));
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.allLayers(ShapeLayer.class);
    }

    @Override
    protected void updateFields(InspectorContext context) {
        long trimmed = context.layers().stream().filter(l -> ((ShapeLayer) l).trim() != null).count();
        boolean stroked = context.layers().stream().allMatch(l -> ((ShapeLayer) l).stroke() != null);
        updating = true;
        try {
            enabled.setIndeterminate(trimmed > 0 && trimmed < context.layers().size());
            enabled.setSelected(trimmed == context.layers().size());
        } finally {
            updating = false;
        }
        strokeHint.setVisible(!stroked);
        strokeHint.setManaged(!stroked);
    }
}
