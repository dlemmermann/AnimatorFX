package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.importer.FxmlImporter;
import com.dlsc.animatorfx.importer.ImportResult;
import com.dlsc.animatorfx.importer.LottieImporter;
import com.dlsc.animatorfx.importer.SvgImporter;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.io.AnimationFile;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * File ▸ Import: SVG, Lottie and FXML files become new untitled documents, and an SVG can also
 * be placed into the active document. What an importer had to leave out is summed up in a
 * notice whose action lists the warnings; a file that cannot be imported is reported in a notice.
 */
public final class ImportActions {

    /** The formats that can be imported as a document. */
    public enum Kind {
        SVG("SVG", "svg"),
        LOTTIE("Lottie", "json"),
        FXML("FXML", "fxml");

        private final String label;
        private final String extension;

        Kind(String label, String extension) {
            this.label = label;
            this.extension = extension;
        }

        /** The format a file is taken for by its extension. */
        public static Optional<Kind> of(Path file) {
            String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
            for (Kind kind : values()) {
                if (name.endsWith("." + kind.extension)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    private final Workspace workspace;
    private final NoticeBar notices;
    private final Supplier<Window> owner;
    private BiFunction<Window, Kind, Optional<Path>> filePicker = this::chooseFile;
    private BiConsumer<String, List<String>> warningsViewer;
    private Path lastDirectory;

    ImportActions(Workspace workspace, NoticeBar notices, Theme theme, Supplier<Window> owner) {
        this.workspace = workspace;
        this.notices = notices;
        this.owner = owner;
        this.warningsViewer = (title, warnings) -> WarningsDialog.show(owner.get(), theme, title, warnings);
    }

    /**
     * Replaces the file dialog; it returns the chosen file, or empty when cancelled.
     */
    public void setFilePicker(BiFunction<Window, Kind, Optional<Path>> picker) {
        filePicker = Objects.requireNonNull(picker, "picker");
    }

    /**
     * Replaces the dialog that lists the warnings of an import; it gets a title and the warnings.
     */
    public void setWarningsViewer(BiConsumer<String, List<String>> viewer) {
        warningsViewer = Objects.requireNonNull(viewer, "viewer");
    }

    /**
     * Asks for a file of the kind and imports it as a new document.
     */
    public Optional<EditorDocument> chooseAndImport(Kind kind) {
        return filePicker.apply(owner.get(), kind).flatMap(file -> importFile(file, kind));
    }

    /**
     * Imports the file as a new untitled document named after it and makes it the active one.
     * The document is dirty, so it is recovered after a crash like other unsaved work. An
     * untouched untitled document that is the only open one makes way for it.
     *
     * @return the document; empty when the file could not be imported (a notice says why)
     */
    public Optional<EditorDocument> importFile(Path file, Kind kind) {
        EditorDocument document;
        List<String> warnings;
        try {
            String name = baseName(file);
            if (kind == Kind.FXML) {
                FxmlImporter.Result result = FxmlImporter.read(file.toUri().toURL());
                document = EditorDocument.imported(result.file());
                for (FxmlImporter.SlotPreview preview : result.previews()) {
                    Image image = new Image(new ByteArrayInputStream(preview.pngSnapshot()));
                    if (!image.isError()) {
                        document.getSlotPreviews().put(preview.slotName(), image);
                    }
                }
                warnings = result.warnings();
            } else {
                ImportResult result = read(file, kind, name);
                document = EditorDocument.imported(result.file());
                warnings = result.warnings();
            }
        } catch (IOException | RuntimeException | OutOfMemoryError | StackOverflowError e) {
            failed(file, e);
            return Optional.empty();
        }
        EditorDocument placeholder = workspace.getDocuments().size() == 1 ? workspace.getDocuments().get(0) : null;
        workspace.add(document);
        if (placeholder != null && FileActions.isUntouched(placeholder)) {
            workspace.remove(placeholder);
        }
        report(file, warnings);
        return Optional.of(document);
    }

    /**
     * Asks for an SVG file and places it into the active document.
     */
    public boolean chooseSvgIntoDocument() {
        EditorDocument document = workspace.getActiveDocument();
        return document != null && filePicker.apply(owner.get(), Kind.SVG)
                .map(file -> importSvgInto(document, file)).orElse(false);
    }

    /**
     * Adds the layers of an SVG file to the document as one group, named after the file, on top
     * of the other layers, together with the images it embeds. This is one undo step. The group
     * is selected.
     *
     * @return whether the file was imported; a notice says why when it was not
     */
    public boolean importSvgInto(EditorDocument document, Path file) {
        ImportResult result;
        try {
            result = read(file, Kind.SVG, baseName(file));
        } catch (IOException | RuntimeException | OutOfMemoryError | StackOverflowError e) {
            failed(file, e);
            return false;
        }
        AnimationFile content = result.file();
        Animation target = document.getAnimation();
        List<Layer> layers = new ArrayList<>();
        for (Layer layer : content.animation().layers()) {
            layers.add(lasting(layer, target.duration()));
        }
        GroupLayer group = new GroupLayer(LayerBase.create(content.animation().name(), target.duration()), layers);
        Map<Asset, byte[]> assets = new LinkedHashMap<>();
        for (Asset asset : content.animation().assets()) {
            assets.put(asset, content.assets().get(asset.id()));
        }
        document.execute(Commands.addLayers("Import SVG", null, target.layers().size(), List.of(group)), assets);
        document.getSelection().layerIds().setAll(group.id());
        report(file, result.warnings());
        return true;
    }

    /** An SVG has no time of its own: its layers are shown for as long as the document runs. */
    private static Layer lasting(Layer layer, double duration) {
        Layer result = layer.withBase(layer.base().withInPoint(0).withOutPoint(duration));
        if (result instanceof GroupLayer group) {
            List<Layer> children = new ArrayList<>();
            for (Layer child : group.children()) {
                children.add(lasting(child, duration));
            }
            return group.withChildren(children);
        }
        return result;
    }

    private static ImportResult read(Path file, Kind kind, String name) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            return kind == Kind.SVG ? SvgImporter.read(in, name) : LottieImporter.read(in, name);
        }
    }

    /** Also for a file that exhausts memory or the stack: it must not take the editor down. */
    private void failed(Path file, Throwable e) {
        String reason = e instanceof Error ? "the file is too large or too deeply nested"
                : e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
        notices.show("Could not import " + file.getFileName() + ": " + reason);
    }

    private void report(Path file, List<String> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        String summary = "Imported " + file.getFileName() + " with " + warnings.size()
                + (warnings.size() == 1 ? " warning." : " warnings.");
        notices.show(summary, "Show warnings", () -> warningsViewer.accept(summary, warnings));
    }

    private static String baseName(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.isBlank() ? EditorDocument.UNTITLED : base;
    }

    private Optional<Path> chooseFile(Window window, Kind kind) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import " + kind.label);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                kind.label + " files (*." + kind.extension + ")", "*." + kind.extension));
        if (lastDirectory != null && Files.isDirectory(lastDirectory)) {
            chooser.setInitialDirectory(lastDirectory.toFile());
        }
        File file = chooser.showOpenDialog(window);
        if (file == null) {
            return Optional.empty();
        }
        lastDirectory = file.toPath().toAbsolutePath().getParent();
        return Optional.of(file.toPath());
    }
}
