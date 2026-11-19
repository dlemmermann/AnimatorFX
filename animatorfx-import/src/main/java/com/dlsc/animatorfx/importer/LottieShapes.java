package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Geometries;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Trim;
import com.dlsc.animatorfx.model.Vec2;

import com.lottie4j.core.definition.FillRule;
import com.lottie4j.core.definition.GradientType;
import com.lottie4j.core.definition.LineCap;
import com.lottie4j.core.definition.LineJoin;
import com.lottie4j.core.definition.StarType;
import com.lottie4j.core.definition.StrokeDashType;
import com.lottie4j.core.definition.TrimMultipleShapes;
import com.lottie4j.core.model.animation.Animated;
import com.lottie4j.core.model.shape.BaseShape;
import com.lottie4j.core.model.shape.UnknownShape;
import com.lottie4j.core.model.shape.grouping.Group;
import com.lottie4j.core.model.shape.modifier.Merge;
import com.lottie4j.core.model.shape.modifier.OffsetPath;
import com.lottie4j.core.model.shape.modifier.Pucker;
import com.lottie4j.core.model.shape.modifier.Repeater;
import com.lottie4j.core.model.shape.modifier.RoundedCorners;
import com.lottie4j.core.model.shape.modifier.TrimPath;
import com.lottie4j.core.model.shape.modifier.Twist;
import com.lottie4j.core.model.shape.modifier.ZigZag;
import com.lottie4j.core.model.shape.shape.Ellipse;
import com.lottie4j.core.model.shape.shape.Path;
import com.lottie4j.core.model.shape.shape.Polystar;
import com.lottie4j.core.model.shape.shape.Rectangle;
import com.lottie4j.core.model.shape.style.Fill;
import com.lottie4j.core.model.shape.style.GradientFill;
import com.lottie4j.core.model.shape.style.GradientStroke;
import com.lottie4j.core.model.transform.StrokeDash;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Converts the content of a Lottie shape layer.
 * <p>
 * Lottie lists the items of a group top-most first, and a fill, stroke or trim applies to every
 * geometry listed before it. The model has one geometry per shape layer, so a group becomes a
 * group layer with one shape layer per geometry and style. A fill directly below its stroke
 * shares a shape layer with it, which is how the model paints a shape anyway.
 */
final class LottieShapes {

    /** A fill or stroke item. */
    private record Style(boolean stroke, BaseShape item) {

        boolean gradient() {
            return item instanceof GradientFill || item instanceof GradientStroke;
        }
    }

    /** What a gradient fill and a gradient stroke have in common. */
    private record Gradient(GradientType type, Animated start, Animated end, Animated highlight, Integer count,
                            Animated colors) {

        static Gradient of(BaseShape item) {
            if (item instanceof GradientFill fill) {
                return new Gradient(fill.gradientType(), fill.startingPoint(), fill.endPoint(),
                        fill.highlightLength(), fill.colors() == null ? null : fill.colors().numberOfColors(),
                        fill.colors() == null ? null : fill.colors().colors());
            }
            GradientStroke stroke = (GradientStroke) item;
            return new Gradient(stroke.gradientType(), stroke.startingPoint(), stroke.endPoint(), null,
                    stroke.colors() == null ? null : stroke.colors().numberOfColors(),
                    stroke.colors() == null ? null : stroke.colors().colors());
        }
    }

    /**
     * A geometry item as the model expresses it.
     *
     * @param transform     places the geometry, whose own origin is its top left corner, in the group
     * @param gradientShift for a geometry that does not move: what to add to a point of the
     *                      group to get the point of the layer; {@code null} otherwise
     * @param inputs        the properties the outline depends on
     */
    private record Geo(String name, Geometry geometry, Transform transform, Vec2 gradientShift,
                       List<Animatable<?>> inputs) {

        boolean isPath() {
            return geometry instanceof Geometry.PathGeometry;
        }
    }

    private final LottieProperties properties;
    private final LottieTransforms transforms;
    private final Warnings warnings;
    private final Limits limits;
    private final double duration;

