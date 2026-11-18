package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * New, open, save, save as, revert and close. Failures are reported in the notice bar. The
 * workspace never stays empty: closing the last document opens a new untitled one.
 */
public final class FileActions {

    private final Workspace workspace;
    private final Dialogs dialogs;
    private final NoticeBar notices;
    private final Supplier<Window> owner;

    public FileActions(Workspace workspace, Dialogs dialogs, NoticeBar notices, Supplier<Window> owner) {
        this.workspace = workspace;
        this.dialogs = dialogs;
        this.notices = notices;
        this.owner = owner;
    }

    public EditorDocument newDocument() {
        EditorDocument document = EditorDocument.untitled();
        workspace.add(document);
        return document;
    }

    /**
     * Asks for a file and opens it.
     */
    public void open() {
        dialogs.chooseOpenFile(owner.get()).ifPresent(this::open);
    }

    /**
     * Opens the file, or activates the document that already shows it. An untouched untitled
     * document that is the only open one makes way for the opened file.
     *
     * @return the document; empty when the file could not be read (a notice says why)
     */
    public Optional<EditorDocument> open(Path file) {
        Optional<EditorDocument> existing = workspace.findByPath(file);
        if (existing.isPresent()) {
            workspace.setActiveDocument(existing.get());
            return existing;
        }
        EditorDocument document;
        try {
            document = EditorDocument.open(file);
        } catch (IOException | RuntimeException e) {
            notices.show("Could not open " + file.getFileName() + ": " + reason(e));
            return Optional.empty();
        }
        EditorDocument placeholder = workspace.getDocuments().size() == 1 ? workspace.getDocuments().get(0) : null;
        workspace.add(document);
        if (placeholder != null && isUntouched(placeholder)) {
            workspace.remove(placeholder);
        }
        return Optional.of(document);
    }

    /**
     * Saves the active document.
     *
     * @return whether the document was saved
     */
    public boolean save() {
        EditorDocument document = workspace.getActiveDocument();
        return document != null && save(document);
    }

    /**
     * Saves the document to its file, asking for one when it is untitled. A document that was
     * opened from a file that is no {@code .afx}, such as a bare {@code animation.json}, asks
     * too: what is saved is an archive, which must not replace the source under its name.
     *
     * @return whether the document was saved; {@code false} when cancelled or failed
     */
    public boolean save(EditorDocument document) {
        if (document.getPath() == null || !isAfx(document.getPath())) {
            return saveAs(document);
        }
        try {
            document.save();
            return true;
        } catch (IOException | RuntimeException e) {
            notices.show("Could not save " + document.getTitle() + ": " + reason(e));
            return false;
        }
    }

    public boolean saveAs() {
        EditorDocument document = workspace.getActiveDocument();
        return document != null && saveAs(document);
    }

    /**
     * Asks for a file and saves the document to it. A file that another tab shows is refused
     * with a notice: two documents with one file would overwrite each other.
     *
     * @return whether the document was saved; {@code false} when cancelled, refused or failed
     */
    public boolean saveAs(EditorDocument document) {
        Optional<Path> target = dialogs.chooseSaveFile(owner.get(), suggestedName(document));
        if (target.isEmpty()) {
            return false;
        }
        Optional<EditorDocument> other = workspace.findByPath(target.get()).filter(d -> d != document);
        if (other.isPresent()) {
            notices.show(target.get().getFileName() + " is open in another tab (" + other.get().getTitle()
                    + "). Close that tab or choose another name.");
            return false;
        }
        try {
            document.saveAs(target.get());
            return true;
        } catch (IOException | RuntimeException e) {
            notices.show("Could not save " + target.get().getFileName() + ": " + reason(e));
            return false;
        }
    }

    /**
     * Reloads the active document from its file, after asking when it has unsaved changes.
     */
    public void revert() {
        EditorDocument document = workspace.getActiveDocument();
        if (document == null || document.getPath() == null) {
            return;
        }
        if (document.isDirty() && !dialogs.confirmRevert(owner.get(), document.getTitle())) {
            return;
        }
        try {
            document.revert();
        } catch (IOException | RuntimeException e) {
            notices.show("Could not revert " + document.getTitle() + ": " + reason(e));
        }
    }

    public boolean close() {
        EditorDocument document = workspace.getActiveDocument();
        return document != null && close(document);
    }

    /**
     * Closes the document. A dirty document asks first: Save, Don't Save or Cancel.
     *
     * @return whether the document was closed
     */
    public boolean close(EditorDocument document) {
        if (document.isDirty()) {
            switch (dialogs.confirmClose(owner.get(), document.getTitle())) {
                case SAVE -> {
                    if (!save(document)) {
                        return false;
                    }
                }
                case DISCARD -> {
                }
                case CANCEL -> {
                    return false;
                }
            }
        }
        discard(document);
        return true;
    }

    /**
     * Removes the document without asking and without saving.
     */
    public void discard(EditorDocument document) {
        workspace.remove(document);
        if (workspace.getDocuments().isEmpty()) {
            newDocument();
        }
    }

    private static boolean isAfx(Path file) {
        return file.getFileName() != null && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".afx");
    }

    /** The title, with the extension of a source file that is no {@code .afx} replaced. */
    private static String suggestedName(EditorDocument document) {
        String title = document.getTitle();
        if (document.getPath() == null || isAfx(document.getPath())) {
            return title;
        }
        int dot = title.lastIndexOf('.');
        return (dot > 0 ? title.substring(0, dot) : title) + ".afx";
    }

    static boolean isUntouched(EditorDocument document) {
        return document.getPath() == null && !document.isDirty() && !document.isCanUndo() && !document.isCanRedo();
    }

    private static String reason(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
