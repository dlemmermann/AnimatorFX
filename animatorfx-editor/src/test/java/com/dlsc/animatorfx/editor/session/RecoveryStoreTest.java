package com.dlsc.animatorfx.editor.session;

import com.dlsc.animatorfx.editor.session.RecoveryStore.Recovered;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.io.AnimationFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryStoreTest {

    @TempDir
    Path dir;

    private static AnimationFile file(String name) {
        return new AnimationFile(Animation.create(name, 320, 200, 2), Map.of());
    }

    private List<String> fileNames() throws IOException {
        try (Stream<Path> files = Files.list(dir.resolve("recovery"))) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void storeAndLoadADocumentWithItsOriginalPath() {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));
        Path original = dir.resolve("work/doc.afx");

        store.store("doc-1", file("One"), original);

        Recovered recovered = new RecoveryStore(dir.resolve("recovery")).load("doc-1").orElseThrow();
        assertEquals("doc-1", recovered.docId());
        assertEquals(file("One").animation(), recovered.file().animation());
        assertEquals(original, recovered.originalPath());
    }

    @Test
    void untitledDocumentsAreStoredWithoutAPath() {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));

        store.store("untitled-1", file("Untitled"), null);

        assertNull(store.load("untitled-1").orElseThrow().originalPath());
    }

    @Test
    void assetBytesAreRecovered() {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));
        Animation animation = Animation.create("A", 10, 10, 1)
                .withAssets(List.of(new Asset("img", Asset.Kind.IMAGE, "assets/img.png", "img.png")));

        store.store("doc-1", new AnimationFile(animation, Map.of("img", new byte[]{1, 2, 3})), null);

        assertArrayEquals(new byte[]{1, 2, 3}, store.load("doc-1").orElseThrow().file().assets().get("img"));
    }

    @Test
    void storingAgainReplacesTheCopy() {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));
        store.store("doc-1", file("First"), null);

        store.store("doc-1", file("Second"), null);

        assertEquals("Second", store.load("doc-1").orElseThrow().file().animation().name());
        assertEquals(1, store.all().size());
    }

    @Test
    void anUnknownDocumentIsEmptyAndAnEmptyStoreNeedsNoDirectory() {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));

        assertEquals(Optional.empty(), store.load("nope"));
        assertTrue(store.all().isEmpty());
        store.discard("nope");
        assertFalse(Files.exists(dir.resolve("recovery")));
    }

    @Test
    void discardDeletesTheCopyAndItsIndexEntry() throws IOException {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));
        store.store("doc-1", file("One"), null);
        store.store("doc-2", file("Two"), dir.resolve("two.afx"));

        store.discard("doc-1");

        assertTrue(store.load("doc-1").isEmpty());
        assertEquals(List.of("doc-2"), store.all().stream().map(Recovered::docId).toList());
        assertEquals(List.of("doc-2.afx", "index.json"), fileNames());
    }

    @Test
    void storingLeavesNoTemporaryFiles() throws IOException {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));

        store.store("doc-1", file("One"), null);
        store.store("doc-1", file("One again"), null);

        assertEquals(List.of("doc-1.afx", "index.json"), fileNames());
    }

    @Test
    void anUnreadableCopyIsSkippedAndKeptUnderAnotherName() throws IOException {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));
        store.store("doc-1", file("One"), dir.resolve("one.afx"));
        store.store("doc-2", file("Two"), null);
        Files.writeString(dir.resolve("recovery/doc-1.afx"), "not a document");

        assertTrue(store.load("doc-1").isEmpty());
        assertEquals(List.of("doc-2"), store.all().stream().map(Recovered::docId).toList());

        List<RecoveryStore.Unreadable> unreadable = store.takeUnreadable();
        assertEquals(1, unreadable.size());
        assertEquals(dir.resolve("one.afx"), unreadable.get(0).originalPath());
        Path kept = unreadable.get(0).movedTo();
        assertTrue(kept.getFileName().toString().startsWith("doc-1.afx.unreadable-"), kept.toString());
        assertEquals("not a document", Files.readString(kept));
        assertFalse(Files.exists(dir.resolve("recovery/doc-1.afx")));
        assertEquals(List.of(), store.takeUnreadable(), "reported once");

        // neither discarding the document nor a new copy of it touches the one that was moved aside
        store.discard("doc-1");
        store.store("doc-1", file("One again"), null);
        store.discard("doc-1");
        assertEquals("not a document", Files.readString(kept));
        assertEquals(List.of("doc-2"), store.all().stream().map(Recovered::docId).toList());
    }

    @Test
    void aCopyThatCouldNotBeReadBackIsNotWrittenAndThePreviousOneStays() throws IOException {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));
        store.store("doc-1", file("Good"), null);
        byte[] good = Files.readAllBytes(dir.resolve("recovery/doc-1.afx"));
        SlotLayer slot = new SlotLayer(LayerBase.create("s", 2), "card", 10, 10, Transform.IDENTITY);
        Animation broken = Animation.create("Broken", 320, 200, 2).withLayers(List.of(slot,
                slot.withBase(slot.base().withId("other"))));

        UncheckedIOException failure = assertThrows(UncheckedIOException.class,
                () -> store.store("doc-1", new AnimationFile(broken, Map.of()), null));

        assertEquals("layers[1].slotName: Duplicate slot name: card", failure.getCause().getMessage());
        assertArrayEquals(good, Files.readAllBytes(dir.resolve("recovery/doc-1.afx")));
        assertEquals(List.of("doc-1.afx", "index.json"), fileNames());
        assertEquals("Good", store.load("doc-1").orElseThrow().file().animation().name());
    }

    @Test
    void aCorruptIndexStillRecoversTheCopiesAsUntitled() throws IOException {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));
        store.store("doc-1", file("One"), dir.resolve("one.afx"));
        Files.writeString(dir.resolve("recovery/index.json"), "{{{");

        Recovered recovered = store.load("doc-1").orElseThrow();

        assertEquals("One", recovered.file().animation().name());
        assertNull(recovered.originalPath());
        assertEquals(1, store.all().size());
    }

    @Test
    void documentIdsThatAreNotPlainFileNamesAreRejected() {
        RecoveryStore store = new RecoveryStore(dir.resolve("recovery"));

        assertThrows(IllegalArgumentException.class, () -> store.store("../evil", file("X"), null));
        assertTrue(store.load("../evil").isEmpty());
    }
}
