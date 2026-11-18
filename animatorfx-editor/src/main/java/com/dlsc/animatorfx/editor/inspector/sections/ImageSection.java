package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.editor.inspector.FieldRow;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.editor.inspector.fields.Combos;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.ImageLayer;
import java.util.List;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

/**
 * The image asset and the size of the selected image layers.
 */
public final class ImageSection extends SectionBase {

    public static final String ASSET_ID = "inspector-image-asset";
    public static final String WIDTH_ID = "inspector-image-width";
    public static final String HEIGHT_ID = "inspector-image-height";

    private final ComboBox<Asset> asset = new ComboBox<>();
    private final NumberField width = new NumberField(PropertyUnits.NONE);
    private final NumberField height = new NumberField(PropertyUnits.NONE);
    private List<Asset> listedAssets;
    private boolean updating;

    public ImageSection() {
        super("Image");
        asset.setId(ASSET_ID);
        asset.setPromptText("Mixed");
        asset.setMaxWidth(Double.MAX_VALUE);
        asset.setMinWidth(0);
        asset.setConverter(new StringConverter<>() {
            @Override
            public String toString(Asset value) {
                return value == null ? "" : value.name();
            }

            @Override
            public Asset fromString(String text) {
                return null;
            }
        });
        Combos.compact(asset);
        asset.valueProperty().addListener((obs, old, value) -> {
            if (!updating && value != null) {
                context().edit("Change Image", ImageLayer.class, l -> l.withAssetId(value.id()), null);
            }
        });
        row("Asset", asset);

        width.setId(WIDTH_ID);
        height.setId(HEIGHT_ID);
        for (NumberField field : new NumberField[]{width, height}) {
            field.setRange(0, Double.MAX_VALUE);
            field.setMaxWidth(Double.MAX_VALUE);
            field.setMinWidth(0);
            HBox.setHgrow(field, Priority.ALWAYS);
            field.setOnAdjustEnd(() -> context().endGesture());
        }
        width.setOnChange((value, adjusting) -> context().edit("Change Width", ImageLayer.class,
                l -> l.withWidth(value), adjusting ? width : null));
        height.setOnChange((value, adjusting) -> context().edit("Change Height", ImageLayer.class,
                l -> l.withHeight(value), adjusting ? height : null));
        HBox size = new HBox(width, height);
        size.getStyleClass().add("vec2-field");
        FieldRow row = row("Size", size);
        width.installScrubbing(row.getLabel());
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.allLayers(ImageLayer.class);
    }

    @Override
    protected void updateFields(InspectorContext context) {
        updating = true;
        try {
            if (context.animation().assets() != listedAssets) {
                listedAssets = context.animation().assets();
                asset.getItems().setAll(listedAssets.stream().filter(a -> a.kind() == Asset.Kind.IMAGE).toList());
            }
            String id = context.common(ImageLayer.class, ImageLayer::assetId);
            asset.setValue(asset.getItems().stream().filter(a -> a.id().equals(id)).findFirst().orElse(null));
        } finally {
            updating = false;
        }
        width.setValue(context.common(ImageLayer.class, ImageLayer::width));
        height.setValue(context.common(ImageLayer.class, ImageLayer::height));
    }
}
