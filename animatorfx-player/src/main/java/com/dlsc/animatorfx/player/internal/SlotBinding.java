package com.dlsc.animatorfx.player.internal;

import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.player.LayerState;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.Effect;

/**
 * A node bound to a slot, either embedded in the view or driven in place.
 * <p>
 * An in-place binding remembers the node's values at bind time and puts them back on
 * {@link #release()}. An embedded binding does not touch the node's properties: the slot's
 * container carries transform, opacity, mask and effects.
 */
public final class SlotBinding {

    private final Node node;
    private final boolean embedded;

    private final double translateX;
    private final double translateY;
    private final double scaleX;
    private final double scaleY;
    private final double rotate;
    private final double opacity;
    private final boolean visible;
    private final Effect effect;

    private EffectChain effects = new EffectChain();
    private boolean effectReplaced;
    private Group container;

    private SlotBinding(Node node, boolean embedded) {
        this.node = node;
        this.embedded = embedded;
        translateX = node.getTranslateX();
        translateY = node.getTranslateY();
        scaleX = node.getScaleX();
        scaleY = node.getScaleY();
        rotate = node.getRotate();
        opacity = node.getOpacity();
        visible = node.isVisible();
        effect = node.getEffect();
    }

    public static SlotBinding inPlace(Node node) {
        return new SlotBinding(node, false);
    }

    public static SlotBinding embedded(Node node) {
        return new SlotBinding(node, true);
    }

    public boolean isEmbedded() {
        return embedded;
    }

    /**
     * Drives an in-place node relative to the slot's rest transform (its pose at time 0): only
     * the slot's own transform counts, not that of its parent groups; skew and masks are ignored.
     * Does nothing for an embedded binding.
     */
    public void apply(SlotLayer slot, LayerState state) {
        if (embedded) {
            return;
        }
        Transform rest = slot.restTransform();
        Vec2 restPosition = rest.position().at(0);
        Vec2 restScale = rest.scale().at(0);
        node.setTranslateX(state.position().x() - restPosition.x());
        node.setTranslateY(state.position().y() - restPosition.y());
        node.setScaleX(ratio(state.scale().x(), restScale.x()));
        node.setScaleY(ratio(state.scale().y(), restScale.y()));
        node.setRotate(state.rotation() - rest.rotation().at(0));
        node.setOpacity(opacity * state.opacity());
        node.setVisible(state.visible());
        if (!state.effects().isEmpty()) {
            effects.apply(node, state.effects());
            effectReplaced = true;
        } else if (effectReplaced) {
            // a slot without effects leaves the node its own effect
            node.setEffect(effect);
            effects = new EffectChain();
            effectReplaced = false;
        }
    }

    private static double ratio(double value, double rest) {
        return rest == 0 ? 1 : value / rest;
    }

    /**
     * Makes an embedded node the only child of the slot's container and gives a resizable node
     * the slot's size. Does nothing for an in-place binding.
     */
    public void attach(Group container, double width, double height) {
        if (embedded && container != null) {
            if (node.getParent() != container) {
                detach();
                container.getChildren().setAll(node);
            }
            this.container = container;
            if (node.isResizable()) {
                node.resize(width, height);
            }
        }
    }

    /**
     * Ends the binding: an in-place node gets back the values it had at bind time, an embedded
     * node leaves the view (it is not returned to its former parent and keeps its size).
     */
    public void release() {
        if (embedded) {
            detach();
            return;
        }
        node.setTranslateX(translateX);
        node.setTranslateY(translateY);
        node.setScaleX(scaleX);
        node.setScaleY(scaleY);
        node.setRotate(rotate);
        node.setOpacity(opacity);
        node.setVisible(visible);
        node.setEffect(effect);
    }

    private void detach() {
        if (container != null) {
            container.getChildren().remove(node);
            container = null;
        }
    }
}
