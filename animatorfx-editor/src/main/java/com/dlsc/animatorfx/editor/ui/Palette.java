package com.dlsc.animatorfx.editor.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

/**
 * Gives code that draws on a {@code Canvas} the looked-up colours of the stylesheet. Add one as
 * an (invisible, zero-size) child of the drawing node: the rule {@code .palette} in
 * {@code editor.css} maps every {@link Tone} to its looked-up colour, so the values follow the
 * theme, and {@link #setOnChange} reports when to draw again.
 */
public final class Palette extends Region {

    /** One colour of the editor palette; the CSS property is {@code -afx-palette-<name>}. */
    public enum Tone {
        BG, PANEL, PANEL_ALT, ROW_ALT, LINE, FG, MUTED, ACCENT, ACCENT_SOFT, ACCENT_BAR, KEY, KEY_SOFT, KEY_BAR, PLAYHEAD,
        HOVER, GRID, ON_ACCENT, AUDIO, AUDIO_SOFT, AUDIO_BAR, WAVE;

        String cssProperty() {
            return "-afx-palette-" + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private static final Map<Tone, CssMetaData<Palette, Paint>> META = new EnumMap<>(Tone.class);
    private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

    static {
        for (Tone tone : Tone.values()) {
            META.put(tone, new CssMetaData<>(tone.cssProperty(), PaintConverter.getInstance(), Color.TRANSPARENT) {
                @Override
                public boolean isSettable(Palette palette) {
                    return true;
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(Palette palette) {
                    return palette.values.get(tone);
                }
            });
        }
        List<CssMetaData<? extends Styleable, ?>> all = new ArrayList<>(Region.getClassCssMetaData());
        all.addAll(META.values());
        STYLEABLES = Collections.unmodifiableList(all);
    }

    private final Map<Tone, StyleableObjectProperty<Paint>> values = new EnumMap<>(Tone.class);
    private Runnable onChange = () -> {
    };

    public Palette() {
        getStyleClass().add("palette");
        setManaged(false);
        setMouseTransparent(true);
        for (Tone tone : Tone.values()) {
            StyleableObjectProperty<Paint> value = new SimpleStyleableObjectProperty<>(META.get(tone), this,
                    tone.name(), Color.TRANSPARENT);
            value.addListener((obs, oldPaint, newPaint) -> onChange.run());
            values.put(tone, value);
        }
    }

    /** The colour of the tone in the current theme; transparent until CSS has been applied. */
    public Color get(Tone tone) {
        return values.get(tone).get() instanceof Color color ? color : Color.TRANSPARENT;
    }

    /** The colour of the tone with its opacity multiplied by the factor. */
    public Color get(Tone tone, double opacity) {
        Color color = get(tone);
        return Color.color(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity() * opacity);
    }

    /** Called whenever a colour changes, which is when the theme changes. */
    public void setOnChange(Runnable handler) {
        onChange = handler == null ? () -> {
        } : handler;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return STYLEABLES;
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return STYLEABLES;
    }
}
