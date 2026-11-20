package com.dlsc.animatorfx.importer;

import com.lottie4j.core.model.animation.Animation;

import java.util.List;

/**
 * A document converted to Lottie.
 *
 * @param animation  the animation in the Lottie4J model
 * @param background the background colour of the document as {@code #rrggbb}, which Lottie itself
 *                   has no place for; {@code null} for a transparent background
 * @param warnings   what could not be taken over or was approximated, one line per kind
 */
public record LottieExport(Animation animation, String background, List<String> warnings) {

    public LottieExport {
        warnings = List.copyOf(warnings);
    }
}
