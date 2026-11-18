package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.model.Animation;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The tabbed panel at the right: Inspector, Assets and Document. The tabs only summarise the
 * document for now; the real editors replace their content through {@link #setTabContent}.
 */
public final class SidePanel extends StackPane {

    public static final String INSPECTOR = "inspector";
    public static final String ASSETS = "assets";
    public static final String DOCUMENT = "document";

    private final ObjectProperty<EditorDocument> document = new SimpleObjectProperty<>(this, "document");
    private final TabPane tabs = new TabPane();
    private final Label selectionSummary = hint("");
    private final Label assetSummary = hint("");
    private final Label name = value();
    private final Label size = value();
    private final Label duration = value();
    private final Label frameRate = value();
    private final InvalidationListener update = obs -> update();

    public SidePanel() {
        getStyleClass().add("side-panel");
        setMinWidth(0);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab(INSPECTOR, "Inspector", section("Selection", selectionSummary)),
                tab(ASSETS, "Assets", section("Assets", assetSummary)),
                tab(DOCUMENT, "Document", section("Document", documentGrid())));
        getChildren().add(tabs);

        document.addListener((obs, oldDoc, newDoc) -> {
            if (oldDoc != null) {
                oldDoc.animationProperty().removeListener(update);
                oldDoc.getSelection().layerIds().removeListener(update);
            }
            if (newDoc != null) {
                newDoc.animationProperty().addListener(update);
                newDoc.getSelection().layerIds().addListener(update);
            }
            update();
        });
        update();
    }

    /**
     * The document that the panel describes.
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

    /**
     * The id of the selected tab: {@link #INSPECTOR}, {@link #ASSETS} or {@link #DOCUMENT}.
     */
    public String getActiveTab() {
        return tabs.getSelectionModel().getSelectedItem().getId();
    }

    /**
     * Selects the tab with that id; an unknown id leaves the selection as it is.
     */
    public void setActiveTab(String tabId) {
        tabs.getTabs().stream().filter(t -> t.getId().equals(tabId)).findFirst()
                .ifPresent(tabs.getSelectionModel()::select);
    }

    /**
     * Replaces the content of one of the tabs.
     *
     * @throws IllegalArgumentException if there is no tab with that id
     */
    public void setTabContent(String tabId, Node content) {
        Tab tab = tabs.getTabs().stream().filter(t -> t.getId().equals(tabId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such tab: " + tabId));
        tab.setContent(content);
    }

    /**
     * The tab pane, for listening to tab changes.
     */
    public TabPane getTabPane() {
        return tabs;
    }

    private void update() {
        EditorDocument doc = getDocument();
        if (doc == null) {
            return;
        }
        Animation animation = doc.getAnimation();
        int selected = doc.getSelection().layerIds().size();
        selectionSummary.setText(selected == 0 ? "No selection" : Formats.count(selected, "layer") + " selected");
        assetSummary.setText(animation.assets().isEmpty() ? "No assets" : Formats.count(animation.assets().size(), "asset"));
        name.setText(animation.name());
        size.setText(Formats.size(animation.width(), animation.height()));
        duration.setText(Formats.timecode(animation.duration()));
        frameRate.setText(Formats.frameRate(animation.frameRate()));
    }

    private GridPane documentGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("field-grid");
        String[] labels = {"Name", "Size", "Duration", "Frame rate"};
        Label[] values = {name, size, duration, frameRate};
        for (int row = 0; row < labels.length; row++) {
            Label label = new Label(labels[row]);
            label.getStyleClass().add("field-label");
            grid.addRow(row, label, values[row]);
            GridPane.setHgrow(values[row], Priority.ALWAYS);
        }
        return grid;
    }

    private static Tab tab(String id, String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setId(id);
        return tab;
    }

    private static VBox section(String title, Node content) {
        Label header = new Label(title.toUpperCase());
        header.getStyleClass().add("section-title");
        VBox section = new VBox(header, content);
        section.getStyleClass().add("section");
        VBox box = new VBox(section);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        return label;
    }

    private static Label value() {
        Label label = new Label();
        label.getStyleClass().addAll("value-field", "mono");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
