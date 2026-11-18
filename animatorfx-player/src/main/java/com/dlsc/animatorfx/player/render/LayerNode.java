package com.dlsc.animatorfx.player.render;

import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.player.LayerState;
import com.dlsc.animatorfx.player.internal.EffectChain;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;

import java.util.Arrays;
import java.util.Objects;

/**
 * One layer in the node tree: the node, and the state last written to it so that the next frame
 * only writes what changed.
 */
abstract class LayerNode {

    final String id;
    final Node node;

    /** A geometry-only copy for a {@code CLIP} mask: opaque, unstroked, without effects. */
    final boolean geometryOnly;

    /** The node is the clip of another layer's node, which places and shows it. */
    final boolean clip;

    private final Affine affine = new Affine();
    private final EffectChain effects = new EffectChain();
    private Layer layer;
    private LayerState last;

    LayerNode(String id, Node node, boolean geometryOnly, boolean clip) {
        this.id = id;
        this.node = node;
        this.geometryOnly = geometryOnly;
        this.clip = clip;
        node.getTransforms().add(affine);
    }

    static LayerNode create(Layer layer, boolean geometryOnly, boolean clip) {
        return switch (layer) {
            case ShapeLayer shape -> new ShapeNode(shape.id(), geometryOnly, clip);
            case TextLayer text -> new TextNode(text.id(), geometryOnly, clip);
            case ImageLayer image -> new ImageNode(image.id(), geometryOnly, clip);
            default -> new ContainerNode(layer.id(), geometryOnly, clip);
        };
    }

    /**
     * Takes over the layer's static values. The next frame is written in full.
     */
    final void setLayer(Layer layer, Assets assets, boolean force) {
        if (force || layer != this.layer) {
            this.layer = layer;
            last = null;
            writeStatics(layer, assets);
        }
    }

    /** The matrix of the last frame, or {@code null} before the first. */
    final double[] matrix() {
        return last == null ? null : last.matrix();
    }

    final void placeAt(double mxx, double mxy, double tx, double myx, double myy, double ty) {
        affine.setToTransform(mxx, mxy, tx, myx, myy, ty);
    }

    final void apply(LayerState state) {
        LayerState old = last;
        if (state == old) {
            return;
        }
        last = state;
        if (!clip) {
            if (old == null || old.visible() != state.visible()) {
                node.setVisible(state.visible());
            }
            if (old == null || !Arrays.equals(old.matrix(), state.matrix())) {
                double[] m = state.matrix();
                placeAt(m[0], m[1], m[2], m[3], m[4], m[5]);
            }
        }
        if (!geometryOnly) {
            if (old == null || old.opacity() != state.opacity()) {
                node.setOpacity(state.opacity());
            }
            effects.apply(node, state.effects());
        }
        writeValues(old, state);
    }

    abstract void writeStatics(Layer layer, Assets assets);

    /**
     * @param old the state written before, or {@code null} if everything has to be written
     */
    abstract void writeValues(LayerState old, LayerState state);

    static boolean changed(Object before, Object now) {
        return before != now && !Objects.equals(before, now);
    }

    static void writeStrokeStatics(Shape shape, Stroke stroke) {
        if (stroke == null) {
            return;
        }
        shape.setStrokeLineCap(StrokeLineCap.valueOf(stroke.cap().name()));
        shape.setStrokeLineJoin(StrokeLineJoin.valueOf(stroke.join().name()));
        shape.setStrokeMiterLimit(stroke.miterLimit());
        if (!shape.getStrokeDashArray().equals(stroke.dashArray())) {
            shape.getStrokeDashArray().setAll(stroke.dashArray());
        }
    }

    static void writeStroke(Shape shape, LayerState old, LayerState state) {
        if (old == null || changed(old.strokePaint(), state.strokePaint())) {
            shape.setStroke(PaintConverter.toFx(state.strokePaint()));
        }
        if (old == null || old.strokeWidth() != state.strokeWidth()) {
            shape.setStrokeWidth(state.strokeWidth());
        }
        if (old == null || old.dashOffset() != state.dashOffset()) {
            shape.setStrokeDashOffset(state.dashOffset());
        }
    }

    /** Groups and slots: the node is a plain container. */
    private static final class ContainerNode extends LayerNode {

        ContainerNode(String id, boolean geometryOnly, boolean clip) {
            super(id, new Group(), geometryOnly, clip);
        }

        @Override
        void writeStatics(Layer layer, Assets assets) {
        }

        @Override
        void writeValues(LayerState old, LayerState state) {
        }
    }

    /**
     * A group holding the outline as a path that is filled and, as long as nothing is trimmed
     * away, also stroked. A layer with a trim has a second path for the trimmed stroke.
     */
    private static final class ShapeNode extends LayerNode {

