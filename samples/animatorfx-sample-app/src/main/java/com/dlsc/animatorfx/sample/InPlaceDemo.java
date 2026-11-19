package com.dlsc.animatorfx.sample;

import com.dlsc.animatorfx.player.AnimationPlayer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * In-place binding: the button stays where the application's own layout puts it, and an
 * {@link AnimationPlayer} without any view drives its translation, scale, rotation and opacity
 * from the slot of {@code attention.afx}. The animation carries a sound cue at its start, which
 * the player plays as well.
 */
public final class InPlaceDemo extends HBox {

    private static final String SLOT = "target";

    private final AnimationPlayer player = new AnimationPlayer(Samples.load(Samples.ATTENTION));
    private final Button button = new Button("Click me");

    public InPlaceDemo() {
        super(12);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(14, 16, 14, 16));
        button.setId("in-place-button");
        button.setOnAction(event -> wiggle());
        // unbinding gives the button back exactly as it was before the animation took it
        player.setOnFinished(event -> player.unbind(SLOT));
        getChildren().addAll(new Label("In place:"), button,
                new Label("an ordinary button of this layout, animated by an AnimationPlayer when clicked"));
    }

    public Button getButton() {
        return button;
    }

    public AnimationPlayer getPlayer() {
        return player;
    }

    private void wiggle() {
        player.bindInPlace(SLOT, button);
        player.stop();
        player.play();
    }
}
