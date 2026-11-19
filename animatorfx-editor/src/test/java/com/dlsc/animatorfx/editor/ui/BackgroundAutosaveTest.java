package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.audio.EditorAudio;
import com.dlsc.animatorfx.editor.audio.RecordingAudioOutput;
import com.dlsc.animatorfx.editor.audio.SoundPreview;
import com.dlsc.animatorfx.editor.audio.TestSounds;
import com.dlsc.animatorfx.editor.session.RecoveryStore;
import com.dlsc.animatorfx.editor.session.SessionStore;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.io.AnimationFile;
import javafx.event.Event;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The periodic recovery copies are written off the JavaFX thread. What must hold whatever the
 * order of things: a failing write is reported and stops nothing; a copy never outlives a save
 * or a close; quitting persists the latest content; the JavaFX thread never waits for a write
 * except when quitting. The store of these tests holds a write back until the test lets it go.
 */
class BackgroundAutosaveTest {

    /** A recovery store whose writes can be held back and made to fail. */
    private static final class GatedStore extends RecoveryStore {

        private final Semaphore entered = new Semaphore(0);
        private volatile CountDownLatch gate;
        private volatile Throwable failure;

        GatedStore(Path dir) {
            super(dir);
        }

        /** The next writes wait until the returned latch is counted down. */
        CountDownLatch hold() {
            gate = new CountDownLatch(1);
            return gate;
        }

        void failOnceWith(Throwable throwable) {
            failure = throwable;
        }

        /** Waits until a write has begun. */
        void awaitWrite() throws InterruptedException {
            assertTrue(entered.tryAcquire(10, TimeUnit.SECONDS), "no write began");
        }

