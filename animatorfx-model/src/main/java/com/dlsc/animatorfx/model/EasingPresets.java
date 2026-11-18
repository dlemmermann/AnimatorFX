package com.dlsc.animatorfx.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holder for {@link Easing#presets()}: the CSS keywords followed by the easings.net families.
 */
final class EasingPresets {

    static final Map<String, Easing> ALL = create();

    private EasingPresets() {
    }

    private static Map<String, Easing> create() {
        Map<String, Easing> m = new LinkedHashMap<>();
        put(m, "Ease", 0.25, 0.1, 0.25, 1);
        put(m, "Ease In", 0.42, 0, 1, 1);
        put(m, "Ease Out", 0, 0, 0.58, 1);
        put(m, "Ease In Out", 0.42, 0, 0.58, 1);
        put(m, "Sine In", 0.12, 0, 0.39, 0);
        put(m, "Sine Out", 0.61, 1, 0.88, 1);
        put(m, "Sine In Out", 0.37, 0, 0.63, 1);
        put(m, "Quad In", 0.11, 0, 0.5, 0);
        put(m, "Quad Out", 0.5, 1, 0.89, 1);
        put(m, "Quad In Out", 0.45, 0, 0.55, 1);
        put(m, "Cubic In", 0.32, 0, 0.67, 0);
        put(m, "Cubic Out", 0.33, 1, 0.68, 1);
        put(m, "Cubic In Out", 0.65, 0, 0.35, 1);
        put(m, "Quart In", 0.5, 0, 0.75, 0);
        put(m, "Quart Out", 0.25, 1, 0.5, 1);
        put(m, "Quart In Out", 0.76, 0, 0.24, 1);
        put(m, "Expo In", 0.7, 0, 0.84, 0);
        put(m, "Expo Out", 0.16, 1, 0.3, 1);
        put(m, "Expo In Out", 0.87, 0, 0.13, 1);
        put(m, "Back In", 0.36, 0, 0.66, -0.56);
        put(m, "Back Out", 0.34, 1.56, 0.64, 1);
        put(m, "Back In Out", 0.68, -0.6, 0.32, 1.6);
        return Collections.unmodifiableMap(m);
    }

    private static void put(Map<String, Easing> map, String name, double x1, double y1, double x2, double y2) {
        map.put(name, new Easing.CubicBezier(x1, y1, x2, y2));
    }
}
