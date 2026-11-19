package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.importer.LottieExporter;
import com.dlsc.animatorfx.model.io.AnimationFile;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * File ▸ Export: the active document as a dotLottie archive ({@code .lottie}) or as Lottie JSON
 * ({@code .json}). The document itself is not touched and stays an {@code .afx} document: Lottie
 * cannot hold everything it can. What the export had to leave out is summed up in a notice whose
 * action lists the warnings; a file that cannot be written is reported in a notice.
 */
public final class ExportActions {

    private static final String DOT_LOTTIE = ".lottie";
    private static final String JSON = ".json";

    private final Workspace workspace;
    private final NoticeBar notices;
    private final Supplier<Window> owner;
    private BiFunction<Window, String, Optional<Path>> filePicker = this::chooseFile;
    private BiConsumer<String, List<String>> warningsViewer;
    private Path lastDirectory;

    ExportActions(Workspace workspace, NoticeBar notices, Dialogs dialogs, Supplier<Window> owner) {
        this.workspace = workspace;
        this.notices = notices;
        this.owner = owner;
        this.warningsViewer = dialogs::showWarnings;
    }

    /**
     * Replaces the file dialog; it gets the suggested file name and returns the chosen file, or
     * empty when cancelled.
     */
    public void setFilePicker(BiFunction<Window, String, Optional<Path>> picker) {
        filePicker = Objects.requireNonNull(picker, "picker");
    }

    /**
     * Replaces the dialog that lists the warnings of an export; it gets a title and the warnings.
     */
    public void setWarningsViewer(BiConsumer<String, List<String>> viewer) {
        warningsViewer = Objects.requireNonNull(viewer, "viewer");
    }

    /** Asks for a file and writes the active document to it. */
    public void chooseAndExportLottie() {
        EditorDocument document = workspace.getActiveDocument();
        if (document == null) {
            return;
        }
        filePicker.apply(owner.get(), document.getTitle() + DOT_LOTTIE)
                .ifPresent(file -> exportLottie(document, file));
    }

    /**
     * Writes a document as Lottie: as JSON to a file whose name ends with {@code .json}, as a
     * dotLottie archive to any other, with {@code .lottie} added to a name without it.
     *
     * @return whether the file was written
     */
    public boolean exportLottie(EditorDocument document, Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean json = name.endsWith(JSON);
        Path target = json || name.endsWith(DOT_LOTTIE) ? file
                : file.resolveSibling(file.getFileName() + DOT_LOTTIE);
        AnimationFile content = document.getFileContent();
        List<String> warnings;
        try {
            // written beside the target first: a failed export must not leave half a file behind
            Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".export", ".tmp");
            try {
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                    warnings = json ? LottieExporter.writeJson(content, out)
                            : LottieExporter.writeDotLottie(content, out);
                }
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException | OutOfMemoryError | StackOverflowError e) {
            String reason = e instanceof Error ? "the document is too large or too deeply nested"
                    : e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName()
                    : e.getMessage();
            notices.show("Could not export " + target.getFileName() + ": " + reason);
            return false;
        }
        if (warnings.isEmpty()) {
            notices.show("Exported " + target.getFileName() + ".");
        } else {
            String summary = "Exported " + target.getFileName() + " with " + warnings.size()
                    + (warnings.size() == 1 ? " warning." : " warnings.");
            notices.show(summary, "Show warnings", () -> warningsViewer.accept(summary, warnings), false);
        }
        return true;
    }

    private Optional<Path> chooseFile(Window window, String suggestedName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Lottie");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("dotLottie files (*.lottie)", "*" + DOT_LOTTIE),
                new FileChooser.ExtensionFilter("Lottie JSON files (*.json)", "*" + JSON));
        chooser.setInitialFileName(suggestedName);
        if (lastDirectory != null && Files.isDirectory(lastDirectory)) {
            chooser.setInitialDirectory(lastDirectory.toFile());
        }
        File file = chooser.showSaveDialog(window);
        if (file == null) {
            return Optional.empty();
        }
        lastDirectory = file.toPath().toAbsolutePath().getParent();
        return Optional.of(file.toPath());
    }
}
