package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * A text. {@code fontAssetId} names a font asset of the document and is {@code null} when the
 * font family is to be resolved by the platform; {@code stroke} may be {@code null}. The
 * alignment is about the origin of the layer: the text starts at x = 0 ({@code LEFT}), is
 * centred on it ({@code CENTER}) or ends there ({@code RIGHT}); its top is at y = 0.
 */
public record TextLayer(LayerBase base, String text, String fontFamily, String fontAssetId,
                        Animatable<Double> fontSize, Weight fontWeight, boolean italic, Align alignment,
                        Animatable<Paint> fill, Stroke stroke) implements Layer {

    public enum Weight {NORMAL, BOLD}

    public enum Align {LEFT, CENTER, RIGHT}

    public TextLayer {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(fontFamily, "fontFamily");
        Objects.requireNonNull(fontSize, "fontSize");
        Objects.requireNonNull(fontWeight, "fontWeight");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(fill, "fill");
    }

    @Override
    public TextLayer withBase(LayerBase base) {
        return new TextLayer(base, text, fontFamily, fontAssetId, fontSize, fontWeight, italic, alignment, fill,
                stroke);
    }

    public TextLayer withText(String text) {
        return new TextLayer(base, text, fontFamily, fontAssetId, fontSize, fontWeight, italic, alignment, fill,
                stroke);
    }

    public TextLayer withFontFamily(String fontFamily) {
        return new TextLayer(base, text, fontFamily, fontAssetId, fontSize, fontWeight, italic, alignment, fill,
                stroke);
    }

    /**
     * @param fontAssetId the id of a font asset, or {@code null} for a platform font
     */
    public TextLayer withFontAssetId(String fontAssetId) {
        return new TextLayer(base, text, fontFamily, fontAssetId, fontSize, fontWeight, italic, alignment, fill,
                stroke);
    }

    public TextLayer withFontSize(Animatable<Double> fontSize) {
        return new TextLayer(base, text, fontFamily, fontAssetId, fontSize, fontWeight, italic, alignment, fill,
                stroke);
    }

    public TextLayer withFontWeight(Weight fontWeight) {
        return new TextLayer(base, text, fontFamily, fontAssetId, fontSize, fontWeight, italic, alignment, fill,
                stroke);
    }

    public TextLayer withItalic(boolean italic) {
        return new TextLayer(base, text, fontFamily, fontAssetId, fontSize, fontWeight, italic, alignment, fill,
                stroke);
    }

    public TextLayer withAlignment(Align alignment) {
        return new TextLayer(base, text, fontFamily, fontAssetId, fontSize, fontWeight, italic, alignment, fill,
                stroke);
    }

    public TextLayer withFill(Animatable<Paint> fill) {
        return new TextLayer(base, text, fontFamily, fontAssetId, fontSize, fontWeight, italic, alignment, fill,
                stroke);
    }

    /**
     * @param stroke the stroke, or {@code null} for none
     */
    public TextLayer withStroke(Stroke stroke) {
        return new TextLayer(base, text, fontFamily, fontAssetId, fontSize, fontWeight, italic, alignment, fill,
                stroke);
    }
}
