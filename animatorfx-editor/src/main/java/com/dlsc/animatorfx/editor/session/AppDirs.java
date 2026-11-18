package com.dlsc.animatorfx.editor.session;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Where the editor keeps its per-user state.
 */
public final class AppDirs {

    /** System property that replaces the platform directory, for tests and portable setups. */
    public static final String CONFIG_DIR_PROPERTY = "animatorfx.configDir";

    private AppDirs() {
    }

    /**
     * {@code ~/Library/Application Support/AnimatorFX} on macOS, {@code %APPDATA%\AnimatorFX} on
     * Windows and {@code $XDG_CONFIG_HOME/animatorfx} (default {@code ~/.config/animatorfx})
     * elsewhere, unless the system property {@value #CONFIG_DIR_PROPERTY} names a directory.
     */
    public static Path configDir() {
        String override = System.getProperty(CONFIG_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return configDir(System.getProperty("os.name", ""), System.getProperty("user.home", "."), System.getenv());
    }

    static Path configDir(String osName, String userHome, Map<String, String> environment) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(userHome, "Library", "Application Support", "AnimatorFX");
        }
        if (os.contains("win")) {
            String appData = environment.get("APPDATA");
            return appData == null || appData.isBlank()
                    ? Path.of(userHome, "AppData", "Roaming", "AnimatorFX") : Path.of(appData, "AnimatorFX");
        }
        String xdg = environment.get("XDG_CONFIG_HOME");
        return xdg == null || xdg.isBlank() ? Path.of(userHome, ".config", "animatorfx") : Path.of(xdg, "animatorfx");
    }
}
