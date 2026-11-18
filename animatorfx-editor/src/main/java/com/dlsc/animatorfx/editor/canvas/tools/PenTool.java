package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.canvas.LayerNames;
import com.dlsc.animatorfx.editor.doc.Command;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.ui.ToolKind;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.PathData.Vertex;
import com.dlsc.animatorfx.model.ShapeLayer;

import java.util.List;
import java.util.UUID;

/**
 * The pen: draws a path layer point by point, see {@link PathDrawTool} for the gestures.
 *
 * <p>The layer is in the document from the first click on ("Path 1", "Path 2", …), so the path
 * shows as it is drawn: a 2 px accent stroke without a fill. A closed path gets the fill of new
 * shapes and no stroke. Drawing a whole path is one undo step. When it is finished the layer has
 * its anchor in the middle of its box, is selected, and the select tool takes over.
 */
public final class PenTool extends PathDrawTool {

    private String layerId;
    private String name;
    private String parentId;

    public PenTool(CanvasView canvas) {
        super(canvas);
    }

    @Override
    protected String parentId() {
        layerId = null;
        parentId = canvas.getEnteredGroupId();
        return parentId;
    }

    @Override
    protected boolean canClose() {
        return true;
    }

    @Override
    protected Command command(List<Vertex> vertices, boolean closed, boolean finished) {
        Animation animation = canvas.getDocument().getAnimation();
        if (layerId == null) {
            layerId = UUID.randomUUID().toString();
            name = LayerNames.next(animation, "Path");
            ShapeLayer layer = NewLayers.path(layerId, name, animation.duration(), vertices, closed, finished);
            return Commands.addLayer(parentId, canvas.getGeometry().scope().size(), layer);
        }
        ShapeLayer layer = NewLayers.path(layerId, name, animation.duration(), vertices, closed, finished);
        return Commands.updateLayer("Add Layer", layerId, old -> layer);
    }

    @Override
    protected boolean exists() {
        return canvas.getGeometry().layer(layerId).isPresent();
    }

    @Override
    protected void finished(boolean replaced) {
        canvas.getDocument().getSelection().layerIds().setAll(layerId);
        if (!replaced) {
            canvas.setActiveTool(ToolKind.SELECT);
        }
    }
}
