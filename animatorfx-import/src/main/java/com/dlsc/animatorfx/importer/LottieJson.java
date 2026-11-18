package com.dlsc.animatorfx.importer;

import java.util.List;
import java.util.Map;

/**
 * Lenient access to a parsed JSON tree: a value of an unexpected type reads as missing.
 */
final class LottieJson {

    private LottieJson() {
    }

    /** The member of an object, or {@code null} if there is none or {@code object} is no object. */
    static Object get(Object object, String key) {
        return object instanceof Map<?, ?> map ? map.get(key) : null;
    }

    static boolean has(Object object, String key) {
        return object instanceof Map<?, ?> map && map.containsKey(key);
    }

    /** The elements of an array; empty for anything else. */
    @SuppressWarnings("unchecked")
    static List<Object> list(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    static List<Object> list(Object object, String key) {
        return list(get(object, key));
    }

    /** A number, also when wrapped in an array, as Lottie does with scalars. */
    static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        return fallback;
    }

    static double number(Object object, String key, double fallback) {
        return number(get(object, key), fallback);
    }

    static String string(Object object, String key, String fallback) {
        return get(object, key) instanceof String text ? text : fallback;
    }

    /** {@code true} or a number other than 0. */
    static boolean flag(Object object, String key) {
        return number(get(object, key), 0) != 0;
    }

    /**
     * The numbers of an array, or a single number as an array of one.
     *
     * @return {@code null} if the value is neither
     */
    static double[] numbers(Object value) {
        if (value instanceof Number number) {
            return new double[]{number.doubleValue()};
        }
        if (!(value instanceof List<?> list)) {
            return null;
        }
        double[] result = new double[list.size()];
        for (int i = 0; i < result.length; i++) {
            if (!(list.get(i) instanceof Number number)) {
                return null;
            }
            result[i] = number.doubleValue();
        }
        return result;
    }
}
