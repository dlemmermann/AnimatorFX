package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.session.SessionState;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The open documents, the active one and the recent files. Every part of the window follows
 * {@link #activeDocumentProperty()}.
 */
public final class Workspace {

    private final ObservableList<EditorDocument> documents = FXCollections.observableArrayList();
    private final ObservableList<Path> recentFiles = FXCollections.observableArrayList();
    private final ObjectProperty<EditorDocument> activeDocument = new SimpleObjectProperty<>(this, "activeDocument");
    private final ChangeListener<Path> pathListener = (obs, oldPath, newPath) -> {
        if (newPath != null) {
            addRecentFile(newPath);
        }
    };

    public ObservableList<EditorDocument> getDocuments() {
        return documents;
    }

    /**
     * Most recent first, at most {@value SessionState#MAX_RECENT_FILES}. The file of every opened
     * or saved document is added automatically.
     */
    public ObservableList<Path> getRecentFiles() {
        return recentFiles;
    }

    /**
     * The document that the window shows and edits; {@code null} only while no document is open.
     *
     * @return the active document property
     */
    public final ObjectProperty<EditorDocument> activeDocumentProperty() {
        return activeDocument;
    }

    public final EditorDocument getActiveDocument() {
        return activeDocument.get();
    }

    public final void setActiveDocument(EditorDocument value) {
        activeDocument.set(value);
    }

    /**
     * Adds the document after the others and makes it the active one.
     */
    public void add(EditorDocument document) {
        documents.add(document);
        document.pathProperty().addListener(pathListener);
        if (document.getPath() != null) {
            addRecentFile(document.getPath());
        }
        setActiveDocument(document);
    }

    /**
     * Removes the document. When it was the active one, its right neighbour, or else its left
     * neighbour, becomes active.
     */
    public void remove(EditorDocument document) {
        int index = documents.indexOf(document);
        if (index < 0) {
            return;
        }
        document.pathProperty().removeListener(pathListener);
        documents.remove(index);
        if (getActiveDocument() == document) {
            setActiveDocument(documents.isEmpty() ? null : documents.get(Math.min(index, documents.size() - 1)));
        }
    }

    public Optional<EditorDocument> findByPath(Path file) {
        Path wanted = normalize(file);
        return documents.stream()
                .filter(d -> d.getPath() != null && normalize(d.getPath()).equals(wanted))
                .findFirst();
    }

    public void addRecentFile(Path file) {
        recentFiles.setAll(SessionState.addRecent(recentFiles, file));
    }

    private static Path normalize(Path file) {
        return file.toAbsolutePath().normalize();
    }
}
