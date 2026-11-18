package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses SVG path data ({@code d} attribute) into a Bezier path.
 */
public final class SvgPathParser {

    private static final double SAME_POINT = 1e-9;

    private SvgPathParser() {
    }

    /**
     * Understands every path command. Quadratic curves are elevated to cubics exactly and arcs
     * are approximated by cubics spanning at most 90° each. Every subpath becomes a contour; a
     * subpath without a segment draws nothing and is dropped. As SVG demands, malformed data
     * yields everything up to the error instead of failing.
     */
    public static PathData parse(String d) {
        return parse(d, null);
    }

    /**
     * A number that is not finite or absurdly large ends the path data like any other error.
     *
     * @param warnings told about such a number; may be {@code null}
     */
    static PathData parse(String d, Warnings warnings) {
        Builder builder = new Builder();
        Scanner scanner = new Scanner(d == null ? "" : d);
        try {
            scanner.run(builder);
        } catch (MalformedPath e) {
            // keep what was read
        }
        if (scanner.unusableNumber && warnings != null) {
            warnings.add("Path data was cut off at a number that is not finite or too large", "path", "paths");
        }
        return builder.build();
    }

    private static final class MalformedPath extends Exception {
        MalformedPath() {
            super(null, null, false, false);
        }
    }

    private static final class Scanner {

        private final String text;
        private int pos;
        boolean unusableNumber;

        Scanner(String text) {
            this.text = text;
        }

        void run(Builder out) throws MalformedPath {
            char command = 0;
            while (true) {
                skipSeparators(false);
                if (pos >= text.length()) {
                    return;
                }
                char c = text.charAt(pos);
                if (Character.isLetter(c)) {
                    pos++;
                    command = c;
                    if (c == 'Z' || c == 'z') {
                        out.close();
                        continue;
                    }
                } else if (command == 0 || command == 'Z' || command == 'z') {
                    throw new MalformedPath();
                } else if (command == 'M') {
                    command = 'L';
                } else if (command == 'm') {
                    command = 'l';
                }
                segment(command, out);
            }
        }

        private void segment(char command, Builder out) throws MalformedPath {
            boolean relative = Character.isLowerCase(command);
            double ox = relative ? out.x : 0;
            double oy = relative ? out.y : 0;
            switch (Character.toUpperCase(command)) {
                case 'M' -> {
                    double x = number();
                    double y = number();
                    out.moveTo(ox + x, oy + y);
                }
                case 'L' -> {
                    double x = number();
                    double y = number();
                    out.lineTo(ox + x, oy + y);
                }
                case 'H' -> out.lineTo(ox + number(), out.y);
                case 'V' -> out.lineTo(out.x, oy + number());
                case 'C' -> {
                    double[] n = numbers(6);
                    out.cubicTo(ox + n[0], oy + n[1], ox + n[2], oy + n[3], ox + n[4], oy + n[5]);
                }
                case 'S' -> {
                    double[] n = numbers(4);
                    out.smoothCubicTo(ox + n[0], oy + n[1], ox + n[2], oy + n[3]);
                }
                case 'Q' -> {
                    double[] n = numbers(4);
                    out.quadTo(ox + n[0], oy + n[1], ox + n[2], oy + n[3]);
                }
                case 'T' -> {
                    double x = number();
                    double y = number();
                    out.smoothQuadTo(ox + x, oy + y);
                }
                case 'A' -> {
                    double rx = number();
                    double ry = number();
                    double rotation = number();
                    boolean largeArc = flag();
                    boolean sweep = flag();
                    double x = number();
                    double y = number();
                    out.arcTo(rx, ry, rotation, largeArc, sweep, ox + x, oy + y);
                }
                default -> throw new MalformedPath();
            }
        }

        private double[] numbers(int count) throws MalformedPath {
            double[] result = new double[count];
            for (int i = 0; i < count; i++) {
                result[i] = number();
            }
            return result;
        }

        private void skipSeparators(boolean comma) {
            boolean seenComma = !comma;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ',' && !seenComma) {
                    seenComma = true;
                } else if (!(c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f')) {
                    return;
                }
                pos++;
            }
        }

