package com.dlsc.animatorfx.sample;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.AnimationPlayer;
import com.dlsc.animatorfx.player.AnimationView;
import com.dlsc.animatorfx.player.Fit;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.Locale;

/**
 * One sample: an {@link AnimationView} with transport controls. The card sample gets a real
 * label and a real button embedded into its two slots.
 */
public final class SampleShowcase extends BorderPane {

    static final String TITLE_STYLE = "-fx-text-fill: white; -fx-font-size: 19;";

    private final AnimationView view;
    private final Label title = new Label("Hello, AnimatorFX");
    private final Button action = new Button("Replay");

    public SampleShowcase(String name, AnimationFile file) {
        Animation animation = file.animation();
        view = new AnimationView(file);
        view.setFit(Fit.CONTAIN);
        view.setCycleCount(AnimationPlayer.INDEFINITE);
        view.setAutoPlay(true);

        if (name.equals("card")) {
            title.setId("card-title");
            title.setStyle(TITLE_STYLE);
            action.setId("card-action");
            action.setOnAction(event -> restart());
            // embedded: the nodes become part of the animation and follow the slot layers
            view.bind("title", title);
            view.bind("action", action);
        }

        StackPane stage = new StackPane(view);
        stage.getStyleClass().add("sample-stage");
        stage.setPadding(new Insets(24));
        stage.setMinSize(0, 0);
        setCenter(stage);

        Button play = new Button();
        play.setId("play");
        play.setPrefWidth(70);
        play.textProperty().bind(Bindings.when(view.statusProperty().isEqualTo(AnimationPlayer.Status.PLAYING))
                .then("Pause").otherwise("Play"));
        play.setOnAction(event -> {
            if (view.getStatus() == AnimationPlayer.Status.PLAYING) {
                view.pause();
            } else {
                view.play();
            }
        });

        Slider time = new Slider(0, animation.duration(), 0);
        time.setId("time");
        // moving the slider seeks; playback moves the slider
        time.valueProperty().bindBidirectional(view.currentTimeProperty());
        HBox.setHgrow(time, Priority.ALWAYS);

        Label timecode = new Label();
        timecode.setId("timecode");
        timecode.setMinWidth(90);
        timecode.textProperty().bind(Bindings.createStringBinding(
                () -> String.format(Locale.ROOT, "%.2f / %.2f s", view.getCurrentTime(), animation.duration()),
                view.currentTimeProperty()));

        CheckBox loop = new CheckBox("Loop");
        loop.setId("loop");
        loop.setSelected(true);
        loop.selectedProperty().addListener((observable, old, selected) ->
                view.setCycleCount(selected ? AnimationPlayer.INDEFINITE : 1));

        ChoiceBox<Double> rate = new ChoiceBox<>();
        rate.setId("rate");
        rate.getItems().addAll(0.25, 0.5, 1.0, 2.0);
        rate.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                if (value == null) {
                    return "";
                }
                return (value % 1 == 0 ? String.valueOf(value.intValue()) : String.valueOf(value)) + "×";
            }

            @Override
            public Double fromString(String text) {
                return Double.valueOf(text.replace("×", ""));
            }
        });
        rate.setValue(1.0);
        rate.valueProperty().addListener((observable, old, value) -> view.setRate(value));

        HBox transport = new HBox(10, play, time, timecode, loop, rate);
        transport.setAlignment(Pos.CENTER_LEFT);

        HBox markers = new HBox(8);
        markers.setId("markers");
        markers.setAlignment(Pos.CENTER_LEFT);
        Button whole = new Button("Whole animation");
        whole.setOnAction(event -> restart());
        markers.getChildren().addAll(new Label("Play:"), whole);
        for (Marker marker : animation.markers()) {
            Button button = new Button(marker.name());
            button.setId("marker-" + marker.name());
            button.setOnAction(event -> view.playMarker(marker.name()));
            markers.getChildren().add(button);
        }

        VBox controls = new VBox(10, transport, markers);
        controls.setPadding(new Insets(12));
        setBottom(controls);
    }

    public AnimationView getView() {
        return view;
    }

    private void restart() {
        view.stop();
        view.play();
    }
}
