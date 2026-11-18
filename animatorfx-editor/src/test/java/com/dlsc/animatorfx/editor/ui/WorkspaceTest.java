package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceTest {

    @Test
    void addingADocumentActivatesIt() {
        Workspace workspace = new Workspace();
        EditorDocument a = EditorDocument.untitled();
        EditorDocument b = EditorDocument.untitled();

        workspace.add(a);
        workspace.add(b);

        assertEquals(List.of(a, b), workspace.getDocuments());
        assertSame(b, workspace.getActiveDocument());
    }

    @Test
    void removingTheActiveDocumentActivatesItsNeighbour() {
        Workspace workspace = new Workspace();
        EditorDocument a = EditorDocument.untitled();
        EditorDocument b = EditorDocument.untitled();
        EditorDocument c = EditorDocument.untitled();
        workspace.add(a);
        workspace.add(b);
        workspace.add(c);
        workspace.setActiveDocument(b);

        workspace.remove(b);
        assertSame(c, workspace.getActiveDocument());
        workspace.remove(c);
        assertSame(a, workspace.getActiveDocument());
        workspace.remove(a);
        assertNull(workspace.getActiveDocument());
    }

    @Test
    void removingAnInactiveDocumentKeepsTheActiveOne() {
        Workspace workspace = new Workspace();
        EditorDocument a = EditorDocument.untitled();
        EditorDocument b = EditorDocument.untitled();
        workspace.add(a);
        workspace.add(b);

        workspace.remove(a);

        assertSame(b, workspace.getActiveDocument());
    }

    @Test
    void documentsAreFoundByTheirFile() {
        Workspace workspace = new Workspace();
        EditorDocument a = EditorDocument.untitled();
        a.setPath(Path.of("some/dir/../a.afx"));
        workspace.add(a);

        assertSame(a, workspace.findByPath(Path.of("some/a.afx")).orElseThrow());
        assertTrue(workspace.findByPath(Path.of("some/b.afx")).isEmpty());
    }

    @Test
    void recentFilesAreMostRecentFirstWithoutDuplicates() {
        Workspace workspace = new Workspace();

        workspace.addRecentFile(Path.of("a.afx"));
        workspace.addRecentFile(Path.of("b.afx"));
        workspace.addRecentFile(Path.of("a.afx"));

        assertEquals(List.of(Path.of("a.afx"), Path.of("b.afx")), workspace.getRecentFiles());
    }
}
