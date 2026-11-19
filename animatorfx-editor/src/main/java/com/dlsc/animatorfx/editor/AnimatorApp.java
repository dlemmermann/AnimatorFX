package com.dlsc.animatorfx.editor;

import com.dlsc.animatorfx.editor.audio.AudioDiagnosis;
import com.dlsc.animatorfx.editor.session.AppDirs;
import com.dlsc.animatorfx.editor.session.RecoveryStore;
import com.dlsc.animatorfx.editor.session.SessionStore;
import com.dlsc.animatorfx.editor.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * The AnimatorFX editor. Restores the last session on start; {@code .afx} files given as
 * arguments are opened in addition. Quitting does not ask to save: the session and recovery
 * copies of unsaved documents are stored and come back on the next start.
 */
public final class AnimatorApp extends Application {

    private MainWindow window;

    @Override
    public void start(Stage stage) {
        Path configDir = AppDirs.configDir();
        window = new MainWindow(stage, new SessionStore(configDir), new RecoveryStore(configDir.resolve("recovery")));
        for (String argument : getParameters().getUnnamed()) {
            try {
                window.getFileActions().open(Path.of(argument));
            } catch (InvalidPathException e) {
                window.getNoticeBar().show("Could not open " + argument + ": not a valid file name.");
            }
        }
        window.show();
    }

    @Override
    public void stop() {
        if (window != null) {
            window.shutdown();
        }
    }

    public static void main(String[] args) {
        if (AudioDiagnosis.requested(args)) {
            // no window: reports whether JavaFX media works in this runtime, see AudioDiagnosis
            System.exit(AudioDiagnosis.run(System.out));
        }
        launch(args);
    }
}
