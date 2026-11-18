package com.dlsc.animatorfx.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects one line per kind of problem, in the order the kinds were first met.
 */
final class Warnings {

    private record Unit(String singular, String plural) {
    }

    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final Map<String, Unit> units = new LinkedHashMap<>();

    /** A problem whose line reads {@code message}, followed by "(N occurrences)" from the second time on. */
    void add(String message) {
        counts.merge(message, 1, Integer::sum);
    }

    /** A problem that is reported once, without a count. */
    void once(String message) {
        counts.putIfAbsent(message, 1);
    }

    /** A problem whose line always carries its count, for example "(3 elements ignored)". */
    void add(String message, String singular, String plural) {
        units.putIfAbsent(message, new Unit(singular, plural));
        add(message);
    }

    List<String> lines() {
        List<String> lines = new ArrayList<>();
        counts.forEach((message, count) -> {
            Unit unit = units.get(message);
            if (unit != null) {
                lines.add(message + " (" + count + " " + (count == 1 ? unit.singular() : unit.plural()) + ")");
            } else {
                lines.add(count == 1 ? message : message + " (" + count + " occurrences)");
            }
        });
        return lines;
    }
}
