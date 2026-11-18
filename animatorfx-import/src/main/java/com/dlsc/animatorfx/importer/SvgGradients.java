package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.Vec2;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves SVG paint values, including references to linear and radial gradients, to model
 * paints in the coordinates of the painted layer.
 */
final class SvgGradients {

    /**
     * Where the painted geometry sits.
     *
     * @param box     the bounding box {@code {x, y, width, height}} of the geometry in layer
     *                coordinates, or {@code null} if it is unknown
     * @param originX the user space x that is x = 0 in layer coordinates
     */
    record Target(double[] box, double originX, double originY) {
    }

    private final Map<String, Element> ids;
    private final Warnings warnings;
    private final double viewportWidth;
    private final double viewportHeight;

    SvgGradients(Map<String, Element> ids, Warnings warnings, double viewportWidth, double viewportHeight) {
        this.ids = ids;
        this.warnings = warnings;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    /**
     * @param value   the {@code fill} or {@code stroke} value
     * @param opacity multiplied into the alpha of every colour
     */
    Paint paint(String value, double opacity, SvgStyle style, Target target) {
        String text = value.trim();
        if (text.startsWith("url(")) {
            int close = text.indexOf(')');
            String fallback = close < 0 ? "" : text.substring(close + 1).trim();
            Element reference = close < 0 ? null : ids.get(referenceId(text.substring(4, close)));
            String kind = reference == null ? "" : reference.getLocalName();
            if (kind.equals("linearGradient") || kind.equals("radialGradient")) {
                return gradient(reference, opacity, style, target);
            }
            if (!kind.equals("pattern")) {
                warnings.add("Paint references that cannot be resolved are ignored");
            }
            return fallback.isEmpty() ? new Paint.None() : paint(fallback, opacity, style, target);
        }
        if (text.equalsIgnoreCase("none")) {
            return new Paint.None();
        }
        Rgba color = color(text, style);
        if (color == null) {
            warnings.add("Paint values that are not understood are treated as none");
            return new Paint.None();
        }
        return new Paint.Solid(withOpacity(color, opacity));
    }

    static String referenceId(String url) {
        String id = url.trim();
        if (id.length() >= 2 && (id.charAt(0) == '"' || id.charAt(0) == '\'')) {
            id = id.substring(1, id.length() - 1).trim();
        }
        return id.startsWith("#") ? id.substring(1) : "";
    }

    private static Rgba color(String text, SvgStyle style) {
        if (text.equalsIgnoreCase("currentColor")) {
            Rgba current = SvgColors.parse(style.get("color", "black"));
            return current == null ? Rgba.BLACK : current;
        }
        return SvgColors.parse(text);
    }

    private static Rgba withOpacity(Rgba color, double opacity) {
        return opacity == 1 ? color : new Rgba(color.r(), color.g(), color.b(), color.a() * opacity);
    }

    private Paint gradient(Element gradient, double opacity, SvgStyle style, Target target) {
        List<Element> chain = chain(gradient);
        List<Paint.Stop> stops = stops(chain, opacity, style);
        if (stops.isEmpty()) {
            return new Paint.None();
        }
        Paint lastColor = new Paint.Solid(stops.get(stops.size() - 1).color());
        if (stops.size() == 1) {
            return lastColor;
        }
        boolean boxUnits = !attribute(chain, "gradientUnits", "objectBoundingBox").equals("userSpaceOnUse");
        double[] toLayer;
        if (boxUnits) {
            double[] box = target.box();
            if (box == null) {
                warnings.add("Gradients relative to the bounding box of a text are approximated");
                return lastColor;
            }
            if (!(box[2] > 0 && box[3] > 0)) {
                // SVG paints nothing with a bounding box gradient on a shape without area
                return new Paint.None();
            }
            toLayer = Matrix.multiply(Matrix.translate(box[0], box[1]), Matrix.scale(box[2], box[3]));
        } else {
            toLayer = Matrix.translate(-target.originX(), -target.originY());
        }
        String transformText = attribute(chain, "gradientTransform", "");
        double[] gradientTransform = SvgTransforms.parse(transformText);
        if (gradientTransform == null) {
            warnings.add("Transforms that cannot be parsed are ignored");
            gradientTransform = Matrix.identity();
        }
        toLayer = Matrix.multiply(toLayer, gradientTransform);

        String spread = attribute(chain, "spreadMethod", "pad");
        if (!spread.equals("pad")) {
            warnings.add("Gradient spread methods other than pad are not supported; the gradients are padded");
        }
        if (gradient.getLocalName().equals("linearGradient")) {
            return linear(chain, boxUnits, toLayer, stops, lastColor);
        }
        return radial(chain, boxUnits, toLayer, stops, lastColor);
    }

    private Paint linear(List<Element> chain, boolean boxUnits, double[] toLayer, List<Paint.Stop> stops,
                         Paint lastColor) {
        double x1 = coordinate(chain, "x1", "0%", boxUnits, viewportWidth);
        double y1 = coordinate(chain, "y1", "0%", boxUnits, viewportHeight);
        double x2 = coordinate(chain, "x2", "100%", boxUnits, viewportWidth);
        double y2 = coordinate(chain, "y2", "0%", boxUnits, viewportHeight);
        if (x1 == x2 && y1 == y2) {
            return lastColor;
        }
        Vec2 start = Matrix.apply(toLayer, x1, y1);
        Vec2 end = Matrix.apply(toLayer, x2, y2);
        // Lines of equal colour are perpendicular to the vector only before the transform. The
        // same gradient has its vector along the normal of the transformed lines.
        Vec2 along = Matrix.applyLinear(toLayer, -(y2 - y1), x2 - x1);
        double normalX = -along.y();
        double normalY = along.x();
        double lengthSquared = normalX * normalX + normalY * normalY;
        if (lengthSquared == 0) {
            return lastColor;
        }
        double distance = ((end.x() - start.x()) * normalX + (end.y() - start.y()) * normalY) / lengthSquared;
        if (distance == 0) {
            return lastColor;
        }
        return new Paint.LinearGradient(start,
                new Vec2(start.x() + normalX * distance, start.y() + normalY * distance), stops);
    }

    private Paint radial(List<Element> chain, boolean boxUnits, double[] toLayer, List<Paint.Stop> stops,
                         Paint lastColor) {
        double cx = coordinate(chain, "cx", "50%", boxUnits, viewportWidth);
        double cy = coordinate(chain, "cy", "50%", boxUnits, viewportHeight);
        double diagonal = Math.hypot(viewportWidth, viewportHeight) / Math.sqrt(2);
        double r = coordinate(chain, "r", "50%", boxUnits, diagonal);
        String fx = attribute(chain, "fx", null);
        String fy = attribute(chain, "fy", null);
        if ((fx != null && coordinate(chain, "fx", "50%", boxUnits, viewportWidth) != cx)
                || (fy != null && coordinate(chain, "fy", "50%", boxUnits, viewportHeight) != cy)) {
            warnings.add("Focal points of radial gradients are not supported; the centre is used");
        }
        if (!(r > 0)) {
            return lastColor;
        }
        if (!Matrix.isUniform(toLayer)) {
            warnings.add("Radial gradients that are stretched to an ellipse are drawn as circles");
        }
        return new Paint.RadialGradient(Matrix.apply(toLayer, cx, cy), r * Matrix.meanScale(toLayer), stops);
    }

    /** The gradient followed by the gradients it inherits from through {@code href}. */
    private List<Element> chain(Element gradient) {
        List<Element> chain = new ArrayList<>();
        Set<Element> seen = new HashSet<>();
        Element current = gradient;
        while (current != null && seen.add(current)) {
            chain.add(current);
            String href = SvgImporter.href(current);
            Element next = href.startsWith("#") ? ids.get(href.substring(1)) : null;
            String kind = next == null ? "" : next.getLocalName();
            current = kind.equals("linearGradient") || kind.equals("radialGradient") ? next : null;
        }
        return chain;
    }

    private static String attribute(List<Element> chain, String name, String fallback) {
        for (Element element : chain) {
            if (element.hasAttribute(name)) {
                return element.getAttribute(name).trim();
            }
        }
        return fallback;
    }

    private static double coordinate(List<Element> chain, String name, String fallback, boolean boxUnits,
                                     double percentBase) {
        String text = attribute(chain, name, fallback);
        return boxUnits ? SvgLength.fraction(text, SvgLength.fraction(fallback, 0))
                : SvgLength.parse(text, percentBase, SvgLength.parse(fallback, percentBase, 0));
    }

    /** The stops of the first gradient in the chain that has any. */
    private static List<Paint.Stop> stops(List<Element> chain, double opacity, SvgStyle paintedStyle) {
        List<Paint.Stop> stops = new ArrayList<>();
        for (Element gradient : chain) {
            double previous = 0;
            for (Node child = gradient.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element stop) || !"stop".equals(stop.getLocalName())) {
                    continue;
                }
                SvgStyle style = SvgStyle.of(stop, null);
                double offset = Math.clamp(SvgLength.fraction(stop.getAttribute("offset"), 0), 0.0, 1.0);
                previous = Math.max(previous, offset);
                String colorText = style.get("stop-color", "black");
                Rgba color = colorText.equalsIgnoreCase("currentColor") ? color(colorText, paintedStyle)
                        : SvgColors.parse(colorText);
                double stopOpacity = Math.clamp(SvgLength.fraction(style.get("stop-opacity"), 1), 0.0, 1.0);
                stops.add(new Paint.Stop(previous, withOpacity(color == null ? Rgba.BLACK : color,
                        stopOpacity * opacity)));
            }
            if (!stops.isEmpty()) {
                break;
            }
        }
        return stops;
    }
}
