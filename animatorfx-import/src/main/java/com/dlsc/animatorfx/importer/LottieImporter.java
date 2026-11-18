package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationFormatException;
import com.dlsc.animatorfx.model.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dlsc.animatorfx.importer.LottieJson.get;
import static com.dlsc.animatorfx.importer.LottieJson.list;

/**
 * Converts a Lottie (Bodymovin) JSON animation to an animated document.
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
        Object root = Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        double width = dimension(root, "w", Double.MIN_VALUE, Limits.MAX_MAGNITUDE);
        double height = dimension(root, "h", Double.MIN_VALUE, Limits.MAX_MAGNITUDE);
        double frameRate = dimension(root, "fr", MIN_FRAME_RATE, MAX_FRAME_RATE);
        if (!(get(root, "layers") instanceof List<?>)) {
            throw new AnimationFormatException("Not a valid Lottie document: there are no layers", "layers");
        }
        double inPoint = LottieJson.number(root, "ip", 0);
        double outPoint = LottieJson.number(root, "op", inPoint);
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
        if (LottieJson.flag(root, "ddd")) {
            warnings.add("3D layers are not supported; they are treated as 2D", "layer", "layers");
        }
        int expressions = expressions(root);
        for (int i = 0; i < expressions; i++) {
            warnings.add("Expressions are not supported", "property", "properties");
        }
        Map<String, Object> assets = new HashMap<>();
        for (Object asset : list(root, "assets")) {
            assets.putIfAbsent(LottieJson.string(asset, "id", ""), asset);
        }
        Map<String, Object> fonts = new HashMap<>();
        for (Object font : list(get(root, "fonts"), "list")) {
            fonts.putIfAbsent(LottieJson.string(font, "fName", ""), font);
        }
        LottieProperties properties = new LottieProperties(warnings, frameRate);
        LottieLayers converter = new LottieLayers(warnings, properties, duration, assets, fonts);
        List<Layer> layers = converter.composition(list(root, "layers"), -inPoint);

        String documentName = LottieJson.string(root, "nm", "");
        Animation animation = DocumentCheck.repair(new Animation(Animation.CURRENT_FORMAT,
                documentName.isBlank() ? name : documentName, width, height, frameRate, duration, Rgba.TRANSPARENT,
                markers(list(root, "markers"), properties, -inPoint, duration), converter.assets(), layers),
                warnings);
        return new ImportResult(new AnimationFile(animation, converter.assetBytes()), warnings.lines());
    }

    private static double dimension(Object root, String key, double min, double max)
            throws AnimationFormatException {
        double value = LottieJson.number(root, key, Double.NaN);
        if (!(value >= min && value <= max)) {
            throw new AnimationFormatException("Not a valid Lottie document: \"" + key + "\" is "
                    + (Double.isNaN(value) ? "missing" : "out of range (" + value + ")"), key);
        }
        return value;
    }

    /** Counts the properties that carry an expression ({@code x}) next to their value ({@code k}). */
    private static int expressions(Object node) {
        int count = 0;
        if (node instanceof Map<?, ?> map) {
            if (map.get("x") instanceof String && map.containsKey("k")) {
                count++;
            }
            for (Object value : map.values()) {
                count += expressions(value);
            }
        } else if (node instanceof List<?> list) {
            for (Object value : list) {
                count += expressions(value);
            }
        }
        return count;
    }

    /**
     * Markers with their time {@code tm} and duration {@code dr} in frames. The comment
     * {@code cm} is the name, or a JSON object with a {@code name}. Names are made unique.
     */
    private static List<Marker> markers(List<Object> source, LottieProperties properties, double frameOffset,
                                        double duration) {
        List<Marker> markers = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object marker : source) {
            String name = LottieJson.string(marker, "cm", "").trim();
            if (name.startsWith("{")) {
                try {
                    name = LottieJson.string(Json.parse(name), "name", name).trim();
                } catch (AnimationFormatException e) {
                    // not JSON after all: the comment is the name
                }
            }
            if (name.isEmpty()) {
                name = "Marker " + (markers.size() + 1);
            }
            String unique = name;
            for (int suffix = 2; !names.add(unique); suffix++) {
                unique = name + " " + suffix;
            }
            double frame = LottieJson.number(marker, "tm", 0);
            double start = Math.clamp(properties.time(frame, frameOffset), 0.0, duration);
            double end = Math.clamp(properties.time(frame + Math.max(0, LottieJson.number(marker, "dr", 0)),
                    frameOffset), 0.0, duration);
            markers.add(new Marker(unique, start, end));
        }
        return markers;
    }
}