    LottieShapes(LottieProperties properties, LottieTransforms transforms, Warnings warnings, Limits limits,
                 double duration) {
        this.properties = properties;
        this.transforms = transforms;
        this.warnings = warnings;
        this.limits = limits;
        this.duration = duration;
    }

    /** The layers for the {@code shapes} of a shape layer, bottom to top. */
    List<Layer> content(List<BaseShape> items, double frameOffset) {
        return content(items, frameOffset, List.of(), null);
    }

    /**
     * @param inherited     the styles of the groups around that apply to this group, top-most first
     * @param inheritedTrim the trim item of a group around that applies to this group, or {@code null}
     */
    private List<Layer> content(List<BaseShape> items, double frameOffset, List<Style> inherited,
                                TrimPath inheritedTrim) {
        int count = items.size();
        Geo[] geometries = new Geo[count];
        Style[] styles = new Style[count];
        boolean[] groups = new boolean[count];
        List<Integer> trims = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BaseShape item = items.get(i);
            if (item == null || item.isHidden()) {
                continue;
            }
            switch (item) {
                case Group group -> groups[i] = true;
                case Rectangle rectangle -> geometries[i] = geometry(rectangle, frameOffset);
                case Ellipse ellipse -> geometries[i] = geometry(ellipse, frameOffset);
                case Polystar star -> geometries[i] = geometry(star, frameOffset);
                case Path path -> geometries[i] = geometry(path, frameOffset);
                case Fill fill -> styles[i] = new Style(false, item);
                case GradientFill fill -> styles[i] = new Style(false, item);
                case com.lottie4j.core.model.shape.style.Stroke stroke -> styles[i] = new Style(true, item);
                case GradientStroke stroke -> styles[i] = new Style(true, item);
                case TrimPath trim -> trims.add(i);
                case com.lottie4j.core.model.shape.grouping.Transform transform -> {
                    // read with the group it belongs to
                }
                case Repeater repeater -> unsupported("Repeaters are");
                case Merge merge -> unsupported("Merge paths are");
                case RoundedCorners corners -> unsupported("Round corners are");
                case Pucker pucker -> unsupported("Pucker and bloat is");
                case Twist twist -> unsupported("Twist is");
                case OffsetPath offset -> unsupported("Offset paths are");
                case ZigZag zigZag -> unsupported("Zig zag is");
                case UnknownShape unknown -> unsupported("Shape items of type \"" + unknown.type() + "\" are");
                default -> unsupported("Shape items of type \"" + item.shapeType().label() + "\" are");
            }
        }
        if (trims.size() > 1) {
            warnings.add("Several trim paths in one group are not supported; the first one applies", "group",
                    "groups");
        }

