package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.doc.Command;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Rgba;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The effects of the selected layers, in the order in which they are applied: a heading per
 * effect with buttons to move it up or down and to remove it, and under it the parameters of the
 * effect, each with a keyframe diamond. "Add Effect" appends a Gaussian blur, a drop shadow or a
 * glow.
 *
 * <p>With several layers selected the effects show when all layers have the same kinds of
 * effects in the same order; values that differ show as mixed and every action applies to all of
 * them. Otherwise only "Add Effect" is offered.
 *
 * <p>The property keys of effects are index-based ({@code effects[1].radius}). Reordering and
 * removing work on the list of effects itself, so keyframes stay with their effect.
 */
public final class EffectsSection extends SectionBase {

    public static final String ADD_ID = "inspector-effects-add";
    public static final String ADD_BLUR_ID = "inspector-effects-add-blur";
    public static final String ADD_SHADOW_ID = "inspector-effects-add-shadow";
    public static final String ADD_GLOW_ID = "inspector-effects-add-glow";
    public static final String HINT_ID = "inspector-effects-hint";
    /** The ids of the buttons of an effect are these prefixes followed by the index of the effect. */
    public static final String UP_ID = "inspector-effect-up-";
    public static final String DOWN_ID = "inspector-effect-down-";
    public static final String REMOVE_ID = "inspector-effect-remove-";
    public static final String TITLE_ID = "inspector-effect-title-";

    private final MenuButton add = new MenuButton("Add Effect");
    private final Label hint = new Label();
    private final HBox footer = new HBox(add);
    /** The kinds of effects that the rows were built for; {@code null} for layers that differ. */
    private List<Class<?>> built;
    private boolean builtOnce;

    public EffectsSection() {
        super("Effects");
        add.setId(ADD_ID);
        add.getStyleClass().add("section-menu-button");
        add.setFocusTraversable(false);
        add.getItems().addAll(
                item(ADD_BLUR_ID, "Gaussian Blur", EffectsSection::blur),
                item(ADD_SHADOW_ID, "Drop Shadow", EffectsSection::dropShadow),
                item(ADD_GLOW_ID, "Glow", EffectsSection::glow));
        footer.getStyleClass().add("section-footer");
        hint.setId(HINT_ID);
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
    }

    /** A Gaussian blur of radius 8. */
    public static Effect blur() {
        return new Effect.Blur(Animatable.of(8.0));
    }

    /** A black shadow at 50 % alpha, radius 12, 4 px below the layer, without spread. */
    public static Effect dropShadow() {
        return new Effect.DropShadow(Animatable.of(new Rgba(0, 0, 0, 0.5)), Animatable.of(12.0), Animatable.of(0.0),
                Animatable.of(4.0), Animatable.of(0.0));
    }

    /** A glow of level 50 %. */
    public static Effect glow() {
        return new Effect.Glow(Animatable.of(0.5));
    }

    public static String name(Effect effect) {
        return switch (effect) {
            case Effect.Blur blur -> "Gaussian Blur";
            case Effect.DropShadow shadow -> "Drop Shadow";
            case Effect.Glow glow -> "Glow";
        };
    }

    private MenuItem item(String id, String text, Supplier<Effect> effect) {
        MenuItem item = new MenuItem(text);
        item.setId(id);
        item.setOnAction(e -> onAll("Add Effect", layer -> Commands.addEffect(layer.id(), effect.get())));
        return item;
    }

    /** Runs a command per selected layer as one undo step. */
    private void onAll(String name, Function<Layer, Command> command) {
        List<Command> commands = new ArrayList<>();
        for (Layer layer : context().layers()) {
            commands.add(command.apply(layer));
        }
        context().document().execute(Commands.batch(name, commands));
    }

    /** The kinds of the effects that all selected layers share, in order; {@code null} when they differ. */
    private static List<Class<?>> kinds(InspectorContext context) {
        List<Class<?>> common = null;
        for (Layer layer : context.layers()) {
            List<Class<?>> kinds = new ArrayList<>();
            layer.base().effects().forEach(effect -> kinds.add(effect.getClass()));
            if (common != null && !common.equals(kinds)) {
                return null;
            }
            common = kinds;
        }
        return common;
    }

    @Override
    protected void prepare(InspectorContext context) {
        List<Class<?>> kinds = kinds(context);
        if (builtOnce && Objects.equals(kinds, built)) {
            return;
        }
        built = kinds;
        builtOnce = true;
        clearContent();
        List<Effect> effects = kinds == null ? List.of() : context.layers().get(0).base().effects();
        for (int i = 0; i < effects.size(); i++) {
            build(i, effects.get(i), effects.size());
        }
        hint.setText(kinds == null ? "The selected layers have different effects."
                : "No effects.");
        if (effects.isEmpty()) {
            add(hint);
        }
        add(footer);
    }

    private void build(int index, Effect effect, int count) {
        Label title = new Label(name(effect));
        title.setId(TITLE_ID + index);
        title.getStyleClass().add("effect-title");
        Label number = new Label(String.valueOf(index + 1));
        number.getStyleClass().addAll("effect-index", "mono");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button up = button(UP_ID + index, Material2AL.KEYBOARD_ARROW_UP, "Apply earlier");
        Button down = button(DOWN_ID + index, Material2AL.KEYBOARD_ARROW_DOWN, "Apply later");
        Button remove = button(REMOVE_ID + index, Material2AL.CLOSE, "Remove the effect");
        up.setDisable(index == 0);
        down.setDisable(index == count - 1);
        up.setOnAction(e -> onAll("Reorder Effects", layer -> Commands.moveEffect(layer.id(), index, index - 1)));
        down.setOnAction(e -> onAll("Reorder Effects", layer -> Commands.moveEffect(layer.id(), index, index + 1)));
        remove.setOnAction(e -> onAll("Remove Effect", layer -> Commands.removeEffect(layer.id(), index)));
        HBox header = new HBox(number, title, spacer, up, down, remove);
        header.getStyleClass().add("effect-header");
        if (index == 0) {
            header.getStyleClass().add("first");
        }
        header.setAlignment(Pos.CENTER_LEFT);
        add(header);

        String prefix = "effects[" + index + "].";
        switch (effect) {
            case Effect.Blur blur -> number(prefix + "radius", "Radius").field().setRange(0, Double.MAX_VALUE);
            case Effect.DropShadow shadow -> {
                color(prefix + "color", "Color");
                number(prefix + "radius", "Radius").field().setRange(0, Double.MAX_VALUE);
                number(prefix + "offsetX", "Offset X");
                number(prefix + "offsetY", "Offset Y");
                number(prefix + "spread", "Spread").field().setRange(0, 1);
            }
            case Effect.Glow glow -> number(prefix + "level", "Level").field().setRange(0, 1);
        }
    }

    private static Button button(String id, Ikon icon, String tooltip) {
        Button button = new Button(null, new FontIcon(icon));
        button.setId(id);
        button.getStyleClass().setAll("icon-button", "section-toggle");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.allVisual();
    }
}
