package com.dlsc.animatorfx.player.render;

import javafx.scene.image.Image;
import javafx.scene.text.Font;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The decoded images and fonts of the current document. Assets are decoded on first use and kept
 * for as long as the document supplies the same bytes; an asset that cannot be decoded is
 * reported once and then treated as absent.
 */
final class Assets {

    private static final System.Logger LOG = System.getLogger(Renderer.class.getName());

    /** {@code value} is null when the asset is missing or broken. */
    private record Loaded<T>(byte[] bytes, T value) {
    }

    private final Map<String, Loaded<Image>> images = new HashMap<>();
    private final Map<String, Loaded<String>> fontFamilies = new HashMap<>();
    private Map<String, byte[]> bytes = Map.of();

    /**
     * Switches to the asset content of a document.
     *
     * @return whether an asset decoded earlier has different content now
     */
    boolean use(Map<String, byte[]> bytes) {
        this.bytes = bytes;
        return dropStale(images) | dropStale(fontFamilies);
    }

    private <T> boolean dropStale(Map<String, Loaded<T>> loaded) {
        boolean dropped = false;
        for (Iterator<Map.Entry<String, Loaded<T>>> i = loaded.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry<String, Loaded<T>> entry = i.next();
            if (bytes.get(entry.getKey()) != entry.getValue().bytes()) {
                i.remove();
                dropped = true;
            }
        }
        return dropped;
    }

    /**
     * @return the image, or {@code null} if the asset is missing or not a readable image
     */
    Image image(String assetId) {
        return images.computeIfAbsent(assetId, id -> {
            byte[] content = bytes.get(id);
            Image image = null;
            if (content == null) {
                LOG.log(System.Logger.Level.WARNING, "Image asset ''{0}'' is missing; rendering without it", id);
            } else {
                try {
                    image = new Image(new ByteArrayInputStream(content));
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING, "Image asset '" + id + "' cannot be read", e);
                }
                if (image != null && image.isError()) {
                    LOG.log(System.Logger.Level.WARNING, "Image asset '" + id + "' cannot be read",
                            image.getException());
                    image = null;
                }
            }
            return new Loaded<>(content, image);
        }).value();
    }

    /**
     * @return the family name of the font asset, or {@code null} if it is missing or not a readable font
     */
    String fontFamily(String assetId) {
        return fontFamilies.computeIfAbsent(assetId, id -> {
            byte[] content = bytes.get(id);
            String family = null;
            if (content == null) {
                LOG.log(System.Logger.Level.WARNING, "Font asset ''{0}'' is missing; using the layer''s font family",
                        id);
            } else {
                Font font = null;
                try {
                    font = Font.loadFont(new ByteArrayInputStream(content), 12);
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING, "Font asset '" + id + "' cannot be read", e);
                }
                if (font == null) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Font asset ''{0}'' cannot be read; using the layer''s font family", id);
                } else {
                    family = font.getFamily();
                }
            }
            return new Loaded<>(content, family);
        }).value();
    }
}
