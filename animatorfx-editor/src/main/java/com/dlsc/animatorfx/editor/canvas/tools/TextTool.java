package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.Box;
import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.canvas.LayerGeometry;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.Matrices;
import com.dlsc.animatorfx.editor.ui.ToolKind;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.Cursor;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Places text and edits it in place. A click on empty canvas adds a text layer reading "Text"
 * and opens an editor over it; a click on a text layer, or a double-click on one with the select
 * tool, edits that layer. Enter or a click elsewhere commits, Escape cancels.
 *
 * <p>Placing and typing a new text is one undo step, and so is an edit of an existing one. A
 * text committed empty is removed. Cancelling a new text keeps the placeholder layer, unless the
 * editor was emptied first, which removes it.
 */
public final class TextTool implements Tool {

    public static final String DEFAULT_TEXT = "Text";
    /** The id of the inline editor, for lookups. */
    public static final String EDITOR_ID = "canvas-text-editor";

    private final CanvasView canvas;
    private TextField editor;
    /** The document being edited; the canvas may have moved on to another one when the editor closes. */
    private EditorDocument document;
    private String layerId;
    private boolean isNew;
    private Object gestureKey;
    private boolean closing;

    public TextTool(CanvasView canvas) {
        this.canvas = canvas;
        canvas.getDoubleClickHandlers().add(layer -> {
            if (layer instanceof TextLayer) {
                edit(layer.id());
                return true;
            }
            return false;
        });
    }

    public boolean isEditing() {
        return editor != null;
    }

    @Override
    public void pressed(Pointer p) {
        if (isEditing()) {
            commit();
            return;
        }
        LayerGeometry geometry = canvas.getGeometry();
        EditorDocument doc = canvas.getDocument();
        Optional<Layer> hit = geometry.layerAt(p.view()).flatMap(geometry::layer);
        if (hit.orElse(null) instanceof TextLayer text) {
            doc.getSelection().layerIds().setAll(text.id());
            edit(text.id());
            return;
        }
        String parentId = canvas.getEnteredGroupId();
        TextLayer layer = NewLayers.text(doc.getAnimation(), geometry.viewToGroup(parentId, p.view()), DEFAULT_TEXT);
        doc.endGesture();
        gestureKey = new Object();
        doc.executeCoalesced(gestureKey, Commands.addLayer(parentId, geometry.scope().size(), layer));
        doc.getSelection().layerIds().setAll(layer.id());
        open(layer.id(), true);
    }

    /**
     * Opens the inline editor on an existing text layer.
     */
    public void edit(String textLayerId) {
        if (isEditing()) {
            commit();
        }
        if (canvas.getGeometry().layer(textLayerId).orElse(null) instanceof TextLayer) {
            open(textLayerId, false);
        }
    }

    private void open(String id, boolean fresh) {
        TextLayer layer = (TextLayer) canvas.getGeometry().layer(id).orElseThrow();
        document = canvas.getDocument();
        layerId = id;
        isNew = fresh;
        TextField field = new TextField(layer.text());
        field.setId(EDITOR_ID);
        field.getStyleClass().add("canvas-text-editor");
        field.setOnAction(e -> commit());
        field.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancel();
                e.consume();
            }
        });
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && editor == field) {
                commit();
            }
        });
        field.textProperty().addListener(obs -> place());
        editor = field;
        canvas.getEditorLayer().getChildren().add(field);
        place();
        field.requestFocus();
        field.selectAll();
    }

    /** Puts the editor over the text, in the text's font at the size it has on screen. */
    private void place() {
        if (editor == null) {
            return;
        }
        LayerGeometry geometry = canvas.getGeometry();
        if (!(geometry.layer(layerId).orElse(null) instanceof TextLayer layer)) {
            return;
        }
        double[] toView = geometry.localToView(layer);
        Vec2 down = Matrices.applyLinear(toView, new Vec2(0, 1));
        double size = Math.max(6, layer.fontSize().at(canvas.getTime()) * Math.hypot(down.x(), down.y()));
        editor.setStyle(String.format(Locale.ROOT, "-fx-font-family: \"%s\"; -fx-font-size: %.2fpx;",
                layer.fontFamily().replace("\"", ""), size));
        Text measure = new Text(editor.getText().isEmpty() ? " " : editor.getText());
        measure.setFont(Font.font(layer.fontFamily(), size));
        double width = Math.ceil(measure.getLayoutBounds().getWidth()) + size;
        double height = Math.ceil(measure.getLayoutBounds().getHeight()) + 4;
        Box box = geometry.localBox(layer);
        Vec2 at = Matrices.apply(toView, new Vec2(box.minX(), box.minY()));
        editor.resizeRelocate(Math.rint(at.x()) - 3, Math.rint(at.y()) - 2, width + 6, height);
    }

    @Override
    public void viewChanged() {
        place();
    }

    /** Writes the editor's text to the layer and closes the editor. */
    public void commit() {
        if (editor == null || closing) {
            return;
        }
        String text = editor.getText();
        close();
        EditorDocument doc = document;
        if (Layers.find(doc.getAnimation(), layerId).orElse(null) instanceof TextLayer layer) {
            if (text.isEmpty()) {
                remove(doc);
            } else if (isNew) {
                doc.executeCoalesced(gestureKey, Commands.updateLayer("Add Layer", layerId,
                        l -> ((TextLayer) l).withText(text)));
                doc.endGesture();
            } else if (!text.equals(layer.text())) {
                doc.execute(Commands.updateLayer("Edit Text", layerId, l -> ((TextLayer) l).withText(text)));
            }
        }
        canvas.setActiveTool(ToolKind.SELECT);
    }

    @Override
    public boolean cancel() {
        if (editor == null || closing) {
            return false;
        }
        boolean emptied = editor.getText().isEmpty();
        close();
        if (isNew) {
            if (emptied) {
                remove(document);
            } else {
                document.endGesture();
            }
        }
        canvas.setActiveTool(ToolKind.SELECT);
        return true;
    }

    /** Removes the layer: a new one leaves no trace in the undo history. */
    private void remove(EditorDocument doc) {
        if (isNew) {
            doc.cancelGesture();
        }
        if (Layers.find(doc.getAnimation(), layerId).isPresent()) {
            doc.execute(Commands.removeLayers(List.of(layerId)));
        }
    }

    private void close() {
        closing = true;
        try {
            TextField field = editor;
            editor = null;
            canvas.getEditorLayer().getChildren().remove(field);
            canvas.requestFocus();
        } finally {
            closing = false;
        }
    }

    @Override
    public void finish() {
        commit();
    }

    @Override
    public Cursor cursor(Pointer pointer) {
        return Cursor.TEXT;
    }
}
