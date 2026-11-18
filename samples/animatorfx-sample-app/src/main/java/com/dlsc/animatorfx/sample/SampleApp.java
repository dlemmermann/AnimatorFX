package com.dlsc.animatorfx.sample;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Plays the sample animations with nothing but {@code animatorfx-player} and JavaFX.
 */
public final class SampleApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("AnimatorFX Sample");
        stage.setScene(new Scene(new SampleBrowser(), 900, 640));
        stage.show();
    }
}
