package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.audio.AudioImport;
import com.dlsc.animatorfx.editor.canvas.tools.NewLayers;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Vec2;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * The Assets tab: the images and fonts of the document, each with a thumbnail (or an "Aa" sample
 * in the font), its name, kind, size and the number of layers that use it. Add… takes PNG, JPEG,
 * TTF and OTF files; an image asset can be placed on the canvas as a new image layer; an asset
 * can be removed once no layer uses it. Adding, placing and removing are undo steps.
 */
public final class AssetsView extends VBox {

    public static final String ADD_ID = "assets-add";
    public static final String MESSAGE_ID = "assets-message";
    public static final String LIST_ID = "assets-list";

    private static final double THUMBNAIL = 36;

    private final ObjectProperty<EditorDocument> document = new SimpleObjectProperty<>(this, "document");
    private final VBox list = new VBox();
    private final Label message = new Label();
    private final Label empty = new Label("No assets yet. Add images or fonts, or drop image files on the canvas.");
    private final Map<String, Image> images = new HashMap<>();
    private final Map<String, Font> fonts = new HashMap<>();
    private final Map<String, Label> details = new HashMap<>();
    private List<Asset> listed;
    private Function<Window, List<Path>> filePicker = AssetsView::chooseFiles;
    private final InvalidationListener update = obs -> update();

    public AssetsView() {
        getStyleClass().add("assets-view");
        Button add = new Button("Add…", new FontIcon(Material2AL.ADD));
        add.setId(ADD_ID);
        add.getStyleClass().add("section-button");
        add.setOnAction(e -> {
            Window owner = getScene() == null ? null : getScene().getWindow();
            addFiles(filePicker.apply(owner));
        });
        Label title = new Label("ASSETS");
        title.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(title, spacer, add);
        header.getStyleClass().add("panel-header");
        header.setAlignment(Pos.CENTER_LEFT);

        message.setId(MESSAGE_ID);
        message.getStyleClass().add("inline-message");
        message.setWrapText(true);
        show(message, false);
        empty.getStyleClass().add("hint");
        empty.setWrapText(true);
        list.setId(LIST_ID);
        list.getStyleClass().add("asset-list");
        VBox content = new VBox(message, empty, list);
        content.getStyleClass().add("panel-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("inspector-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(header, scroll);

        document.addListener((obs, oldDoc, newDoc) -> {
            if (oldDoc != null) {
                oldDoc.animationProperty().removeListener(update);
            }
            if (newDoc != null) {
                newDoc.animationProperty().addListener(update);
            }
            listed = null;
            images.clear();
            fonts.clear();
            setMessage(null);
            update();
        });
        update();
    }

    /**
     * The document whose assets the view lists.
     *
     * @return the document property
     */
    public final ObjectProperty<EditorDocument> documentProperty() {
        return document;
    }

    public final EditorDocument getDocument() {
        return document.get();
    }

    public final void setDocument(EditorDocument value) {
        document.set(value);
    }

    /** Replaces the file dialog of the Add button; it returns the chosen files, none when cancelled. */
    public void setFilePicker(Function<Window, List<Path>> picker) {
        filePicker = Objects.requireNonNull(picker, "picker");
    }

    /** The inline message: why an asset could not be added or removed; {@code null} when there is none. */
    public String getMessage() {
        return message.isVisible() ? message.getText() : null;
    }

    private void setMessage(String text) {
        message.setText(text == null ? "" : text);
        show(message, text != null);
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static List<Path> chooseFiles(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add Assets");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images, fonts and sounds", "*.png", "*.jpg", "*.jpeg", "*.ttf", "*.otf",
                        "*.mp3", "*.wav", "*.aif", "*.aiff", "*.m4a", "*.aac"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("Fonts", "*.ttf", "*.otf"),
                new FileChooser.ExtensionFilter("Sounds", "*.mp3", "*.wav", "*.aif", "*.aiff", "*.m4a", "*.aac"));
        List<File> files = chooser.showOpenMultipleDialog(owner);
        return files == null ? List.of() : files.stream().map(File::toPath).toList();
    }

    // ------------------------------------------------------------------ actions

    /** Adds image and font files as assets, one undo step each; files that cannot be read are named in the message. */
    public void addFiles(List<Path> files) {
        EditorDocument doc = getDocument();
        if (doc == null) {
            return;
        }
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (AudioImport.isAudio(file)) {
                addSound(doc, file);
                continue;
            }
            Asset.Kind kind = switch (extension) {
                case "png", "jpg", "jpeg" -> Asset.Kind.IMAGE;
                case "ttf", "otf" -> Asset.Kind.FONT;
                default -> null;
            };
            if (kind == null) {
                problems.add(name + " is not an image (PNG, JPEG), a font (TTF, OTF) or a sound (MP3, WAV, AIFF, AAC).");
                continue;
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (IOException e) {
                problems.add("Cannot read " + name + ": " + e.getMessage());
                continue;
            }
            boolean readable = kind == Asset.Kind.IMAGE ? !new Image(new ByteArrayInputStream(bytes)).isError()
                    : Font.loadFont(new ByteArrayInputStream(bytes), 12) != null;
            if (!readable) {
                problems.add(name + " cannot be read as " + (kind == Asset.Kind.IMAGE ? "an image." : "a font."));
                continue;
            }
            String id = UUID.randomUUID().toString();
            doc.addAsset(new Asset(id, kind, "assets/" + id + "." + extension, name), bytes);
        }
        setMessage(problems.isEmpty() ? null : String.join("\n", problems));
    }

    /** A sound is checked by the media stack, which may answer a moment later. */
    private void addSound(EditorDocument doc, Path file) {
        AudioImport.read(file, text -> {
            setMessage(text);
            return () -> setMessage(null);
        }).whenComplete((sound, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                setMessage("Could not add " + file.getFileName() + ": "
                        + (cause instanceof AudioImport.Refused ? cause.getMessage() : "it cannot be read."));
            } else if (getDocument() == doc) {
                AudioImport.addAsset(doc, sound);
            }
        });
    }