        private final Group group;
        private final Path outline = new Path();
        private Path trimmed;
        private boolean split;

        ShapeNode(String id, boolean geometryOnly, boolean clip) {
            super(id, new Group(), geometryOnly, clip);
            group = (Group) node;
            outline.setStroke(null);
            group.getChildren().add(outline);
        }

        @Override
        void writeStatics(Layer layer, Assets assets) {
            ShapeLayer shape = (ShapeLayer) layer;
            writeStrokeStatics(outline, shape.stroke());
            if (shape.trim() != null && shape.stroke() != null && !geometryOnly) {
                if (trimmed == null) {
                    trimmed = new Path();
                    trimmed.setStroke(null);
                    trimmed.setVisible(false);
                    group.getChildren().add(trimmed);
                }
                writeStrokeStatics(trimmed, shape.stroke());
            } else if (trimmed != null) {
                group.getChildren().remove(trimmed);
                trimmed = null;
            }
            split = false;
        }

        @Override
        void writeValues(LayerState old, LayerState state) {
            PathData fillPath = state.fillPath();
            if (fillPath == null) {
                return;
            }
            if (old == null || changed(old.fillPath(), fillPath)) {
                PathConverter.write(fillPath, outline.getElements());
            }
            if (geometryOnly) {
                if (old == null) {
                    outline.setFill(Color.BLACK);
                }
                return;
            }
            if (old == null || changed(old.fill(), state.fill())) {
                outline.setFill(PaintConverter.toFx(state.fill()));
            }

            boolean splitNow = trimmed != null && state.strokePath() != fillPath;
            boolean switched = old == null || splitNow != split;
            split = splitNow;
            if (switched && trimmed != null) {
                trimmed.setVisible(splitNow);
            }
            if (splitNow) {
                if (switched) {
                    outline.setStroke(null);
                }
                if (switched || changed(old.strokePath(), state.strokePath())) {
                    PathConverter.write(state.strokePath(), trimmed.getElements());
                }
                writeStroke(trimmed, switched ? null : old, state);
            } else {
                writeStroke(outline, switched ? null : old, state);
            }
        }
    }

    private static final class TextNode extends LayerNode {

        private final Text text;
        private String family;
        private FontWeight weight;
        private FontPosture posture;
        private TextLayer.Align alignment = TextLayer.Align.LEFT;

        TextNode(String id, boolean geometryOnly, boolean clip) {
            super(id, new Text(), geometryOnly, clip);
            text = (Text) node;
            text.setTextOrigin(VPos.TOP);
        }

        @Override
        void writeStatics(Layer layer, Assets assets) {
            TextLayer source = (TextLayer) layer;
            text.setText(source.text());
            alignment = source.alignment();
            // lines against each other; where the text is against the origin is set with the font
            text.setTextAlignment(TextAlignment.valueOf(alignment.name()));
            String assetFamily = source.fontAssetId() == null ? null : assets.fontFamily(source.fontAssetId());
            family = assetFamily != null ? assetFamily : source.fontFamily();
            weight = source.fontWeight() == TextLayer.Weight.BOLD ? FontWeight.BOLD : FontWeight.NORMAL;
            posture = source.italic() ? FontPosture.ITALIC : FontPosture.REGULAR;
            writeStrokeStatics(text, source.stroke());
        }

        @Override
        void writeValues(LayerState old, LayerState state) {
            if (old == null || old.fontSize() != state.fontSize()) {
                text.setFont(Font.font(family, weight, posture, state.fontSize()));
                align();
            }
            if (geometryOnly) {
                if (old == null) {
                    text.setFill(Color.BLACK);
                    text.setStroke(null);
                }
                return;
            }
            Paint fill = state.fill();
            if (old == null || changed(old.fill(), fill)) {
                text.setFill(PaintConverter.toFx(fill));
            }
            writeStroke(text, old, state);
        }

        /**
         * Places the text against the layer origin: it starts there, is centred on it, or ends
         * there. JavaFX aligns only the lines of a text against each other, which shows nothing
         * for a single line.
         */
        private void align() {
            double width = text.getLayoutBounds().getWidth();
            text.setX(switch (alignment) {
                case LEFT -> 0;
                case CENTER -> -width / 2;
                case RIGHT -> -width;
            });
        }
    }

    private static final class ImageNode extends LayerNode {

        private final ImageView view;

        ImageNode(String id, boolean geometryOnly, boolean clip) {
            super(id, new ImageView(), geometryOnly, clip);
            view = (ImageView) node;
        }

        @Override
        void writeStatics(Layer layer, Assets assets) {
            ImageLayer source = (ImageLayer) layer;
            view.setImage(assets.image(source.assetId()));
            view.setFitWidth(source.width());
            view.setFitHeight(source.height());
        }

        @Override
        void writeValues(LayerState old, LayerState state) {
        }
    }
}
