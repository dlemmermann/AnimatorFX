package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.io.AnimationFile;

import java.util.List;
import java.util.Objects;

/**
 * An imported document with its assets, and one line for every kind of feature of the source
 * that could not be converted.
 */
public record ImportResult(AnimationFile file, List<String> warnings) {

    public ImportResult {
        Objects.requireNonNull(file, "file");
        warnings = List.copyOf(warnings);
    }
}
