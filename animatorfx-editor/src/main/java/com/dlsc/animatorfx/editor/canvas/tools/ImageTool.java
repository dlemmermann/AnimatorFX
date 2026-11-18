package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.ui.ToolKind;
import javafx.application.Platform;

/**
 * Adds an image: choosing the tool asks for a PNG or JPEG file
 * ({@link com.dlsc.animatorfx.editor.canvas.CanvasActions#setImagePicker the file dialog} can be
 * replaced, which is what tests do), adds it as an asset with an image layer in the middle of
 * the artboard, and hands over to the select tool. Image files can also be dropped onto the canvas.
 */
public final class ImageTool implements Tool {

    private final CanvasView canvas;

    public ImageTool(CanvasView canvas) {
        this.canvas = canvas;
    }

    @Override
    public void activate() {
        // after the tool change has gone through: the file dialog blocks
        Platform.runLater(() -> {
            if (canvas.getActiveTool() != ToolKind.IMAGE) {
                return;
            }
            if (canvas.getDocument() != null) {
                canvas.getActions().chooseImage();
            }
            canvas.setActiveTool(ToolKind.SELECT);
        });
    }
}
