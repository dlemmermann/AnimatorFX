package com.dlsc.animatorfx.model.io;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonWriterTest {

    @Test
    void writesPrettyWithInsertionOrder() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("z", 1.0);
        root.put("a", "text");
        root.put("point", List.of(1.0, 2.5));
        root.put("emptyArray", List.of());
        root.put("emptyObject", Map.of());
        root.put("mixed", Arrays.asList("x", List.of(1.0, 2.0), null, true));
        root.put("nested", Map.of("k", false));

        assertEquals("""
                {
                  "z": 1,
                  "a": "text",
                  "point": [1, 2.5],
                  "emptyArray": [],
                  "emptyObject": {},
                  "mixed": [
                    "x",
                    [1, 2],
                    null,
                    true
                  ],
                  "nested": {
                    "k": false
                  }
                }""", JsonWriter.write(root));
    }

    @Test
    void writesIntegralDoublesWithoutFraction() {
        assertEquals("1", JsonWriter.write(1.0));
        assertEquals("-250", JsonWriter.write(-250.0));
        assertEquals("0", JsonWriter.write(0.0));
        assertEquals("-0", JsonWriter.write(-0.0));
        assertEquals("7", JsonWriter.write(7));
        assertEquals("7", JsonWriter.write(7L));
        assertEquals("0.1", JsonWriter.write(0.1));
        assertEquals("0.30000000000000004", JsonWriter.write(0.1 + 0.2));
    }

    @Test
    void numbersRoundTripExactly() throws Exception {
        for (double d : new double[]{0, -0.0, 1, -1, 0.1, 1.0 / 3, 1e-7, 1.5e-300, 123456789012345.0, 1e15, 1e21,
                9.007199254740993e15, Double.MAX_VALUE, Double.MIN_VALUE, -2.5e-5, Math.PI}) {
            Object back = Json.parse(JsonWriter.write(d));
            assertEquals(Double.doubleToLongBits(d), Double.doubleToLongBits((Double) back), JsonWriter.write(d));
        }
    }

    @Test
    void rejectsNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(List.of(Float.NEGATIVE_INFINITY)));
    }

    @Test
    void rejectsUnsupportedValues() {
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(Map.of(1, "x")));
    }

    @Test
    void escapesStrings() throws Exception {
        String text = "quote\" backslash\\ newline\n tab\t nul\u0000 bell\u0007 é ✓ \uD83D\uDE00 lone\uD800";

        String json = JsonWriter.write(text);

        assertEquals("\"quote\\\" backslash\\\\ newline\\n tab\\t nul\\u0000 bell\\u0007 é ✓ \uD83D\uDE00 lone\\ud800\"",
                json);
        assertEquals(text, Json.parse(json));
    }

    @Test
    void writesLongNumberArraysOverSeveralLines() {
        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            many.add((double) i);
        }

        assertEquals(19, JsonWriter.write(many).lines().count());
    }

    @Test
    void roundTripsThroughTheParser() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("b", Arrays.asList(1.5, "two", null, false, Map.of("deep", List.of(List.of(1.0, 2.0)))));
        root.put("a", Map.of());
        root.put("", "empty key");

        Object back = Json.parse(JsonWriter.write(root));

        assertEquals(root, back);
        assertEquals(List.of("b", "a", ""), List.copyOf(((Map<?, ?>) back).keySet()));
    }
}
