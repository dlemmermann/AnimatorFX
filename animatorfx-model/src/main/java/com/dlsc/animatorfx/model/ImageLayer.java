package com.dlsc.animatorfx.model;

import java.util.Objects;

/**
 * An image asset drawn at {@code (0,0)} with the given size.
 */
public record ImageLayer(LayerBase base, String assetId, double width, double height) implements Layer {

    public ImageLayer {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(assetId, "assetId");
    }

    @Override
    public ImageLayer withBase(LayerBase base) {
        return new ImageLayer(base, assetId, width, height);
    }

    public ImageLayer withAssetId(String assetId) {
        return new ImageLayer(base, assetId, width, height);
    }

    public ImageLayer withWidth(double width) {
        return new ImageLayer(base, assetId, width, height);
    }

    public ImageLayer withHeight(double height) {
        return new ImageLayer(base, assetId, width, height);
    }
}