        List<Layer> out = new ArrayList<>();
        // styles of the groups around lie below everything this group paints itself
        for (int s = inherited.size() - 1; s >= 0; s--) {
            Style style = inherited.get(s);
            for (int g = count - 1; g >= 0; g--) {
                if (geometries[g] != null) {
                    add(out, layer(geometries[g], style.stroke() ? null : style, style.stroke() ? style : null,
                            trimFor(g, trims, items, inheritedTrim), frameOffset));
                }
            }
        }
        boolean[] merged = new boolean[count];
        for (int i = count - 1; i >= 0; i--) {
            if (styles[i] != null && !merged[i]) {
                Style fill = styles[i].stroke() ? null : styles[i];
                Style stroke = styles[i].stroke() ? styles[i] : null;
                int above = i;
                if (fill != null && i > 0 && styles[i - 1] != null && styles[i - 1].stroke()) {
                    stroke = styles[i - 1];
                    merged[i - 1] = true;
                    above = i - 1;
                }
                for (int g = above - 1; g >= 0; g--) {
                    if (geometries[g] != null) {
                        add(out, layer(geometries[g], fill, stroke, trimFor(g, trims, items, inheritedTrim),
                                frameOffset));
                    }
                }
            } else if (groups[i]) {
                List<Style> down = new ArrayList<>();
                for (int s = i + 1; s < count; s++) {
                    if (styles[s] != null) {
                        down.add(styles[s]);
                    }
                }
                down.addAll(inherited);
                add(out, group((Group) items.get(i), frameOffset, down, trimFor(i, trims, items, inheritedTrim)));
            }
        }
        return out;
    }

    private static void add(List<Layer> out, Layer layer) {
        if (layer != null) {
            out.add(layer);
        }
    }

    private void unsupported(String what) {
        warnings.add(what + " not supported", "shape item ignored", "shape items ignored");
    }

    /** The first trim listed after the item, which is the one that applies to it. */
    private static TrimPath trimFor(int index, List<Integer> trims, List<BaseShape> items, TrimPath inheritedTrim) {
        for (int trim : trims) {
            if (trim > index) {
                return (TrimPath) items.get(trim);
            }
        }
        return inheritedTrim;
    }

    private Layer group(Group item, double frameOffset, List<Style> inherited, TrimPath trim) {
        List<BaseShape> items = item.shapes() == null ? List.of() : item.shapes();
        com.lottie4j.core.model.shape.grouping.Transform transformItem = null;
        for (BaseShape child : items) {
            if (child instanceof com.lottie4j.core.model.shape.grouping.Transform transform) {
                transformItem = transform;
            }
        }
        if (!limits.layer() || !limits.enter()) {
            return null;
        }
        List<Layer> children;
        try {
            children = content(items, frameOffset, inherited, trim);
        } finally {
            limits.leave();
        }
        if (children.isEmpty()) {
            return null;
        }
        String name = item.name() != null ? item.name() : "Group";
        LottieTransforms.Placement placement = transforms.read(transformItem, frameOffset);
        boolean plain = placement.transform().equals(Transform.IDENTITY)
                && placement.opacity().equals(Animatable.of(1.0));
        if (plain && children.size() == 1) {
            // a group that only wraps one shape adds nothing
            Layer only = children.get(0);
            return only.withBase(only.base().withName(name));
        }
        LayerBase base = LayerBase.create(name, duration).withTransform(placement.transform())
                .withOpacity(placement.opacity());
        return new GroupLayer(base, children);
    }

    /**
     * Lottie places rectangles, ellipses and stars by their centre; in the model they start at
     * their top left corner. The layer's anchor is put at the centre (half the size, following
     * the size when it is animated) and its position at the Lottie position.
     */
    private Geo geometry(Rectangle item, double frameOffset) {
        Animatable<Double> radius = properties.number(item.roundedCornerRadius(), frameOffset, 0, 1);
        Animatable<Vec2> size = properties.vec2(item.size(), frameOffset, Vec2.ZERO, 1);
        return centered(named(item.name(), "Rectangle"), new Geometry.Rect(size, radius), item.position(), size,
                radius, frameOffset);
    }

    private Geo geometry(Ellipse item, double frameOffset) {
        Animatable<Vec2> size = properties.vec2(item.size(), frameOffset, Vec2.ZERO, 1);
        return centered(named(item.name(), "Ellipse"), new Geometry.Ellipse(size), item.position(), size,
                Animatable.of(0.0), frameOffset);
    }

    private Geo centered(String name, Geometry geometry, Animated p, Animatable<Vec2> size,
                         Animatable<Double> radius, double frameOffset) {
        Animatable<Vec2> position = properties.vec2(p, frameOffset, Vec2.ZERO, 1);
        Transform transform = Transform.IDENTITY.withPosition(position)
                .withAnchor(Keyframes.map(size, s -> s.times(0.5)));
        Vec2 shift = position.isAnimated() || size.isAnimated() ? null
                : size.at(0).times(0.5).minus(position.at(0));
        return new Geo(name, geometry, transform, shift, List.of(position, size, radius));
    }

    private Geo geometry(Polystar item, double frameOffset) {
        Animatable<Vec2> position = properties.vec2(item.position(), frameOffset, Vec2.ZERO, 1);
        Animatable<Double> points = properties.number(item.points(), frameOffset, 5, 1);
        if (points.isAnimated()) {
            warnings.add("Animated point counts of stars and polygons are not supported", "shape", "shapes");
        }
        boolean polygon = item.starType() == StarType.POLYGON;
        int least = polygon ? 3 : 2;
        double wanted = Math.rint(points.at(0));
        if (wanted > Limits.MAX_POINTS) {
            warnings.add("Stars and polygons have at most " + Limits.MAX_POINTS + " points", "shape",
                    "shapes");
        }
        int count = (int) Math.clamp(wanted, least, Limits.MAX_POINTS);
        Animatable<Double> outer = properties.number(item.outerRadius(), frameOffset, 0, 1);
        Animatable<Double> inner = properties.number(item.innerRadius(), frameOffset, 0, 1);
        Animatable<Double> rotation = properties.number(item.rotation(), frameOffset, 0, 1);
        if (rounded(item.outerRoundness(), frameOffset) || (!polygon && rounded(item.innerRoundness(), frameOffset))) {
            warnings.add("Rounded stars and polygons are not supported; their corners are sharp", "shape",
                    "shapes");
        }
        Geometry geometry = polygon ? new Geometry.Polygon(count, outer)
                : new Geometry.Star(count, outer, inner);
        Transform transform = Transform.IDENTITY.withPosition(position).withRotation(rotation)
                .withAnchor(Keyframes.map(outer, r -> new Vec2(r, r)));
        return new Geo(named(item.name(), polygon ? "Polygon" : "Star"), geometry, transform, null,
                List.of(position, outer, inner, rotation));
    }

    private Geo geometry(Path item, double frameOffset) {
        Animatable<PathData> path = properties.path(item.bezier(), frameOffset);
        return new Geo(named(item.name(), "Path"), new Geometry.PathGeometry(path), Transform.IDENTITY, Vec2.ZERO,
                List.of(path));
    }

    private static String named(String name, String fallback) {
        return name != null ? name : fallback;
    }

    private boolean rounded(Animated property, double frameOffset) {
        Animatable<Double> roundness = properties.number(property, frameOffset, 0, 1);
        return roundness.isAnimated() || roundness.at(0) != 0;
    }

    /** The outline in the coordinates of the group, for paints that need those. */
    private Geo asPath(Geo geo) {
        Animatable<PathData> path = Keyframes.combine(
                time -> transformed(Geometries.toPath(geo.geometry(), time), geo.transform().matrixAt(time)),
                this::sampled, geo.inputs().toArray(Animatable<?>[]::new));
        return new Geo(geo.name(), new Geometry.PathGeometry(path), Transform.IDENTITY, Vec2.ZERO, List.of(path));
    }

    private static PathData transformed(PathData path, double[] matrix) {
        List<PathData.Contour> contours = new ArrayList<>();
        for (PathData.Contour contour : path.contours()) {
            List<PathData.Vertex> vertices = new ArrayList<>();
            for (PathData.Vertex vertex : contour.vertices()) {
                vertices.add(new PathData.Vertex(Matrix.apply(matrix, vertex.point()),
                        Matrix.applyLinear(matrix, vertex.in().x(), vertex.in().y()),
                        Matrix.applyLinear(matrix, vertex.out().x(), vertex.out().y())));
            }
            contours.add(new PathData.Contour(vertices, contour.closed()));
        }
        return new PathData(contours);
    }

    private void sampled() {
        warnings.add("Properties animated with different timing are combined by sampling at their key times",
                "value", "values");
    }

    private Layer layer(Geo geo, Style fill, Style stroke, TrimPath trimItem, double frameOffset) {
        if (!limits.layer()) {
            return null;
        }
        Geo placed = geo;
        boolean gradient = (fill != null && fill.gradient()) || (stroke != null && stroke.gradient());
        if (gradient && geo.gradientShift() == null) {
            // gradient points are in group coordinates, which a moving geometry cannot follow
            placed = asPath(geo);
        }
        Vec2 shift = gradient ? placed.gradientShift() : Vec2.ZERO;
        Animatable<Paint> fillPaint = fill == null ? Animatable.of(new Paint.None())
                : paint(fill.item(), frameOffset, shift);
        Stroke line = stroke == null ? null : stroke(stroke.item(), frameOffset, shift);
        Trim trim = null;
        if (trimItem != null) {
            if (fill != null) {
                warnings.add("Trim paths apply to strokes only; trimmed fills are drawn whole", "shape", "shapes");
            }
            if (line != null) {
                trim = trim(trimItem, frameOffset);
            }
        }
        LayerBase base = LayerBase.create(placed.name(), duration).withTransform(placed.transform());
        return new ShapeLayer(base, placed.geometry(), fillPaint, line, trim);
    }

    private Trim trim(TrimPath item, double frameOffset) {
        if (item.trimMultipleShapes() == TrimMultipleShapes.SIMULTANEOUSLY) {
            warnings.add("Trimming several paths one after the other is not supported; they are trimmed together",
                    "trim path", "trim paths");
        }
        return new Trim(properties.number(item.segmentStart(), frameOffset, 0, 0.01),
                properties.number(item.segmentEnd(), frameOffset, 100, 0.01),
                properties.number(item.offset(), frameOffset, 0, 1.0 / 360));
    }

    private Stroke stroke(BaseShape item, double frameOffset, Vec2 shift) {
        LineCap lineCap;
        LineJoin lineJoin;
        Integer miterLimit;
        Animated width;
        List<StrokeDash> strokeDashes;
        if (item instanceof com.lottie4j.core.model.shape.style.Stroke solid) {
            lineCap = solid.lineCap();
            lineJoin = solid.lineJoin();
            miterLimit = solid.miterLimit();
            width = solid.strokeWidth();
            strokeDashes = solid.strokeDashes();
        } else {
            GradientStroke gradient = (GradientStroke) item;
            lineCap = gradient.lineCap();
            lineJoin = gradient.lineJoin();
            miterLimit = gradient.miterLimit();
            width = gradient.strokeWidth();
            strokeDashes = gradient.strokeDashes();
        }
        Stroke.Cap cap = lineCap == LineCap.BUTT ? Stroke.Cap.BUTT
                : lineCap == LineCap.SQUARE ? Stroke.Cap.SQUARE : Stroke.Cap.ROUND;
        Stroke.Join join = lineJoin == LineJoin.MITER ? Stroke.Join.MITER
                : lineJoin == LineJoin.BEVEL ? Stroke.Join.BEVEL : Stroke.Join.ROUND;
        List<Double> dashes = new ArrayList<>();
        Animatable<Double> dashOffset = Animatable.of(0.0);
        for (StrokeDash dash : strokeDashes == null ? List.<StrokeDash>of() : strokeDashes) {
            Animatable<Double> value = properties.number(dash.length(), frameOffset, 0, 1);
            if (dash.type() == StrokeDashType.OFFSET) {
                dashOffset = value;
            } else {
                if (value.isAnimated()) {
                    warnings.add("Animated dash lengths are not supported; the first value is used", "stroke",
                            "strokes");
                }
                dashes.add(Math.max(0, value.at(0)));
            }
        }
        if (dashes.stream().mapToDouble(Double::doubleValue).sum() == 0) {
            dashes.clear();
        } else if (dashes.size() % 2 == 1) {
            dashes.addAll(List.copyOf(dashes));
        }
        return new Stroke(paint(item, frameOffset, shift), properties.number(width, frameOffset, 1, 1), cap,
                join, Math.max(1, miterLimit == null ? 4 : miterLimit), dashes, dashOffset);
    }

    /**
     * The paint of a fill or stroke item. Its opacity is multiplied into the alpha of the colours.
     *
     * @param shift added to gradient points to bring them from group to layer coordinates
     */
    private Animatable<Paint> paint(BaseShape item, double frameOffset, Vec2 shift) {
        FillRule fillRule = item instanceof Fill fill ? fill.fillRule()
                : item instanceof GradientFill fill ? fill.fillRule() : null;
        if (fillRule == FillRule.EVEN_ODD) {
            warnings.add("The even-odd fill rule is not supported; the non-zero rule is used", "fill", "fills");
        }
        Animated itemOpacity = switch (item) {
            case Fill fill -> fill.opacity();
            case com.lottie4j.core.model.shape.style.Stroke stroke -> stroke.opacity();
            case GradientFill fill -> fill.opacity();
            case GradientStroke stroke -> stroke.opacity();
            default -> null;
        };
        Animatable<Double> opacity = properties.number(itemOpacity, frameOffset, 100, 0.01);
        if (item instanceof Fill || item instanceof com.lottie4j.core.model.shape.style.Stroke) {
            Animated itemColor = item instanceof Fill fill ? fill.color()
                    : ((com.lottie4j.core.model.shape.style.Stroke) item).color();
            Animatable<Rgba> color = properties.color(itemColor, frameOffset, Rgba.BLACK);
            return Keyframes.combine(time -> new Paint.Solid(faded(color.at(time), opacity.at(time))),
                    this::sampled, color, opacity);
        }
        Gradient gradient = Gradient.of(item);
        Animatable<Double> highlight = properties.number(gradient.highlight(), frameOffset, 0, 1);
        if (highlight.isAnimated() || highlight.at(0) != 0) {
            warnings.add("Highlights of radial gradients are not supported; the centre is used", "gradient",
                    "gradients");
        }
        boolean radial = gradient.type() == GradientType.RADIAL;
        Animatable<Vec2> start = properties.vec2(gradient.start(), frameOffset, Vec2.ZERO, 1);
        Animatable<Vec2> end = properties.vec2(gradient.end(), frameOffset, Vec2.ZERO, 1);
        int count = gradient.count() == null ? 0 : gradient.count();
        Animatable<double[]> colors = properties.numbers(gradient.colors(), frameOffset, new double[0]);
        return Keyframes.combine(time -> {
            List<Paint.Stop> stops = stops(colors.at(time), count, opacity.at(time));
            if (stops.isEmpty()) {
                return new Paint.None();
            }
            Vec2 from = start.at(time).plus(shift);
            Vec2 to = end.at(time).plus(shift);
            return radial ? new Paint.RadialGradient(from, Math.hypot(to.x() - from.x(), to.y() - from.y()), stops)
                    : new Paint.LinearGradient(from, to, stops);
        }, this::sampled, start, end, opacity, colors);
    }

    private static Rgba faded(Rgba color, double opacity) {
        return new Rgba(color.r(), color.g(), color.b(), color.a() * Math.clamp(opacity, 0.0, 1.0));
    }

    /**
     * Lottie stores {@code count} colour stops as {@code offset, r, g, b}, optionally followed
     * by alpha stops as {@code offset, alpha} at offsets of their own. The model has one list of
     * stops: one at every offset of either kind, with the other kind interpolated there.
     */
    static List<Paint.Stop> stops(double[] values, int count, double opacity) {
        int colorCount = Math.min(count, values.length / 4);
        if (colorCount <= 0) {
            return List.of();
        }
        int alphaCount = (values.length - 4 * colorCount) / 2;
        TreeSet<Double> offsets = new TreeSet<>();
        for (int i = 0; i < colorCount; i++) {
            offsets.add(values[4 * i]);
        }
        for (int i = 0; i < alphaCount; i++) {
            offsets.add(values[4 * colorCount + 2 * i]);
        }
        List<Paint.Stop> stops = new ArrayList<>();
        for (double offset : offsets) {
            double alpha = alphaCount == 0 ? 1 : at(values, 4 * colorCount, alphaCount, 2, 1, offset);
            stops.add(new Paint.Stop(Math.clamp(offset, 0.0, 1.0), faded(new Rgba(
                    unit(at(values, 0, colorCount, 4, 1, offset)), unit(at(values, 0, colorCount, 4, 2, offset)),
                    unit(at(values, 0, colorCount, 4, 3, offset)), unit(alpha)), opacity)));
        }
        return stops;
    }

    private static double unit(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }

    /**
     * Interpolates one component of the stops stored from {@code base} on, {@code stride}
     * values per stop with the offset first.
     */
    private static double at(double[] values, int base, int count, int stride, int component, double offset) {
        if (offset <= values[base]) {
            return values[base + component];
        }
        for (int i = 1; i < count; i++) {
            double left = values[base + stride * (i - 1)];
            double right = values[base + stride * i];
            if (offset <= right) {
                double from = values[base + stride * (i - 1) + component];
                double to = values[base + stride * i + component];
                return right == left ? to : from + (to - from) * (offset - left) / (right - left);
            }
        }
        return values[base + stride * (count - 1) + component];
    }
}
