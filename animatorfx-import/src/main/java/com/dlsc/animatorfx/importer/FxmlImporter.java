package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.io.AnimationFile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the nodes of an FXML file that have an {@code fx:id} into slot layers.
 * <p>
 * The file is loaded without its controller. To make that possible for ordinary files, the
 * {@code fx:controller} attribute, event handler attributes that name a controller method
 * ({@code onAction="#save"}) and {@code fx:script} elements are removed from the text before it
 * is loaded. A file that binds to its controller ({@code ${controller.title}}) or needs classes
 * that are not available cannot be loaded and is reported as an {@link IOException}.
 * {@code %key} resource references resolve to the key itself; locations relative to the file
 * ({@code @style.css}) resolve as usual.
 * <p>
 * FXML is loaded as trusted input, like code: loading it instantiates the classes it names, runs
 * their constructors and setters and follows {@code fx:include}. Import only files you would run.
 */
public final class FxmlImporter {

    private static final double DURATION = 3;
    private static final double FALLBACK_WIDTH = 800;
    private static final double FALLBACK_HEIGHT = 600;
    /** Pixels; a node wider or higher than this gets no preview picture. */
    private static final double MAX_PREVIEW_SIZE = 4096;

    private static final Pattern CONTROLLER = Pattern.compile("\\s+fx:controller\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");
    private static final Pattern HANDLER = Pattern.compile("\\s+on[A-Z]\\w*\\s*=\\s*(\"#[^\"]*\"|'#[^']*')");
    private static final Pattern SCRIPT = Pattern.compile("<fx:script\\b[^>]*?(/>|>.*?</fx:script\\s*>)",
            Pattern.DOTALL);
    private static final Pattern CONTROLLER_EXPRESSION = Pattern.compile("\\$\\{[^}]*\\bcontroller\\b[^}]*}");

    /**
     * The picture of a node, shown by the editor inside the slot's rectangle.
     *
     * @param pngSnapshot the node as a PNG image
     */
    public record SlotPreview(String slotName, byte[] pngSnapshot) {

        public SlotPreview {
            Objects.requireNonNull(slotName, "slotName");
            Objects.requireNonNull(pngSnapshot, "pngSnapshot");
        }
    }

    /**
     * @param file     a document of the size of the laid-out root with one slot layer per node
     * @param previews the pictures of the nodes that could be drawn
     * @param warnings one line for everything that was left out
     */
    public record Result(AnimationFile file, List<SlotPreview> previews, List<String> warnings) {

        public Result {
            Objects.requireNonNull(file, "file");
            previews = List.copyOf(previews);
            warnings = List.copyOf(warnings);
        }
    }

    private FxmlImporter() {
    }

    /**
     * Loads the FXML, lays its root out at its preferred size (800 × 600 if it has none) and
     * makes a slot for every node with an {@code fx:id} that is part of the scene graph. The
     * slots are named after the {@code fx:id}, lie flat in the document at the layout bounds of
     * their nodes (in the coordinates of the root), and are ordered as the nodes are painted, so
     * a node comes after its ancestors. The document is 3 seconds long at 60 frames per second
     * and named after the file.
     *
     * @throws IOException           if the file cannot be read or loaded
     * @throws IllegalStateException if not called on the JavaFX application thread
     */
    public static Result read(URL fxml) throws IOException {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("FXML must be imported on the JavaFX application thread");
        }
        Warnings warnings = new Warnings();
        String text;
        try (InputStream in = fxml.openStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        FXMLLoader loader = new FXMLLoader(fxml, new KeysAsValues());
        Object loaded = load(loader, withoutController(text, warnings));
        if (!(loaded instanceof Node root)) {
            throw new IOException("The root of the FXML is not a node but "
                    + (loaded == null ? "nothing" : loaded.getClass().getName()));
        }

        Parent sceneRoot = root instanceof Parent parent ? parent : new Group(root);
        Scene scene = new Scene(sceneRoot);
        sceneRoot.applyCss();
        if (sceneRoot.isResizable()) {
            double width = sceneRoot.prefWidth(-1);
            double height = sceneRoot.prefHeight(width);
            boolean usable = width > 0 && height > 0 && Double.isFinite(width) && Double.isFinite(height);
            sceneRoot.resize(usable ? width : FALLBACK_WIDTH, usable ? height : FALLBACK_HEIGHT);
        }
        sceneRoot.applyCss();
        sceneRoot.layout();

        Bounds layout = root.getLayoutBounds();
        Point2D origin = root.localToScene(layout.getMinX(), layout.getMinY());
        boolean sized = layout.getWidth() > 0 && layout.getHeight() > 0;
        String name = name(fxml);
        Animation animation = Animation.create(name, sized ? layout.getWidth() : FALLBACK_WIDTH,
                sized ? layout.getHeight() : FALLBACK_HEIGHT, DURATION);

        List<Layer> layers = new ArrayList<>();
        List<SlotPreview> previews = new ArrayList<>();
        for (Map.Entry<Node, String> entry : slotNodes(loader.getNamespace(), sceneRoot, scene, warnings)) {
            Node node = entry.getKey();
            String slotName = entry.getValue();
            // The layout bounds are what a host lays out and resizes; the visual bounds can be
            // larger (a button's background reaches below it, effects spill over).
            Bounds bounds = node.localToScene(node.getLayoutBounds());
            Transform transform = Transform.at(bounds.getMinX() - origin.getX(), bounds.getMinY() - origin.getY());
            layers.add(new SlotLayer(LayerBase.create(slotName, DURATION).withTransform(transform), slotName,
                    bounds.getWidth(), bounds.getHeight(), transform));
            if (bounds.getWidth() > MAX_PREVIEW_SIZE || bounds.getHeight() > MAX_PREVIEW_SIZE) {
                noPreview(warnings);
            } else if (bounds.getWidth() >= 1 && bounds.getHeight() >= 1) {
                SnapshotParameters parameters = new SnapshotParameters();
                parameters.setFill(Color.TRANSPARENT);
                Bounds inParent = node.localToParent(node.getLayoutBounds());
                parameters.setViewport(new Rectangle2D(inParent.getMinX(), inParent.getMinY(), inParent.getWidth(),
                        inParent.getHeight()));
                try {
                    WritableImage image = node.snapshot(parameters, null);
                    previews.add(new SlotPreview(slotName, PngWriter.encode(image)));
                } catch (RuntimeException e) {
                    noPreview(warnings);
                }
            }
        }
        Animation checked = DocumentCheck.repair(animation.withLayers(layers), warnings);
        return new Result(new AnimationFile(checked, Map.of()), previews, warnings.lines());
    }