        @Override
        public void store(String docId, AnimationFile file, Path originalPathOrNull) {
            entered.release();
            CountDownLatch latch = gate;
            if (latch != null) {
                try {
                    assertTrue(latch.await(20, TimeUnit.SECONDS), "the test never let the write go");
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
            Throwable thrown = failure;
            if (thrown != null) {
                failure = null;
                if (thrown instanceof Error error) {
                    throw error;
                }
                throw (RuntimeException) thrown;
            }
            super.store(docId, file, originalPathOrNull);
        }
    }

    @TempDir
    Path dir;

    private GatedStore store;
    private MainWindow window;
    private EditorDocument doc;

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    @BeforeEach
    void openWindow() {
        store = new GatedStore(dir.resolve("config").resolve("recovery"));
        window = fx(() -> {
            MainWindow w = new MainWindow(new Stage(), new SessionStore(dir.resolve("config")), store);
            w.getStage().setWidth(1000);
            w.getStage().setHeight(700);
            w.show();
            return w;
        });
        // only the tests ask for copies: a tick of the five-second timer would be one more writer
        fx(() -> {
            window.getSession().stopAutosaveTimer();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        doc = window.getWorkspace().getActiveDocument();
        edit("first");
    }

    @AfterEach
    void closeWindow() {
        CountDownLatch latch = store.gate;
        if (latch != null) {
            latch.countDown();
        }
        fx(() -> {
            window.shutdown();
            window.getStage().hide();
            return null;
        });
    }

    private static <T> T fx(Callable<T> action) {
        try {
            return WaitForAsyncUtils.asyncFx(action).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** Runs on the JavaFX thread and fails when that takes long: the thread must not wait for a write. */
    private static void promptly(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("The JavaFX thread was held up", e);
        }
    }

    /** A new content: one more layer with that name. */
    private void edit(String name) {
        fx(() -> {
            doc.execute(Commands.addLayer(null, doc.getAnimation().layers().size(),
                    new SlotLayer(LayerBase.create(name, 3), name, 10, 10, Transform.IDENTITY)));
            return null;
        });
    }

    private CompletableFuture<Void> autosave() {
        return fx(() -> window.getSession().autosaveInBackground());
    }

    /** Waits until every write and removal asked for so far is done and taken note of. */
    private void settle() throws Exception {
        autosave().get(10, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Optional<List<String>> copy() {
        return new RecoveryStore(dir.resolve("config").resolve("recovery")).load(doc.getId())
                .map(recovered -> recovered.file().animation().layers().stream().map(l -> l.name()).toList());
    }

    private List<String> notices() {
        return fx(() -> window.getNoticeBar().getMessages());
    }

    private String status() {
        return fx(() -> ((javafx.scene.control.Label) window.getScene().lookup("#status-autosave")).getText());
    }

    // ------------------------------------------------------------------ the plain case

    @Test
    void theCopyIsWrittenInTheBackgroundAndReportedAfterwards() throws Exception {
        CountDownLatch gate = store.hold();
        CompletableFuture<Void> running = autosave();
        store.awaitWrite();

        // the write is under way and the JavaFX thread is free
        promptly(() -> assertFalse(running.isDone()));
        assertEquals("Unsaved changes", status());

        gate.countDown();
        settle();
        assertEquals(Optional.of(List.of("first")), copy());
        assertTrue(status().startsWith("Autosaved "), status());

        // unchanged content is not written again
        assertTrue(autosave().isDone());
        assertFalse(store.entered.tryAcquire());
    }

    // ------------------------------------------------------------------ (a) failures

    @Test
    void anErrorOnTheWriterThreadIsReportedAndTheNextCopyIsStillWritten() throws Exception {
        store.failOnceWith(new OutOfMemoryError("Java heap space (test)"));

        settle();

        assertEquals(Optional.empty(), copy());
        assertEquals(1, notices().size(), notices().toString());
        assertTrue(notices().get(0).startsWith("Recovery copy could not be written: Java heap space (test)"),
                notices().toString());

        edit("second");
        settle();
        assertEquals(Optional.of(List.of("first", "second")), copy());
        assertEquals(1, notices().size(), "told once per document");
    }

    @Test
    void aFailingWriteDoesNotKeepTheEditorFromQuitting() throws Exception {
        store.failOnceWith(new IllegalStateException("disk on fire"));
        CountDownLatch gate = store.hold();
        autosave();
        store.awaitWrite();

        Future<Void> quit = WaitForAsyncUtils.asyncFx(() -> window.shutdown());
        gate.countDown();
        quit.get(10, TimeUnit.SECONDS);

        // the write that failed was made up for on the way out
        assertEquals(Optional.of(List.of("first")), copy());
    }

    @Test
    void aCopyThatFailedWithAnErrorIsTriedAgainWhenTheEditorQuits() throws Exception {
        store.failOnceWith(new OutOfMemoryError("Java heap space (test)"));
        settle();
        assertEquals(Optional.empty(), copy());
        store.entered.drainPermits();
        // the timer does not try the same content again
        assertTrue(autosave().isDone());
        assertFalse(store.entered.tryAcquire(), "the same content was handed to the writer again");

        fx(() -> {
            window.shutdown();
            return null;
        });

        assertEquals(Optional.of(List.of("first")), copy());
    }

    @Test
    void quittingPersistsTheSessionAlsoWhenReleasingTheSoundFails() throws Exception {
        com.dlsc.animatorfx.editor.audio.EditorAudio.setOutputFactory(file -> new com.dlsc.animatorfx.editor.audio.RecordingAudioOutput(file) {
            @Override
            public void dispose() {
                throw new NoClassDefFoundError("media stack gone (test)");
            }
        });
        try {
            fx(() -> {
                doc.addAsset(new com.dlsc.animatorfx.model.Asset("snd", com.dlsc.animatorfx.model.Asset.Kind.AUDIO,
                        "assets/snd.wav", "snd.wav"), com.dlsc.animatorfx.editor.audio.TestSounds.tone(0.5, 0.5));
                doc.execute(Commands.addAudioLayer("a", "sound", "snd", 0, 0.5));
                // makes the player ask for its output
                window.getCanvasView().scrub(0.1);
                return null;
            });

            fx(() -> {
                window.shutdown();
                return null;
            });

            assertTrue(copy().isPresent(), "the recovery copy");
            assertTrue(java.nio.file.Files.exists(dir.resolve("config").resolve("session.json")), "the session");
        } finally {
            com.dlsc.animatorfx.editor.audio.EditorAudio.setOutputFactory(null);
        }
    }

    // ------------------------------------------------------------------ (b) save while a write is under way

    @Test
    void aCopyThatLandsAfterItsDocumentWasSavedIsRemoved() throws Exception {
        CountDownLatch gate = store.hold();
        autosave();
        store.awaitWrite();

        promptly(() -> saveAs("saved.afx"));
        gate.countDown();
        settle();

        assertEquals(Optional.empty(), copy());
        assertEquals("Saved", status());
    }

    @Test
    void afterSaveAndAnotherEditOnlyACopyOfTheNewContentAppears() throws Exception {
        CountDownLatch gate = store.hold();
        autosave();
        store.awaitWrite();

        promptly(() -> saveAs("saved.afx"));
        edit("after-save");
        gate.countDown();
        // the write of the old content and its removal are done; nothing wrote the new content yet
        fx(() -> window.getSession().whenRecoveryIdle()).get(10, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(Optional.empty(), copy(), "the copy from before the save must not survive");
        assertEquals("Unsaved changes", status());

        settle();
        assertEquals(Optional.of(List.of("first", "after-save")), copy());
    }

    // ------------------------------------------------------------------ (c) close while a write is under way

    @Test
    void aDocumentClosedWhileItsCopyIsWrittenLeavesNoCopyEvenWhenTheEditorQuitsAtOnce() throws Exception {
        CountDownLatch gate = store.hold();
        autosave();
        store.awaitWrite();

        promptly(() -> window.getWorkspace().remove(doc));
        Future<Void> quit = WaitForAsyncUtils.asyncFx(() -> window.shutdown());
        gate.countDown();
        quit.get(10, TimeUnit.SECONDS);

        assertEquals(Optional.empty(), copy());
        assertEquals(List.of(), new RecoveryStore(dir.resolve("config").resolve("recovery")).all().stream()
                .filter(recovered -> recovered.docId().equals(doc.getId())).toList());
    }

    // ------------------------------------------------------------------ (d) quit while a write is under way

    @Test
    void quittingWhileACopyIsWrittenPersistsTheLatestContent() throws Exception {
        CountDownLatch gate = store.hold();
        autosave();
        store.awaitWrite();
        edit("latest");

        Future<Void> quit = WaitForAsyncUtils.asyncFx(() -> window.shutdown());
        gate.countDown();
        quit.get(10, TimeUnit.SECONDS);

        assertEquals(Optional.of(List.of("first", "latest")), copy());
    }

    @Test
    void closingWithAnUndrainableWriterStopsSoundFirstAndKeepsTheWindowOpen() throws Exception {
        CountDownLatch playbackStopped = new CountDownLatch(1);
        CountDownLatch previewStopped = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        EditorAudio.setOutputFactory(file -> {
            int index = created.getAndIncrement();
            RecordingAudioOutput output = new RecordingAudioOutput(file) {
                @Override
                public void stopCues() {
                    super.stopCues();
                    if (index == 1) {
                        playbackStopped.countDown();
                    }
                }

                @Override
                public void dispose() {
                    super.dispose();
                    if (index == 0) {
                        previewStopped.countDown();
                    }
                }
            };
            return output;
        });
        CountDownLatch gate = store.hold();
        try {
            SoundPreview.shared().stop();
            fx(() -> {
                doc.addAsset(new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "sound.wav"),
                        TestSounds.tone(1, 0.5));
                doc.execute(Commands.addAudioLayer("audio", "sound", "snd", 0, 0.5));
                SoundPreview.shared().play(this, doc.getFileContent(), "snd", 0, 1, 0, 1);
                window.getCanvasView().togglePlayback();
                return null;
            });
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> created.get() >= 2);

            autosave();
            store.awaitWrite();
            edit("latest");

            AtomicReference<Thread> closeThread = new AtomicReference<>();
            AtomicReference<Boolean> closeConsumed = new AtomicReference<>(false);
            CountDownLatch closeStarted = new CountDownLatch(1);
            Future<Void> close = WaitForAsyncUtils.asyncFx(() -> {
                closeThread.set(Thread.currentThread());
                WindowEvent event = new WindowEvent(window.getStage(), WindowEvent.WINDOW_CLOSE_REQUEST);
                closeStarted.countDown();
                try {
                    Event.fireEvent(window.getStage(), event);
                    closeConsumed.set(event.isConsumed());
                } finally {
                    Thread.interrupted();
                }
                return null;
            });

            assertTrue(closeStarted.await(2, TimeUnit.SECONDS), "close request did not start");
            boolean playbackStoppedBeforeDrain = playbackStopped.await(1, TimeUnit.SECONDS);
            boolean previewStoppedBeforeDrain = previewStopped.await(1, TimeUnit.SECONDS);
            Thread waitingForDrain = closeThread.get();
            assertTrue(waitingForDrain != null);
            waitingForDrain.interrupt();
            close.get(3, TimeUnit.SECONDS);

            assertTrue(playbackStoppedBeforeDrain, "playback must stop before recovery drain");
            assertTrue(previewStoppedBeforeDrain, "previews must stop before recovery drain");
            assertTrue(closeConsumed.get(), "the close request must be cancelled when recovery cannot drain");
            assertTrue(window.getWorkspace().getDocuments().contains(doc), "the dirty document stays open");

            gate.countDown();
            settle();
            assertEquals(Optional.of(List.of("first", "sound", "latest")), copy());

            fx(() -> {
                window.getCanvasView().togglePlayback();
                assertTrue(window.getCanvasView().isPlaying(), "playback remains available after cancelling close");
                window.getCanvasView().pausePlayback();
                return null;
            });
        } finally {
            gate.countDown();
            SoundPreview.shared().stop();
            EditorAudio.setOutputFactory(null);
        }
    }

    @Test
    void aSynchronousAutosaveWaitsForTheOneInTheBackgroundAndWritesTheLatestContent() throws Exception {
        CountDownLatch gate = store.hold();
        autosave();
        store.awaitWrite();
        edit("later");

        Future<Void> now = WaitForAsyncUtils.asyncFx(() -> {
            window.getSession().autosaveNow();
            return null;
        });
        gate.countDown();
        now.get(10, TimeUnit.SECONDS);

        assertEquals(Optional.of(List.of("first", "later")), copy());
        assertTrue(status().startsWith("Autosaved "), status());
    }

    private void saveAs(String name) {
        try {
            doc.saveAs(dir.resolve(name));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
