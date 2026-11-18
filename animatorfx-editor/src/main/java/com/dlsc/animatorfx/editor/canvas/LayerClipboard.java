package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.Layer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The in-application clipboard for layers: the copied layers, bottom to top, with the bytes of
 * the assets they use, so a paste works in another document too.
 */
public final class LayerClipboard {

    private static final LayerClipboard SHARED = new LayerClipboard();

    private List<Layer> layers = List.of();
    private Map<Asset, byte[]> assets = Map.of();
    private int pasteCount;

    /** The clipboard that all windows and documents of the application share. */
    public static LayerClipboard shared() {
        return SHARED;
    }

    public void set(List<Layer> layers, Map<Asset, byte[]> assets) {
        this.layers = List.copyOf(layers);
        this.assets = new LinkedHashMap<>(assets);
        pasteCount = 0;
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public List<Layer> getLayers() {
        return layers;
    }

    public Map<Asset, byte[]> getAssets() {
        return assets;
    }

    /** Counts the pastes since the last copy, so each paste can be offset a little further. */
    public int nextPaste() {
        return ++pasteCount;
    }

    public void clear() {
        set(List.of(), Map.of());
    }
}
