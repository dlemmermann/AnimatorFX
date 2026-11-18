package com.dlsc.animatorfx.model.io;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesNestedObjectsAndArrays() throws Exception {
        Object value = Json.parse("""
                { "a": { "b": [1, {"c": true}, null, "x"] }, "d": false, "e": {}, "f": [] }
                """);

        Map<?, ?> root = assertInstanceOf(Map.class, value);
        assertEquals(List.of("a", "d", "e", "f"), List.copyOf(root.keySet()));
        Map<?, ?> a = (Map<?, ?>) root.get("a");
        assertEquals(Arrays.asList(1.0, Map.of("c", true), null, "x"), a.get("b"));
        assertEquals(false, root.get("d"));
        assertEquals(Map.of(), root.get("e"));
        assertEquals(List.of(), root.get("f"));
    }

    @Test
    void parsesScalarsAtTheRoot() throws Exception {
        assertEquals("x", Json.parse(" \"x\" "));
        assertEquals(true, Json.parse("true"));
        assertNull(Json.parse("null"));
    }

    @Test
    void parsesEscapes() throws Exception {
        assertEquals("a\nb\"c\u00e9\\/\t\r\b\f", Json.parse("\"a\\nb\\\"c\\u00e9\\\\\\/\\t\\r\\b\\f\""));
        assertEquals("\uD83D\uDE00", Json.parse("\"\\uD83D\\uDE00\""));
        assertEquals("é ✓", Json.parse("\"é ✓\""));
    }

    @Test
    void parsesNumbers() throws Exception {
        assertEquals(-1500.0, Json.parse("-1.5e3"));
        assertEquals(0.0, Json.parse("0"));
        assertEquals(12.25, Json.parse("12.25"));
        assertEquals(0.002, Json.parse("2E-3"));
        assertEquals(100.0, Json.parse("1e+2"));
        assertEquals(Double.doubleToLongBits(-0.0), Double.doubleToLongBits((Double) Json.parse("-0")));
    }

    @Test
    void reportsLineAndColumnOfAnError() {
        AnimationFormatException e = assertThrows(AnimationFormatException.class,
                () -> Json.parse("{\"a\": 1,\n  \"b\": }"));

        assertTrue(e.getMessage().contains("line 2, column 8"), e.getMessage());
    }

    @Test
    void rejectsMalformedInput() {
        for (String text : List.of("", "  ", "{", "[1,]", "{\"a\":1,}", "{a:1}", "{\"a\" 1}", "[1 2]", "01", "1.", ".5",
                "1e", "+1", "-", "tru", "nul", "\"abc", "\"a\\x\"", "\"a\\u12g4\"", "\"a\nb\"", "'a'", "NaN",
                "Infinity", "1e999", "// c\n1", "{\"a\":1}}", "1 2", "[", "{\"a\":")) {
            assertThrows(AnimationFormatException.class, () -> Json.parse(text), text);
        }
    }

    @Test
    void rejectsTrailingGarbageWithItsPosition() {
        AnimationFormatException e = assertThrows(AnimationFormatException.class, () -> Json.parse("{} x"));

        assertTrue(e.getMessage().contains("line 1, column 4"), e.getMessage());
    }

    @Test
    void lastDuplicateKeyWins() throws Exception {
        assertEquals(Map.of("a", 2.0, "b", 3.0), Json.parse("{\"a\":1,\"b\":3,\"a\":2}"));
    }

    @Test
    void limitsNestingDepth() throws Exception {
        String ok = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH);
        assertInstanceOf(List.class, Json.parse(ok));

        String tooDeep = "[".repeat(Json.MAX_DEPTH + 1) + "]".repeat(Json.MAX_DEPTH + 1);
        assertThrows(AnimationFormatException.class, () -> Json.parse(tooDeep));
        assertThrows(AnimationFormatException.class, () -> Json.parse("[".repeat(200_000)));
        assertThrows(AnimationFormatException.class, () -> Json.parse("{\"a\":".repeat(200_000)));
    }
}
