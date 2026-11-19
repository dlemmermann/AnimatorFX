package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.importer.ImportResult;
import com.dlsc.animatorfx.importer.LottieImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** File ▸ Export in the running window. */
class ExportUiTest extends EditorUiTest {

    private final List<Path> picked = new ArrayList<>();
    private final List<String> suggested = new ArrayList<>();

    @BeforeEach
    void stubThePicker() {
        window.getExportActions().setFilePicker((owner, name) -> {
            suggested.add(name);
            return picked.isEmpty() ? Optional.empty() : Optional.of(picked.remove(0));
        });
    }

    private void export() {
        run(() -> window.menuItem("menu-file-export-lottie").fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void theDocumentIsWrittenAsADotLottieArchive() throws IOException {
        add(rect("box", 10, 10, 40, 20));
        Path target = dir.resolve("out.lottie");
        picked.add(target);
        export();

        assertEquals(List.of(doc.getTitle() + ".lottie"), suggested);
        try (ZipFile zip = new ZipFile(target.toFile())) {
            assertNotNull(zip.getEntry("manifest.json"));
            assertNotNull(zip.getEntry("a/animation.json"));
            // what was written is a Lottie animation the importer reads
            try (InputStream in = zip.getInputStream(zip.getEntry("a/animation.json"))) {
                ImportResult back = LottieImporter.read(in, "back");
                assertEquals(1, back.file().animation().layers().size());
            }
        }
        assertEquals(List.of("Exported out.lottie."), fx(() -> window.getNoticeBar().getMessages()));
        // exporting is no saving: the document is as it was
        assertTrue(doc.isDirty());
    }

    @Test
    void aNameThatEndsWithJsonGetsPlainLottieJson() throws IOException {
        add(rect("box", 10, 10, 40, 20));
        Path target = dir.resolve("out.json");
        picked.add(target);
        export();

        assertTrue(Files.readString(target).startsWith("{"));
    }

    @Test
    void aNameWithoutExtensionGetsTheOneOfDotLottie() {
        add(rect("box", 10, 10, 40, 20));
        picked.add(dir.resolve("plain"));
        export();

        assertTrue(Files.exists(dir.resolve("plain.lottie")));
        assertFalse(Files.exists(dir.resolve("plain")));
    }

    @Test
    void cancellingWritesNothing() throws IOException {
        export();

        assertEquals(List.of(), fx(() -> window.getNoticeBar().getMessages()));
        try (var files = Files.list(dir)) {
            assertTrue(files.noneMatch(file -> file.toString().endsWith(".lottie")));
        }
    }
}
