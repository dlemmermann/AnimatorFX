package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.session.RecoveryStore;
import com.dlsc.animatorfx.editor.session.SessionStore;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.io.AnimationFile;
import javafx.stage.Stage;
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
    void aSynchronousAutosaveWaitsForTheOneInTheBackgroundAndWritesTheLatestContent() throws Exception {
        CountDownLatch gate = store.hold();
        autosave();
        store.awaitWrite();
        edit("later");

        Future<Void> now = WaitForAsyncUtils.asyncFx(() -> window.getSession().autosaveNow());
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
