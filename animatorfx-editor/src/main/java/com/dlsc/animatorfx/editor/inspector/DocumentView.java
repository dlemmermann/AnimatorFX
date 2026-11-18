package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.editor.inspector.fields.ColorField;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.editor.inspector.fields.TextValueField;
import com.dlsc.animatorfx.editor.timeline.Markers;
import com.dlsc.animatorfx.editor.timeline.TimeScale;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Marker;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * The Document tab: name, size, duration, frame rate and background of the document, its
 * markers (the same ones the timeline shows), and the "Copy Java Snippet" button, which puts the
 * code of {@link JavaSnippet} on the clipboard.
 *
 * <p>Shortening the duration deletes nothing: keyframes and out points after the end stay as
 * they are and simply are not reached.
 */
public final class DocumentView extends StackPane {

    public static final String NAME_ID = "document-name";
    public static final String WIDTH_ID = "document-width";
    public static final String HEIGHT_ID = "document-height";
    public static final String DURATION_ID = "document-duration";
    public static final String FRAME_RATE_ID = "document-frame-rate";
    public static final String BACKGROUND_ID = "document-background";
    public static final String ADD_MARKER_ID = "document-add-marker";
    public static final String MARKERS_ID = "document-markers";
    public static final String COPY_SNIPPET_ID = "document-copy-snippet";

    private static final PropertyUnits FPS = new PropertyUnits(1, " fps");

    private final ObjectProperty<EditorDocument> document = new SimpleObjectProperty<>(this, "document");
    private final TextValueField name = new TextValueField();
    private final NumberField width = new NumberField(PropertyUnits.NONE);
    private final NumberField height = new NumberField(PropertyUnits.NONE);
    private final NumberField duration = new NumberField(PropertyUnits.SECONDS);
    private final NumberField frameRate = new NumberField(FPS);
    private final ColorField background = new ColorField();
    private final VBox markerList = new VBox();
    private final List<MarkerRow> markerRows = new ArrayList<>();
    private final Label noMarkers = new Label("No markers. A marker names a time range that can be played on its own.");
    private final Button copy = new Button("Copy Java Snippet", new FontIcon(Material2AL.CODE));
    private Consumer<String> clipboard = DocumentView::toSystemClipboard;
    private final InvalidationListener update = obs -> update();

