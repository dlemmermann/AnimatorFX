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

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static com.dlsc.animatorfx.importer.LottieJson.get;
import static com.dlsc.animatorfx.importer.LottieJson.list;

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
    private record Style(boolean stroke, Object json) {

        boolean gradient() {
            String type = LottieJson.string(json, "ty", "");
            return type.equals("gf") || type.equals("gs");
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
    List<Layer> content(List<Object> items, double frameOffset) {
        return content(items, frameOffset, List.of(), null);
    }

    /**
     * @param inherited     the styles of the groups around that apply to this group, top-most first
     * @param inheritedTrim the trim item of a group around that applies to this group, or {@code null}
     */
    private List<Layer> content(List<Object> items, double frameOffset, List<Style> inherited,
                                Object inheritedTrim) {
        int count = items.size();
        Geo[] geometries = new Geo[count];
        Style[] styles = new Style[count];
        boolean[] groups = new boolean[count];
        List<Integer> trims = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Object item = items.get(i);
            if (LottieJson.flag(item, "hd")) {
                continue;
            }
            String type = LottieJson.string(item, "ty", "");
            switch (type) {
                case "gr" -> groups[i] = true;
                case "rc", "el", "sr", "sh" -> geometries[i] = geometry(item, type, frameOffset);
                case "fl", "gf" -> styles[i] = new Style(false, item);
                case "st", "gs" -> styles[i] = new Style(true, item);
                case "tm" -> trims.add(i);
                case "tr" -> {
                }
                case "rp" -> unsupported("Repeaters are");
                case "mm" -> unsupported("Merge paths are");
                case "rd" -> unsupported("Round corners are");
                case "pb" -> unsupported("Pucker and bloat is");
                case "tw" -> unsupported("Twist is");
                case "op" -> unsupported("Offset paths are");
                case "zz" -> unsupported("Zig zag is");
                default -> unsupported("Shape items of type \"" + type + "\" are");
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
                add(out, group(items.get(i), frameOffset, down, trimFor(i, trims, items, inheritedTrim)));
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
    private static Object trimFor(int index, List<Integer> trims, List<Object> items, Object inheritedTrim) {
        for (int trim : trims) {
            if (trim > index) {
                return items.get(trim);
            }
        }
        return inheritedTrim;
    }

    private Layer group(Object item, double frameOffset, List<Style> inherited, Object trim) {
        List<Object> items = list(item, "it");
        Object transformItem = null;
        for (Object child : items) {
            if ("tr".equals(get(child, "ty"))) {
                transformItem = child;
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
        String name = LottieJson.string(item, "nm", "Group");
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
    private Geo geometry(Object item, String type, double frameOffset) {
        Animatable<Vec2> position = properties.vec2(get(item, "p"), frameOffset, Vec2.ZERO, 1);
        switch (type) {
            case "rc", "el" -> {
                Animatable<Vec2> size = properties.vec2(get(item, "s"), frameOffset, Vec2.ZERO, 1);
                Animatable<Double> radius = properties.number(get(item, "r"), frameOffset, 0, 1);
                boolean rect = type.equals("rc");
                Geometry geometry = rect ? new Geometry.Rect(size, radius) : new Geometry.Ellipse(size);
                Transform transform = Transform.IDENTITY.withPosition(position)
                        .withAnchor(Keyframes.map(size, s -> s.times(0.5)));
                Vec2 shift = position.isAnimated() || size.isAnimated() ? null
                        : size.at(0).times(0.5).minus(position.at(0));
                return new Geo(LottieJson.string(item, "nm", rect ? "Rectangle" : "Ellipse"), geometry, transform,
                        shift, List.of(position, size, radius));
            }
            case "sr" -> {
                Animatable<Double> points = properties.number(get(item, "pt"), frameOffset, 5, 1);
                if (points.isAnimated()) {
                    warnings.add("Animated point counts of stars and polygons are not supported", "shape", "shapes");
                }
                boolean polygon = LottieJson.number(item, "sy", 1) == 2;
                int least = polygon ? 3 : 2;
                double wanted = Math.rint(points.at(0));
                if (wanted > Limits.MAX_POINTS) {
                    warnings.add("Stars and polygons have at most " + Limits.MAX_POINTS + " points", "shape",
                            "shapes");
                }
                int count = (int) Math.clamp(wanted, least, Limits.MAX_POINTS);
                Animatable<Double> outer = properties.number(get(item, "or"), frameOffset, 0, 1);
                Animatable<Double> inner = properties.number(get(item, "ir"), frameOffset, 0, 1);
                Animatable<Double> rotation = properties.number(get(item, "r"), frameOffset, 0, 1);
                if (rounded(get(item, "os"), frameOffset) || (!polygon && rounded(get(item, "is"), frameOffset))) {
                    warnings.add("Rounded stars and polygons are not supported; their corners are sharp", "shape",
                            "shapes");
                }
                Geometry geometry = polygon ? new Geometry.Polygon(count, outer)
                        : new Geometry.Star(count, outer, inner);
                Transform transform = Transform.IDENTITY.withPosition(position).withRotation(rotation)
                        .withAnchor(Keyframes.map(outer, r -> new Vec2(r, r)));
                return new Geo(LottieJson.string(item, "nm", polygon ? "Polygon" : "Star"), geometry, transform,
                        null, List.of(position, outer, inner, rotation));
            }
            default -> {
                Animatable<PathData> path = properties.path(get(item, "ks"), frameOffset);
                return new Geo(LottieJson.string(item, "nm", "Path"), new Geometry.PathGeometry(path),
                        Transform.IDENTITY, Vec2.ZERO, List.of(path));
            }
        }
    }

    private boolean rounded(Object property, double frameOffset) {
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

    private Layer layer(Geo geo, Style fill, Style stroke, Object trimItem, double frameOffset) {
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
                : paint(fill.json(), frameOffset, shift);
        Stroke line = stroke == null ? null : stroke(stroke.json(), frameOffset, shift);
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

    private Trim trim(Object item, double frameOffset) {
        if (LottieJson.number(item, "m", 1) == 2) {
            warnings.add("Trimming several paths one after the other is not supported; they are trimmed together",
                    "trim path", "trim paths");
        }
        return new Trim(properties.number(get(item, "s"), frameOffset, 0, 0.01),
                properties.number(get(item, "e"), frameOffset, 100, 0.01),
                properties.number(get(item, "o"), frameOffset, 0, 1.0 / 360));
    }

    private Stroke stroke(Object item, double frameOffset, Vec2 shift) {
        Stroke.Cap cap = switch ((int) LottieJson.number(item, "lc", 2)) {
            case 1 -> Stroke.Cap.BUTT;
            case 3 -> Stroke.Cap.SQUARE;
            default -> Stroke.Cap.ROUND;
        };
        Stroke.Join join = switch ((int) LottieJson.number(item, "lj", 2)) {
            case 1 -> Stroke.Join.MITER;
            case 3 -> Stroke.Join.BEVEL;
            default -> Stroke.Join.ROUND;
        };
        List<Double> dashes = new ArrayList<>();
        Animatable<Double> dashOffset = Animatable.of(0.0);
        for (Object dash : list(item, "d")) {
            Animatable<Double> value = properties.number(get(dash, "v"), frameOffset, 0, 1);
            if ("o".equals(get(dash, "n"))) {
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
        return new Stroke(paint(item, frameOffset, shift), properties.number(get(item, "w"), frameOffset, 1, 1), cap,
                join, Math.max(1, LottieJson.number(item, "ml", 4)), dashes, dashOffset);
    }

    /**
     * The paint of a fill or stroke item. Its opacity is multiplied into the alpha of the colours.
     *
     * @param shift added to gradient points to bring them from group to layer coordinates
     */
    private Animatable<Paint> paint(Object item, double frameOffset, Vec2 shift) {
        String type = LottieJson.string(item, "ty", "");
        if (LottieJson.number(item, "r", 1) == 2 && (type.equals("fl") || type.equals("gf"))) {
            warnings.add("The even-odd fill rule is not supported; the non-zero rule is used", "fill", "fills");
        }
        Animatable<Double> opacity = properties.number(get(item, "o"), frameOffset, 100, 0.01);
        if (type.equals("fl") || type.equals("st")) {
            Animatable<Rgba> color = properties.color(get(item, "c"), frameOffset, Rgba.BLACK);
            return Keyframes.combine(time -> new Paint.Solid(faded(color.at(time), opacity.at(time))),
                    this::sampled, color, opacity);
        }
        Animatable<Double> highlight = properties.number(get(item, "h"), frameOffset, 0, 1);
        if (highlight.isAnimated() || highlight.at(0) != 0) {
            warnings.add("Highlights of radial gradients are not supported; the centre is used", "gradient",
                    "gradients");
        }
        boolean radial = LottieJson.number(item, "t", 1) == 2;
        Animatable<Vec2> start = properties.vec2(get(item, "s"), frameOffset, Vec2.ZERO, 1);
        Animatable<Vec2> end = properties.vec2(get(item, "e"), frameOffset, Vec2.ZERO, 1);
        Object gradient = get(item, "g");
        int count = (int) LottieJson.number(gradient, "p", 0);
        Animatable<double[]> colors = properties.numbers(get(gradient, "k"), frameOffset, new double[0]);
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