        private boolean flag() throws MalformedPath {
            skipSeparators(true);
            if (pos < text.length() && (text.charAt(pos) == '0' || text.charAt(pos) == '1')) {
                return text.charAt(pos++) == '1';
            }
            throw new MalformedPath();
        }

        private double number() throws MalformedPath {
            skipSeparators(true);
            int start = pos;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            int digits = digits();
            if (pos < text.length() && text.charAt(pos) == '.') {
                pos++;
                digits += digits();
            }
            if (digits == 0) {
                pos = start;
                throw new MalformedPath();
            }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                int mark = pos;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                if (digits() == 0) {
                    pos = mark;
                }
            }
            double value = Double.parseDouble(text.substring(start, pos));
            if (!Limits.usable(value)) {
                unusableNumber = true;
                throw new MalformedPath();
            }
            return value;
        }

        private int digits() {
            int start = pos;
            while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
                pos++;
            }
            return pos - start;
        }
    }

    /** Collects contours; tangents are kept absolute until {@link #build()}. */
    private static final class Builder {

        /** {@code {x, y, inX, inY, outX, outY}} with absolute control points. */
        private final List<List<double[]>> contours = new ArrayList<>();
        private final List<Boolean> closed = new ArrayList<>();
        private List<double[]> current;
        private boolean started;
        double x;
        double y;
        private double startX;
        private double startY;
        /** The second control point of the preceding cubic, or the control point of the preceding quadratic. */
        private double controlX;
        private double controlY;
        private char previous;

        void moveTo(double px, double py) {
            started = true;
            current = null;
            x = startX = px;
            y = startY = py;
            previous = 'M';
        }

        private List<double[]> contour() throws MalformedPath {
            if (!started) {
                throw new MalformedPath();
            }
            if (current == null) {
                current = new ArrayList<>();
                current.add(new double[]{x, y, x, y, x, y});
                contours.add(current);
                closed.add(false);
            }
            return current;
        }

        void lineTo(double px, double py) throws MalformedPath {
            contour().add(new double[]{px, py, px, py, px, py});
            x = px;
            y = py;
            previous = 'L';
        }

        void cubicTo(double c1x, double c1y, double c2x, double c2y, double px, double py) throws MalformedPath {
            cubic(c1x, c1y, c2x, c2y, px, py);
            controlX = c2x;
            controlY = c2y;
            previous = 'C';
        }

        private void cubic(double c1x, double c1y, double c2x, double c2y, double px, double py)
                throws MalformedPath {
            List<double[]> vertices = contour();
            double[] from = vertices.get(vertices.size() - 1);
            from[4] = c1x;
            from[5] = c1y;
            vertices.add(new double[]{px, py, c2x, c2y, px, py});
            x = px;
            y = py;
        }

        void smoothCubicTo(double c2x, double c2y, double px, double py) throws MalformedPath {
            boolean reflect = previous == 'C';
            cubicTo(reflect ? 2 * x - controlX : x, reflect ? 2 * y - controlY : y, c2x, c2y, px, py);
        }

        void quadTo(double cx, double cy, double px, double py) throws MalformedPath {
            cubic(x + (cx - x) * 2 / 3, y + (cy - y) * 2 / 3, px + (cx - px) * 2 / 3, py + (cy - py) * 2 / 3, px, py);
            controlX = cx;
            controlY = cy;
            previous = 'Q';
        }

        void smoothQuadTo(double px, double py) throws MalformedPath {
            if (previous == 'Q') {
                quadTo(2 * x - controlX, 2 * y - controlY, px, py);
                return;
            }
            // the control point is the current point: a straight line
            double cx = x;
            double cy = y;
            lineTo(px, py);
            controlX = cx;
            controlY = cy;
            previous = 'Q';
        }

        /** Endpoint to centre parameterisation of the SVG implementation notes (F.6). */
        void arcTo(double rx, double ry, double rotation, boolean largeArc, boolean sweep, double px, double py)
                throws MalformedPath {
            contour();
            double x0 = x;
            double y0 = y;
            previous = 'A';
            if (Math.abs(px - x0) < SAME_POINT && Math.abs(py - y0) < SAME_POINT) {
                return;
            }
            rx = Math.abs(rx);
            ry = Math.abs(ry);
            if (rx == 0 || ry == 0) {
                lineTo(px, py);
                previous = 'A';
                return;
            }
            double phi = Math.toRadians(rotation % 360);
            double cos = Math.cos(phi);
            double sin = Math.sin(phi);
            double dx = (x0 - px) / 2;
            double dy = (y0 - py) / 2;
            double x1 = cos * dx + sin * dy;
            double y1 = -sin * dx + cos * dy;
            double lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry);
            if (lambda > 1) {
                double grow = Math.sqrt(lambda);
                rx *= grow;
                ry *= grow;
            }
            double numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
            double denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1;
            double factor = Math.sqrt(Math.max(0, numerator / denominator));
            if (largeArc == sweep) {
                factor = -factor;
            }
            double cx1 = factor * rx * y1 / ry;
            double cy1 = -factor * ry * x1 / rx;
            double cx = cos * cx1 - sin * cy1 + (x0 + px) / 2;
            double cy = sin * cx1 + cos * cy1 + (y0 + py) / 2;
            double theta = Math.atan2((y1 - cy1) / ry, (x1 - cx1) / rx);
            double end = Math.atan2((-y1 - cy1) / ry, (-x1 - cx1) / rx);
            double delta = end - theta;
            if (sweep && delta < 0) {
                delta += 2 * Math.PI;
            } else if (!sweep && delta > 0) {
                delta -= 2 * Math.PI;
            }
            int segments = Math.max(1, (int) Math.ceil(Math.abs(delta) / (Math.PI / 2) - 1e-9));
            double step = delta / segments;
            double handle = 4.0 / 3 * Math.tan(step / 4);
            double fromX = x0;
            double fromY = y0;
            for (int i = 0; i < segments; i++) {
                double a = theta + i * step;
                double b = a + step;
                boolean last = i == segments - 1;
                double toX = last ? px : cx + cos * rx * Math.cos(b) - sin * ry * Math.sin(b);
                double toY = last ? py : cy + sin * rx * Math.cos(b) + cos * ry * Math.sin(b);
                // derivative of the ellipse point with respect to the angle
                double daX = -cos * rx * Math.sin(a) - sin * ry * Math.cos(a);
                double daY = -sin * rx * Math.sin(a) + cos * ry * Math.cos(a);
                double dbX = -cos * rx * Math.sin(b) - sin * ry * Math.cos(b);
                double dbY = -sin * rx * Math.sin(b) + cos * ry * Math.cos(b);
                cubic(fromX + handle * daX, fromY + handle * daY, toX - handle * dbX, toY - handle * dbY, toX, toY);
                fromX = toX;
                fromY = toY;
            }
        }

        void close() throws MalformedPath {
            if (!started) {
                throw new MalformedPath();
            }
            if (current != null) {
                double[] first = current.get(0);
                double[] last = current.get(current.size() - 1);
                if (current.size() > 1 && Math.abs(first[0] - last[0]) < SAME_POINT
                        && Math.abs(first[1] - last[1]) < SAME_POINT) {
                    first[2] = last[2];
                    first[3] = last[3];
                    current.remove(current.size() - 1);
                }
                closed.set(contours.size() - 1, true);
            }
            current = null;
            x = startX;
            y = startY;
            previous = 'Z';
        }

        PathData build() {
            List<PathData.Contour> result = new ArrayList<>();
            for (int i = 0; i < contours.size(); i++) {
                List<double[]> source = contours.get(i);
                if (source.size() < 2 && !curved(source.get(0))) {
                    continue;
                }
                List<PathData.Vertex> vertices = new ArrayList<>(source.size());
                for (double[] v : source) {
                    vertices.add(new PathData.Vertex(new Vec2(v[0], v[1]), new Vec2(v[2] - v[0], v[3] - v[1]),
                            new Vec2(v[4] - v[0], v[5] - v[1])));
                }
                result.add(new PathData.Contour(vertices, closed.get(i)));
            }
            return result.isEmpty() ? PathData.EMPTY : new PathData(result);
        }

        private static boolean curved(double[] v) {
            return v[2] != v[0] || v[3] != v[1] || v[4] != v[0] || v[5] != v[1];
        }
    }
}