    /** Adds an audio layer for an audio asset at the playhead, on top, and selects it. */
    public Optional<String> placeAudio(String assetId) {
        EditorDocument doc = getDocument();
        return doc == null ? Optional.empty() : AudioImport.place(doc, assetId);
    }

    /** Removes an asset as one undo step; refuses, with a message, while layers or cues use it. */
    public void remove(String assetId) {
        EditorDocument doc = getDocument();
        Asset asset = doc == null ? null
                : doc.getAnimation().assets().stream().filter(a -> a.id().equals(assetId)).findFirst().orElse(null);
        if (asset == null) {
            return;
        }
        int users = usage(doc.getAnimation(), assetId);
        if (users > 0) {
            boolean cues = doc.getAnimation().cues().stream().anyMatch(cue -> cue.assetId().equals(assetId));
            String what = users == 1 ? (cues ? " cue" : " layer") : (cues ? " layers and cues" : " layers");
            setMessage("“" + asset.name() + "” is used by " + users + what
                    + ". Delete or reassign " + (users == 1 ? "it" : "them") + " first.");
            return;
        }
        setMessage(null);
        doc.execute(Commands.setDocument("Remove Asset",
                a -> a.withAssets(a.assets().stream().filter(other -> !other.id().equals(assetId)).toList())));
    }

    /** Adds an image layer for an image asset in the middle of the artboard, on top, and selects it. */
    public Optional<String> place(String assetId) {
        EditorDocument doc = getDocument();
        Image image = doc == null ? null : image(doc, assetId);
        if (image == null || image.isError()) {
            return Optional.empty();
        }
        Animation animation = doc.getAnimation();
        double fit = Math.min(1, Math.min(animation.width() / image.getWidth(), animation.height() / image.getHeight()));
        ImageLayer layer = NewLayers.image(animation, assetId, new Vec2(animation.width() / 2, animation.height() / 2),
                new Vec2(image.getWidth() * fit, image.getHeight() * fit));
        doc.execute(Commands.batch("Add Image", List.of(Commands.addLayer(null, animation.layers().size(), layer))));
        doc.getSelection().layerIds().setAll(layer.id());
        return Optional.of(layer.id());
    }

