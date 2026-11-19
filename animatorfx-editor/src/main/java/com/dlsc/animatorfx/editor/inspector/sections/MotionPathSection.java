package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.inspector.FieldRow;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.PropertyRows;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.MotionPath;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The motion path of the one selected layer. Without one, "Add Motion Path" starts drawing it on
 * the canvas. With one: "Edit Path" opens it in path editing, the bin removes it, "Orient to
 * path" turns the layer along the path, and Progress (0–100 %, animatable) says how far along
 * the path the layer is. A new path comes with two progress keyframes, 0 % at the layer's
 * in-point and 100 % at its out-point.
 *
 * <p>The path replaces the layer's position; a line says so while the position has keyframes.
 * Shown for a single selected layer, because a path is drawn for one layer.
 */
public final class MotionPathSection extends SectionBase {

    public static final String ADD_ID = "inspector-motion-add";
    public static final String EDIT_ID = "inspector-motion-edit";
    public static final String REMOVE_ID = "inspector-motion-remove";
    public static final String ORIENT_ID = "inspector-motion-orient";
    public static final String HINT_ID = "inspector-motion-hint";

    private final Button add = new Button("Add Motion Path", new FontIcon(Material2AL.GESTURE));
    private final Button edit = new Button("Edit Path");
    private final Button remove = new Button(null, new FontIcon(Material2AL.DELETE_OUTLINE));
    private final CheckBox orient = new CheckBox("Orient to path");
    private final Label info = new Label();
    private final FieldRow editRow;
    private final PropertyRows.NumberRow progress;
    private final Label hint = new Label();
    private Consumer<String> drawAction = id -> {
    };
    private Consumer<String> editAction = id -> {
    };
    private boolean updating;

    public MotionPathSection() {
        super("Motion Path");
        add.setId(ADD_ID);
        add.getStyleClass().add("section-button");
        add.setOnAction(e -> drawAction.accept(selected().id()));
        add(add);

        edit.setId(EDIT_ID);
        edit.getStyleClass().add("section-button");
        edit.setMinWidth(Button.USE_PREF_SIZE);
        edit.setOnAction(e -> editAction.accept(selected().id()));
        info.getStyleClass().addAll("hint", "mono");
        info.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox path = new HBox(edit, info);
        path.getStyleClass().add("vec2-field");
        editRow = row("Path", path);

        progress = number("motionPath.progress", "Progress");
        progress.field().setRange(0, 1);

        orient.setId(ORIENT_ID);
        orient.getStyleClass().add("section-check");
        orient.setOnAction(e -> {
            if (!updating) {
                boolean on = orient.isSelected();
                context().edit(on ? "Orient to Path" : "Keep Orientation", Layer.class, layer -> {
                    MotionPath motion = layer.base().motionPath();
                    return motion == null ? layer : layer.withBase(layer.base().withMotionPath(motion.withOrientToPath(on)));
                }, null);
            }
        });
        add(orient);

        hint.setId(HINT_ID);
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        add(hint);

        remove.setId(REMOVE_ID);
        remove.getStyleClass().setAll("icon-button", "section-toggle");
        remove.setFocusTraversable(false);
        remove.setTooltip(new Tooltip("Remove the motion path"));
        remove.setOnAction(e -> context().document().execute(Commands.setMotionPath(selected().id(), null)));
        actions().getChildren().add(remove);
    }

    /**
     * Connects the buttons to the canvas.
     *
     * @param draw starts drawing a motion path for the layer with that id
     * @param edit opens the motion path of the layer with that id in path editing
     */
    public void setActions(Consumer<String> draw, Consumer<String> edit) {
        drawAction = Objects.requireNonNull(draw, "draw");
        editAction = Objects.requireNonNull(edit, "edit");
    }

    public PropertyRows.NumberRow progress() {
        return progress;
    }

    private Layer selected() {
        return context().layers().get(0);
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.layers().size() == 1 && context.allVisual();
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    @Override
    protected void updateFields(InspectorContext context) {
        Layer layer = selected();
        MotionPath motion = layer.base().motionPath();
        boolean present = motion != null;
        show(add, !present);
        show(remove, present);
        show(orient, present);
        editRow.show(present);
        boolean positionKeyed = layer.base().transform().position().isAnimated();
        hint.setText(!present ? "Draw a path on the canvas for the layer to travel along."
                : positionKeyed ? "The path replaces the position; the position keyframes have no effect."
                : "The path replaces the position of the layer.");
        if (present) {
            int points = motion.path().contours().stream().mapToInt(c -> c.vertices().size()).sum();
            info.setText(points + " pts");
            updating = true;
            try {
                orient.setSelected(motion.orientToPath());
            } finally {
                updating = false;
            }
        }
    }
}
