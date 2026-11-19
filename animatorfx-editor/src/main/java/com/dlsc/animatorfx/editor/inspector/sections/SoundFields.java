package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.audio.AudioInfo;
import com.dlsc.animatorfx.editor.inspector.fields.Combos;
import com.dlsc.animatorfx.model.Asset;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

/** The editors that the Audio and the Cue section have in common. */
final class SoundFields {

    private SoundFields() {
    }

    /** A combo box of audio assets by name; "Mixed" without a value. */
    static ComboBox<Asset> assetCombo(String id) {
        ComboBox<Asset> combo = new ComboBox<>();
        combo.setId(id);
        combo.setPromptText("Mixed");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setMinWidth(0);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Asset value) {
                return value == null ? "" : value.name();
            }

            @Override
            public Asset fromString(String text) {
                return null;
            }
        });
        Combos.compact(combo);
        return combo;
    }

    static Button previewButton(String id, FontIcon icon, String tooltip) {
        Button button = new Button(null, icon);
        button.setId(id);
        button.getStyleClass().setAll("icon-button", "section-toggle");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    /** {@code "1.250 s · WAVE · 44.1 kHz · stereo"}; a dash while nothing is known. */
    static String describe(Optional<AudioInfo> info) {
        return info.map(i -> i.durationText() + " · " + i.describe()).orElse("—");
    }
}
