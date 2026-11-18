package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.canvas.LayerNames;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.editor.inspector.FieldRow;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.editor.inspector.fields.TextValueField;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * The slot name (a Java identifier that is unique in the document), the reference size, and the
 * rest pose of the selected slots. The rest pose is the transform that in-place bound nodes are
 * animated relative to; the button sets it to the transform at the playhead.
 */
public final class SlotSection extends SectionBase {

    public static final String NAME_ID = "inspector-slot-name";
    public static final String WIDTH_ID = "inspector-slot-width";
    public static final String HEIGHT_ID = "inspector-slot-height";
    public static final String REST_ID = "inspector-slot-rest";

    private final TextValueField name = new TextValueField();
    private final NumberField width = new NumberField(PropertyUnits.NONE);
    private final NumberField height = new NumberField(PropertyUnits.NONE);
    private String editedSlotId;

    public SlotSection() {
        super("Slot");
        name.setId(NAME_ID);
        name.getStyleClass().add("mono");
        name.setValidator(text -> edited().filter(slot -> !text.equals(slot.slotName()))
                .flatMap(slot -> LayerNames.slotNameError(context().animation(), text)));
        // a commit on blur can come after the selection or the document changed: it renames the
        // slot the name was typed for, or nothing when that slot is gone
        name.setOnCommit(text -> edited().ifPresent(slot -> context().document().execute(
                Commands.updateLayer("Rename Slot", slot.id(), l -> l instanceof SlotLayer s ? s.withSlotName(text) : l))));
        name.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && context() != null) {
                editedSlotId = selectedSlotId(context());
            }
        });
        row("Name", name);

        width.setId(WIDTH_ID);
        height.setId(HEIGHT_ID);
        for (NumberField field : new NumberField[]{width, height}) {
            field.setRange(0, Double.MAX_VALUE);
            field.setMaxWidth(Double.MAX_VALUE);
            field.setMinWidth(0);
            HBox.setHgrow(field, Priority.ALWAYS);
            field.setOnAdjustEnd(() -> context().endGesture());
        }
        width.setOnChange((value, adjusting) -> context().edit("Change Width", SlotLayer.class,
                l -> l.withWidth(value), adjusting ? width : null));
        height.setOnChange((value, adjusting) -> context().edit("Change Height", SlotLayer.class,
                l -> l.withHeight(value), adjusting ? height : null));
        HBox size = new HBox(width, height);
        size.getStyleClass().add("vec2-field");
        FieldRow row = row("Size", size);
        width.installScrubbing(row.getLabel());

        Button rest = new Button("Set Rest Pose from Current");
        rest.setId(REST_ID);
        rest.getStyleClass().add("section-button");
        rest.setTooltip(new Tooltip("Nodes bound in place are animated relative to the rest pose"));
        rest.setOnAction(e -> {
            double time = context().time();
            context().edit("Set Rest Pose", SlotLayer.class, l -> {
                Transform t = l.base().transform();
                return l.withRestTransform(new Transform(Animatable.of(t.anchor().at(time)),
                        Animatable.of(t.position().at(time)), Animatable.of(t.scale().at(time)),
                        Animatable.of(t.rotation().at(time)), Animatable.of(t.skew().at(time))));
            }, null);
        });
        add(rest);
    }

    /** The slot whose name is being edited, if the document still has it. */
    private Optional<SlotLayer> edited() {
        if (context() == null || editedSlotId == null) {
            return Optional.empty();
        }
        return Layers.find(context().animation(), editedSlotId)
                .filter(SlotLayer.class::isInstance).map(SlotLayer.class::cast);
    }

    /** The id of the selected slot when exactly one is selected, else {@code null}. */
    private static String selectedSlotId(InspectorContext context) {
        return context.layers().size() == 1 && context.layers().get(0) instanceof SlotLayer slot ? slot.id() : null;
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.allLayers(SlotLayer.class);
    }

    @Override
    protected void updateFields(InspectorContext context) {
        // slot names are unique, so only one slot at a time can be named
        boolean typing = name.isFocused() && !java.util.Objects.equals(name.getText(), name.getValue());
        if (!typing) {
            // typed text stays with the slot it was typed for
            editedSlotId = selectedSlotId(context);
        }
        name.setDisable(context.layers().size() != 1);
        name.setValue(context.common(SlotLayer.class, SlotLayer::slotName));
        width.setValue(context.common(SlotLayer.class, SlotLayer::width));
        height.setValue(context.common(SlotLayer.class, SlotLayer::height));
    }
}
