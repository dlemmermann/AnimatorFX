package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;

import java.util.Optional;
import java.util.Set;

/**
 * Names for new layers and slots.
 */
public final class LayerNames {

    private static final Set<String> RESERVED = Set.of("abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
            "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "true", "false", "null", "_");

    private LayerNames() {
    }

    /**
     * The prefix followed by the next free number: one more than the highest number that a layer
     * named "{@code <prefix> <number>}" uses anywhere in the document.
     */
    public static String next(Animation animation, String prefix) {
        int highest = 0;
        for (Layer layer : Layers.flatten(animation)) {
            String name = layer.name();
            if (name.startsWith(prefix + " ")) {
                String rest = name.substring(prefix.length() + 1);
                if (!rest.isEmpty() && rest.length() < 9 && rest.chars().allMatch(c -> c >= '0' && c <= '9')) {
                    highest = Math.max(highest, Integer.parseInt(rest));
                }
            }
        }
        return prefix + " " + (highest + 1);
    }

    /**
     * The first of "slot1", "slot2", … that no slot of the document uses.
     */
    public static String nextSlotName(Animation animation) {
        return Commands.nextSlotName(Commands.slotNames(animation));
    }

    /**
     * Why the name cannot be used for a new slot, as a sentence for the user; empty for a name
     * that is a Java identifier and not in use.
     */
    public static Optional<String> slotNameError(Animation animation, String name) {
        if (name == null || name.isEmpty()) {
            return Optional.of("Enter a name.");
        }
        boolean identifier = Character.isJavaIdentifierStart(name.charAt(0))
                && name.chars().skip(1).allMatch(Character::isJavaIdentifierPart);
        if (!identifier) {
            return Optional.of("Not a Java identifier: use letters, digits and _ only, not starting with a digit.");
        }
        if (RESERVED.contains(name)) {
            return Optional.of("\u201c" + name + "\u201d is a Java keyword.");
        }
        if (Commands.slotNames(animation).contains(name)) {
            return Optional.of("A slot named \u201c" + name + "\u201d already exists.");
        }
        return Optional.empty();
    }
}
