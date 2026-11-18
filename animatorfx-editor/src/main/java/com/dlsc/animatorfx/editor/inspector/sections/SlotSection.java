package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.canvas.LayerNames;
import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.editor.inspector.FieldRow;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.editor.inspector.fields.TextValueField;
import com.dlsc.animatorfx.model.Animatable;
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

    public SlotSection() {
        super("Slot");
        name.setId(NAME_ID);
        name.getStyleClass().add("mono");
        name.setValidator(text -> {
            SlotLayer slot = (SlotLayer) context().layers().get(0);
            return text.equals(slot.slotName()) ? Optional.empty() : LayerNames.slotNameError(context().animation(), text);
        });
        name.setOnCommit(text -> context().edit("Rename Slot", SlotLayer.class, l -> l.withSlotName(text), null));
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

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.allLayers(SlotLayer.class);
    }

    @Override
    protected void updateFields(InspectorContext context) {
        // slot names are unique, so only one slot at a time can be named
        name.setDisable(context.layers().size() != 1);
        name.setValue(context.common(SlotLayer.class, SlotLayer::slotName));
        width.setValue(context.common(SlotLayer.class, SlotLayer::width));
        height.setValue(context.common(SlotLayer.class, SlotLayer::height));
    }
}
