package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationFormatException;
import com.lottie4j.core.exception.LottieFileException;
import com.lottie4j.core.file.LottieFileLoader;
import com.lottie4j.core.helper.ModelWalker;
import com.lottie4j.core.model.animation.Animated;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts a Lottie (Bodymovin) JSON animation to an animated document. The file is read by
 * Lottie4J; this class and its helpers turn what it read into our model.
 */
public final class LottieImporter {

    private static final double MIN_FRAME_RATE = 1;
    private static final double MAX_FRAME_RATE = 1000;
    /** Seconds: 24 hours. */
    private static final double MAX_DURATION = 24 * 60 * 60;

    private LottieImporter() {
    }

    /**
     * Reads a Lottie document. Time 0 of the result is the in point of the Lottie animation.
     * Features that cannot be converted are skipped and named in the warnings.
     *
     * @param name the name of the document if the file does not carry one
     * @throws IOException if the input cannot be read, is not JSON or is not a Lottie document
     *                     with a usable size ({@code w}, {@code h}), frame rate ({@code fr}, 1 to
     *                     1000) and time range ({@code ip}, {@code op}; at most 24 hours)
     */
    public static ImportResult read(InputStream in, String name) throws IOException {
        com.lottie4j.core.model.animation.Animation source;
        try {
            source = LottieFileLoader.load(in);
        } catch (LottieFileException e) {
            throw new AnimationFormatException("Not a valid Lottie document: " + e.getMessage(), "", e);
        }
        double width = dimension(source.width(), "w", Double.MIN_VALUE, Limits.MAX_MAGNITUDE);
        double height = dimension(source.height(), "h", Double.MIN_VALUE, Limits.MAX_MAGNITUDE);
        double frameRate = dimension(source.framesPerSecond(), "fr", MIN_FRAME_RATE, MAX_FRAME_RATE);
        if (source.layers() == null) {
            throw new AnimationFormatException("Not a valid Lottie document: there are no layers", "layers");
        }
        double inPoint = source.inPoint() != null ? source.inPoint() : 0;
        double outPoint = source.outPoint() != null ? source.outPoint() : inPoint;
        if (!Limits.usable(inPoint) || !Limits.usable(outPoint)) {
            throw new AnimationFormatException("Not a valid Lottie document: the in and out points \"ip\" and"
                    + " \"op\" are out of range", "ip");
        }
        double duration = Math.max(0, (outPoint - inPoint) / frameRate);
        if (duration > MAX_DURATION) {
            throw new AnimationFormatException("Not a valid Lottie document: it would run for more than 24 hours",
                    "op");
        }

        Warnings warnings = new Warnings();
        if (source.has3dLayers() != null && source.has3dLayers() != 0) {
            warnings.add("3D layers are not supported; they are treated as 2D", "layer", "layers");
        }
        ModelWalker.visit(source, part -> {
            if (part instanceof Animated property && property.hasExpression()) {
                warnings.add("Expressions are not supported", "property", "properties");
            }
        });
        Map<String, com.lottie4j.core.model.asset.Asset> assets = new HashMap<>();
        if (source.assets() != null) {
            for (com.lottie4j.core.model.asset.Asset asset : source.assets()) {
                if (asset != null) {
                    assets.putIfAbsent(asset.id() != null ? asset.id() : "", asset);
                }
            }
        }
        LottieProperties properties = new LottieProperties(warnings, frameRate);
        LottieLayers converter = new LottieLayers(warnings, properties, duration, assets, source.fonts());
        List<Layer> layers = converter.composition(source.layers(), -inPoint);

        String documentName = source.name() != null ? source.name() : "";
        Animation animation = DocumentCheck.repair(new Animation(Animation.CURRENT_FORMAT,
                documentName.isBlank() ? name : documentName, width, height, frameRate, duration, Rgba.TRANSPARENT,
                markers(source.markers(), properties, -inPoint, duration), converter.assets(), layers),
                warnings);
        return new ImportResult(new AnimationFile(animation, converter.assetBytes()), warnings.lines());
    }

    private static double dimension(Number value, String key, double min, double max)
            throws AnimationFormatException {
        double number = value == null ? Double.NaN : value.doubleValue();
        if (!(number >= min && number <= max)) {
            throw new AnimationFormatException("Not a valid Lottie document: \"" + key + "\" is "
                    + (value == null ? "missing" : "out of range (" + number + ")"), key);
        }
        return number;
    }

    /**
     * Markers with their time and duration in frames, and the name Lottie4J reads from their
     * comment. Names are made unique.
     */
    private static List<Marker> markers(List<com.lottie4j.core.model.animation.Marker> source,
                                        LottieProperties properties, double frameOffset, double duration) {
        List<Marker> markers = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (com.lottie4j.core.model.animation.Marker marker : source == null
                ? List.<com.lottie4j.core.model.animation.Marker>of() : source) {
            String name = marker.name();
            if (name.isEmpty()) {
                name = "Marker " + (markers.size() + 1);
            }
            String unique = name;
            for (int suffix = 2; !names.add(unique); suffix++) {
                unique = name + " " + suffix;
            }
            double frame = marker.time() != null ? marker.time() : 0;
            double length = marker.duration() != null ? Math.max(0, marker.duration()) : 0;
            double start = Math.clamp(properties.time(frame, frameOffset), 0.0, duration);
            double end = Math.clamp(properties.time(frame + length, frameOffset), 0.0, duration);
            markers.add(new Marker(unique, start, end));
        }
        return markers;
    }
}
