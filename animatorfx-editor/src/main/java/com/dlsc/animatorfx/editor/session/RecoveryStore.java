package com.dlsc.animatorfx.editor.session;

import com.dlsc.animatorfx.editor.doc.DocumentFiles;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationIO;
import com.dlsc.animatorfx.model.io.Json;
import com.dlsc.animatorfx.model.io.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Recovery copies of dirty documents: one {@code <docId>.afx} per document plus {@code index.json},
 * which remembers the file each copy belongs to.
 */
public class RecoveryStore {

    /**
     * @param originalPath the file the document belongs to, or {@code null} for an untitled document
     */
    public record Recovered(String docId, AnimationFile file, Path originalPath) {
    }

    /**
     * A recovery copy that could not be read and was renamed so that it is neither offered
     * again nor deleted.
     *
     * @param originalPath the file the document belongs to, or {@code null} for an untitled document
     * @param movedTo      where the copy is now
     */
    public record Unreadable(String docId, Path originalPath, Path movedTo) {
    }

    private static final System.Logger LOG = System.getLogger(RecoveryStore.class.getName());
    private static final String INDEX = "index.json";
    private static final String EXTENSION = ".afx";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private final Path dir;
    private final List<Unreadable> unreadable = new ArrayList<>();

    public RecoveryStore(Path dir) {
        this.dir = Objects.requireNonNull(dir, "dir");
    }

    /**
     * Stores or replaces the recovery copy of a document. The copy is read back before it is
     * written: a document that could not be recovered from it leaves the previous copy in place.
     *
     * @throws IllegalArgumentException if the id is not usable as a file name
     * @throws UncheckedIOException     if the copy cannot be written; its cause is an
     *                                  {@link com.dlsc.animatorfx.model.io.AnimationFormatException}
     *                                  when the document could not be read back
     */
    public synchronized void store(String docId, AnimationFile file, Path originalPathOrNull) {
        if (!isSafe(docId)) {
            throw new IllegalArgumentException("Not a usable document id: " + docId);
        }
        try {
            Files.createDirectories(dir);
            DocumentFiles.write(file, copyOf(docId));
            Map<String, String> index = readIndex();
            index.put(docId, originalPathOrNull == null ? null : originalPathOrNull.toString());
            writeIndex(index);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The recovery copy of the document; empty when there is none or when it cannot be read. A
     * copy that cannot be read is renamed, see {@link #takeUnreadable()}.
     */
    public synchronized Optional<Recovered> load(String docId) {
        if (!isSafe(docId) || !Files.exists(copyOf(docId))) {
            return Optional.empty();
        }
        return read(docId, readIndex().get(docId));
    }

    /**
     * Deletes the recovery copy of the document, if there is one.
     */
    public synchronized void discard(String docId) {
        if (!isSafe(docId) || !Files.exists(dir)) {
            return;
        }
        try {
            Files.deleteIfExists(copyOf(docId));
            Map<String, String> index = readIndex();
            if (index.containsKey(docId)) {
                index.remove(docId);
                writeIndex(index);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not delete the recovery copy of " + docId + " (" + e + ")");
        }
    }

    /**
     * Every readable recovery copy. A copy without an index entry is recovered as untitled; one
     * that cannot be read is renamed, see {@link #takeUnreadable()}.
     */
    public synchronized List<Recovered> all() {
        if (!Files.exists(dir)) {
            return List.of();
        }
        Map<String, String> index = readIndex();
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(EXTENSION))
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .filter(RecoveryStore::isSafe)
                    .sorted()
                    .forEach(ids::add);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not list the recovery copies in " + dir + " (" + e + ")");
        }
        List<Recovered> out = new ArrayList<>();
        for (String id : ids) {
            read(id, index.get(id)).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    /**
     * The unreadable copies that were moved aside since the last call, for telling the user.
     */
    public synchronized List<Unreadable> takeUnreadable() {
        List<Unreadable> taken = List.copyOf(unreadable);
        unreadable.clear();
        return taken;
    }

    private Optional<Recovered> read(String docId, String originalPath) {
        try {
            AnimationFile file = AnimationIO.read(copyOf(docId));
            return Optional.of(new Recovered(docId, file, originalPath == null ? null : Path.of(originalPath)));
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Recovery copy is unreadable: " + copyOf(docId) + " (" + e + ")");
            moveAside(docId, originalPath);
            return Optional.empty();
        }
    }

    /**
     * Renames an unreadable copy to {@code <name>.unreadable-<timestamp>}, which no other method
     * of the store touches: it may be all that is left of the work.
     */
    private void moveAside(String docId, String originalPath) {
        Path copy = copyOf(docId);
        String base = copy.getFileName() + ".unreadable-" + LocalDateTime.now().format(TIMESTAMP);
        try {
            Path target = copy.resolveSibling(base);
            for (int i = 1; Files.exists(target); i++) {
                target = copy.resolveSibling(base + "-" + i);
            }
            Files.move(copy, target);
            unreadable.add(new Unreadable(docId, originalPath == null ? null : Path.of(originalPath), target));
            Map<String, String> index = readIndex();
            if (index.containsKey(docId)) {
                index.remove(docId);
                writeIndex(index);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not rename the unreadable recovery copy: " + copy + " (" + e + ")");
        }
    }

    private static boolean isSafe(String docId) {
        return docId != null && SAFE_ID.matcher(docId).matches();
    }

    private Path copyOf(String docId) {
        return dir.resolve(docId + EXTENSION);
    }

    /** Document id to original path ({@code null} for untitled); empty when missing or unreadable. */
    private Map<String, String> readIndex() {
        Map<String, String> index = new LinkedHashMap<>();
        Path file = dir.resolve(INDEX);
        if (!Files.exists(file)) {
            return index;
        }
        try {
            Object root = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (root instanceof Map<?, ?> map && map.get("documents") instanceof Map<?, ?> documents) {
                for (Map.Entry<?, ?> entry : documents.entrySet()) {
                    Object path = entry.getValue() instanceof Map<?, ?> doc ? doc.get("path") : null;
                    index.put((String) entry.getKey(), path instanceof String s ? s : null);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Recovery index is unreadable: " + file + " (" + e + ")");
            index.clear();
        }
        return index;
    }

    private void writeIndex(Map<String, String> index) throws IOException {
        Map<String, Object> documents = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : index.entrySet()) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("path", entry.getValue());
            documents.put(entry.getKey(), doc);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("documents", documents);
        AtomicFiles.writeString(dir.resolve(INDEX), JsonWriter.write(root));
    }
}
