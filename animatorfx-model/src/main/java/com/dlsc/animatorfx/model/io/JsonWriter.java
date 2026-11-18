package com.dlsc.animatorfx.model.io;

import java.util.Collection;
import java.util.Map;

/**
 * Writes the value tree produced by {@link Json#parse} as pretty-printed JSON: two spaces of
 * indentation, object members in iteration order, and short arrays of numbers on one line.
 */
public final class JsonWriter {

    /** Arrays of only numbers up to this length stay on one line. */
    private static final int INLINE_LIMIT = 16;

    private JsonWriter() {
    }

    /**
     * @param value a {@code Map} with string keys, a {@code Collection}, {@code String}, {@code Number},
     *              {@code Boolean} or {@code null}, nested to any depth
     * @throws IllegalArgumentException for any other value and for numbers that are not finite
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, 0);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value, int indent) {
        switch (value) {
            case null -> out.append("null");
            case String s -> string(out, s);
            case Boolean b -> out.append(b.booleanValue());
            case Number n -> number(out, n);
            case Map<?, ?> map -> object(out, map, indent);
            case Collection<?> list -> array(out, list, indent);
            default -> throw new IllegalArgumentException("Cannot write a " + value.getClass().getName() + " as JSON");
        }
    }

    private static void object(StringBuilder out, Map<?, ?> map, int indent) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Object member names must be strings but got: " + entry.getKey());
            }
            out.append(first ? "\n" : ",\n");
            first = false;
            indent(out, indent + 1);
            string(out, key);
            out.append(": ");
            write(out, entry.getValue(), indent + 1);
        }
        out.append('\n');
        indent(out, indent);
        out.append('}');
    }

    private static void array(StringBuilder out, Collection<?> list, int indent) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        boolean inline = list.size() <= INLINE_LIMIT && list.stream().allMatch(Number.class::isInstance);
        boolean first = true;
        for (Object element : list) {
            if (inline) {
                out.append(first ? "" : ", ");
            } else {
                out.append(first ? "\n" : ",\n");
                indent(out, indent + 1);
            }
            first = false;
            write(out, element, indent + 1);
        }
        if (!inline) {
            out.append('\n');
            indent(out, indent);
        }
        out.append(']');
    }

    private static void indent(StringBuilder out, int indent) {
        out.repeat("  ", indent);
    }

    private static void number(StringBuilder out, Number number) {
        if (number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte) {
            out.append(number.longValue());
            return;
        }
        double value = number instanceof Float f ? Double.parseDouble(f.toString()) : number.doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("JSON cannot represent " + number);
        }
        // Below 2^53 every integral double is exactly a long, so no digit is invented.
        if (value == Math.rint(value) && Math.abs(value) < 0x1p53) {
            if (value == 0 && Double.doubleToRawLongBits(value) != 0) {
                out.append('-');
            }
            out.append((long) value);
        } else {
            out.append(value);
        }
    }

    private static void string(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == '\u2028' || c == '\u2029' || isLoneSurrogate(s, i)) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** A lone surrogate cannot be encoded as UTF-8, so it is escaped. */
    private static boolean isLoneSurrogate(String s, int i) {
        char c = s.charAt(i);
        if (Character.isHighSurrogate(c)) {
            return i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1));
        }
        if (Character.isLowSurrogate(c)) {
            return i == 0 || !Character.isHighSurrogate(s.charAt(i - 1));
        }
        return false;
    }
}
