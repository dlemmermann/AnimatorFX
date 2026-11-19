package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.JsonWriter;
import com.lottie4j.core.exception.LottieFileException;
import com.lottie4j.core.file.LottieFileLoader;
import com.lottie4j.core.file.LottieFileSaver;
import com.lottie4j.core.model.animation.Animation;
import com.lottie4j.core.model.dot.DotLottie;
import com.lottie4j.core.model.dot.Manifest;
import com.lottie4j.core.model.dot.ManifestAnimation;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/**
 * Writes a document as a Lottie animation: as a dotLottie archive ({@code .lottie}) or as plain
 * Lottie JSON.
 * <p>
 * Lottie cannot express everything a document can hold, so this is an export and no second file
 * format: what is lost or approximated is reported as warnings, in the wording of the importers.
 * The conversion follows the conventions {@link LottieImporter} reads, so that a document that
 * was imported from Lottie looks the same after it was exported again. The Lottie model, its
 * validation and the files are those of Lottie4J.
 */
public final class LottieExporter {

    private static final String GENERATOR = "AnimatorFX";

    private LottieExporter() {
    }

    /**
     * Converts a document.
     *
     * @return the animation and what could not be taken over
     * @throws IOException if the document cannot be expressed as a valid Lottie animation
     */
    public static LottieExport convert(AnimationFile file) throws IOException {
        Objects.requireNonNull(file, "file");
        Warnings warnings = new Warnings();
        LottieDocumentWriter writer = new LottieDocumentWriter(file, warnings);
        String json = JsonWriter.write(writer.document());
        try {
            Animation animation = LottieFileLoader.loadJson(json);
            return new LottieExport(animation, writer.background(), warnings.lines());
        } catch (LottieFileException e) {
            throw new IOException("The document could not be converted to Lottie: " + e.getMessage(), e);
        }
    }

    /**
     * Writes a document as a dotLottie archive with one animation. Images and sounds are
     * embedded in the animation; the background colour of the document goes into the manifest.
     *
     * @param out the stream to write to; it is not closed
     * @return what could not be taken over
     */
    public static List<String> writeDotLottie(AnimationFile file, OutputStream out) throws IOException {
        LottieExport export = convert(file);
        Manifest manifest = new Manifest("2", GENERATOR, null, List.of(new ManifestAnimation("animation", null,
                null, null, null, null, null, export.background(), null)), null, null, null);
        try {
            LottieFileSaver.saveDotLottie(
                    new DotLottie(manifest, List.of(export.animation()), null, null, null, null), out);
        } catch (LottieFileException e) {
            throw new IOException("The dotLottie file could not be written: " + e.getMessage(), e);
        }
        return export.warnings();
    }

    /**
     * Writes a document as Lottie JSON. The format has no place for the background colour.
     *
     * @param out the stream to write to; it is not closed
     * @return what could not be taken over
     */
    public static List<String> writeJson(AnimationFile file, OutputStream out) throws IOException {
        LottieExport export = convert(file);
        try {
            LottieFileSaver.save(export.animation(), out);
        } catch (LottieFileException e) {
            throw new IOException("The Lottie file could not be written: " + e.getMessage(), e);
        }
        return export.warnings();
    }
}