    /** How many layers and sound cues use the asset. */
    public static int usage(Animation animation, String assetId) {
        int count = 0;
        for (Layer layer : Layers.flatten(animation)) {
            if (layer instanceof ImageLayer image && image.assetId().equals(assetId)
                    || layer instanceof TextLayer text && assetId.equals(text.fontAssetId())
                    || layer instanceof AudioLayer audio && audio.assetId().equals(assetId)) {
                count++;
            }
        }
        for (SoundCue cue : animation.cues()) {
            if (cue.assetId().equals(assetId)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ list

    private Image image(EditorDocument doc, String assetId) {
        return images.computeIfAbsent(assetId, id -> {
            byte[] bytes = doc.getFileContent().assets().get(id);
            return bytes == null ? null : new Image(new ByteArrayInputStream(bytes));
        });
    }

    private void update() {
        EditorDocument doc = getDocument();
        List<Asset> assets = doc == null ? List.of() : doc.getAnimation().assets();
        if (assets != listed) {
            listed = assets;
            details.clear();
            List<Node> rows = new ArrayList<>();
            for (Asset asset : assets) {
                rows.add(row(doc, asset));
            }
            list.getChildren().setAll(rows);
            show(empty, assets.isEmpty());
        }
        if (doc != null) {
            // the usage counts change with the layers; the rows stay
            for (Asset asset : assets) {
                Label detail = details.get(asset.id());
                if (detail != null) {
                    detail.setText(detail(doc, asset));
                }
            }
        }
    }

    private static String detail(EditorDocument doc, Asset asset) {
        byte[] bytes = doc.getFileContent().assets().get(asset.id());
        int users = usage(doc.getAnimation(), asset.id());
        String size = bytes == null ? "missing" : String.format(Locale.ROOT, "%.1f KB", bytes.length / 1024.0);
        String kind = switch (asset.kind()) {
            case IMAGE -> "Image";
            case FONT -> "Font";
            case AUDIO -> "Audio";
        };
        return kind + " · " + size + " · "
                + (users == 0 ? "unused" : "used " + users + "×");
    }

    private Node row(EditorDocument doc, Asset asset) {
        StackPane thumbnail = new StackPane();
        thumbnail.getStyleClass().add("asset-thumbnail");
        thumbnail.setMinSize(THUMBNAIL, THUMBNAIL);
        thumbnail.setMaxSize(THUMBNAIL, THUMBNAIL);
        if (asset.kind() == Asset.Kind.IMAGE) {
            Image image = image(doc, asset.id());
            if (image != null && !image.isError()) {
                ImageView view = new ImageView(image);
                view.setPreserveRatio(true);
                view.setFitWidth(THUMBNAIL - 4);
                view.setFitHeight(THUMBNAIL - 4);
                thumbnail.getChildren().add(view);
            }
        } else if (asset.kind() == Asset.Kind.FONT) {
            Label sample = new Label("Aa");
            sample.getStyleClass().add("font-sample");
            Font font = fonts.computeIfAbsent(asset.id(), id -> {
                byte[] bytes = doc.getFileContent().assets().get(id);
                return bytes == null ? null : Font.loadFont(new ByteArrayInputStream(bytes), 18);
            });
            if (font != null) {
                // the sample is in the asset's own font, which no stylesheet can name
                sample.setFont(font);
            }
            thumbnail.getChildren().add(sample);
        }

        Label name = new Label(asset.name());
        name.getStyleClass().add("asset-name");
        name.setMinWidth(0);
        Label detail = new Label(detail(doc, asset));
        detail.getStyleClass().addAll("asset-detail", "mono");
        detail.setMinWidth(0);
        details.put(asset.id(), detail);
        VBox text = new VBox(name, detail);
        text.setAlignment(Pos.CENTER_LEFT);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(thumbnail, text);
        row.getStyleClass().add("asset-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setUserData(asset.id());
        if (asset.kind() == Asset.Kind.IMAGE) {
            Button place = iconButton(Material2AL.ADD_PHOTO_ALTERNATE, "Place on the canvas as a new image layer");
            place.getStyleClass().add("asset-place");
            place.setOnAction(e -> place(asset.id()));
            row.getChildren().add(place);
        }
        Button remove = iconButton(Material2AL.DELETE_OUTLINE, "Remove the asset");
        remove.getStyleClass().add("asset-remove");
        remove.setOnAction(e -> remove(asset.id()));
        row.getChildren().add(remove);
        return row;
    }

    private static Button iconButton(org.kordamp.ikonli.Ikon icon, String tooltip) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().setAll("icon-button", "section-toggle");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }
}
