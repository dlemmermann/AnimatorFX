package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.PropertyRows;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.editor.inspector.fields.TextValueField;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.TextLayer;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * The content and the font of the selected texts: text, font family (the font assets of the
 * document first, then the installed families), size, bold, italic and alignment.
 */
public final class TextSection extends SectionBase {

    public static final String TEXT_ID = "inspector-text-content";
    public static final String FONT_ID = "inspector-text-font";
    public static final String BOLD_ID = "inspector-text-bold";
    public static final String ITALIC_ID = "inspector-text-italic";

    /**
     * A font to choose.
     *
     * @param assetId the font asset, or {@code null} for an installed family
     */
    public record FontChoice(String label, String family, String assetId) {

        @Override
        public String toString() {
            return label;
        }
    }

    private final TextValueField text = new TextValueField();
    private final ComboBox<FontChoice> font = new ComboBox<>();
    private final PropertyRows.NumberRow size;
    private final ToggleButton bold = toggle(BOLD_ID, Material2AL.FORMAT_BOLD, "Bold");
    private final ToggleButton italic = toggle(ITALIC_ID, Material2AL.FORMAT_ITALIC, "Italic");
    private final Map<TextLayer.Align, ToggleButton> alignments = new HashMap<>();
    private final Map<String, String> assetFamilies = new HashMap<>();
    private List<Asset> listedAssets;
    private boolean updating;

    public TextSection() {
        super("Text");
        text.setId(TEXT_ID);
        text.setOnCommit(value -> context().edit("Change Text", TextLayer.class, l -> l.withText(value), null));
        row("Text", text);

        font.setId(FONT_ID);
        font.getStyleClass().add("compact-combo");
        font.setPromptText("Mixed");
        font.setMaxWidth(Double.MAX_VALUE);
        font.setMinWidth(0);
        font.setVisibleRowCount(14);
        font.valueProperty().addListener((obs, old, choice) -> {
            if (!updating && choice != null) {
                context().edit("Change Font", TextLayer.class,
                        l -> l.withFontFamily(choice.family()).withFontAssetId(choice.assetId()), null);
            }
        });
        row("Font", font);

        size = number("fontSize", "Size");
        size.field().setRange(1, 10000);

        bold.selectedProperty().addListener((obs, was, on) -> {
            if (!updating) {
                context().edit("Change Font Weight", TextLayer.class,
                        l -> l.withFontWeight(on ? TextLayer.Weight.BOLD : TextLayer.Weight.NORMAL), null);
            }
        });
        italic.selectedProperty().addListener((obs, was, on) -> {
            if (!updating) {
                context().edit("Change Font Style", TextLayer.class, l -> l.withItalic(on), null);
            }
        });
        HBox style = new HBox(bold, italic, gap());
        style.getStyleClass().add("toggle-row");
        Ikon[] icons = {Material2AL.FORMAT_ALIGN_LEFT, Material2AL.FORMAT_ALIGN_CENTER, Material2AL.FORMAT_ALIGN_RIGHT};
        for (TextLayer.Align align : TextLayer.Align.values()) {
            String name = align.name().charAt(0) + align.name().substring(1).toLowerCase();
            ToggleButton button = toggle("inspector-text-align-" + align.name().toLowerCase(), icons[align.ordinal()],
                    "Align " + name.toLowerCase());
            button.setOnAction(e -> {
                // a click on the active alignment keeps it
                button.setSelected(true);
                context().edit("Change Alignment", TextLayer.class, l -> l.withAlignment(align), null);
            });
            alignments.put(align, button);
            style.getChildren().add(button);
        }
        row("Style", style);
    }

    private static Region gap() {
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        gap.setMaxWidth(10);
        gap.setMinWidth(10);
        return gap;
    }

    private static ToggleButton toggle(String id, Ikon icon, String tooltip) {
        ToggleButton button = new ToggleButton(null, new FontIcon(icon));
        button.setId(id);
        button.getStyleClass().setAll("icon-button", "section-toggle", "boxed");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    public PropertyRows.NumberRow size() {
        return size;
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.allLayers(TextLayer.class);
    }

    /** The font assets, then the installed families; rebuilt only when the assets change. */
    private void listFonts(InspectorContext context) {
        Animation animation = context.animation();
        if (animation.assets() == listedAssets) {
            return;
        }
        listedAssets = animation.assets();
        List<FontChoice> choices = new ArrayList<>();
        Map<String, byte[]> content = context.document().getFileContent().assets();
        for (Asset asset : animation.assets()) {
            if (asset.kind() == Asset.Kind.FONT) {
                String family = assetFamilies.computeIfAbsent(asset.id(), id -> {
                    byte[] bytes = content.get(id);
                    Font loaded = bytes == null ? null : Font.loadFont(new ByteArrayInputStream(bytes), 12);
                    return loaded == null ? asset.name() : loaded.getFamily();
                });
                choices.add(new FontChoice(family + "  (" + asset.name() + ")", family, asset.id()));
            }
        }
        for (String family : Font.getFamilies()) {
            choices.add(new FontChoice(family, family, null));
        }
        font.getItems().setAll(choices);
    }

    @Override
    protected void updateFields(InspectorContext context) {
        text.setValue(context.common(TextLayer.class, TextLayer::text));
        updating = true;
        try {
            listFonts(context);
            String family = context.common(TextLayer.class, TextLayer::fontFamily);
            // a null asset id is a value of its own, so "common" cannot tell it from "mixed"
            List<String> assetIds = context.layers().stream().map(l -> ((TextLayer) l).fontAssetId()).distinct().toList();
            FontChoice choice = null;
            if (family != null && assetIds.size() == 1) {
                String assetId = assetIds.get(0);
                choice = font.getItems().stream()
                        .filter(c -> Objects.equals(c.assetId(), assetId) && (assetId != null || c.family().equals(family)))
                        .findFirst().orElseGet(() -> new FontChoice(family, family, assetId));
            }
            font.setValue(choice);
            TextLayer.Weight weight = context.common(TextLayer.class, TextLayer::fontWeight);
            bold.setSelected(weight == TextLayer.Weight.BOLD);
            italic.setSelected(Boolean.TRUE.equals(context.common(TextLayer.class, TextLayer::italic)));
            TextLayer.Align align = context.common(TextLayer.class, TextLayer::alignment);
            alignments.forEach((key, button) -> button.setSelected(key == align));
        } finally {
            updating = false;
        }
    }
}
