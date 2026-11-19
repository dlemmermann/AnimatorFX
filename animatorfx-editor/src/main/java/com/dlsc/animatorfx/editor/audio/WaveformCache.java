package com.dlsc.animatorfx.editor.audio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;
import java.util.HashMap;
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
 * <p>The info of a sound ({@link AudioProbe}) never waits for a waveform. WAV and AIFF are
 * decoded exactly on a background thread ({@link WaveformDecoder}); other formats are analysed
 * one at a time through JavaFX media ({@link SpectrumAnalyser}), which is approximate, takes as
 * long as the sound, and fills in while it runs. Use on the JavaFX application thread; results
 * arrive there and are announced through {@link #revisionProperty()}.
 */
public final class WaveformCache {

    private static final WaveformCache SHARED = new WaveformCache();

    /** A byte array as a map key: the same array, not equal content, and not kept alive by the map. */
    private static final class Key extends WeakReference<byte[]> {

        private final int hash;

        Key(byte[] bytes, ReferenceQueue<byte[]> queue) {
            super(bytes, queue);
            hash = System.identityHashCode(bytes);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            byte[] mine = get();
            return mine != null && other instanceof Key key && key.get() == mine;
        }
    }

    private static final class Entry {

        /** Read on the decoder thread too. */
        volatile WeakReference<byte[]> content;
        final String extension;
        final CompletableFuture<AudioInfo> info = new CompletableFuture<>();
        Waveform waveform;
        boolean started;
        SpectrumAnalyser.Analysis analysis;
        Path file;
        /** Whether the probe has got as far as asking the media stack, or needed no asking. */
        boolean probing;
        final List<Runnable> onProbing = new ArrayList<>();

        void probing() {
            probing = true;
            List<Runnable> waiting = List.copyOf(onProbing);
            onProbing.clear();
            waiting.forEach(Runnable::run);
        }

        Entry(byte[] bytes, String extension) {
            this.content = new WeakReference<>(bytes);
            this.extension = extension;
        }
    }

    private final Map<Key, Entry> entries = new HashMap<>();
    private final ReferenceQueue<byte[]> collected = new ReferenceQueue<>();
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
     * Like {@link #info(byte[], String)}, and says when the answer is being worked on.
     *
     * @param onProbing called once, on the JavaFX application thread, when the media stack is
     *                  about to be asked (see {@link AudioProbe}); at once when that has
     *                  happened already or is not needed
     */
    public CompletableFuture<AudioInfo> info(byte[] bytes, String extension, Runnable onProbing) {
        Entry entry = entry(bytes, extension);
        if (entry.probing) {
            onProbing.run();
        } else {
            entry.onProbing.add(onProbing);
        }
        return entry.info;
    }

    /**
     * Makes what is known, or being worked out, about a sound belong to another array with the
     * same content as well: a document copies the content it is given.
     */
    public void transfer(byte[] from, byte[] to) {
        Entry entry = entries.get(new Key(from, null));
        if (entry != null && from != to) {
            entry.content = new WeakReference<>(to);
            entries.put(new Key(to, collected), entry);
        }
    }

    private Entry entry(byte[] bytes, String extension) {
        Entry entry = entries.get(new Key(bytes, null));
        if (entry == null) {
            purge();
            entry = new Entry(bytes, extension == null ? "" : extension);
            entries.put(new Key(bytes, collected), entry);
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
            entry.probing();
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
            // the length comes from a probe of its own: the analysis may be a long way off
            AudioProbe.probe(bytes, entry.extension, entry::probing).whenComplete((info, error) -> {
                if (error != null) {
                    entry.info.completeExceptionally(error);
                } else {
                    entry.info.complete(info);
                    waiting.add(entry);
                    next();
                }
                changed();
            });
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
                    file.toFile().deleteOnExit();
                    Files.write(file, bytes);
                }
            } catch (IOException | RuntimeException e) {
                if (file != null) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException | RuntimeException ignored) {
                        // removed when the application exits
                    }
                }
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
        entry.analysis = SpectrumAnalyser.analyse(file.toUri(),
                seconds -> {
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

    /** Drops the keys whose array has been collected; an entry goes with its last key. */
    private void purge() {
        Reference<? extends byte[]> gone;
        while ((gone = collected.poll()) != null) {
            entries.remove(gone);
        }
    }
}
