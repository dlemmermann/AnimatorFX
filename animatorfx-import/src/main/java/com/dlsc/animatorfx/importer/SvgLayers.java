package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Vec2;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts the rendered elements of an SVG document to layers. One instance converts one
 * document.
 */
final class SvgLayers {

    private static final String INKSCAPE_NAMESPACE = "http://www.inkscape.org/namespaces/inkscape";

    /** Elements that render nothing by themselves, or whose lack of support is reported for the document as a whole. */
    private static final Set<String> NOT_RENDERED = Set.of("defs", "title", "desc", "metadata", "symbol", "clipPath",
            "linearGradient", "radialGradient", "style", "filter", "pattern", "mask", "marker", "animate",
            "animateTransform", "animateMotion", "animateColor", "set", "foreignObject", "script", "view");

    /** The share of the font size that lies above the baseline, for want of font metrics. */
    private static final double ASCENT = 0.8;
    /** The average advance of a character as a share of the font size. */
    private static final double ADVANCE = 0.55;

    private final Map<String, Element> ids;
    private final Warnings warnings;
    private final double viewportWidth;
    private final double viewportHeight;
    private final double viewportDiagonal;
    private final SvgGradients gradients;
    private final Map<String, Integer> counters = new HashMap<>();
    private final List<Asset> assets = new ArrayList<>();
    private final Map<String, byte[]> assetBytes = new LinkedHashMap<>();
    private final Map<String, String> assetIdsByHref = new HashMap<>();
    private final Set<Element> expanding = new HashSet<>();
    private final Limits limits;
    private int clipDepth;
    /** Set by {@link #clip} when the clip path could not be built in full. */
    private boolean clipIncomplete;

    SvgLayers(Map<String, Element> ids, Warnings warnings, double viewportWidth, double viewportHeight) {
        this.ids = ids;
        this.warnings = warnings;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.viewportDiagonal = Math.hypot(viewportWidth, viewportHeight) / Math.sqrt(2);
        this.gradients = new SvgGradients(ids, warnings, viewportWidth, viewportHeight);
        this.limits = new Limits(warnings);
    }

    List<Asset> assets() {
        return assets;
    }

    Map<String, byte[]> assetBytes() {
        return assetBytes;
    }

    static double opacity(SvgStyle style, Warnings warnings) {
        return Math.clamp(SvgLength.fraction(style.get("opacity"), 1, warnings), 0.0, 1.0);
    }

    private double unit(String text) {
        return Math.clamp(SvgLength.fraction(text, 1, warnings), 0.0, 1.0);
    }

    /**
     * The layers of the child elements, bottom to top: SVG paints later siblings over earlier
     * ones, which is the order of the model.
     */
    List<Layer> children(Element parent, SvgStyle parentStyle) {
        List<Layer> out = new ArrayList<>();
        for (Element child : elements(parent)) {
            convert(child, parentStyle, Matrix.identity(), out);
        }
        return out;
    }

