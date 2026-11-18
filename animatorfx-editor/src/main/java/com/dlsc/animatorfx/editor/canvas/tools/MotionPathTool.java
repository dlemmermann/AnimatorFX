package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.doc.Command;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.MotionPath;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.PathData.Contour;
import com.dlsc.animatorfx.model.PathData.Vertex;

import java.util.List;

/**
 * Draws the motion path of a layer, a modal tool with the gestures of the pen (see
 * {@link PathDrawTool}); the path stays open. It is drawn in the coordinates of the layer's
 * parent, where the layer's position lives.
 *
 * <p>From the second point on the layer is on the path: its progress runs from 0 % at its
 * in-point to 100 % at its out-point (see {@link Commands#addMotionPath}), so scrubbing shows the
 * motion at once. Drawing the whole path is one undo step. The path replaces the layer's
 * position; when that has keyframes, a notice says that they no longer have an effect.
 */
public final class MotionPathTool extends PathDrawTool {

    private final String layerId;
    private boolean added;

    public MotionPathTool(CanvasView canvas, String layerId) {
        super(canvas);
        this.layerId = layerId;
    }

    public String getLayerId() {
        return layerId;
    }

    @Override
    public void activate() {
        super.activate();
        canvas.notice("Click to place the points of the motion path, drag to curve it. Enter finishes, Esc cancels.");
    }

    @Override
    protected String parentId() {
        added = false;
        return Layers.parentOf(canvas.getDocument().getAnimation(), layerId).map(Layer::id).orElse(null);
    }

    @Override
    protected boolean canClose() {
        return false;
    }

    @Override
    protected Command command(List<Vertex> vertices, boolean closed, boolean finished) {
        if (vertices.size() < 2) {
            // a path of one point leads nowhere; the layer stays where it is until there are two
            return added ? Commands.setMotionPath(layerId, null) : null;
        }
        PathData path = new PathData(List.of(new Contour(vertices, false)));
        if (!added || canvas.getGeometry().layer(layerId).map(l -> l.base().motionPath()).isEmpty()) {
            added = true;
            return Commands.addMotionPath(layerId, path);
        }
        return Commands.updateLayer("Add Motion Path", layerId, layer -> {
            MotionPath motion = layer.base().motionPath();
            return motion == null ? layer : layer.withBase(layer.base().withMotionPath(motion.withPath(path)));
        });
    }

    @Override
    protected boolean exists() {
        return canvas.getGeometry().layer(layerId).isPresent();
    }

    @Override
    protected void finished(boolean replaced) {
        boolean keyed = canvas.getGeometry().layer(layerId)
                .map(layer -> layer.base().transform().position().isAnimated()).orElse(false);
        if (keyed) {
            canvas.notice("The layer now follows its motion path; its position keyframes have no effect.");
        }
        leave(replaced);
    }

    @Override
    protected void dropped(boolean replaced) {
        leave(replaced);
    }

    private void leave(boolean replaced) {
        if (!replaced && canvas.getModalTool() == this) {
            canvas.setModalTool(null);
        }
    }
}