    private static void noPreview(Warnings warnings) {
        warnings.add("Nodes that are too large to be drawn have no preview", "slot", "slots");
    }

    /** Removes what only works with the controller. */
    private static String withoutController(String text, Warnings warnings) throws IOException {
        Matcher controller = CONTROLLER.matcher(text);
        String controllerName = null;
        if (controller.find()) {
            controllerName = controller.group(2) != null ? controller.group(2) : controller.group(3);
            warnings.add("The controller " + controllerName + " was left out: the FXML is loaded without it");
        }
        String result = controller.replaceAll("");
        Matcher expression = CONTROLLER_EXPRESSION.matcher(result);
        if (expression.find()) {
            throw new IOException("The FXML cannot be loaded without its controller"
                    + (controllerName == null ? "" : " " + controllerName) + ": it binds to " + expression.group());
        }
        Matcher handler = HANDLER.matcher(result);
        while (handler.find()) {
            warnings.add("Event handlers were left out", "handler", "handlers");
        }
        result = handler.replaceAll("");
        Matcher script = SCRIPT.matcher(result);
        while (script.find()) {
            warnings.add("Scripts were left out", "script", "scripts");
        }
        return script.replaceAll("");
    }

    private static Object load(FXMLLoader loader, String text) throws IOException {
        try {
            return loader.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof ClassNotFoundException missing) {
                    throw new IOException("The FXML cannot be loaded: the class " + missing.getMessage()
                            + " is not available", e);
                }
            }
            String message = e.getMessage() == null || e.getMessage().isBlank() ? e.toString() : e.getMessage().trim();
            throw new IOException("The FXML cannot be loaded: " + message, e);
        }
    }

    /**
     * The nodes that become slots with their names, in paint order: parents before children,
     * siblings bottom to top.
     */
    private static List<Map.Entry<Node, String>> slotNodes(Map<String, Object> namespace, Parent sceneRoot,
                                                           Scene scene, Warnings warnings) {
        Map<Node, String> names = new IdentityHashMap<>();
        namespace.forEach((key, value) -> {
            if (!(value instanceof Node node)) {
                return;
            }
            if (node.getScene() != scene) {
                warnings.add("Nodes with an fx:id that are not part of the scene graph are skipped", "node", "nodes");
            } else if (!isIdentifier(key)) {
                warnings.add("Nodes whose fx:id is not a valid identifier are skipped", "node", "nodes");
            } else {
                names.putIfAbsent(node, key);
            }
        });
        List<Map.Entry<Node, String>> ordered = new ArrayList<>();
        collect(sceneRoot, names, ordered);
        return ordered;
    }

    private static boolean isIdentifier(String name) {
        return !name.isEmpty() && Character.isJavaIdentifierStart(name.charAt(0))
                && name.chars().skip(1).allMatch(Character::isJavaIdentifierPart);
    }

    private static void collect(Node node, Map<Node, String> names, List<Map.Entry<Node, String>> out) {
        String name = names.get(node);
        if (name != null) {
            out.add(Map.entry(node, name));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, names, out);
            }
        }
    }

    private static String name(URL fxml) {
        String path = URLDecoder.decode(fxml.getPath(), StandardCharsets.UTF_8);
        String file = path.substring(path.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        String name = dot > 0 ? file.substring(0, dot) : file;
        return name.isEmpty() ? "FXML" : name;
    }

    /** Resolves every {@code %key} to the key itself. */
    private static final class KeysAsValues extends ResourceBundle {

        @Override
        protected Object handleGetObject(String key) {
            return key;
        }

        @Override
        public boolean containsKey(String key) {
            return true;
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.emptyEnumeration();
        }
    }
}
