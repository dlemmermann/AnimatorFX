package com.dlsc.animatorfx.model.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A value of a parsed JSON tree that knows where it is, so that type errors can name their path.
 * A member that is absent and one that is {@code null} are both "missing".
 */
final class Node {

    private final Node parent;
    private final String key;
    private final int index;
    private final Object value;

    private Node(Node parent, String key, int index, Object value) {
        this.parent = parent;
        this.key = key;
        this.index = index;
        this.value = value;
    }

    static Node root(Object value) {
        return new Node(null, null, -1, value);
    }

    boolean isMissing() {
        return value == null;
    }

    boolean isObject() {
        return value instanceof Map<?, ?>;
    }

    boolean isString() {
        return value instanceof String;
    }

    /** Whether this is an object that has the given member, even a {@code null} one. */
    boolean has(String member) {
        return value instanceof Map<?, ?> map && map.containsKey(member);
    }

    /** The member of this object, which may be missing. */
    Node get(String member) throws AnimationFormatException {
        return new Node(this, member, -1, map().get(member));
    }

    Node required(String member) throws AnimationFormatException {
        Node node = get(member);
        if (node.isMissing()) {
            throw node.error("Missing required member");
        }
        return node;
    }

    List<Node> elements() throws AnimationFormatException {
        if (!(value instanceof List<?> list)) {
            throw error("Expected an array but found " + describe());
        }
        List<Node> nodes = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            nodes.add(new Node(this, null, i, list.get(i)));
        }
        return nodes;
    }

    /** The elements of an array member; none when the member is missing. */
    List<Node> elements(String member) throws AnimationFormatException {
        Node node = get(member);
        return node.isMissing() ? List.of() : node.elements();
    }

    double number() throws AnimationFormatException {
        if (!(value instanceof Number number)) {
            throw error("Expected a number but found " + describe());
        }
        double d = number.doubleValue();
        if (!Double.isFinite(d)) {
            throw error("Expected a finite number but found " + d);
        }
        return d;
    }

    double number(double fallback) throws AnimationFormatException {
        return isMissing() ? fallback : number();
    }

    int integer() throws AnimationFormatException {
        double d = number();
        if (d != Math.rint(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
            throw error("Expected an integer but found " + d);
        }
        return (int) d;
    }

    String string() throws AnimationFormatException {
        if (!(value instanceof String s)) {
            throw error("Expected a string but found " + describe());
        }
        return s;
    }

    String string(String fallback) throws AnimationFormatException {
        return isMissing() ? fallback : string();
    }

    boolean bool(boolean fallback) throws AnimationFormatException {
        if (isMissing()) {
            return fallback;
        }
        if (!(value instanceof Boolean b)) {
            throw error("Expected true or false but found " + describe());
        }
        return b;
    }

    AnimationFormatException error(String message) {
        return new AnimationFormatException(message, path());
    }

    AnimationFormatException error(String message, Throwable cause) {
        return new AnimationFormatException(message, path(), cause);
    }

    String path() {
        StringBuilder out = new StringBuilder();
        appendPath(out);
        return out.toString();
    }

    private void appendPath(StringBuilder out) {
        if (parent == null) {
            return;
        }
        parent.appendPath(out);
        if (key == null) {
            out.append('[').append(index).append(']');
        } else {
            if (!out.isEmpty()) {
                out.append('.');
            }
            out.append(key);
        }
    }

    private Map<?, ?> map() throws AnimationFormatException {
        if (!(value instanceof Map<?, ?> map)) {
            throw error("Expected an object but found " + describe());
        }
        return map;
    }

    private String describe() {
        return switch (value) {
            case null -> "nothing";
            case Map<?, ?> m -> "an object";
            case List<?> l -> "an array";
            case String s -> "a string";
            case Number n -> "a number";
            case Boolean b -> "a boolean";
            default -> "a " + value.getClass().getName();
        };
    }
}
