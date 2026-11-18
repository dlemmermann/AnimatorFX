package com.dlsc.animatorfx.editor.session;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppDirsTest {

    @Test
    void theSystemPropertyOverridesThePlatformDirectory() {
        String previous = System.getProperty(AppDirs.CONFIG_DIR_PROPERTY);
        System.setProperty(AppDirs.CONFIG_DIR_PROPERTY, "/tmp-like/custom");
        try {
            assertEquals(Path.of("/tmp-like/custom"), AppDirs.configDir());
        } finally {
            if (previous == null) {
                System.clearProperty(AppDirs.CONFIG_DIR_PROPERTY);
            } else {
                System.setProperty(AppDirs.CONFIG_DIR_PROPERTY, previous);
            }
        }
    }

    @Test
    void macOsUsesApplicationSupport() {
        assertEquals(Path.of("/Users/ann/Library/Application Support/AnimatorFX"),
                AppDirs.configDir("Mac OS X", "/Users/ann", Map.of()));
    }

    @Test
    void windowsUsesAppDataWithAFallbackBelowTheHomeDirectory() {
        assertEquals(Path.of("C:/Users/ann/AppData/Roaming", "AnimatorFX"),
                AppDirs.configDir("Windows 11", "C:/Users/ann", Map.of("APPDATA", "C:/Users/ann/AppData/Roaming")));
        assertEquals(Path.of("C:/Users/ann", "AppData", "Roaming", "AnimatorFX"),
                AppDirs.configDir("Windows 11", "C:/Users/ann", Map.of()));
    }

    @Test
    void linuxUsesXdgConfigHomeWithAFallbackToDotConfig() {
        assertEquals(Path.of("/xdg/animatorfx"),
                AppDirs.configDir("Linux", "/home/ann", Map.of("XDG_CONFIG_HOME", "/xdg")));
        assertEquals(Path.of("/home/ann/.config/animatorfx"), AppDirs.configDir("Linux", "/home/ann", Map.of()));
        assertEquals(Path.of("/home/ann/.config/animatorfx"),
                AppDirs.configDir("Linux", "/home/ann", Map.of("XDG_CONFIG_HOME", "")));
    }
}
