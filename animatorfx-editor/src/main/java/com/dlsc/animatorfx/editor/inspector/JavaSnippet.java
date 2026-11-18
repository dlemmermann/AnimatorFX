package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.SlotLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Generates the Java code that loads a document with the runtime library, binds its slots and
 * plays it: a small class with one factory method, ready to paste into an application.
 */
public final class JavaSnippet {

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
        List<String> names = parameterNames(slots);
        names.forEach(name -> parameters.add("Node " + name));
        out.append("    public static AnimationView create(").append(parameters).append(") {\n");
        out.append("        AnimationFile file;\n");
        out.append("        try {\n");
        out.append("            file = AnimationIO.read(").append(className).append(".class.getResource(")
                .append(literal(fileName)).append("));\n");
        out.append("        } catch (IOException e) {\n");
        out.append("            throw new UncheckedIOException(e);\n");
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

    /** The editor only accepts Java identifiers as slot names; anything else gets a neutral name. */
    private static List<String> parameterNames(List<String> slots) {
        List<String> names = new ArrayList<>();
        for (String slot : slots) {
            boolean usable = !slot.isEmpty() && Character.isJavaIdentifierStart(slot.charAt(0))
                    && slot.chars().allMatch(Character::isJavaIdentifierPart)
                    && !slot.equals("file") && !slot.equals("view") && !names.contains(slot);
            names.add(usable ? slot : "slot" + (names.size() + 1));
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
