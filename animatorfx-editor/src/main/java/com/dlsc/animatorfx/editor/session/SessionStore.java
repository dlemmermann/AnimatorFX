package com.dlsc.animatorfx.editor.session;

import com.dlsc.animatorfx.model.io.Json;
import com.dlsc.animatorfx.model.io.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Reads and writes {@code session.json} in the configuration directory.
 */
public final class SessionStore {

    private static final System.Logger LOG = System.getLogger(SessionStore.class.getName());
    private static final String FILE_NAME = "session.json";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path file;

    public SessionStore(Path dir) {
        this.file = Objects.requireNonNull(dir, "dir").resolve(FILE_NAME);
    }

    /**
     * Never fails: a missing file gives the defaults, and a corrupt or unreadable file is renamed
     * to {@code session.json.corrupt-<timestamp>} before the defaults are returned.
     */
    public SessionState load() {
        if (!Files.exists(file)) {
            return SessionState.defaults();
        }
        try {
            return SessionState.fromJson(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Session state is unreadable, using defaults: " + file + " (" + e + ")");
            moveAside();
            return SessionState.defaults();
        }
    }

    /**
     * Writes the state atomically: to a temporary file that is then moved into place.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    public void save(SessionState state) {
        try {
            AtomicFiles.writeString(file, JsonWriter.write(state.toJson()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void moveAside() {
        String base = FILE_NAME + ".corrupt-" + LocalDateTime.now().format(TIMESTAMP);
        try {
            Path target = file.resolveSibling(base);
            for (int i = 1; Files.exists(target); i++) {
                target = file.resolveSibling(base + "-" + i);
            }
            Files.move(file, target);
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not rename the unreadable session state: " + file + " (" + e + ")");
        }
    }
}
