package com.dlsc.animatorfx.editor.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * The look of the editor: AtlantaFX Primer Dark or Primer Light as the user-agent stylesheet plus
 * {@code editor.css}, whose looked-up colours switch with the style class {@code light} on the
 * scene root.
 */
public final class Theme {

    public enum Mode {
        DARK, LIGHT,
        /** Follows {@code Platform.getPreferences().colorSchemeProperty()}. */
        SYSTEM
    }

    /** Style class of the scene root while the light palette is active. */
    public static final String LIGHT_STYLE_CLASS = "light";

    private static final String STYLESHEET = Objects.requireNonNull(
            Theme.class.getResource("/com/dlsc/animatorfx/editor/editor.css"), "editor.css").toExternalForm();
    private static final List<String> MONO_FAMILIES = List.of("JetBrains Mono", "SF Mono", "Menlo", "Consolas",
            "DejaVu Sans Mono", "Liberation Mono");

    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(this, "mode", Mode.SYSTEM);
    private final ReadOnlyBooleanWrapper dark = new ReadOnlyBooleanWrapper(this, "dark");
    private final List<Scene> scenes = new ArrayList<>();
    private final InvalidationListener update = obs -> apply();
    private String monoStylesheet;

    public Theme() {
        mode.addListener(update);
        Platform.getPreferences().colorSchemeProperty().addListener(update);
        apply();
    }

    /**
     * The theme setting: dark, light or follow the system.
     *
     * @return the mode property
     */
    public final ObjectProperty<Mode> modeProperty() {
        return mode;
    }

    public final Mode getMode() {
        return mode.get();
    }

    public final void setMode(Mode value) {
        mode.set(Objects.requireNonNull(value, "mode"));
    }

    /**
     * Whether the dark palette is in effect, after resolving {@link Mode#SYSTEM}.
     *
     * @return the dark property
     */
    public final ReadOnlyBooleanProperty darkProperty() {
        return dark.getReadOnlyProperty();
    }

    public final boolean isDark() {
        return dark.get();
    }

    /**
     * Switches between dark and light, leaving {@link Mode#SYSTEM} for the opposite of what is shown.
     */
    public void toggle() {
        setMode(isDark() ? Mode.LIGHT : Mode.DARK);
    }

    /**
     * Styles the scene with {@code editor.css} and keeps the {@code light} style class of its
     * root in step with the theme until {@link #uninstall}.
     */
    public void install(Scene scene) {
        if (!scene.getStylesheets().contains(STYLESHEET)) {
            scene.getStylesheets().addAll(STYLESHEET, monoStylesheet());
        }
        scenes.add(scene);
        applyTo(scene);
    }

    public void uninstall(Scene scene) {
        scenes.remove(scene);
    }

    /**
     * Stops following the system colour scheme.
     */
    public void dispose() {
        Platform.getPreferences().colorSchemeProperty().removeListener(update);
        scenes.clear();
    }

    /**
     * The mode with that name; the fallback for {@code null} and unknown names.
     */
    public static Mode parseMode(String name, Mode fallback) {
        for (Mode mode : Mode.values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return fallback;
    }

    private void apply() {
        boolean isDark = getMode() == Mode.DARK
                || (getMode() == Mode.SYSTEM && Platform.getPreferences().getColorScheme() == ColorScheme.DARK);
        dark.set(isDark);
        String userAgent = isDark ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet();
        if (!userAgent.equals(Application.getUserAgentStylesheet())) {
            Application.setUserAgentStylesheet(userAgent);
        }
        scenes.forEach(this::applyTo);
    }

    private void applyTo(Scene scene) {
        Parent root = scene.getRoot();
        if (isDark()) {
            root.getStyleClass().remove(LIGHT_STYLE_CLASS);
        } else if (!root.getStyleClass().contains(LIGHT_STYLE_CLASS)) {
            root.getStyleClass().add(LIGHT_STYLE_CLASS);
        }
    }

    /** CSS cannot name fallback font families, so the first installed monospaced family goes into a generated rule. */
    private String monoStylesheet() {
        if (monoStylesheet == null) {
            List<String> installed = Font.getFamilies();
            String family = MONO_FAMILIES.stream().filter(installed::contains).findFirst().orElse("Monospaced");
            String css = ".mono, .mono .text { -fx-font-family: \"" + family + "\"; }";
            monoStylesheet = "data:text/css;base64,"
                    + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
        }
        return monoStylesheet;
    }
}
