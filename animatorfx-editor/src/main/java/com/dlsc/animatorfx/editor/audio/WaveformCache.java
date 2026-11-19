package com.dlsc.animatorfx.editor.audio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;

/**
 * The audio info and the waveforms of the audio assets of all open documents, worked out in the
 * background and kept for the session. An asset is known by its content (the byte array a
 * document holds for it), so nothing here belongs to a document or its undo history, and an
 * entry goes away when no document holds the content any more.
 *
 * <p>WAV and AIFF are decoded exactly on a background thread ({@link WaveformDecoder}); other
 * formats are analysed one at a time through JavaFX media ({@link SpectrumAnalyser}), which is
 * approximate, slower, and fills in while it runs. Use on the JavaFX application thread; results
 * arrive there and are announced through {@link #revisionProperty()}.
 */
public final class WaveformCache {

    private static final WaveformCache SHARED = new WaveformCache();

    private static final class Entry {

        WeakReference<byte[]> content;
        final String extension;
        final CompletableFuture<AudioInfo> info = new CompletableFuture<>();
        Waveform waveform;
        boolean started;
        SpectrumAnalyser.Analysis analysis;
        Path file;

        Entry(byte[] bytes, String extension) {
            this.content = new WeakReference<>(bytes);
            this.extension = extension;
        }
    }

    private final Map<Integer, Entry> entries = new HashMap<>();
    private final Deque<Entry> waiting = new ArrayDeque<>();
    private final ReadOnlyIntegerWrapper revision = new ReadOnlyIntegerWrapper(this, "revision");
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "animatorfx-waveforms");
        thread.setDaemon(true);
        return thread;
    });
    private Entry analysing;

    public WaveformCache() {
    }

    /** The cache of the application. */
    public static WaveformCache shared() {
        return SHARED;
    }

    /**
     * Counts up whenever an info or a waveform (also a partial one) arrives: the moment to draw again.
     *
     * @return the revision property
     */
    public final ReadOnlyIntegerProperty revisionProperty() {
        return revision.getReadOnlyProperty();
    }

    public final int getRevision() {
        return revision.get();
    }

    /**
     * The waveform of a sound, once it is there; asking starts the work. Empty until then, and
     * for good when the sound cannot be analysed.
     *
     * @param extension the extension of the asset's file, by which the media stack tells the format
     */
    public Optional<Waveform> waveform(byte[] bytes, String extension) {
        return Optional.ofNullable(entry(bytes, extension).waveform);
    }

    /** The info of a sound when it is known already; asking starts the work. */
    public Optional<AudioInfo> knownInfo(byte[] bytes, String extension) {
        CompletableFuture<AudioInfo> info = entry(bytes, extension).info;
        return info.isDone() && !info.isCompletedExceptionally() ? Optional.of(info.join()) : Optional.empty();
    }

    /**
     * The info of a sound. Completes on the JavaFX application thread: at once for WAV and AIFF,
     * after the media stack has opened the file for other formats, and exceptionally when the
     * sound cannot be read at all.
     */
    public CompletableFuture<AudioInfo> info(byte[] bytes, String extension) {
        return entry(bytes, extension).info;
    }

    /**
     * Makes what is known, or being worked out, about a sound belong to another array with the
     * same content: a document copies the content it is given.
     */
    public void transfer(byte[] from, byte[] to) {
        Entry entry = entries.get(System.identityHashCode(from));
        if (entry != null && entry.content.get() == from && from != to) {
            entries.remove(System.identityHashCode(from));
            entry.content = new WeakReference<>(to);
            entries.put(System.identityHashCode(to), entry);
        }
    }

    private Entry entry(byte[] bytes, String extension) {
        int key = System.identityHashCode(bytes);
        Entry entry = entries.get(key);
        if (entry == null || entry.content.get() != bytes) {
            purge();
            entry = new Entry(bytes, extension == null ? "" : extension);
            entries.put(key, entry);
        }
        if (!entry.started) {
            entry.started = true;
            start(entry, bytes);
        }
        return entry;
    }

    private void start(Entry entry, byte[] bytes) {
        Optional<AudioInfo> header = AudioInfo.read(bytes);
        if (header.isPresent()) {
            entry.info.complete(header.get());
            decoder.execute(() -> {
                byte[] content = entry.content.get();
                Waveform waveform = null;
                if (content != null) {
                    try {
                        waveform = WaveformDecoder.decode(new ByteArrayInputStream(content));
                    } catch (Exception | OutOfMemoryError e) {
                        // no waveform: the bar stays plain
                    }
                }
                Waveform result = waveform;
                Platform.runLater(() -> {
                    entry.waveform = result;
                    changed();
                });
            });
        } else {
            waiting.add(entry);
            next();
        }
    }

    /** Starts the next analysis through the media stack; they run one at a time. */
    private void next() {
        if (analysing != null) {
            return;
        }
        Entry entry = waiting.poll();
        while (entry != null && entry.content.get() == null) {
            entry = waiting.poll();
        }
        if (entry == null) {
            return;
        }
        Entry current = entry;
        analysing = current;
        byte[] bytes = current.content.get();
        decoder.execute(() -> {
            Path file = null;
            try {
                if (bytes != null) {
                    file = Files.createTempFile("animatorfx-waveform", "." + (current.extension.isBlank() ? "bin" : current.extension));
                    Files.write(file, bytes);
                    file.toFile().deleteOnExit();
                }
            } catch (IOException | RuntimeException e) {
                file = null;
            }
            Path written = file;
            Platform.runLater(() -> analyse(current, written));
        });
    }

    private void analyse(Entry entry, Path file) {
        if (file == null) {
            done(entry, Optional.empty());
            return;
        }
        entry.file = file;
        String format = AudioInfo.formatOf(entry.extension);
        entry.analysis = SpectrumAnalyser.analyse(file.toUri(),
                seconds -> {
                    entry.info.complete(new AudioInfo(seconds, 0, 0, format));
                    changed();
                },
                partial -> {
                    if (entry.content.get() == null) {
                        // no document holds the sound any more
                        entry.analysis.cancel();
                        done(entry, Optional.empty());
                    } else {
                        entry.waveform = partial;
                        changed();
                    }
                },
                result -> done(entry, result));
    }

    private void done(Entry entry, Optional<Waveform> result) {
        if (analysing != entry) {
            return;
        }
        analysing = null;
        entry.analysis = null;
        entry.waveform = result.orElse(null);
        if (!entry.info.isDone()) {
            if (result.isPresent()) {
                entry.info.complete(new AudioInfo(result.get().duration(), 0, 0, AudioInfo.formatOf(entry.extension)));
            } else {
                entry.info.completeExceptionally(new IOException("The sound cannot be read"));
            }
        }
        Path file = entry.file;
        entry.file = null;
        if (file != null) {
            decoder.execute(() -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException | RuntimeException e) {
                    // removed when the application exits
                }
            });
        }
        changed();
        next();
    }

    private void changed() {
        revision.set(revision.get() + 1);
    }

    /** Drops the entries whose content no document holds any more. */
    private void purge() {
        for (Iterator<Entry> it = entries.values().iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (entry.content.get() == null && entry != analysing) {
                it.remove();
            }
        }
    }
}
