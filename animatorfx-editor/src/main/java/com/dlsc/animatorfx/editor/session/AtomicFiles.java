package com.dlsc.animatorfx.editor.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class AtomicFiles {

    private AtomicFiles() {
    }

    /** Writes to a temporary file next to the target and then moves it into place. */
    static void writeString(Path target, String text) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temp, text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
