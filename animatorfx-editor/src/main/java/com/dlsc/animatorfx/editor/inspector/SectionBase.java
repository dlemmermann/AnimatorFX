package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.editor.inspector.fields.KeyframeToggle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * The frame that inspector sections share: a heading that collapses the section when clicked,
 * with room for buttons at its right, and a column of {@link FieldRow}s.
 *
 * <p>A subclass builds its rows in the constructor — {@link #number}, {@link #vec2} and
 * {@link #paint} make rows that are bound to an animatable property, with a keyframe diamond;
 * {@link #row} adds any other editor — and brings the plain editors up to date in
 * {@link #updateFields}. Property rows update themselves and hide when the selected layers do
 * not all have their property.
 */
public abstract class SectionBase implements InspectorSection {

    private static final PseudoClass COLLAPSED = PseudoClass.getPseudoClass("collapsed");

    private final String title;
    private final VBox root = new VBox();
    private final VBox content = new VBox();
    private final HBox actions = new HBox();
    private final FontIcon chevron = new FontIcon(Material2AL.EXPAND_MORE);
    private final List<PropertyRows.PropertyRow> propertyRows = new ArrayList<>();
    private InspectorContext context;
    private boolean collapsed;

    protected SectionBase(String title) {
        this.title = title;
        Label heading = new Label(title.toUpperCase());
        heading.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.getStyleClass().add("section-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(chevron, heading, spacer, actions);
        header.getStyleClass().add("section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        // buttons in the heading consume their clicks
        header.setOnMouseClicked(e -> setCollapsed(!collapsed));
        content.getStyleClass().add("section-content");
        root.getStyleClass().add("section");
        root.getChildren().addAll(header, content);
    }

    @Override
    public final String title() {
        return title;
    }

    @Override
    public final Node node() {
        return root;
    }

    public final boolean isCollapsed() {
        return collapsed;
    }

    /** A collapsed section shows only its heading. The state is kept while the application runs. */
    public final void setCollapsed(boolean value) {
        collapsed = value;
        content.setVisible(!value);
        content.setManaged(!value);
        chevron.setRotate(value ? -90 : 0);
        root.pseudoClassStateChanged(COLLAPSED, value);
    }

    /** The context of the last update: what the editors of the section write to. */
    protected final InspectorContext context() {
        return context;
    }

    /** The place for buttons at the right of the heading. */
    protected final HBox actions() {
        return actions;
    }

    /** Adds a row with a label and an editor that cannot be animated. */
    protected final FieldRow row(String label, Node editor) {
        FieldRow row = new FieldRow(label, editor, null);
        content.getChildren().add(row);
        return row;
    }

    /** Adds a node across the whole width. */
    protected final void add(Node node) {
        content.getChildren().add(node);
    }

    /** Adds a row for a number property; the units follow the property. */
    protected final PropertyRows.NumberRow number(String key, String label) {
        return register(new PropertyRows.NumberRow(this, key, label, PropertyUnits.of(key)));
    }

    /**
     * Adds a row for a vector property.
     *
     * @param linkable whether to offer the link-proportions toggle
     */
    protected final PropertyRows.Vec2Row vec2(String key, String label, String firstName, String secondName,
                                             boolean linkable) {
        return register(new PropertyRows.Vec2Row(this, key, label, PropertyUnits.of(key), firstName, secondName, linkable));
    }

    /** Adds a row for a colour property. */
    protected final PropertyRows.ColorRow color(String key, String label) {
        return register(new PropertyRows.ColorRow(this, key, label));
    }

    /** Adds a row for a paint property. */
    protected final PropertyRows.PaintRow paint(String key, String label) {
        return register(new PropertyRows.PaintRow(this, key, label));
    }

    private <R extends PropertyRows.PropertyRow> R register(R row) {
        propertyRows.add(row);
        content.getChildren().add(row.row());
        return row;
    }

    /**
     * Removes every row and node of the section, for a section whose rows depend on the
     * selection, such as one block of rows per effect. To be called from {@link #prepare}.
     */
    protected final void clearContent() {
        propertyRows.clear();
        content.getChildren().clear();
    }

    /**
     * Runs at the start of every update, before the property rows are brought up to date: the
     * place to rebuild rows whose set depends on the selection. Must not build nodes when
     * nothing structural changed, because it also runs on every move of the playhead.
     */
    protected void prepare(InspectorContext context) {
    }

    @Override
    public final void update(InspectorContext newContext) {
        context = newContext;
        prepare(newContext);
        for (PropertyRows.PropertyRow row : propertyRows) {
            row.update(newContext);
        }
        updateFields(newContext);
    }

    /** The row of a property of this section, when there is one. */
    public final Optional<PropertyRows.PropertyRow> propertyRow(String key) {
        return propertyRows.stream().filter(row -> row.key().equals(key)).findFirst();
    }

    /** Shows the values of the editors that are not property rows. */
    protected void updateFields(InspectorContext context) {
    }

    /** A new keyframe diamond for a property of this section, for editors built by hand. */
    protected final KeyframeToggle toggleFor(String key) {
        KeyframeToggle toggle = new KeyframeToggle();
        toggle.setOnAction(e -> context.toggleKeyframe(key));
        return toggle;
    }
}
