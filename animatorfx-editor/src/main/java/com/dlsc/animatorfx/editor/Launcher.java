package com.dlsc.animatorfx.editor;

import javafx.application.Application;

/**
 * Starts the editor from a plain class, so that it also runs from the class path, where the JVM
 * refuses a main class that extends {@code Application}.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(AnimatorApp.class, args);
    }
}