    public DocumentView() {
        getStyleClass().add("document-view");

        name.setId(NAME_ID);
        name.setOnCommit(text -> change("Rename Document", a -> a.withName(text.strip()), null));
        width.setId(WIDTH_ID);
        height.setId(HEIGHT_ID);
        for (NumberField field : new NumberField[]{width, height}) {
            field.setRange(1, 100_000);
            field.setMaxWidth(Double.MAX_VALUE);
            field.setMinWidth(0);
            HBox.setHgrow(field, Priority.ALWAYS);
        }
        width.setTooltip(new Tooltip("Width"));
        height.setTooltip(new Tooltip("Height"));
        bind(width, "Change Size", (a, value) -> a.withWidth(value));
        bind(height, "Change Size", (a, value) -> a.withHeight(value));
        HBox size = new HBox(width, height);
        size.getStyleClass().add("vec2-field");

        duration.setId(DURATION_ID);
        duration.setDecimals(3);
        duration.setMaxWidth(Double.MAX_VALUE);
        bind(duration, "Change Duration",
                (a, value) -> a.withDuration(Math.max(value, TimeScale.frame(a.frameRate()))));
        frameRate.setId(FRAME_RATE_ID);
        frameRate.setRange(1, 240);
        frameRate.setMaxWidth(Double.MAX_VALUE);
        bind(frameRate, "Change Frame Rate", (a, value) -> a.withFrameRate(value));

        background.setId(BACKGROUND_ID);
        background.setOnChange((color, adjusting) -> change("Change Background", a -> a.withBackground(color),
                adjusting ? background : null));
        background.setOnAdjustEnd(this::endGesture);
        background.getHexField().setTooltip(new Tooltip("#RRGGBBAA; alpha 00 is a transparent background"));

        VBox documentSection = section("Document", null,
                new FieldRow("Name", name, null),
                scrubbable(new FieldRow("Size", size, null), width),
                scrubbable(new FieldRow("Duration", duration, null), duration),
                scrubbable(new FieldRow("Frame rate", frameRate, null), frameRate),
                new FieldRow("Background", background, null));

        Button addMarker = new Button(null, new FontIcon(Material2AL.ADD));
        addMarker.setId(ADD_MARKER_ID);
        addMarker.getStyleClass().setAll("icon-button", "section-toggle");
        addMarker.setFocusTraversable(false);
        addMarker.setTooltip(new Tooltip("Add a marker at the playhead"));
        addMarker.setOnAction(e -> {
            EditorDocument doc = getDocument();
            if (doc != null) {
                doc.execute(Markers.add(Math.clamp(doc.getPlayhead(), 0, doc.getAnimation().duration())));
            }
        });
        noMarkers.getStyleClass().add("hint");
        noMarkers.setWrapText(true);
        markerList.setId(MARKERS_ID);
        markerList.getStyleClass().add("marker-list");
        VBox markerSection = section("Markers", addMarker, noMarkers, markerList);

        copy.setId(COPY_SNIPPET_ID);
        copy.getStyleClass().add("section-button");
        copy.setMaxWidth(Double.MAX_VALUE);
        PauseTransition restore = new PauseTransition(Duration.seconds(1.6));
        restore.setOnFinished(e -> copy.setText("Copy Java Snippet"));
        copy.setOnAction(e -> {
            if (getDocument() != null) {
                clipboard.accept(getSnippet());
                copy.setText("Copied");
                restore.playFromStart();
            }
        });
        Label codeHint = new Label("Java code that loads this animation with AnimationView, binds its slots and plays its markers.");
        codeHint.getStyleClass().add("hint");
        codeHint.setWrapText(true);
        VBox codeSection = section("Code", null, copy, codeHint);

        VBox box = new VBox(documentSection, markerSection, codeSection);
        box.getStyleClass().add("inspector-sections");
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("inspector-scroll");
        getChildren().add(scroll);

        document.addListener((obs, oldDoc, newDoc) -> {
            if (oldDoc != null) {
                oldDoc.animationProperty().removeListener(update);
            }
            if (newDoc != null) {
                newDoc.animationProperty().addListener(update);
            }
            update();
        });
        update();
    }

    private static VBox section(String title, Node action, Node... content) {
        Label heading = new Label(title.toUpperCase());
        heading.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(heading, spacer);
        if (action != null) {
            header.getChildren().add(action);
        }
        header.getStyleClass().addAll("section-header", "fixed");
        header.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(content);
        body.getStyleClass().add("section-content");
        VBox section = new VBox(header, body);
        section.getStyleClass().add("section");
        return section;
    }

    private static FieldRow scrubbable(FieldRow row, NumberField field) {
        field.installScrubbing(row.getLabel());
        return row;
    }

    private interface Setter {
        Animation apply(Animation animation, double value);
    }

    private void bind(NumberField field, String commandName, Setter setter) {
        field.setOnChange((value, adjusting) -> change(commandName, a -> setter.apply(a, value), adjusting ? field : null));
        field.setOnAdjustEnd(this::endGesture);
    }

    /**
     * The document whose settings the view shows and edits.
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

    /** Replaces where the snippet goes; the system clipboard unless set. */
    public void setClipboard(Consumer<String> target) {
        clipboard = Objects.requireNonNull(target, "target");
    }

    /** The Java snippet for the document as it is now. */
    public String getSnippet() {
        EditorDocument doc = getDocument();
        String fileName = doc.getPath() == null || doc.getPath().getFileName() == null ? null
                : doc.getPath().getFileName().toString();
        return JavaSnippet.generate(doc.getAnimation(), fileName);
    }

