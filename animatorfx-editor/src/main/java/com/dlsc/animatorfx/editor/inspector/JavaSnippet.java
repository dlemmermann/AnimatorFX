package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.SlotLayer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Generates the Java code that loads a document with the runtime library, binds its slots and
 * plays it: a small class with one factory method, ready to paste into an application.
 */
public final class JavaSnippet {

    /** Not a likely slot name, and checked against the slot names all the same. */
    private static final String CATCH_VARIABLE = "readFailure";

    private JavaSnippet() {
    }

    /**
     * @param fileNameOrNull the name of the saved file; {@code null} derives one from the document name
     */
    public static String generate(Animation animation, String fileNameOrNull) {
        String className = className(animation.name());
        String fileName = fileNameOrNull != null ? fileNameOrNull : fileName(animation.name());
        List<String> slots = Layers.slots(animation).stream().map(SlotLayer::slotName).toList();

        StringBuilder out = new StringBuilder();
        out.append("import com.dlsc.animatorfx.model.io.AnimationFile;\n");
        out.append("import com.dlsc.animatorfx.model.io.AnimationIO;\n");
        out.append("import com.dlsc.animatorfx.player.AnimationView;\n");
        out.append("import java.io.IOException;\n");
        out.append("import java.io.UncheckedIOException;\n");
        if (!slots.isEmpty()) {
            out.append("import javafx.scene.Node;\n");
        }
        out.append('\n');
        out.append("public final class ").append(className).append(" {\n\n");
        out.append("    private ").append(className).append("() {\n    }\n\n");

        out.append("    /**\n");
        out.append("     * Loads ").append(fileName).append(", which has to be on the class path next to this class");
        if (slots.isEmpty()) {
            out.append(",\n     * and plays it.\n");
        } else {
            out.append(",\n     * puts the nodes into its slots and plays it.\n");
        }
        out.append("     */\n");
        StringJoiner parameters = new StringJoiner(", ");
        List<String> names = parameterNames(slots, className);
        names.forEach(name -> parameters.add("Node " + name));
        out.append("    public static AnimationView create(").append(parameters).append(") {\n");
        out.append("        AnimationFile file;\n");
        out.append("        try {\n");
        out.append("            file = AnimationIO.read(").append(className).append(".class.getResource(")
                .append(literal(fileName)).append("));\n");
        out.append("        } catch (IOException ").append(CATCH_VARIABLE).append(") {\n");
        out.append("            throw new UncheckedIOException(").append(CATCH_VARIABLE).append(");\n");
        out.append("        }\n");
        out.append("        AnimationView view = new AnimationView(file);\n");

        if (!slots.isEmpty()) {
            out.append('\n');
            out.append("        // Slots: each node becomes part of the view and follows its slot layer.\n");
            for (int i = 0; i < slots.size(); i++) {
                out.append("        view.bind(").append(literal(slots.get(i))).append(", ").append(names.get(i))
                        .append(");\n");
            }
            StringJoiner ids = new StringJoiner(", ");
            slots.forEach(slot -> ids.add("#" + slot));
            out.append("        // To animate nodes where they are in your scene instead, give them the ids\n");
            out.append("        // ").append(ids).append(" and call: view.autoBind(root);\n");
        }

        out.append('\n');
        List<Marker> markers = animation.markers();
        if (markers.isEmpty()) {
            out.append("        view.play();\n");
        } else {
            out.append("        // Markers: playMarker plays one named range; view.play() plays everything.\n");
            for (int i = 0; i < markers.size(); i++) {
                Marker marker = markers.get(i);
                out.append(i == 0 ? "        " : "        // ").append("view.playMarker(")
                        .append(literal(marker.name())).append(");  // ").append(seconds(marker.start()))
                        .append(" - ").append(seconds(marker.end())).append('\n');
            }
        }
        out.append("        return view;\n");
        out.append("    }\n");
        out.append("}\n");
        return out.toString();
    }

    /** A class name from the document name: its words in Pascal case, ending in "Animation". */
    public static String className(String documentName) {
        StringBuilder name = new StringBuilder();
        for (String word : documentName.split("[^\\p{Alnum}]+")) {
            if (!word.isEmpty()) {
                name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        if (name.isEmpty()) {
            return "MyAnimation";
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return "Animation" + name;
        }
        return name.toString().endsWith("Animation") ? name.toString() : name + "Animation";
    }

    private static String fileName(String documentName) {
        String base = documentName.strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}]+", "-")
                .replaceAll("^-|-$", "");
        return (base.isEmpty() ? "animation" : base) + ".afx";
    }

    /**
     * The identifiers the snippet uses itself. A slot of such a name gets another parameter name,
     * or the parameter would shadow a class the code needs, or clash with a local variable.
     */
    private static final Set<String> OWN_NAMES = Set.of("file", "view", CATCH_VARIABLE, "AnimationFile", "AnimationIO",
            "AnimationView", "IOException", "UncheckedIOException", "Node", "String", "Object", "Class");

    private static final Set<String> KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
            "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "true", "false", "null", "_", "var", "yield", "record", "sealed", "permits");

    /**
     * One parameter name per slot: the slot name where that is a usable Java identifier that
     * clashes with nothing in the snippet, else {@code slot1}, {@code slot2}, …
     */
    static List<String> parameterNames(List<String> slots, String className) {
        Set<String> taken = new HashSet<>(OWN_NAMES);
        taken.add(className);
        List<String> names = new ArrayList<>();
        // the names that slots keep are settled first, so that no substitute takes one of them
        for (String slot : slots) {
            boolean usable = !slot.isEmpty() && Character.isJavaIdentifierStart(slot.charAt(0))
                    && slot.chars().allMatch(Character::isJavaIdentifierPart) && !KEYWORDS.contains(slot)
                    && taken.add(slot);
            names.add(usable ? slot : null);
        }
        int number = 1;
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i) == null) {
                String substitute;
                do {
                    substitute = "slot" + number++;
                } while (!taken.add(substitute));
                names.set(i, substitute);
            }
        }
        return names;
    }

    private static String seconds(double time) {
        return PropertyUnits.number(time, 2) + " s";
    }

    private static String literal(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
