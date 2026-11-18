package com.dlsc.animatorfx.editor.inspector.fields;

import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

/**
 * The keyframe diamond next to an animatable value: hollow when the property has no keyframe at
 * the playhead, dimmed amber when the property is animated but has no keyframe here, filled amber
 * when there is a keyframe at the playhead. A click fires its action, which adds or removes the
 * keyframe.
 */
public final class KeyframeToggle extends Button {

    public enum State {
        /** Not animated. */
        NONE,
        /** Animated, without a keyframe at the playhead. */
        ANIMATED,
        /** A keyframe at the playhead. */
        KEYED
    }

    private static final PseudoClass ANIMATED = PseudoClass.getPseudoClass("animated");
    private static final PseudoClass KEYED = PseudoClass.getPseudoClass("keyed");

    private State state = State.NONE;

    public KeyframeToggle() {
        Region diamond = new Region();
        diamond.getStyleClass().add("diamond");
        setGraphic(diamond);
        getStyleClass().setAll("keyframe-toggle");
        setFocusTraversable(false);
        setTooltip(new Tooltip("Add or remove a keyframe at the playhead"));
    }

    public State getState() {
        return state;
    }

    public void setState(State value) {
        state = value;
        pseudoClassStateChanged(ANIMATED, value == State.ANIMATED);
        pseudoClassStateChanged(KEYED, value == State.KEYED);
    }
}
