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

    /** System property: {@code true} starts the application with its sound switched off. */
    public static final String MUTED_PROPERTY = "animatorfx.sample.muted";

    @Override
    public void start(Stage stage) {
        SampleBrowser browser = new SampleBrowser(Boolean.getBoolean(MUTED_PROPERTY));
        stage.setTitle("AnimatorFX Sample");
        stage.setScene(new Scene(browser, 900, 640));
        // the views are not used again once the window is gone: give their sound back
        stage.setOnHidden(event -> browser.dispose());
        stage.show();
    }
}
