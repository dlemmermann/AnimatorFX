package com.dlsc.animatorfx.importer;

import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The presentation properties of one element: its attributes overridden by its inline
 * {@code style}, with the inheritable ones falling back to the parent element.
 */
final class SvgStyle {

    private static final Set<String> INHERITED = Set.of("fill", "fill-opacity", "fill-rule", "clip-rule", "stroke",
            "stroke-width", "stroke-opacity", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit",
            "stroke-dasharray", "stroke-dashoffset", "color", "visibility", "font-family", "font-size",
            "font-weight", "font-style", "text-anchor");

    private static final Set<String> NOT_INHERITED = Set.of("opacity", "display", "clip-path", "mask", "filter",
            "stop-color", "stop-opacity");

    private final Map<String, String> own = new HashMap<>();
    private final SvgStyle parent;

    private SvgStyle(SvgStyle parent) {
        this.parent = parent;
    }

    /**
     * @param parent the style of the element to inherit from, or {@code null}
     */
    static SvgStyle of(Element element, SvgStyle parent) {
        SvgStyle style = new SvgStyle(parent);
        for (String name : INHERITED) {
            style.put(name, element.getAttribute(name));
        }
        for (String name : NOT_INHERITED) {
            style.put(name, element.getAttribute(name));
        }
        for (String declaration : element.getAttribute("style").split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0) {
                String name = declaration.substring(0, colon).trim();
                String value = declaration.substring(colon + 1).trim();
                int important = value.indexOf("!important");
                style.put(name, important < 0 ? value : value.substring(0, important).trim());
            }
        }
        return style;
    }

    private void put(String name, String value) {
        if (!value.isEmpty() && (INHERITED.contains(name) || NOT_INHERITED.contains(name))) {
            own.put(name, value);
        }
    }

    /**
     * The value of a property, or {@code null} where the property's default applies.
     */
    String get(String name) {
        String value = own.get(name);
        boolean inherit = value == null ? INHERITED.contains(name) : value.equals("inherit");
        if (inherit) {
            return parent == null ? null : parent.get(name);
        }
        return value;
    }

    String get(String name, String fallback) {
        String value = get(name);
        return value == null ? fallback : value;
    }
}