    private static List<Element> elements(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    /**
     * Adds the layer of an element, preceded by its mask layer if it is clipped.
     *
     * @param pre applied after the element's own transform
     */
    private void convert(Element element, SvgStyle parentStyle, double[] pre, List<Layer> out) {
        if (!SvgImporter.isSvg(element)) {
            return;
        }
        String kind = element.getLocalName();
        if (NOT_RENDERED.contains(kind)) {
            return;
        }
        SvgStyle style = SvgStyle.of(element, parentStyle);
        boolean displayed = !"none".equals(style.get("display"));
        if (clipDepth > 0 && !displayed) {
            return;
        }
        double[] user = Matrix.multiply(pre, transform(element.getAttribute("transform")));
        if (!Limits.usable(user)) {
            warnings.add("Elements whose transform is not finite or too large were dropped", "element", "elements");
            return;
        }
        if (!limits.layer()) {
            return;
        }
        // the clip first: an element whose clip cannot be built is left out, not shown unclipped
        Layer mask = clip(style, user);
        if (clipIncomplete) {
            return;
        }
        boolean leaf = true;
        Layer layer;
        switch (kind) {
            case "g", "a", "svg", "switch" -> {
                leaf = false;
                layer = group(element, kind, style, user);
            }
            case "use" -> {
                leaf = false;
                layer = use(element, style, user);
            }
            case "rect", "circle", "ellipse", "line", "polyline", "polygon", "path" ->
                    layer = shape(element, kind, style, user);
            case "text" -> layer = text(element, style, user);
            case "image" -> layer = image(element, style, user);
            default -> {
                SvgImporter.ignored(warnings, "Element <" + kind + "> is not supported");
                layer = null;
            }
        }
        if (layer == null) {
            return;
        }
        String visibility = style.get("visibility", "visible");
        boolean visible = displayed && !(leaf && (visibility.equals("hidden") || visibility.equals("collapse")));
        LayerBase base = layer.base().withOpacity(Animatable.of(opacity(style, warnings))).withVisible(visible);
        if (mask != null) {
            out.add(mask);
            base = base.withMask(new Mask(mask.id(), Mask.Mode.CLIP));
        }
        out.add(layer.withBase(base));
    }

    private double[] transform(String text) {
        double[] matrix = SvgTransforms.parse(text);
        if (matrix == null) {
            warnings.add("Transforms that cannot be parsed are ignored");
            return Matrix.identity();
        }
        return matrix;
    }

    private String name(Element element) {
        String name = element.getAttribute("id");
        if (name.isEmpty()) {
            name = element.getAttributeNS(INKSCAPE_NAMESPACE, "label");
        }
        if (name.isEmpty()) {
            name = element.getAttribute("aria-label");
        }
        if (name.isEmpty()) {
            name = element.getLocalName() + " " + counters.merge(element.getLocalName(), 1, Integer::sum);
        }
        return name;
    }

    private static LayerBase base(String name, double[] matrix) {
        return LayerBase.create(name, SvgImporter.DURATION).withTransform(Matrix.decompose(matrix));
    }

    private double x(Element element, String attribute) {
        return SvgLength.parse(element.getAttribute(attribute), viewportWidth, 0, warnings);
    }

    private double y(Element element, String attribute) {
        return SvgLength.parse(element.getAttribute(attribute), viewportHeight, 0, warnings);
    }

    private Layer group(Element element, String kind, SvgStyle style, double[] user) {
        String name = name(element);
        double[] matrix = user;
        if (kind.equals("svg")) {
            matrix = Matrix.multiply(user, Matrix.translate(x(element, "x"), y(element, "y")));
            if (element.hasAttribute("viewBox")) {
                warnings.add("The view box of a nested <svg> element is not supported; its content is not scaled");
            }
        }
        List<Layer> content = new ArrayList<>();
        if (limits.enter()) {
            try {
                for (Element child : elements(element)) {
                    convert(child, style, Matrix.identity(), content);
                    if (kind.equals("switch") && SvgImporter.isSvg(child)) {
                        break;
                    }
                }
            } finally {
                limits.leave();
            }
        }
        return new GroupLayer(base(name, matrix), content);
    }

    /** A group at the position of the {@code use} element holding a fresh copy of what it references. */
    private Layer use(Element element, SvgStyle style, double[] user) {
        String href = SvgImporter.href(element);
        if (!href.startsWith("#")) {
            warnings.add("References to other files are not supported", "<use> element ignored",
                    "<use> elements ignored");
            return null;
        }
        Element target = ids.get(href.substring(1));
        if (target == null) {
            warnings.add("References that cannot be resolved are ignored", "<use> element", "<use> elements");
            return null;
        }
        if (!expanding.add(target)) {
            warnings.add("Circular references are ignored", "<use> element", "<use> elements");
            return null;
        }
        try {
            String name = name(element);
            double[] matrix = Matrix.multiply(user, Matrix.translate(x(element, "x"), y(element, "y")));
            List<Layer> content = new ArrayList<>();
            // a chain of references is as deep as the layers it makes: the depth limit ends it
            if (limits.enter()) {
                try {
                    if ("symbol".equals(target.getLocalName()) && SvgImporter.isSvg(target)) {
                        if (limits.layer()) {
                            content.add(group(target, "symbol", SvgStyle.of(target, style), Matrix.identity()));
                        }
                    } else {
                        convert(target, style, Matrix.identity(), content);
                    }
                } finally {
                    limits.leave();
                }
            }
            return new GroupLayer(base(name, matrix), content);
        } finally {
            expanding.remove(target);
        }
    }

    /**
     * The mask layer for an element with a {@code clip-path}: the shapes of the clip path, placed
     * in the user space of the clipped element. Every use gets its own copy.
     *
     * @param user the user space of the clipped element, its own transform included
     * @return the layer, or {@code null} if the element is not clipped
     */
    private Layer clip(SvgStyle style, double[] user) {
        clipIncomplete = false;
        String value = style.get("clip-path", "none").trim();
        if (!value.startsWith("url(") || !value.endsWith(")")) {
            return null;
        }
        Element clipPath = ids.get(SvgGradients.referenceId(value.substring(4, value.length() - 1)));
        if (clipPath == null || !"clipPath".equals(clipPath.getLocalName())) {
            warnings.add("Clip paths that cannot be resolved are ignored");
            return null;
        }
        if (clipDepth > 0) {
            warnings.add("Clip paths on the shapes of a clip path are not supported");
            return null;
        }
        if (clipPath.getAttribute("clipPathUnits").trim().equals("objectBoundingBox")) {
            warnings.add("Clip paths in bounding box units are not supported", "clip path ignored",
                    "clip paths ignored");
            return null;
        }
        double[] matrix = Matrix.multiply(user, transform(clipPath.getAttribute("transform")));
        List<Layer> shapes = new ArrayList<>();
        // the shapes may end up in a group: one level deeper, and one more layer
        if (!limits.enter()) {
            clipIncomplete = true;
            return null;
        }
        clipDepth++;
        try {
            SvgStyle clipStyle = SvgStyle.of(clipPath, null);
            for (Element child : elements(clipPath)) {
                convert(child, clipStyle, matrix, shapes);
            }
        } finally {
            clipDepth--;
            limits.leave();
        }
        // the shapes reset the flag for themselves
        clipIncomplete = limits.stopped();
        if (clipIncomplete) {
            return null;
        }
        String id = clipPath.getAttribute("id");
        String name = id.isEmpty() ? "clipPath" : id;
        if (shapes.size() == 1) {
            Layer only = shapes.get(0);
            return only.withBase(only.base().withName(name));
        }
        if (!limits.layer()) {
            clipIncomplete = true;
            return null;
        }
        return new GroupLayer(LayerBase.create(name, SvgImporter.DURATION), shapes);
    }

    private Layer shape(Element element, String kind, SvgStyle style, double[] user) {
        Geometry geometry;
        double originX = 0;
        double originY = 0;
        double[] box;
        switch (kind) {
            case "rect" -> {
                double width = x(element, "width");
                double height = y(element, "height");
                if (!(width > 0 && height > 0)) {
                    return null;
                }
                originX = x(element, "x");
                originY = y(element, "y");
                geometry = new Geometry.Rect(Animatable.of(new Vec2(width, height)),
                        Animatable.of(cornerRadius(element, width, height)));
                box = new double[]{0, 0, width, height};
            }
            case "circle", "ellipse" -> {
                boolean circle = kind.equals("circle");
                double rx = circle ? SvgLength.parse(element.getAttribute("r"), viewportDiagonal, 0, warnings)
                        : x(element, "rx");
                double ry = circle ? rx : y(element, "ry");
                if (!(rx > 0 && ry > 0)) {
                    return null;
                }
                originX = x(element, "cx") - rx;
                originY = y(element, "cy") - ry;
                geometry = new Geometry.Ellipse(Animatable.of(new Vec2(2 * rx, 2 * ry)));
                box = new double[]{0, 0, 2 * rx, 2 * ry};
            }
            case "line" -> {
                Vec2 start = new Vec2(x(element, "x1"), y(element, "y1"));
                Vec2 end = new Vec2(x(element, "x2"), y(element, "y2"));
                geometry = new Geometry.Line(Animatable.of(start), Animatable.of(end));
                box = new double[]{Math.min(start.x(), end.x()), Math.min(start.y(), end.y()),
                        Math.abs(end.x() - start.x()), Math.abs(end.y() - start.y())};
            }
            default -> {
                PathData path = kind.equals("path") ? SvgPathParser.parse(element.getAttribute("d"), warnings)
                        : points(element.getAttribute("points"), kind.equals("polygon"));
                box = PathBounds.of(path);
                if (box == null) {
                    return null;
                }
                geometry = new Geometry.PathGeometry(Animatable.of(path));
            }
        }
        SvgGradients.Target target = new SvgGradients.Target(box, originX, originY);
        Paint fill = kind.equals("line") ? new Paint.None()
                : gradients.paint(style.get("fill", "black"), unit(style.get("fill-opacity")), style, target);
        boolean outline = kind.equals("path") || kind.equals("polygon") || kind.equals("polyline");
        if (outline && clipDepth == 0 && !(fill instanceof Paint.None) && "evenodd".equals(style.get("fill-rule"))) {
            warnings.add("The even-odd fill rule is not supported; the non-zero rule is used", "shape", "shapes");
        }
        LayerBase base = base(name(element), Matrix.multiply(user, Matrix.translate(originX, originY)));
        return new ShapeLayer(base, geometry, Animatable.of(fill), stroke(style, target), null);
    }

    private double cornerRadius(Element element, double width, double height) {
        boolean hasX = isLength(element.getAttribute("rx"));
        boolean hasY = isLength(element.getAttribute("ry"));
        if (!hasX && !hasY) {
            return 0;
        }
        double rx = hasX ? Math.max(0, x(element, "rx")) : Math.max(0, y(element, "ry"));
        double ry = hasY ? Math.max(0, y(element, "ry")) : rx;
        rx = Math.min(rx, width / 2);
        ry = Math.min(ry, height / 2);
        if (Math.abs(rx - ry) > 1e-9) {
            warnings.add("Rectangles with different horizontal and vertical corner radii use the smaller radius");
        }
        return Math.min(rx, ry);
    }

    private boolean isLength(String text) {
        return !Double.isNaN(SvgLength.parse(text, 0, Double.NaN, warnings));
    }

    /** The points of a polyline or polygon; like path data, the list ends at a number that is not usable. */
    private PathData points(String text, boolean closed) {
        double[] numbers = SvgTransforms.numbers(text);
        if (numbers == null) {
            return PathData.EMPTY;
        }
        List<PathData.Vertex> vertices = new ArrayList<>();
        for (int i = 0; i + 1 < numbers.length; i += 2) {
            if (!Limits.usable(numbers[i]) || !Limits.usable(numbers[i + 1])) {
                warnings.add("Path data was cut off at a number that is not finite or too large", "path", "paths");
                break;
            }
            vertices.add(new PathData.Vertex(new Vec2(numbers[i], numbers[i + 1]), Vec2.ZERO, Vec2.ZERO));
        }
        if (vertices.size() < 2) {
            return PathData.EMPTY;
        }
        return new PathData(List.of(new PathData.Contour(vertices, closed)));
    }

    private Stroke stroke(SvgStyle style, SvgGradients.Target target) {
        Paint paint = gradients.paint(style.get("stroke", "none"), unit(style.get("stroke-opacity")), style, target);
        double width = SvgLength.parse(style.get("stroke-width"), viewportDiagonal, 1, warnings);
        if (paint instanceof Paint.None || !(width > 0)) {
            return null;
        }
        Stroke.Cap cap = switch (style.get("stroke-linecap", "butt")) {
            case "round" -> Stroke.Cap.ROUND;
            case "square" -> Stroke.Cap.SQUARE;
            default -> Stroke.Cap.BUTT;
        };
        Stroke.Join join = switch (style.get("stroke-linejoin", "miter")) {
            case "round" -> Stroke.Join.ROUND;
            case "bevel" -> Stroke.Join.BEVEL;
            default -> Stroke.Join.MITER;
        };
        double miterLimit = Math.max(1, SvgLength.fraction(style.get("stroke-miterlimit"), 4, warnings));
        double dashOffset = SvgLength.parse(style.get("stroke-dashoffset"), viewportDiagonal, 0, warnings);
        return new Stroke(Animatable.of(paint), Animatable.of(width), cap, join, miterLimit,
                dashes(style.get("stroke-dasharray", "none")), Animatable.of(dashOffset));
    }

    private List<Double> dashes(String text) {
        List<Double> dashes = new ArrayList<>();
        double sum = 0;
        for (String part : text.trim().split("[\\s,]+")) {
            double length = SvgLength.parse(part, viewportDiagonal, -1, warnings);
            if (length < 0) {
                return List.of();
            }
            dashes.add(length);
            sum += length;
        }
        if (sum == 0) {
            return List.of();
        }
        if (dashes.size() % 2 == 1) {
            dashes.addAll(List.copyOf(dashes));
        }
        return dashes;
    }

    /**
     * A single line of text. The text origin of the model is the top left corner, that of SVG a
     * point on the baseline chosen by {@code text-anchor}; without the font, ascent and width
     * are estimates.
     */
    private Layer text(Element element, SvgStyle style, double[] user) {
        StringBuilder collected = new StringBuilder();
        Element[] firstSpan = new Element[1];
        collectText(element, collected, firstSpan);
        String content = collected.toString().replaceAll("\\s+", " ").trim();
        double size = SvgLength.parse(style.get("font-size"), 16, 16, warnings);
        if (content.isEmpty() || !(size > 0)) {
            return null;
        }
        Element positioned = element.hasAttribute("x") || element.hasAttribute("y") || firstSpan[0] == null
                ? element : firstSpan[0];
        double x = SvgLength.parse(first(positioned.getAttribute("x")), viewportWidth, 0, warnings);
        double y = SvgLength.parse(first(positioned.getAttribute("y")), viewportHeight, 0, warnings);

        double estimatedWidth = ADVANCE * size * content.length();
        TextLayer.Align align = switch (style.get("text-anchor", "start")) {
            case "middle" -> TextLayer.Align.CENTER;
            case "end" -> TextLayer.Align.RIGHT;
            default -> TextLayer.Align.LEFT;
        };
        double shift = align == TextLayer.Align.CENTER ? estimatedWidth / 2
                : align == TextLayer.Align.RIGHT ? estimatedWidth : 0;
        double originX = x - shift;
        double originY = y - ASCENT * size;
        warnings.add("Text is placed approximately: the baseline and the width are estimated without the font",
                "text", "texts");

        String weight = style.get("font-weight", "normal");
        boolean bold = weight.startsWith("bold") || SvgLength.fraction(weight, 400, null) >= 600;
        String posture = style.get("font-style", "normal");
        SvgGradients.Target target = new SvgGradients.Target(null, originX, originY);
        Paint fill = gradients.paint(style.get("fill", "black"), unit(style.get("fill-opacity")), style, target);
        LayerBase base = base(name(element), Matrix.multiply(user, Matrix.translate(originX, originY)));
        return new TextLayer(base, content, fontFamily(style.get("font-family", "")), null, Animatable.of(size),
                bold ? TextLayer.Weight.BOLD : TextLayer.Weight.NORMAL,
                posture.equals("italic") || posture.startsWith("oblique"), align, Animatable.of(fill),
                stroke(style, target));
    }

    private static void collectText(Element element, StringBuilder out, Element[] firstSpan) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                out.append(child.getNodeValue());
            } else if (child instanceof Element span && "tspan".equals(span.getLocalName())) {
                if (firstSpan[0] == null) {
                    firstSpan[0] = span;
                }
                collectText(span, out, firstSpan);
            }
        }
    }

    private static String first(String list) {
        String trimmed = list.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.split("[\\s,]+")[0];
    }

    /** The first family of the list; the generic CSS families become the logical JavaFX fonts. */
    private static String fontFamily(String list) {
        String first = list.split(",")[0].trim();
        if (first.length() >= 2 && (first.charAt(0) == '\'' || first.charAt(0) == '"')) {
            first = first.substring(1, first.length() - 1).trim();
        }
        return switch (first.toLowerCase(Locale.ROOT)) {
            case "" -> "System";
            case "sans-serif" -> "SansSerif";
            case "serif" -> "Serif";
            case "monospace" -> "Monospaced";
            default -> first;
        };
    }

    private Layer image(Element element, SvgStyle style, double[] user) {
        String href = SvgImporter.href(element);
        if (!href.regionMatches(true, 0, "data:", 0, 5)) {
            warnings.add("External images are not supported", "image ignored", "images ignored");
            return null;
        }
        ImageData data = ImageData.fromDataUri(href);
        if (data == null) {
            warnings.add("Embedded images other than PNG and JPEG are not supported", "image ignored",
                    "images ignored");
            return null;
        }
        boolean hasWidth = isLength(element.getAttribute("width"));
        boolean hasHeight = isLength(element.getAttribute("height"));
        double boxWidth = hasWidth ? x(element, "width") : data.width();
        double boxHeight = hasHeight ? y(element, "height") : data.height();
        boolean known = data.width() > 0 && data.height() > 0;
        if (known && hasWidth != hasHeight) {
            if (hasWidth) {
                boxHeight = boxWidth * data.height() / data.width();
            } else {
                boxWidth = boxHeight * data.width() / data.height();
            }
        }
        if (!(boxWidth > 0 && boxHeight > 0)) {
            return null;
        }
        double x = x(element, "x");
        double y = y(element, "y");
        double width = boxWidth;
        double height = boxHeight;
        String aspect = element.getAttribute("preserveAspectRatio").trim().replaceAll("\\s+", " ");
        if (!aspect.equals("none") && known) {
            if (!(aspect.isEmpty() || aspect.equals("xMidYMid") || aspect.equals("xMidYMid meet"))) {
                warnings.add("preserveAspectRatio values other than xMidYMid meet and none are not supported;"
                        + " the default is used");
            }
            double scale = Math.min(boxWidth / data.width(), boxHeight / data.height());
            width = data.width() * scale;
            height = data.height() * scale;
            x += (boxWidth - width) / 2;
            y += (boxHeight - height) / 2;
        }
        String name = name(element);
        String assetId = assetIdsByHref.computeIfAbsent(href, key -> {
            String id = UUID.randomUUID().toString();
            assets.add(new Asset(id, Asset.Kind.IMAGE, "assets/" + id + "." + data.extension(), name));
            assetBytes.put(id, data.bytes());
            return id;
        });
        return new ImageLayer(base(name, Matrix.multiply(user, Matrix.translate(x, y))), assetId, width, height);
    }
}