    private static void toSystemClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void change(String name, UnaryOperator<Animation> fn, Object gestureKey) {
        EditorDocument doc = getDocument();
        if (doc == null) {
            return;
        }
        if (gestureKey == null) {
            doc.execute(Commands.setDocument(name, fn));
        } else {
            doc.executeCoalesced(gestureKey, Commands.setDocument(name, fn));
        }
    }

    private void endGesture() {
        if (getDocument() != null) {
            getDocument().endGesture();
        }
    }

    private void update() {
        EditorDocument doc = getDocument();
        setDisable(doc == null);
        if (doc == null) {
            return;
        }
        Animation animation = doc.getAnimation();
        name.setValue(animation.name());
        width.setValue(animation.width());
        height.setValue(animation.height());
        duration.setValue(animation.duration());
        frameRate.setValue(animation.frameRate());
        background.setValue(animation.background());

        // one row per marker; rows are added and removed at the end and otherwise updated in place
        List<Marker> markers = animation.markers();
        while (markerRows.size() < markers.size()) {
            MarkerRow row = new MarkerRow();
            markerRows.add(row);
            markerList.getChildren().add(row);
        }
        while (markerRows.size() > markers.size()) {
            markerList.getChildren().remove(markerRows.remove(markerRows.size() - 1));
        }
        for (int i = 0; i < markers.size(); i++) {
            markerRows.get(i).show(markers.get(i));
        }
        noMarkers.setVisible(markers.isEmpty());
        noMarkers.setManaged(markers.isEmpty());
    }

    /** Name, start and end of one marker, and a button that removes it. */
    private final class MarkerRow extends HBox {

        private final TextValueField markerName = new TextValueField();
        private final NumberField start = new NumberField(PropertyUnits.SECONDS);
        private final NumberField end = new NumberField(PropertyUnits.SECONDS);
        private Marker marker;

        MarkerRow() {
            getStyleClass().add("marker-row");
            setAlignment(Pos.CENTER_LEFT);
            markerName.getStyleClass().add("marker-name");
            markerName.setMinWidth(0);
            HBox.setHgrow(markerName, Priority.ALWAYS);
            markerName.setValidator(text -> getDocument() == null ? Optional.empty()
                    : Markers.nameError(getDocument().getAnimation(), text.strip(), marker.name()));
            markerName.setOnCommit(text -> getDocument().execute(Markers.rename(marker.name(), text.strip())));
            for (NumberField field : new NumberField[]{start, end}) {
                field.getStyleClass().add("marker-time");
                field.setDecimals(3);
                field.setOnAdjustEnd(DocumentView.this::endGesture);
            }
            start.setTooltip(new Tooltip("Start"));
            end.setTooltip(new Tooltip("End"));
            start.setOnChange((time, adjusting) -> range(Math.min(time, marker.end()), marker.end(), adjusting ? start : null));
            end.setOnChange((time, adjusting) -> range(marker.start(), Math.max(time, marker.start()), adjusting ? end : null));
            Button remove = new Button(null, new FontIcon(Material2AL.CLOSE));
            remove.getStyleClass().setAll("icon-button", "section-toggle", "marker-remove");
            remove.setFocusTraversable(false);
            remove.setTooltip(new Tooltip("Delete the marker"));
            remove.setOnAction(e -> getDocument().execute(Markers.remove(marker.name())));
            getChildren().addAll(markerName, start, end, remove);
        }

        private void range(double from, double to, Object gestureKey) {
            EditorDocument doc = getDocument();
            if (gestureKey == null) {
                doc.execute(Markers.setRange("Change Marker", marker.name(), from, to));
            } else {
                doc.executeCoalesced(gestureKey, Markers.setRange("Change Marker", marker.name(), from, to));
            }
        }

        void show(Marker value) {
            marker = value;
            markerName.setValue(value.name());
            start.setValue(value.start());
            end.setValue(value.end());
        }
    }
}
