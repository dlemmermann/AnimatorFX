package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.session.RecoveryStore;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The periodic recovery copies are written off the JavaFX thread: a document with a long sound takes a while. */
class BackgroundAutosaveTest extends EditorUiTest {

    private RecoveryStore store() {
        return new RecoveryStore(dir.resolve("config").resolve("recovery"));
    }

    private String status() {
        return fx(() -> ((javafx.scene.control.Label) window.getScene().lookup("#status-autosave")).getText());
    }

    @Test
    void theTimerStoresTheRecoveryCopyInTheBackgroundAndReportsItAfterwards() throws Exception {
        add(rect("a", 10, 10, 50, 50));

        CompletableFuture<Void> done = fx(() -> window.getSession().autosaveInBackground());
        done.get(10, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(doc.getAnimation(), store().load(doc.getId()).orElseThrow().file().animation());
        assertTrue(status().startsWith("Autosaved "), status());

        // unchanged content is not written again
        assertTrue(fx(() -> window.getSession().autosaveInBackground()).isDone());
    }

    @Test
    void aCopyThatFinishesAfterTheDocumentWasClosedIsRemovedAgain() throws Exception {
        add(rect("a", 10, 10, 50, 50));
        String id = doc.getId();

        CompletableFuture<Void> done = fx(() -> {
            CompletableFuture<Void> running = window.getSession().autosaveInBackground();
            window.getWorkspace().remove(doc);
            return running;
        });
        done.get(10, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(store().load(id).isPresent());
    }

    @Test
    void aSynchronousAutosaveWaitsForTheOneInTheBackground() throws Exception {
        add(rect("a", 10, 10, 50, 50));

        run(() -> {
            window.getSession().autosaveInBackground();
            doc.execute(com.dlsc.animatorfx.editor.doc.Commands.updateLayer("Rename Layer", "a",
                    l -> l.withBase(l.base().withName("later"))));
            window.getSession().autosaveNow();
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("later", store().load(doc.getId()).orElseThrow().file().animation().layers().get(0).name());
    }
}
