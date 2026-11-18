package com.dlsc.animatorfx.editor.canvas.tools;

import com.dlsc.animatorfx.editor.canvas.Box;
import com.dlsc.animatorfx.editor.canvas.LayerNames;
import com.dlsc.animatorfx.editor.canvas.PathOps;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;

import java.util.List;

/**
 * The layers that the creation tools make, with their default look. A new layer spans the whole
 * duration and has its anchor in the middle of its box, so rotating and scaling it feels natural;
 * the position is where that middle is. Text is the exception: its anchor is its top-left
 * corner, which stays put when the text changes.
 */
public final class NewLayers {

    /** The accent blue of the editor's dark theme: the fill of new shapes. */
    public static final Rgba SHAPE_COLOR = Rgba.parse("#4C9AFF");
    /** A neutral that reads on the dark and the light artboard: the fill of new text. */
    public static final Rgba TEXT_COLOR = Rgba.parse("#D7DEE8");
    public static final double LINE_WIDTH = 2;
    public static final double TEXT_SIZE = 24;
    public static final String TEXT_FONT = "System";
    public static final int POLYGON_SIDES = 3;
    public static final int STAR_POINTS = 5;
    public static final double STAR_INNER_RATIO = 0.4;

    private NewLayers() {
    }

    private static LayerBase base(Animation animation, String prefix, Vec2 anchor, Vec2 position) {
        Transform transform = Transform.IDENTITY.withAnchor(Animatable.of(anchor)).withPosition(Animatable.of(position));
        return LayerBase.create(LayerNames.next(animation, prefix), animation.duration()).withTransform(transform);
    }

    private static Animatable<Paint> fill() {
        return Animatable.of(new Paint.Solid(SHAPE_COLOR));
    }

    private static Vec2 half(Vec2 size) {
        return size.times(0.5);
    }

    public static ShapeLayer rectangle(Animation animation, Vec2 centre, Vec2 size) {
        return new ShapeLayer(base(animation, "Rectangle", half(size), centre),
                new Geometry.Rect(Animatable.of(size), Animatable.of(0.0)), fill(), null, null);
    }

    public static ShapeLayer ellipse(Animation animation, Vec2 centre, Vec2 size) {
        return new ShapeLayer(base(animation, "Ellipse", half(size), centre),
                new Geometry.Ellipse(Animatable.of(size)), fill(), null, null);
    }

    public static ShapeLayer polygon(Animation animation, Vec2 centre, double radius) {
        return new ShapeLayer(base(animation, "Polygon", new Vec2(radius, radius), centre),
                new Geometry.Polygon(POLYGON_SIDES, Animatable.of(radius)), fill(), null, null);
    }

    public static ShapeLayer star(Animation animation, Vec2 centre, double radius) {
        return new ShapeLayer(base(animation, "Star", new Vec2(radius, radius), centre),
                new Geometry.Star(STAR_POINTS, Animatable.of(radius), Animatable.of(radius * STAR_INNER_RATIO)),
                fill(), null, null);
    }

    /** A stroked line without a fill, from one point to the other in parent coordinates. */
    public static ShapeLayer line(Animation animation, Vec2 from, Vec2 to) {
        Vec2 delta = to.minus(from);
        Stroke stroke = new Stroke(Animatable.of(new Paint.Solid(SHAPE_COLOR)), Animatable.of(LINE_WIDTH),
                Stroke.Cap.ROUND, Stroke.Join.ROUND, 10, List.of(), Animatable.of(0.0));
        return new ShapeLayer(base(animation, "Line", half(delta), from.plus(half(delta))),
                new Geometry.Line(Animatable.of(Vec2.ZERO), Animatable.of(delta)),
                Animatable.of(new Paint.None()), stroke, null);
    }

    private static Stroke lineStroke() {
        return new Stroke(Animatable.of(new Paint.Solid(SHAPE_COLOR)), Animatable.of(LINE_WIDTH),
                Stroke.Cap.ROUND, Stroke.Join.ROUND, 10, List.of(), Animatable.of(0.0));
    }

    /**
     * A path layer from vertices in parent coordinates: closed with the fill of new shapes and no
     * stroke, open with the stroke of a new line and no fill.
     *
     * @param centred {@code true} for a finished path: its coordinates start at the top-left of
     *                its box and the anchor is in the middle of the box, as for the other shapes.
     *                {@code false} while the path is drawn: the layer sits on the first vertex,
     *                which so stays put while vertices are added. Both look the same.
     */
    public static ShapeLayer path(String id, String name, double duration, List<PathData.Vertex> vertices,
                                  boolean closed, boolean centred) {
        PathData inParent = new PathData(List.of(new PathData.Contour(vertices, closed)));
        Box box = PathOps.bounds(inParent);
        Vec2 origin = centred && box != null ? new Vec2(box.minX(), box.minY()) : vertices.get(0).point();
        Vec2 anchor = centred && box != null ? new Vec2(box.width() / 2, box.height() / 2) : Vec2.ZERO;
        Transform transform = Transform.IDENTITY.withAnchor(Animatable.of(anchor))
                .withPosition(Animatable.of(origin.plus(anchor)));
        LayerBase base = LayerBase.create(name, duration).withId(id).withTransform(transform);
        Geometry geometry = new Geometry.PathGeometry(Animatable.of(PathOps.translate(inParent, origin.times(-1))));
        return closed ? new ShapeLayer(base, geometry, fill(), null, null)
                : new ShapeLayer(base, geometry, Animatable.of(new Paint.None()), lineStroke(), null);
    }

    public static TextLayer text(Animation animation, Vec2 topLeft, String text) {
        return new TextLayer(base(animation, "Text", Vec2.ZERO, topLeft), text, TEXT_FONT, null,
                Animatable.of(TEXT_SIZE), TextLayer.Weight.NORMAL, false, TextLayer.Align.LEFT,
                Animatable.of(new Paint.Solid(TEXT_COLOR)), null);
    }

    public static ImageLayer image(Animation animation, String assetId, Vec2 centre, Vec2 size) {
        return new ImageLayer(base(animation, "Image", half(size), centre), assetId, size.x(), size.y());
    }

    /** A slot whose rest transform is the transform it is created with. */
    public static SlotLayer slot(Animation animation, String slotName, Vec2 centre, Vec2 size) {
        LayerBase base = base(animation, "Slot", half(size), centre);
        return new SlotLayer(base, slotName, size.x(), size.y(), base.transform());
    }
}
