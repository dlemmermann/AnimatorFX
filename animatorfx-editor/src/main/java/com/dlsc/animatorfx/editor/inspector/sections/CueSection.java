package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.audio.AudioImport;
import com.dlsc.animatorfx.editor.audio.AudioInfo;
import com.dlsc.animatorfx.editor.audio.SoundPreview;
import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.editor.inspector.fields.TextValueField;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.SoundCue;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * The selected sound cues: name, time, sound, volume and mute, and a button that plays the
 * sound. Shows while cues are selected, which is when no layer is.
 */
public final class CueSection extends SectionBase {

    public static final String NAME_ID = "inspector-cue-name";
    public static final String TIME_ID = "inspector-cue-time";
    public static final String ASSET_ID = "inspector-cue-asset";
    public static final String INFO_ID = "inspector-cue-info";
    public static final String VOLUME_ID = "inspector-cue-volume";
    public static final String MUTE_ID = "inspector-cue-mute";
    public static final String PREVIEW_ID = "inspector-cue-preview";

    private final TextValueField name = new TextValueField();
    private final NumberField time = new NumberField(PropertyUnits.SECONDS);
    private final ComboBox<Asset> asset = SoundFields.assetCombo(ASSET_ID);
    private final Label info = SoundFields.readout(INFO_ID);
    private final NumberField volume = new NumberField(PropertyUnits.PERCENT);
    private final CheckBox mute = new CheckBox("Mute");
    private final FontIcon previewIcon = new FontIcon(Material2MZ.PLAY_ARROW);
    private final Button preview = SoundFields.previewButton(PREVIEW_ID, previewIcon, "Play the sound of the cue");
    private List<Asset> listedAssets;
    private boolean updating;

    public CueSection() {
        super("Cue");
        name.setId(NAME_ID);
        name.setOnCommit(text -> context().editCues("Rename Cue", cue -> cue.withName(text.strip()), null));
        row("Name", name);

        time.setId(TIME_ID);
        time.setDecimals(3);
        time.setRange(0, Double.MAX_VALUE);
        time.setMaxWidth(Double.MAX_VALUE);
        time.setTooltip(new Tooltip("The time at which playback fires the cue"));
        time.setOnChange((value, adjusting) -> context().editCues("Move Cue", cue -> cue.withTime(value),
                adjusting ? time : null));
        time.setOnAdjustEnd(() -> context().endGesture());
        time.installScrubbing(row("Time", time).getLabel());

        asset.valueProperty().addListener((obs, old, value) -> {
            if (!updating && value != null) {
                context().editCues("Change Sound", cue -> cue.withAssetId(value.id()), null);
            }
        });
        row("Asset", asset);
        row("Clip", info);

        volume.setId(VOLUME_ID);
        volume.setRange(0, 1);
        volume.setDecimals(0);
        volume.setMaxWidth(Double.MAX_VALUE);
        volume.setOnChange((value, adjusting) -> context().editCues("Change Cue Volume", cue -> cue.withVolume(value),
                adjusting ? volume : null));
        volume.setOnAdjustEnd(() -> context().endGesture());
        volume.installScrubbing(row("Volume", volume).getLabel());

        mute.setId(MUTE_ID);
        mute.getStyleClass().add("section-check");
        mute.setTooltip(new Tooltip("Playback passes the cue without a sound"));
        mute.setOnAction(e -> {
            if (!updating) {
                boolean on = mute.isSelected();
                context().editCues(on ? "Mute Cue" : "Unmute Cue", cue -> cue.withMuted(on), null);
            }
        });
        row("", mute);

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
        if (context.cues().isEmpty()) {
            return;
        }
        SoundCue cue = context.cues().get(0);
        double length = info(context, cue.assetId()).map(AudioInfo::duration).orElse(Double.NaN);
        player.play(this, context.document().getFileContent(), cue.assetId(), 0, cue.volume(), 0, length);
    }

    private static Optional<AudioInfo> info(InspectorContext context, String assetId) {
        return context.animation().assets().stream().filter(a -> a.id().equals(assetId)).findFirst()
                .flatMap(a -> AudioImport.info(context.document(), a));
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return context.layers().isEmpty() && !context.cues().isEmpty();
    }

    @Override
    protected void updateFields(InspectorContext context) {
        List<SoundCue> cues = context.cues();
        name.setValue(InspectorContext.common(cues.stream().map(SoundCue::name).toList()));
        time.setValue(InspectorContext.common(cues.stream().map(SoundCue::time).toList()));
        volume.setValue(InspectorContext.common(cues.stream().map(SoundCue::volume).toList()));
        updating = true;
        try {
            if (context.animation().assets() != listedAssets) {
                listedAssets = context.animation().assets();
                asset.getItems().setAll(listedAssets.stream().filter(a -> a.kind() == Asset.Kind.AUDIO).toList());
            }
            String id = InspectorContext.common(cues.stream().map(SoundCue::assetId).toList());
            asset.setValue(asset.getItems().stream().filter(a -> a.id().equals(id)).findFirst().orElse(null));
            info.setText(id == null ? "—" : SoundFields.describe(info(context, id)));
            mute.setSelected(cues.stream().allMatch(SoundCue::muted));
        } finally {
            updating = false;
        }
    }
}
