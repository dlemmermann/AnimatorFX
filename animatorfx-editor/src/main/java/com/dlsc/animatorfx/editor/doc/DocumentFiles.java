package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationFormatException;
import com.dlsc.animatorfx.model.io.AnimationIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Writes documents so that what is on disk can always be opened again: reading a file checks
 * more than the model and the writer do, so the bytes are read back before they replace a file.
 */
public final class DocumentFiles {

    private DocumentFiles() {
    }

    /**
     * The document as the bytes of an {@code .afx} archive, after reading them back.
     *
     * @throws AnimationFormatException if the document cannot be written or could not be read
     *                                  again; the message says what is wrong and where
     */
    public static byte[] verifiedBytes(AnimationFile file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            AnimationIO.write(file, out);
        } catch (IllegalArgumentException e) {
            throw new AnimationFormatException(e.getMessage(), "", e);
        }
        byte[] bytes = out.toByteArray();
        AnimationIO.read(new ByteArrayInputStream(bytes));
        return bytes;
    }

    /**
     * Writes the document to a temporary file next to the target and moves it into place. A
     * document that fails {@link #verifiedBytes} touches nothing on disk.
     */
    public static void write(AnimationFile file, Path path) throws IOException {
        byte[] bytes = verifiedBytes(file);
        Path target = path.toAbsolutePath();
        Path temp = target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temp, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
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
