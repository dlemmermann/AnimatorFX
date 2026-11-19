package com.dlsc.animatorfx.player.internal.audio;

import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.io.AnimationFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * JavaFX media plays from a URI, not from bytes: writes the audio assets of one document to a
 * private temporary directory, each when it is first asked for. The directory is removed by
 * {@link #dispose()} and, should that not happen, when the JVM exits.
 */
final class AudioFiles {

    /** The directories still to be removed. */
    private static final Set<Path> LIVE = ConcurrentHashMap.newKeySet();
    private static boolean hookInstalled;

    private final AnimationFile file;
    private final System.Logger logger;
    /** An empty entry is an asset that could not be written. */
    private final Map<String, Optional<URI>> uris = new HashMap<>();
    private final Set<String> names = new HashSet<>();
    private Path directory;
    private boolean disposed;

    AudioFiles(AnimationFile file, System.Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /**
     * Where the content of an asset can be read from; empty, and logged once, when the document
     * has no such asset or no content for it, or when it cannot be written.
     */
    Optional<URI> uri(String assetId) {
        if (disposed) {
            return Optional.empty();
        }
        return uris.computeIfAbsent(assetId, this::write);
    }

    private Optional<URI> write(String assetId) {
        byte[] bytes = file.assets().get(assetId);
        if (bytes == null) {
            logger.log(System.Logger.Level.WARNING, "Audio asset ''{0}'' has no content and is skipped", assetId);
            return Optional.empty();
        }
        try {
            if (directory == null) {
                directory = Files.createTempDirectory("animatorfx-audio");
                deleteAtExit(directory);
            }
            Path target = directory.resolve(fileName(assetId));
            Files.write(target, bytes);
            return Optional.of(target.toUri());
        } catch (IOException | RuntimeException e) {
            logger.log(System.Logger.Level.WARNING, "Audio asset '" + assetId + "' could not be extracted", e);
            return Optional.empty();
        }
    }

    /** The asset id plus the extension of the asset's path, by which the media stack tells the format. */
    private String fileName(String assetId) {
        String extension = "";
        for (Asset asset : file.animation().assets()) {
            if (asset.id().equals(assetId)) {
                String path = asset.path();
                int dot = path.lastIndexOf('.');
                if (dot > path.lastIndexOf('/') && path.substring(dot + 1).matches("[A-Za-z0-9]{1,8}")) {
                    extension = path.substring(dot).toLowerCase();
                }
                break;
            }
        }
        String base = assetId.replaceAll("[^A-Za-z0-9_-]", "_");
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        String name = base + extension;
        for (int i = 2; !names.add(name); i++) {
            name = base + "-" + i + extension;
        }
        return name;
    }

    void dispose() {
        disposed = true;
        uris.clear();
        if (directory != null) {
            Path old = directory;
            directory = null;
            if (delete(old)) {
                LIVE.remove(old);
            }
        }
    }

    private static synchronized void deleteAtExit(Path directory) {
        LIVE.add(directory);
        if (!hookInstalled) {
            hookInstalled = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> LIVE.forEach(AudioFiles::delete),
                    "animatorfx-audio-cleanup"));
        }
    }

    /** Whether the directory is gone; a file may still be held open by the media stack. */
    private static boolean delete(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            files.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            Files.deleteIfExists(directory);
            return true;
        } catch (IOException | UncheckedIOException | SecurityException e) {
            return false;
        }
    }
}
