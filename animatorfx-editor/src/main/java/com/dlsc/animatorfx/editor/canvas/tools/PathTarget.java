package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.doc.Command;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.MotionPath;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.ShapeLayer;

import java.util.Optional;

/**
 * The path that {@link PathEditTool} works on: where it is read from, how a changed path is
 * written back, and in which coordinates it lives.
 */
public sealed interface PathTarget {

    String GEOMETRY_PATH = "geometry.path";

    String layerId();

    /** The path at the time on the canvas; empty when the layer or its path has gone. */
    Optional<PathData> read(CanvasView canvas);

    /** The command that stores an edited path. */
    Command write(CanvasView canvas, PathData path);

    /** The matrix from path coordinates to view pixels; {@code null} when the layer has gone. */
    double[] toView(CanvasView canvas);

    /** The outline of a path layer, in the layer's own coordinates. */
    static PathTarget geometry(String layerId) {
        return new Shape(layerId);
    }

    /** The motion path of a layer, in the coordinates of the layer's parent. */
    static PathTarget motionPath(String layerId) {
        return new Motion(layerId);
    }

    /**
     * The path geometry of a shape layer. It is animatable: an edit follows the auto-key rule, so
     * editing at another time than an existing path keyframe makes the shape morph.
     */
    record Shape(String layerId) implements PathTarget {

        @Override
        public Optional<PathData> read(CanvasView canvas) {
            return canvas.getGeometry().layer(layerId)
                    .filter(layer -> layer instanceof ShapeLayer shape && shape.geometry() instanceof Geometry.PathGeometry)
                    .map(layer -> ((Geometry.PathGeometry) ((ShapeLayer) layer).geometry()).path().at(canvas.getTime()));
        }

        @Override
        public Command write(CanvasView canvas, PathData path) {
            return canvas.valueCommand(layerId, GEOMETRY_PATH, path);
        }

        @Override
        public double[] toView(CanvasView canvas) {
            return canvas.getGeometry().layer(layerId).map(canvas.getGeometry()::localToView).orElse(null);
        }
    }

    /** The motion path of a layer, which is not animatable. */
    record Motion(String layerId) implements PathTarget {

        @Override
        public Optional<PathData> read(CanvasView canvas) {
            return canvas.getGeometry().layer(layerId).map(layer -> layer.base().motionPath()).map(MotionPath::path);
        }

        @Override
        public Command write(CanvasView canvas, PathData path) {
            return Commands.updateLayer("Edit Motion Path", layerId, layer -> withPath(layer, path));
        }

        private static Layer withPath(Layer layer, PathData path) {
            MotionPath motion = layer.base().motionPath();
            return motion == null ? layer : layer.withBase(layer.base().withMotionPath(motion.withPath(path)));
        }

        @Override
        public double[] toView(CanvasView canvas) {
            return canvas.getGeometry().layer(layerId).isPresent() ? canvas.getGeometry().parentToView(layerId) : null;
        }
    }
}
