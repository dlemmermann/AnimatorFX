package com.dlsc.animatorfx.model.io;

import com.dlsc.animatorfx.model.Animation;

import java.util.Map;
import java.util.Objects;

/**
 * A document together with the content of its assets, keyed by {@link com.dlsc.animatorfx.model.Asset#id()}.
 */
public record AnimationFile(Animation animation, Map<String, byte[]> assets) {

    public AnimationFile {
        Objects.requireNonNull(animation, "animation");
        assets = Map.copyOf(assets);
    }
}
