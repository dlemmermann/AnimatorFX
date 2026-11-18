package com.dlsc.animatorfx.sample;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

/**
 * The window content: the list of samples, the selected sample and the in-place demonstration.
 */
public final class SampleBrowser extends BorderPane {

    private final ListView<String> list = new ListView<>();
    private final Map<String, SampleShowcase> showcases = new HashMap<>();
    private final InPlaceDemo inPlace = new InPlaceDemo();
    private final CheckBox dark = new CheckBox("Dark backdrop");

    public SampleBrowser() {
        getStylesheets().add(SampleBrowser.class.getResource("sample.css").toExternalForm());
        list.setId("samples");
        list.setPrefWidth(150);
        list.getItems().addAll(Samples.SHOWCASE);
        list.getSelectionModel().selectedItemProperty().addListener((observable, old, name) -> show(name));
        setLeft(list);

        dark.setId("dark");
        dark.setSelected(true);
        dark.selectedProperty().addListener((observable, old, selected) -> {
            if (selected) {
                getStyleClass().remove("light");
            } else {
                getStyleClass().add("light");
            }
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(inPlace, spacer, dark);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(0, 16, 0, 0));
        setBottom(new VBox(new Separator(), footer));

        list.getSelectionModel().selectFirst();
    }

    public ListView<String> getList() {
        return list;
    }

    /** The showcase of a sample; created on first use. */
    public SampleShowcase getShowcase(String name) {
        return showcases.computeIfAbsent(name, key -> new SampleShowcase(key, Samples.load(key)));
    }

    public InPlaceDemo getInPlaceDemo() {
        return inPlace;
    }

    private void show(String name) {
        // a view that leaves the window pauses by itself and resumes when it comes back
        setCenter(name == null ? null : getShowcase(name));
    }
}
