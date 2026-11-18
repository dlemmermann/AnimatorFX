package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.SlotLayer;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The bottom region. For now it is a read-only overview: the layer names (top-most first) at the
 * left and, at the right, a ruler, one bar per layer for its in/out range and the playhead. A
 * click on a name selects the layer. Property rows, keyframes, dragging, zoom and scroll are not
 * here yet; they replace the content of this class, or the whole region through
 * {@link MainWindow#setTimelineContent}.
 */
public final class TimelinePane extends HBox {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass SLOT = PseudoClass.getPseudoClass("slot");
    private static final double ROW_HEIGHT = 24;
    private static final double INDENT = 14;

    private record Row(Layer layer, int depth) {
    }

    private final ObjectProperty<EditorDocument> document = new SimpleObjectProperty<>(this, "document");
    private final VBox names = new VBox();
    private final Tracks tracks = new Tracks();
    private final InvalidationListener rebuild = obs -> rebuild();
    private final InvalidationListener playhead = obs -> tracks.requestLayout();
    private List<Row> rows = List.of();

    public TimelinePane() {
        getStyleClass().add("timeline-pane");
        setMinHeight(0);
        names.getStyleClass().add("timeline-names");
        names.setMinWidth(220);
        names.setPrefWidth(220);
        HBox.setHgrow(tracks, Priority.ALWAYS);
        getChildren().addAll(names, tracks);

        document.addListener((obs, oldDoc, newDoc) -> {
            if (oldDoc != null) {
                oldDoc.animationProperty().removeListener(rebuild);
                oldDoc.getSelection().layerIds().removeListener(rebuild);
                oldDoc.playheadProperty().removeListener(playhead);
            }
            if (newDoc != null) {
                newDoc.animationProperty().addListener(rebuild);
                newDoc.getSelection().layerIds().addListener(rebuild);
                newDoc.playheadProperty().addListener(playhead);
            }
            rebuild();
        });
        rebuild();
    }

    /**
     * The document that the timeline shows.
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

    private void rebuild() {
        EditorDocument doc = getDocument();
        List<Row> collected = new ArrayList<>();
        if (doc != null) {
            collect(doc.getAnimation().layers(), 0, collected);
        }
        rows = collected;

        Label header = new Label("LAYERS");
        header.getStyleClass().addAll("timeline-row", "timeline-header", "mono");
        header.setMaxWidth(Double.MAX_VALUE);
        names.getChildren().setAll(header);
        for (Row row : rows) {
            Label name = new Label(row.layer().name());
            name.getStyleClass().add("timeline-row");
            name.setMaxWidth(Double.MAX_VALUE);
            name.setPadding(new Insets(0, 8, 0, 10 + row.depth() * INDENT));
            name.pseudoClassStateChanged(SELECTED, doc.getSelection().layerIds().contains(row.layer().id()));
            name.pseudoClassStateChanged(SLOT, row.layer() instanceof SlotLayer);
            name.setOnMouseClicked(e -> doc.getSelection().layerIds().setAll(row.layer().id()));
            names.getChildren().add(name);
        }
        tracks.rebuild(doc);
    }

    /** Top-most layer first, each group before its children. */
    private static void collect(List<Layer> layers, int depth, List<Row> out) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            out.add(new Row(layer, depth));
            if (layer instanceof GroupLayer group) {
                collect(group.children(), depth + 1, out);
            }
        }
    }

    /** The ruler, the row backgrounds with the layer bars, and the playhead. */
    private final class Tracks extends Pane {

        private static final double PADDING = 12;
        /** Room for the label of the last tick. */
        private static final double END_PADDING = 36;

        private final Pane ruler = new Pane();
        private final List<Region> rowBackgrounds = new ArrayList<>();
        private final List<Region> bars = new ArrayList<>();
        private final List<Label> ticks = new ArrayList<>();
        private final Region playheadLine = new Region();
        private final Region playheadHead = new Region();

        Tracks() {
            getStyleClass().add("timeline-tracks");
            setMinWidth(0);
            ruler.getStyleClass().addAll("timeline-row", "timeline-header", "timeline-ruler");
            playheadLine.getStyleClass().add("playhead");
            playheadHead.getStyleClass().add("playhead-head");
            playheadLine.setManaged(false);
            playheadHead.setManaged(false);
        }

        void rebuild(EditorDocument doc) {
            rowBackgrounds.clear();
            bars.clear();
            ticks.clear();
            getChildren().setAll(ruler);
            ruler.getChildren().clear();
            if (doc == null) {
                return;
            }
            for (Row row : rows) {
                Region background = new Region();
                background.getStyleClass().add("timeline-row");
                background.pseudoClassStateChanged(SELECTED,
                        doc.getSelection().layerIds().contains(row.layer().id()));
                Region bar = new Region();
                bar.getStyleClass().add("layer-bar");
                bar.pseudoClassStateChanged(SLOT, row.layer() instanceof SlotLayer);
                rowBackgrounds.add(background);
                bars.add(bar);
                getChildren().addAll(background, bar);
            }
            double duration = doc.getAnimation().duration();
            double step = tickStep(duration);
            for (double t = 0; t <= duration + 1e-9; t += step) {
                Label tick = new Label(Formats.number(t) + "s");
                tick.getStyleClass().addAll("timeline-tick", "mono");
                tick.setUserData(t);
                ticks.add(tick);
                ruler.getChildren().add(tick);
            }
            getChildren().addAll(playheadLine, playheadHead);
            requestLayout();
        }

        private static double tickStep(double duration) {
            for (double step : new double[]{0.1, 0.25, 0.5, 1, 2, 5, 10, 30, 60}) {
                if (duration / step <= 12) {
                    return step;
                }
            }
            return Math.max(60, duration / 10);
        }

        @Override
        protected void layoutChildren() {
            double width = getWidth();
            ruler.resizeRelocate(0, 0, width, ROW_HEIGHT);
            EditorDocument doc = getDocument();
            if (doc == null) {
                return;
            }
            Animation animation = doc.getAnimation();
            double duration = Math.max(animation.duration(), 1e-6);
            double scale = Math.max(0, width - PADDING - END_PADDING) / duration;
            for (Label tick : ticks) {
                double x = PADDING + (double) tick.getUserData() * scale;
                tick.resizeRelocate(Math.round(x) + 4, 0, tick.prefWidth(-1), ROW_HEIGHT);
            }
            for (int i = 0; i < rows.size(); i++) {
                Layer layer = rows.get(i).layer();
                double y = (i + 1) * ROW_HEIGHT;
                rowBackgrounds.get(i).resizeRelocate(0, y, width, ROW_HEIGHT);
                double start = Math.max(0, Math.min(layer.base().inPoint(), duration));
                double end = Math.max(start, Math.min(layer.base().outPoint(), duration));
                bars.get(i).resizeRelocate(Math.round(PADDING + start * scale), y + 7,
                        Math.max(2, Math.round((end - start) * scale)), ROW_HEIGHT - 14);
            }
            double time = Math.max(0, Math.min(doc.getPlayhead(), duration));
            double x = Math.round(PADDING + time * scale);
            playheadLine.resizeRelocate(x, 0, 1, getHeight());
            playheadHead.resizeRelocate(x - 4, 0, 9, 8);
        }
    }
}
