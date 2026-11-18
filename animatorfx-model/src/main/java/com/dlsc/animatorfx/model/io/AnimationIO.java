package com.dlsc.animatorfx.model.io;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Reads and writes {@code .afx} files: a zip archive with the document as {@code animation.json}
 * and every asset at its {@link Asset#path()}. A bare {@code animation.json} can be read as well
 * when the document has no assets.
 */
public final class AnimationIO {

    private static final String DOCUMENT_ENTRY = "animation.json";

    /**
     * Every entry carries this time, so that equal documents give equal bytes. It must lie after
     * the DOS epoch (1980-01-01): at or before it, ZipEntry adds a time-zone dependent UTC timestamp.
     */
    private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    private AnimationIO() {
    }

    /**
     * @throws AnimationFormatException if the content is not a valid document
     */
    public static AnimationFile read(Path path) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            return read(in);
        }
    }

    /**
     * @throws AnimationFormatException if the content is not a valid document
     */
    public static AnimationFile read(URL url) throws IOException {
        try (InputStream in = new BufferedInputStream(url.openStream())) {
            return read(in);
        }
    }

    /**
     * Reads a zip archive or, when the stream does not start with the zip signature, a bare
     * {@code animation.json}. The stream is read to its end and left open.
     *
     * @throws AnimationFormatException if the content is not a valid document
     */
    public static AnimationFile read(InputStream in) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(in, 2);
        byte[] magic = pushback.readNBytes(2);
        pushback.unread(magic);
        if (magic.length == 2 && magic[0] == 'P' && magic[1] == 'K') {
            return readArchive(pushback);
        }
        return resolveAssets(parse(pushback.readAllBytes()), Map.of());
    }

    /**
     * Writes the archive to a temporary file next to the target and then moves it into place, so
     * that a failure leaves an existing file untouched.
     *
     * @throws IllegalArgumentException if an asset of the document has no bytes or an unusable path
     */
    public static void write(AnimationFile file, Path path) throws IOException {
        Path target = path.toAbsolutePath();
        Path temp = target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            try (OutputStream out = new BufferedOutputStream(
                    Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                write(file, out);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /**
     * Writes the archive: {@code animation.json} first, then the assets in document order. Equal
     * documents give equal bytes. The stream is flushed and left open.
     *
     * @throws IllegalArgumentException if an asset of the document has no bytes or an unusable path;
     *                                  nothing has been written then
     */
    public static void write(AnimationFile file, OutputStream out) throws IOException {
        Animation animation = file.animation();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(DOCUMENT_ENTRY,
                (JsonWriter.write(AnimationJson.toJson(animation)) + "\n").getBytes(StandardCharsets.UTF_8));
        for (Asset asset : animation.assets()) {
            byte[] bytes = file.assets().get(asset.id());
            if (bytes == null) {
                throw new IllegalArgumentException("No bytes for asset " + asset.id() + " (" + asset.path() + ")");
            }
            if (asset.path().isEmpty() || asset.path().endsWith("/") || isUnsafe(asset.path())) {
                throw new IllegalArgumentException("Asset " + asset.id() + " has an unusable path: " + asset.path());
            }
            if (entries.put(asset.path(), bytes) != null) {
                throw new IllegalArgumentException("Asset " + asset.id() + " shares its path with another entry: "
                        + asset.path());
            }
        }
        try (ZipOutputStream zip = new ZipOutputStream(new KeepOpen(out), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTimeLocal(ENTRY_TIME);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }

    private static AnimationFile readArchive(InputStream in) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        // Closing the zip stream frees its inflater; the caller's stream stays open.
        try (ZipInputStream zip = new ZipInputStream(new FilterInputStream(in) {
            @Override
            public void close() {
            }
        }, StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (isUnsafe(entry.getName())) {
                    throw new AnimationFormatException("Unsafe archive entry name: " + entry.getName(), "");
                }
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        } catch (ZipException | IllegalArgumentException e) {
            // ZipInputStream reports undecodable entry names as IllegalArgumentException.
            throw new AnimationFormatException("Not a valid .afx archive: " + e.getMessage(), "", e);
        }
        byte[] document = entries.get(DOCUMENT_ENTRY);
        if (document == null) {
            throw new AnimationFormatException("The archive has no " + DOCUMENT_ENTRY, "");
        }
        return resolveAssets(parse(document), entries);
    }

    private static Animation parse(byte[] utf8) throws AnimationFormatException {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(utf8)).toString();
        } catch (CharacterCodingException e) {
            throw new AnimationFormatException("The document is not valid UTF-8", "", e);
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return AnimationJson.fromJson(Json.parse(text));
    }

    private static AnimationFile resolveAssets(Animation animation, Map<String, byte[]> entries)
            throws AnimationFormatException {
        Map<String, byte[]> assets = new HashMap<>();
        List<Asset> list = animation.assets();
        for (int i = 0; i < list.size(); i++) {
            Asset asset = list.get(i);
            byte[] bytes = asset.path().equals(DOCUMENT_ENTRY) ? null : entries.get(asset.path());
            if (bytes == null) {
                throw new AnimationFormatException("Asset " + asset.id() + " is missing: there is no entry "
                        + asset.path(), "assets[" + i + "].path");
            }
            assets.put(asset.id(), bytes);
        }
        return new AnimationFile(animation, assets);
    }

    /** Absolute names and names that climb out of the archive root ("zip slip"). */
    private static boolean isUnsafe(String name) {
        if (name.startsWith("/") || name.startsWith("\\") || (name.length() >= 2 && name.charAt(1) == ':')) {
            return true;
        }
        return List.of(name.split("[/\\\\]")).contains("..");
    }

    private static final class KeepOpen extends FilterOutputStream {

        KeepOpen(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
