package com.dlsc.animatorfx.player.internal;

import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.player.LayerState.EffectState;
import com.dlsc.animatorfx.player.render.PaintConverter;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.effect.Glow;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The JavaFX effects of one layer. The first effect of the list is applied first, so it is the
 * innermost input; the node carries the last one. Shared by the renderer and in-place slot bindings.
 */
public final class EffectChain {

    private javafx.scene.effect.Effect[] chain = {};
    private List<EffectState> last = List.of();

    /**
     * Writes the effects to the node. The effect instances are kept as long as the list has the
     * same effect types; the node's effect is only replaced when it has not.
     */
    public void apply(Node node, List<EffectState> states) {
        if (states == last) {
            return;
        }
        boolean rebuilt = !matches(states);
        if (rebuilt) {
            chain = new javafx.scene.effect.Effect[states.size()];
            for (int i = 0; i < chain.length; i++) {
                chain[i] = create(states.get(i).source(), i == 0 ? null : chain[i - 1]);
            }
            node.setEffect(chain.length == 0 ? null : chain[chain.length - 1]);
        }
        for (int i = 0; i < chain.length; i++) {
            EffectState state = states.get(i);
            if (rebuilt || !sameValues(state, last.get(i))) {
                write(chain[i], state);
            }
        }
        last = states;
    }

    private boolean matches(List<EffectState> states) {
        if (states.size() != chain.length) {
            return false;
        }
        for (int i = 0; i < chain.length; i++) {
            if (type(states.get(i).source()) != chain[i].getClass()) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameValues(EffectState a, EffectState b) {
        return a == b || (Arrays.equals(a.values(), b.values()) && Objects.equals(a.color(), b.color()));
    }

    private static Class<?> type(Effect source) {
        return switch (source) {
            case Effect.Blur blur -> GaussianBlur.class;
            case Effect.DropShadow shadow -> DropShadow.class;
            case Effect.Glow glow -> Glow.class;
        };
    }

    private static javafx.scene.effect.Effect create(Effect source, javafx.scene.effect.Effect input) {
        switch (source) {
            case Effect.Blur blur -> {
                GaussianBlur effect = new GaussianBlur();
                effect.setInput(input);
                return effect;
            }
            case Effect.DropShadow shadow -> {
                DropShadow effect = new DropShadow();
                effect.setInput(input);
                return effect;
            }
            case Effect.Glow glow -> {
                Glow effect = new Glow();
                effect.setInput(input);
                return effect;
            }
        }
    }

    private static void write(javafx.scene.effect.Effect effect, EffectState state) {
        double[] values = state.values();
        switch (effect) {
            case GaussianBlur blur -> blur.setRadius(values[0]);
            case DropShadow shadow -> {
                shadow.setRadius(values[0]);
                shadow.setOffsetX(values[1]);
                shadow.setOffsetY(values[2]);
                shadow.setSpread(values[3]);
                shadow.setColor(PaintConverter.toFx(state.color()));
            }
            case Glow glow -> glow.setLevel(values[0]);
            default -> throw new IllegalStateException("Unexpected effect: " + effect);
        }
    }
}
