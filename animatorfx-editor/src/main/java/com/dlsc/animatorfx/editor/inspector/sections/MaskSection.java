package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.inspector.FieldRow;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.editor.inspector.fields.Combos;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Mask;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The mask of the one selected layer: another layer of the same parent, chosen from a list
 * (without the layers that the selected one masks itself), and
 * how it masks — Clip uses its outline, Alpha its transparency. The layer that masks is no
 * longer drawn itself, which a line under the choice says; a layer that is used as a mask says
 * by which layers.
 *
 * <p>Shown for a single selected layer, because the choice is among that layer's siblings.
 */
public final class MaskSection extends SectionBase {

    public static final String LAYER_ID = "inspector-mask-layer";
    public static final String CLIP_ID = "inspector-mask-clip";
    public static final String ALPHA_ID = "inspector-mask-alpha";
    public static final String HINT_ID = "inspector-mask-hint";
    public static final String USED_ID = "inspector-mask-used";

    /** An entry of the list; {@code id == null} is "None". */
    public record Choice(String id, String name) {
    }

    private static final Choice NONE = new Choice(null, "None");

    private final ComboBox<Choice> layer = new ComboBox<>();
    private final ToggleButton clip = new ToggleButton("Clip");
    private final ToggleButton alpha = new ToggleButton("Alpha");
    private final FieldRow modeRow;
    private final Label hint = new Label("The mask layer itself is no longer drawn.");
    private final Label used = new Label();
    private boolean updating;

    public MaskSection() {
        super("Mask");
        layer.setId(LAYER_ID);
        layer.setMaxWidth(Double.MAX_VALUE);
        layer.setMinWidth(0);
        layer.setConverter(new StringConverter<>() {
            @Override
            public String toString(Choice choice) {
                return choice == null ? "" : choice.name();
            }

            @Override
            public Choice fromString(String text) {
                return null;
            }
        });
        Combos.compact(layer);
        layer.valueProperty().addListener((obs, old, choice) -> {
            if (!updating && choice != null) {
                setMask(choice.id(), currentMode());
            }
        });
        row("Mask", layer);

        clip.setId(CLIP_ID);
        alpha.setId(ALPHA_ID);
        clip.setTooltip(new Tooltip("The outline of the mask layer cuts this layer"));
        alpha.setTooltip(new Tooltip("The transparency of the mask layer fades this layer"));
        for (ToggleButton button : List.of(clip, alpha)) {
            button.getStyleClass().setAll("segment-button");
            button.setFocusTraversable(false);
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
        }
        clip.setOnAction(e -> chooseMode(Mask.Mode.CLIP));
        alpha.setOnAction(e -> chooseMode(Mask.Mode.ALPHA));
        HBox modes = new HBox(clip, alpha);
        modes.getStyleClass().add("segmented");
        modeRow = row("Mode", modes);

        for (Label label : List.of(hint, used)) {
            label.getStyleClass().add("hint");
            label.setWrapText(true);
            add(label);
        }
        hint.setId(HINT_ID);
        used.setId(USED_ID);
    }

    private Layer selected() {
        return context().layers().get(0);
    }

    private Mask.Mode currentMode() {
        Mask mask = selected().base().mask();
        return mask == null ? Mask.Mode.CLIP : mask.mode();
    }

    private void setMask(String maskLayerId, Mask.Mode mode) {
        context().document().execute(Commands.setMask(selected().id(), maskLayerId == null ? null : new Mask(maskLayerId, mode)));
    }

    private void chooseMode(Mask.Mode mode) {
        Mask mask = selected().base().mask();
        if (mask != null && mask.mode() != mode) {
            setMask(mask.layerId(), mode);
        } else {
            // a click on the active mode keeps it
            showMode(mask == null ? null : mask.mode());
        }
    }

    private void showMode(Mask.Mode mode) {
        clip.setSelected(mode == Mask.Mode.CLIP);
        alpha.setSelected(mode == Mask.Mode.ALPHA);
    }

    /** The other layers of the same parent, top-most first as in the timeline. */
    private static List<Layer> siblings(Animation animation, Layer layer) {
        List<Layer> all = Layers.parentOf(animation, layer.id()).map(GroupLayer::children).orElse(animation.layers());
        List<Layer> others = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (!all.get(i).id().equals(layer.id())) {
                others.add(all.get(i));
            }
        }
        return others;
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.layers().size() == 1;
    }

    @Override
    protected void updateFields(InspectorContext context) {
        Layer selected = selected();
        Animation animation = context.animation();
        List<Choice> choices = new ArrayList<>();
        choices.add(NONE);
        for (Layer sibling : siblings(animation, selected)) {
            // not a layer that this one masks: the two would hide each other
            if (Commands.canMask(animation, selected.id(), sibling.id())) {
                choices.add(new Choice(sibling.id(), sibling.name()));
            }
        }
        Mask mask = selected.base().mask();
        Choice current = mask == null ? NONE
                : choices.stream().filter(c -> mask.layerId().equals(c.id())).findFirst().orElse(NONE);
        updating = true;
        try {
            if (!layer.getItems().equals(choices)) {
                layer.getItems().setAll(choices);
            }
            layer.setValue(current);
            showMode(mask == null ? null : mask.mode());
        } finally {
            updating = false;
        }
        boolean masked = current != NONE;
        modeRow.show(masked);
        hint.setVisible(masked);
        hint.setManaged(masked);
        String users = Layers.flatten(animation).stream()
                .filter(l -> l.base().mask() != null && l.base().mask().layerId().equals(selected.id()))
                .map(l -> "\u201c" + l.name() + "\u201d").collect(Collectors.joining(", "));
        used.setText(users.isEmpty() ? "" : "This layer masks " + users + " and is not drawn itself.");
        used.setVisible(!users.isEmpty());
        used.setManaged(!users.isEmpty());
    }
}
