package com.dlsc.animatorfx.model.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict JSON (RFC 8259) parser. Objects become {@code Map<String, Object>} in document order,
 * arrays {@code List<Object>}, strings {@code String}, numbers {@code Double}, booleans
 * {@code Boolean} and {@code null} stays {@code null}.
 */
public final class Json {

    /** Arrays and objects may be nested this deep. */
    static final int MAX_DEPTH = 512;

    private Json() {
    }

    /**
     * Parses a JSON text. Of duplicate keys in an object the last one wins.
     *
     * @throws AnimationFormatException if the text is not valid JSON; the message names line and column
     */
    public static Object parse(String text) throws AnimationFormatException {
        return new Parser(text).document();
    }

    private static final class Parser {

        private final String text;
        private int pos;
        private int depth;

        Parser(String text) {
            this.text = text;
        }

        Object document() throws AnimationFormatException {
            skipWhitespace();
            Object value = value();
            skipWhitespace();
            if (pos < text.length()) {
                throw error("Unexpected content after the end of the JSON value");
            }
            return value;
        }

        private Object value() throws AnimationFormatException {
            if (pos >= text.length()) {
                throw error("Unexpected end of input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield number();
                    }
                    throw error("Unexpected character " + describe(c));
                }
            };
        }

        private Map<String, Object> object() throws AnimationFormatException {
            enter();
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
            } else {
                while (true) {
                    if (peek() != '"') {
                        throw expected("a member name in double quotes");
                    }
                    String key = string();
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    map.put(key, value());
                    skipWhitespace();
                    if (peek() == ',') {
                        pos++;
                        skipWhitespace();
                    } else if (peek() == '}') {
                        pos++;
                        break;
                    } else {
                        throw expected("',' or '}'");
                    }
                }
            }
            depth--;
            return map;
        }

        private List<Object> array() throws AnimationFormatException {
            enter();
            pos++;
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
            } else {
                while (true) {
                    list.add(value());
                    skipWhitespace();
                    if (peek() == ',') {
                        pos++;
                        skipWhitespace();
                    } else if (peek() == ']') {
                        pos++;
                        break;
                    } else {
                        throw expected("',' or ']'");
                    }
                }
            }
            depth--;
            return list;
        }

        private void enter() throws AnimationFormatException {
            if (++depth > MAX_DEPTH) {
                throw error("Nested deeper than " + MAX_DEPTH + " levels");
            }
        }

        private String string() throws AnimationFormatException {
            int start = ++pos;
            StringBuilder out = null;
            while (true) {
                if (pos >= text.length()) {
                    throw error("Unterminated string");
                }
                char c = text.charAt(pos);
                if (c == '"') {
                    String result = out == null ? text.substring(start, pos) : out.append(text, start, pos).toString();
                    pos++;
                    return result;
                }
                if (c < 0x20) {
                    throw error("Control character in a string must be escaped");
                }
                if (c == '\\') {
                    if (out == null) {
                        out = new StringBuilder();
                    }
                    out.append(text, start, pos);
                    pos++;
                    out.append(escape());
                    start = pos;
                } else {
                    pos++;
                }
            }
        }

        private char escape() throws AnimationFormatException {
            if (pos >= text.length()) {
                throw error("Unterminated string");
            }
            char c = text.charAt(pos++);
            return switch (c) {
                case '"', '\\', '/' -> c;
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> unicode();
                default -> {
                    pos--;
                    throw error("Invalid escape sequence");
                }
            };
        }

        private char unicode() throws AnimationFormatException {
            int code = 0;
            for (int i = 0; i < 4; i++) {
                int digit = pos < text.length() ? Character.digit(text.charAt(pos), 16) : -1;
                if (digit < 0) {
                    throw expected("four hexadecimal digits after \\u");
                }
                code = code * 16 + digit;
                pos++;
            }
            return (char) code;
        }

        private Double number() throws AnimationFormatException {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            if (peek() == '0') {
                pos++;
            } else {
                digits();
            }
            if (peek() == '.') {
                pos++;
                digits();
            }
            if (peek() == 'e' || peek() == 'E') {
                pos++;
                if (peek() == '+' || peek() == '-') {
                    pos++;
                }
                digits();
            }
            double value = Double.parseDouble(text.substring(start, pos));
            if (Double.isInfinite(value)) {
                pos = start;
                throw error("Number is out of range");
            }
            return value;
        }

        private void digits() throws AnimationFormatException {
            int start = pos;
            while (peek() >= '0' && peek() <= '9') {
                pos++;
            }
            if (pos == start) {
                throw expected("a digit");
            }
        }

        private Object literal(String word, Object value) throws AnimationFormatException {
            if (!text.startsWith(word, pos)) {
                throw error("Expected a value such as " + word);
            }
            pos += word.length();
            return value;
        }

        private void expect(char c) throws AnimationFormatException {
            if (peek() != c) {
                throw expected("'" + c + "'");
            }
            pos++;
        }

        /** The current character, or 0 at the end of the input. */
        private char peek() {
            return pos < text.length() ? text.charAt(pos) : 0;
        }

        private void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    return;
                }
                pos++;
            }
        }

        private static String describe(char c) {
            return c < 0x20 || c == 0x7F ? String.format("U+%04X", (int) c) : "'" + c + "'";
        }

        private AnimationFormatException expected(String what) {
            String found = pos < text.length() ? "found " + describe(text.charAt(pos)) : "reached the end of the input";
            return error("Expected " + what + " but " + found);
        }

        private AnimationFormatException error(String message) {
            int line = 1;
            int lineStart = 0;
            for (int i = 0; i < pos; i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    lineStart = i + 1;
                }
            }
            return new AnimationFormatException(
                    "Invalid JSON at line " + line + ", column " + (pos - lineStart + 1) + ": " + message, "");
        }
    }
}
