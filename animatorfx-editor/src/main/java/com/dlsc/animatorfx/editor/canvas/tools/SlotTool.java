package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.Box;
import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.canvas.Handles;
import com.dlsc.animatorfx.editor.canvas.LayerGeometry;
import com.dlsc.animatorfx.editor.canvas.LayerNames;
import com.dlsc.animatorfx.editor.canvas.Quad;
import com.dlsc.animatorfx.editor.canvas.TransformMath;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.ui.ToolKind;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Creates a slot: drag its rectangle (or click for the default size), then name it in the small
 * prompt that opens below it. The name must be a Java identifier that no other slot uses; the
 * prompt says what is wrong and stays open until the name is fine or Escape is pressed. The
 * layer is added when the name is accepted, as one undo step, with its rest transform equal to
 * its transform.
 */
public final class SlotTool implements Tool {

    /** The ids of the name field and of the validation message, for lookups. */
    public static final String NAME_FIELD_ID = "slot-name-field";
    public static final String NAME_ERROR_ID = "slot-name-error";

    private static final double DRAG_THRESHOLD = 3;

    private final CanvasView canvas;
    private Pointer start;
    private String parentId;
    private Vec2 startInParent;
    private Box box;
    private boolean dragging;
    private VBox prompt;
    private TextField field;
    private Label error;

    public SlotTool(CanvasView canvas) {
        this.canvas = canvas;
    }

    public boolean isPrompting() {
        return prompt != null;
    }

    @Override
    public void pressed(Pointer p) {
        if (isPrompting()) {
            cancel();
        }
        start = p;
        parentId = canvas.getEnteredGroupId();
        startInParent = canvas.getGeometry().viewToGroup(parentId, p.view());
        box = null;
        dragging = true;
    }

    @Override
    public void dragged(Pointer p) {
        if (!dragging || (box == null && Handles.distance(start.view(), p.view()) < DRAG_THRESHOLD)) {
            return;
        }
        Vec2 delta = canvas.getGeometry().viewToGroup(parentId, p.view()).minus(startInParent);
        if (p.shift()) {
            delta = TransformMath.constrainSquare(delta);
        }
        box = Box.between(startInParent, startInParent.plus(delta));
        showPreview();
    }

    @Override
    public void released(Pointer p) {
        if (!dragging) {
            return;
        }
        dragging = false;
        if (box == null || box.width() < 1 || box.height() < 1) {
            double half = ShapeTool.DEFAULT_SIZE / 2;
            box = new Box(startInParent.x() - half, startInParent.y() - half,
                    startInParent.x() + half, startInParent.y() + half);
        }
        showPreview();
        openPrompt();
    }

    private void showPreview() {
        canvas.getOverlay().setPreview(Quad.of(box, canvas.getGeometry().groupToView(parentId)), "overlay-slot");
    }

    private void openPrompt() {
        EditorDocument doc = canvas.getDocument();
        field = new TextField(LayerNames.nextSlotName(doc.getAnimation()));
        field.setId(NAME_FIELD_ID);
        field.getStyleClass().add("mono");
        field.setPromptText("slot name");
        error = new Label();
        error.setId(NAME_ERROR_ID);
        error.getStyleClass().add("slot-prompt-error");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);
        Label title = new Label("Slot name");
        title.getStyleClass().add("field-label");
        prompt = new VBox(title, field, error);
        prompt.getStyleClass().add("slot-prompt");
        field.setOnAction(e -> accept());
        field.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancel();
                e.consume();
            }
        });
        field.textProperty().addListener(obs -> showError(null));
        VBox opened = prompt;
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && prompt == opened) {
                // a valid name is taken as typed; anything else gives the slot up
                if (LayerNames.slotNameError(canvas.getDocument().getAnimation(), field.getText()).isEmpty()) {
                    accept();
                } else {
                    cancel();
                }
            }
        });
        canvas.getEditorLayer().getChildren().add(prompt);
        placePrompt();
        field.requestFocus();
        field.selectAll();
    }

    private void showError(String message) {
        error.setText(message == null ? "" : message);
        error.setVisible(message != null);
        error.setManaged(message != null);
        placePrompt();
    }

    private void placePrompt() {
        if (prompt == null) {
            return;
        }
        Box view = Quad.of(box, canvas.getGeometry().groupToView(parentId)).bounds();
        prompt.applyCss();
        double width = 220;
        double height = prompt.prefHeight(width);
        double x = Math.clamp(view.minX(), 4, Math.max(4, canvas.getWidth() - width - 4));
        double y = view.maxY() + 8;
        if (y + height > canvas.getHeight() - 4) {
            y = Math.max(4, view.minY() - height - 8);
        }
        prompt.resizeRelocate(Math.rint(x), Math.rint(y), width, height);
    }

    @Override
    public void viewChanged() {
        if (isPrompting() || dragging) {
            if (box != null) {
                showPreview();
            }
            placePrompt();
        }
    }

    /** Creates the slot when the name in the prompt is valid; shows what is wrong otherwise. */
    private void accept() {
        EditorDocument doc = canvas.getDocument();
        String name = field.getText().trim();
        Optional<String> problem = LayerNames.slotNameError(doc.getAnimation(), name);
        if (problem.isPresent()) {
            showError(problem.get());
            return;
        }
        LayerGeometry geometry = canvas.getGeometry();
        SlotLayer slot = NewLayers.slot(doc.getAnimation(), name, new Vec2(box.centerX(), box.centerY()),
                new Vec2(box.width(), box.height()));
        closePrompt();
        doc.execute(Commands.all("Add Slot", Commands.addLayer(parentId, geometry.scope().size(), slot)));
        doc.getSelection().layerIds().setAll(slot.id());
        canvas.setActiveTool(ToolKind.SELECT);
    }

    private void closePrompt() {
        VBox opened = prompt;
        prompt = null;
        if (opened != null) {
            canvas.getEditorLayer().getChildren().remove(opened);
            canvas.requestFocus();
        }
        canvas.getOverlay().setPreview(null, null);
    }

    @Override
    public boolean cancel() {
        boolean busy = dragging || isPrompting();
        dragging = false;
        box = null;
        closePrompt();
        return busy;
    }

    @Override
    public void finish() {
        cancel();
    }

    @Override
    public Cursor cursor(Pointer pointer) {
        return Cursor.CROSSHAIR;
    }
}
