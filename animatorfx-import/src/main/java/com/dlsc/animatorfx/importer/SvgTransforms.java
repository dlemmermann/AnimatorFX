package com.dlsc.animatorfx.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the SVG {@code transform} attribute.
 */
final class SvgTransforms {

    private SvgTransforms() {
    }

    /**
     * The matrix of a transform list ({@code matrix}, {@code translate}, {@code scale},
     * {@code rotate}, {@code skewX}, {@code skewY}, chained left to right).
     *
     * @return the matrix, or {@code null} if the text is not a valid transform list, in which
     *         case SVG ignores the attribute
     */
    static double[] parse(String text) {
        double[] result = Matrix.identity();
        if (text == null) {
            return result;
        }
        int pos = 0;
        int length = text.length();
        while (true) {
            while (pos < length && (Character.isWhitespace(text.charAt(pos)) || text.charAt(pos) == ',')) {
                pos++;
            }
            if (pos >= length) {
                return result;
            }
            int nameStart = pos;
            while (pos < length && Character.isLetter(text.charAt(pos))) {
                pos++;
            }
            String name = text.substring(nameStart, pos);
            while (pos < length && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
            if (pos >= length || text.charAt(pos) != '(') {
                return null;
            }
            int close = text.indexOf(')', pos);
            if (close < 0) {
                return null;
            }
            double[] n = numbers(text.substring(pos + 1, close));
            pos = close + 1;
            double[] step = step(name, n);
            if (step == null) {
                return null;
            }
            result = Matrix.multiply(result, step);
        }
    }

    private static double[] step(String name, double[] n) {
        if (n == null) {
            return null;
        }
        return switch (name) {
            case "matrix" -> n.length == 6 ? new double[]{n[0], n[2], n[4], n[1], n[3], n[5]} : null;
            case "translate" -> n.length == 1 ? Matrix.translate(n[0], 0)
                    : n.length == 2 ? Matrix.translate(n[0], n[1]) : null;
            case "scale" -> n.length == 1 ? Matrix.scale(n[0], n[0]) : n.length == 2 ? Matrix.scale(n[0], n[1]) : null;
            case "rotate" -> n.length == 1 ? Matrix.rotate(n[0])
                    : n.length == 3 ? Matrix.multiply(Matrix.translate(n[1], n[2]),
                    Matrix.multiply(Matrix.rotate(n[0]), Matrix.translate(-n[1], -n[2]))) : null;
            case "skewX" -> n.length == 1 ? new double[]{1, Math.tan(Math.toRadians(n[0])), 0, 0, 1, 0} : null;
            case "skewY" -> n.length == 1 ? new double[]{1, 0, 0, Math.tan(Math.toRadians(n[0])), 1, 0} : null;
            default -> null;
        };
    }

    /**
     * Numbers separated by white space and/or commas; a sign also starts a new number.
     *
     * @return the numbers, or {@code null} if anything else is in the text
     */
    static double[] numbers(String text) {
        List<Double> values = new ArrayList<>();
        int pos = 0;
        int length = text.length();
        while (true) {
            while (pos < length && (Character.isWhitespace(text.charAt(pos)) || text.charAt(pos) == ',')) {
                pos++;
            }
            if (pos >= length) {
                break;
            }
            int end = numberEnd(text, pos);
            if (end == pos) {
                return null;
            }
            values.add(Double.parseDouble(text.substring(pos, end)));
            pos = end;
        }
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    /** The end of the number starting at {@code start}; {@code start} itself if there is none. */
    static int numberEnd(String text, int start) {
        int pos = start;
        int length = text.length();
        if (pos < length && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
            pos++;
        }
        int digits = 0;
        while (pos < length && Character.isDigit(text.charAt(pos))) {
            pos++;
            digits++;
        }
        if (pos < length && text.charAt(pos) == '.') {
            pos++;
            while (pos < length && Character.isDigit(text.charAt(pos))) {
                pos++;
                digits++;
            }
        }
        if (digits == 0) {
            return start;
        }
        if (pos < length && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            int mark = pos++;
            if (pos < length && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            int exponent = 0;
            while (pos < length && Character.isDigit(text.charAt(pos))) {
                pos++;
                exponent++;
            }
            // "1em" is the number 1 with the unit em
            if (exponent == 0) {
                pos = mark;
            }
        }
        return pos;
    }
}
