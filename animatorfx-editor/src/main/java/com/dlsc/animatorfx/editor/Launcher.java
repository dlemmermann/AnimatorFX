package com.dlsc.animatorfx.editor;

import com.dlsc.animatorfx.editor.audio.AudioDiagnosis;
import javafx.application.Application;

/**
 * Starts the editor from a plain class, so that it also runs from the class path, where the JVM
 * refuses a main class that extends {@code Application}.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        if (AudioDiagnosis.requested(args)) {
            System.exit(AudioDiagnosis.run(System.out));
        }
        Application.launch(AnimatorApp.class, args);
    }
}
