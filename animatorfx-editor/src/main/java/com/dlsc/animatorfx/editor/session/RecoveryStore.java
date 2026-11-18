package com.dlsc.animatorfx.editor.session;

import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationIO;
import com.dlsc.animatorfx.model.io.Json;
import com.dlsc.animatorfx.model.io.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
public final class RecoveryStore {

    /**
     * @param originalPath the file the document belongs to, or {@code null} for an untitled document
     */
    public record Recovered(String docId, AnimationFile file, Path originalPath) {
    }

    private static final System.Logger LOG = System.getLogger(RecoveryStore.class.getName());
    private static final String INDEX = "index.json";
    private static final String EXTENSION = ".afx";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private final Path dir;

    public RecoveryStore(Path dir) {
        this.dir = Objects.requireNonNull(dir, "dir");
    }

    /**
     * Stores or replaces the recovery copy of a document.
     *
     * @throws IllegalArgumentException if the id is not usable as a file name
     * @throws UncheckedIOException     if the copy cannot be written
     */
    public void store(String docId, AnimationFile file, Path originalPathOrNull) {
        if (!isSafe(docId)) {
            throw new IllegalArgumentException("Not a usable document id: " + docId);
        }
        try {
            Files.createDirectories(dir);
            AnimationIO.write(file, copyOf(docId));
            Map<String, String> index = readIndex();
            index.put(docId, originalPathOrNull == null ? null : originalPathOrNull.toString());
            writeIndex(index);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The recovery copy of the document; empty when there is none or when it cannot be read.
     */
    public Optional<Recovered> load(String docId) {
        if (!isSafe(docId) || !Files.exists(copyOf(docId))) {
            return Optional.empty();
        }
        return read(docId, readIndex().get(docId));
    }

    /**
     * Deletes the recovery copy of the document, if there is one.
     */
    public void discard(String docId) {
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
     * Every readable recovery copy. A copy without an index entry is recovered as untitled.
     */
    public List<Recovered> all() {
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

    private Optional<Recovered> read(String docId, String originalPath) {
        try {
            AnimationFile file = AnimationIO.read(copyOf(docId));
            return Optional.of(new Recovered(docId, file, originalPath == null ? null : Path.of(originalPath)));
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Recovery copy is unreadable: " + copyOf(docId) + " (" + e + ")");
            return Optional.empty();
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
