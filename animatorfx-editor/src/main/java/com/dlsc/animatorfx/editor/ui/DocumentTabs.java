package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import javafx.beans.InvalidationListener;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The document strip: one tab per open document with its title, a dot while it is dirty and a
 * close button, followed by a button for a new document. A click activates a document, a middle
 * click or the close button asks to close it.
 */
public final class DocumentTabs extends HBox {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final Workspace workspace;
    private final Consumer<EditorDocument> closeRequest;
    private final Button newButton = new Button(null, new FontIcon(Material2AL.ADD));

    /**
     * @param closeRequest called when the user asks to close a document
     * @param newRequest   called when the user asks for a new document
     */
    public DocumentTabs(Workspace workspace, Consumer<EditorDocument> closeRequest, Runnable newRequest) {
        this.workspace = workspace;
        this.closeRequest = closeRequest;
        getStyleClass().add("document-tabs");
        newButton.setId("new-document");
        newButton.getStyleClass().add("icon-button");
        newButton.setFocusTraversable(false);
        newButton.setTooltip(new Tooltip("New document"));
        newButton.setOnAction(e -> newRequest.run());

        workspace.getDocuments().addListener((InvalidationListener) obs -> rebuild());
        workspace.activeDocumentProperty().addListener(obs -> updateSelection());
        rebuild();
    }

    private void rebuild() {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        for (EditorDocument document : workspace.getDocuments()) {
            nodes.add(new Tab(document));
        }
        nodes.add(newButton);
        getChildren().setAll(nodes);
        updateSelection();
    }

    private void updateSelection() {
        for (javafx.scene.Node node : getChildren()) {
            if (node instanceof Tab tab) {
                tab.pseudoClassStateChanged(SELECTED, tab.document == workspace.getActiveDocument());
            }
        }
    }

    private final class Tab extends HBox {

        private final EditorDocument document;

        Tab(EditorDocument document) {
            this.document = document;
            getStyleClass().add("document-tab");

            Label title = new Label();
            title.getStyleClass().add("document-title");
            title.textProperty().bind(document.titleProperty());

            Region dot = new Region();
            dot.getStyleClass().add("dirty-dot");
            dot.visibleProperty().bind(document.dirtyProperty());

            Button close = new Button(null, new FontIcon(Material2AL.CLOSE));
            close.getStyleClass().addAll("icon-button", "tab-close");
            close.setFocusTraversable(false);
            close.setOnAction(e -> closeRequest.accept(document));

            getChildren().addAll(title, dot, close);
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.MIDDLE) {
                    closeRequest.accept(document);
                } else if (e.getButton() == MouseButton.PRIMARY) {
                    workspace.setActiveDocument(document);
                }
            });
        }
    }
}
