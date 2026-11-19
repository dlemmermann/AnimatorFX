package com.dlsc.animatorfx.player.internal.audio;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.io.AnimationFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Audio assets as temporary files. */
class AudioFilesTest {

    static final class RecordingLogger implements System.Logger {

        final List<String> warnings = new ArrayList<>();

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
            if (level == Level.WARNING) {
                warnings.add(message + (thrown == null ? "" : ": " + thrown));
            }
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            if (level == Level.WARNING) {
                warnings.add(java.text.MessageFormat.format(format, params));
            }
        }
    }

    private final RecordingLogger logger = new RecordingLogger();

    private static AnimationFile file() {
        Animation animation = Animation.create("test", 10, 10, 1).withAssets(List.of(
                new Asset("snd", Asset.Kind.AUDIO, "assets/snd.WAV", "snd.wav"),
                new Asset("a/b", Asset.Kind.AUDIO, "assets/one.mp3", "one.mp3"),
                new Asset("a?b", Asset.Kind.AUDIO, "assets/two.mp3", "two.mp3"),
                new Asset("gone", Asset.Kind.AUDIO, "assets/gone.wav", "gone.wav")));
        return new AnimationFile(animation, Map.of("snd", new byte[]{1, 2, 3}, "a/b", new byte[]{4},
                "a?b", new byte[]{5}));
    }

    @Test
    void anAssetIsWrittenOnceUnderItsIdWithTheExtensionOfItsPath() throws IOException {
        AudioFiles files = new AudioFiles(file(), logger);

        URI uri = files.uri("snd").orElseThrow();
        Path path = Path.of(uri);

        assertEquals("snd.wav", path.getFileName().toString());
        assertTrue(path.getParent().getFileName().toString().startsWith("animatorfx-audio"));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(path));
        assertEquals(uri, files.uri("snd").orElseThrow());
        files.dispose();
    }

    @Test
    void idsThatAreNoFileNamesStillGetFilesOfTheirOwn() throws IOException {
        AudioFiles files = new AudioFiles(file(), logger);

        Path one = Path.of(files.uri("a/b").orElseThrow());
        Path two = Path.of(files.uri("a?b").orElseThrow());

        assertNotEquals(one, two);
        assertEquals(one.getParent(), two.getParent());
        assertTrue(one.getFileName().toString().endsWith(".mp3"));
        assertArrayEquals(new byte[]{4}, Files.readAllBytes(one));
        assertArrayEquals(new byte[]{5}, Files.readAllBytes(two));
        files.dispose();
    }

    @Test
    void anAssetWithoutContentIsLoggedOnceAndSkipped() {
        AudioFiles files = new AudioFiles(file(), logger);

        assertEquals(Optional.empty(), files.uri("gone"));
        assertEquals(Optional.empty(), files.uri("gone"));
        assertEquals(Optional.empty(), files.uri("unknown"));

        assertEquals(2, logger.warnings.size(), logger.warnings.toString());
        files.dispose();
    }

    @Test
    void disposeRemovesTheFilesAndTheDirectory() {
        AudioFiles files = new AudioFiles(file(), logger);
        Path path = Path.of(files.uri("snd").orElseThrow());

        files.dispose();
        files.dispose();

        assertFalse(Files.exists(path));
        assertFalse(Files.exists(path.getParent()));
        assertEquals(Optional.empty(), files.uri("snd"));
    }
}
