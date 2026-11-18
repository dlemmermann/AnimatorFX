package com.dlsc.animatorfx.model.io;

import java.io.IOException;
import java.util.Objects;

/**
 * A document that cannot be read because its content is malformed.
 */
public class AnimationFormatException extends IOException {

    private final String jsonPath;

    /**
     * @param jsonPath the path of the offending element, for example
     *                 {@code layers[2].transform.position.keyframes[1].t}; empty for the document as a whole
     */
    public AnimationFormatException(String message, String jsonPath) {
        this(message, jsonPath, null);
    }

    public AnimationFormatException(String message, String jsonPath, Throwable cause) {
        super(Objects.requireNonNull(jsonPath, "jsonPath").isEmpty() ? message : jsonPath + ": " + message, cause);
        this.jsonPath = jsonPath;
    }

    /**
     * The path of the offending element; empty when the error concerns the document as a whole
     * (for example a JSON syntax error, whose message carries line and column instead).
     */
    public String jsonPath() {
        return jsonPath;
    }
}
