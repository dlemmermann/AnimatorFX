package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.audio.AudioImport;
import com.dlsc.animatorfx.editor.audio.AudioInfo;
import com.dlsc.animatorfx.editor.audio.SoundPreview;
import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.PropertyRows;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * The sound of the selected audio layers: the audio asset with its length and format, where in
 * the sound the layer starts (clip offset), volume and balance (both animatable), loop and mute,
 * and a button that plays the clip from its offset.
 */
public final class AudioSection extends SectionBase {

    public static final String ASSET_ID = "inspector-audio-asset";
    public static final String INFO_ID = "inspector-audio-info";
    public static final String OFFSET_ID = "inspector-audio-offset";
    public static final String LOOP_ID = "inspector-audio-loop";
    public static final String MUTE_ID = "inspector-audio-mute";
    public static final String PREVIEW_ID = "inspector-audio-preview";

    private final ComboBox<Asset> asset = SoundFields.assetCombo(ASSET_ID);
    private final Label info = SoundFields.readout(INFO_ID);
    private final NumberField offset = new NumberField(PropertyUnits.SECONDS);
    private final CheckBox loop = new CheckBox("Loop");
    private final CheckBox mute = new CheckBox("Mute");
    private final FontIcon previewIcon = new FontIcon(Material2MZ.PLAY_ARROW);
    private final Button preview = SoundFields.previewButton(PREVIEW_ID, previewIcon, "Play the clip from its offset");
    private List<Asset> listedAssets;
    private boolean updating;

    public AudioSection() {
        super("Audio");
        asset.valueProperty().addListener((obs, old, value) -> {
            if (!updating && value != null) {
                context().edit("Change Sound", AudioLayer.class, l -> l.withAssetId(value.id()), null);
            }
        });
        row("Asset", asset);

        row("Clip", info);

        offset.setId(OFFSET_ID);
        offset.setDecimals(3);
        offset.setRange(0, Double.MAX_VALUE);
        offset.setMaxWidth(Double.MAX_VALUE);
        offset.setTooltip(new Tooltip("Clip offset: the position in the sound at the in-point of the layer"));
        offset.setOnChange((value, adjusting) -> context().edit("Change Clip Offset", AudioLayer.class,
                l -> l.withClipOffset(Math.max(0, value)), adjusting ? offset : null));
        offset.setOnAdjustEnd(() -> context().endGesture());
        offset.installScrubbing(row("Offset", offset).getLabel());

        PropertyRows.NumberRow volume = number("volume", "Volume");
        volume.field().setRange(0, 1);
        volume.field().setDecimals(0);
        PropertyRows.NumberRow balance = number("balance", "Balance");
        balance.field().setRange(-1, 1);
        balance.field().setDecimals(0);
        balance.field().setTooltip(new Tooltip("-100 % is all left, 0 % the middle, 100 % all right"));

        loop.setId(LOOP_ID);
        mute.setId(MUTE_ID);
        loop.getStyleClass().add("section-check");
        mute.getStyleClass().add("section-check");
        loop.setTooltip(new Tooltip("Start the sound over when it ends, for as long as the layer lasts"));
        mute.setTooltip(new Tooltip("Keep the layer silent"));
        loop.setOnAction(e -> {
            if (!updating) {
                boolean on = loop.isSelected();
                context().edit(on ? "Loop Sound" : "Play Sound Once", AudioLayer.class, l -> l.withLoop(on), null);
            }
        });
        mute.setOnAction(e -> {
            if (!updating) {
                boolean on = mute.isSelected();
                context().edit(on ? "Mute Layer" : "Unmute Layer", AudioLayer.class, l -> l.withMuted(on), null);
            }
        });
        HBox switches = new HBox(loop, mute);
        switches.getStyleClass().add("check-row");
        row("", switches);

        preview.setOnAction(e -> togglePreview());
        SoundPreview.shared().ownerProperty().addListener((obs, old, owner) ->
                previewIcon.setIconCode(owner == this ? Material2MZ.STOP : Material2MZ.PLAY_ARROW));
        actions().getChildren().add(preview);
    }

    private void togglePreview() {
        SoundPreview player = SoundPreview.shared();
        if (player.getOwner() == this) {
            player.stop();
            return;
        }
        InspectorContext context = context();
        AudioLayer layer = context.layers().stream().filter(AudioLayer.class::isInstance).map(AudioLayer.class::cast)
                .findFirst().orElse(null);
        if (layer == null) {
            return;
        }
        double length = info(context, layer.assetId()).map(AudioInfo::duration).orElse(Double.NaN);
        player.play(this, context.document().getFileContent(), layer.assetId(), layer.clipOffset(),
                layer.volume().at(context.time()), layer.balance().at(context.time()), length);
    }

    private static Optional<AudioInfo> info(InspectorContext context, String assetId) {
        return context.animation().assets().stream().filter(a -> a.id().equals(assetId)).findFirst()
                .flatMap(a -> AudioImport.info(context.document(), a));
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.allLayers(AudioLayer.class);
    }

    @Override
    protected void updateFields(InspectorContext context) {
        updating = true;
        try {
            if (context.animation().assets() != listedAssets) {
                listedAssets = context.animation().assets();
                asset.getItems().setAll(listedAssets.stream().filter(a -> a.kind() == Asset.Kind.AUDIO).toList());
            }
            String id = context.common(AudioLayer.class, AudioLayer::assetId);
            asset.setValue(asset.getItems().stream().filter(a -> a.id().equals(id)).findFirst().orElse(null));
            info.setText(id == null ? "—" : SoundFields.describe(info(context, id)));
            loop.setSelected(context.layers().stream().allMatch(l -> l instanceof AudioLayer a && a.loop()));
            mute.setSelected(context.layers().stream().allMatch(l -> l instanceof AudioLayer a && a.muted()));
        } finally {
            updating = false;
        }
        offset.setValue(context.common(AudioLayer.class, AudioLayer::clipOffset));
    }
}
