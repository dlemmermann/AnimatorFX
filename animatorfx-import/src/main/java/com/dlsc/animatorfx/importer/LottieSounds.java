package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.dlsc.animatorfx.importer.LottieJson.get;

/**
 * Converts the audio layers of a Lottie document. Only sounds embedded in the file as a base64
 * {@code data:} URI are taken over; nothing is ever read from a path or a URL.
 */
final class LottieSounds {

    /** The largest sound that is taken over, as in the editor's own audio import. */
    static final int MAX_BYTES = 50 * 1024 * 1024;

    private static final String EXTERNAL = "Audio layers that reference external sound files were skipped";
    private static final String MISSING = "Audio layers whose sound is missing from the file were skipped";
    private static final String NO_SOUND = "Audio layers whose asset is not a sound were skipped";
    private static final String UNKNOWN_FORMAT = "Embedded sounds in a format that is not supported were skipped";
    private static final String UNDECODABLE = "Embedded sounds that cannot be decoded or are empty were skipped";
    private static final String TOO_LARGE = "Embedded sounds of more than 50 MB were skipped";

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("mpeg", "mp3"), Map.entry("mp3", "mp3"), Map.entry("mpeg3", "mp3"), Map.entry("x-mpeg", "mp3"),
            Map.entry("wav", "wav"), Map.entry("x-wav", "wav"), Map.entry("wave", "wav"),
            Map.entry("vnd.wave", "wav"),
            Map.entry("aiff", "aiff"), Map.entry("x-aiff", "aiff"),
            Map.entry("mp4", "m4a"), Map.entry("x-m4a", "m4a"), Map.entry("m4a", "m4a"),
            Map.entry("aac", "aac"), Map.entry("x-aac", "aac"), Map.entry("aacp", "aac"));

    /** Levels of the left and the right channel that stand for full volume. */
    private static final double[] FULL = {0, 0};
    /** Stands for levels that are no numbers. */
    private static final double[] UNKNOWN = {};

    /** What became of one Lottie asset: the id of the audio asset, or why there is none. */
    private record Sound(String assetId, String problem) {
    }

    private final Warnings warnings;
    private final LottieProperties properties;
    private final double duration;
    private final Map<String, Object> lottieAssets;
    private final List<Asset> assets;
    private final Map<String, byte[]> assetBytes;
    private final Map<String, Sound> sounds = new HashMap<>();

    /**
     * @param assets     the assets of the document that is built; sounds are added to it
     * @param assetBytes their content by asset id
     */
    LottieSounds(Warnings warnings, LottieProperties properties, double duration, Map<String, Object> lottieAssets,
                 List<Asset> assets, Map<String, byte[]> assetBytes) {
        this.warnings = warnings;
        this.properties = properties;
        this.duration = duration;
        this.lottieAssets = lottieAssets;
        this.assets = assets;
        this.assetBytes = assetBytes;
    }

    /**
     * The audio layer for a Lottie layer of type 6. It sounds from the in point {@code ip} to
     * the out point {@code op}. The sound itself begins at the start time {@code st} of the
     * layer: what lies between that and the in point is the clip offset. Transform, parent,
     * masks and mattes mean nothing to a sound and are left out.
     *
     * @return {@code null}, with a warning, if the sound is not embedded in the file or cannot be used
     */
    Layer layer(Object json, String name, double frameOffset) {
        String reference = LottieJson.string(json, "refId", "");
        Sound sound = sounds.computeIfAbsent(reference, this::sound);
        if (sound.assetId() == null) {
            warnings.add(sound.problem(), "layer", "layers");
            return null;
        }
        double in = LottieJson.has(json, "ip") ? clamp(properties.time(LottieJson.number(json, "ip", 0), frameOffset))
                : 0;
        double out = LottieJson.has(json, "op")
                ? clamp(properties.time(LottieJson.number(json, "op", 0), frameOffset)) : duration;
        double start = properties.time(LottieJson.number(json, "st", 0), frameOffset);
        double clipOffset = Limits.usable(start) ? Math.max(0, in - start) : 0;
        LayerBase base = LayerBase.create(name, duration)
                .withVisible(!LottieJson.flag(json, "hd")).withInPoint(in).withOutPoint(Math.max(in, out));
        return new AudioLayer(base, sound.assetId(), clipOffset, volume(json, frameOffset), Animatable.of(0.0), false,
                false);
    }

    private double clamp(double time) {
        return Math.clamp(time, 0.0, duration);
    }

    /**
     * The audio levels {@code au.lv} are {@code [left, right]} in decibels, 0 being the sound as
     * it is. The model has one volume from 0 to 1: the louder channel decides, and levels above
     * 0 dB are limited to full volume.
     */
    private Animatable<Double> volume(Object json, double frameOffset) {
        Object levels = get(get(json, "au"), "lv");
        if (levels == null) {
            return Animatable.of(1.0);
        }
        Animatable<double[]> channels = properties.read(levels, frameOffset, FULL, value -> {
            double[] numbers = LottieJson.numbers(value);
            return numbers == null || numbers.length == 0 ? UNKNOWN : numbers;
        });
        boolean unknown = channels.at(0) == UNKNOWN;
        for (Keyframe<double[]> keyframe : channels.keyframes()) {
            unknown |= keyframe.value() == UNKNOWN;
        }
        if (unknown) {
            warnings.add("Audio levels that are not understood are ignored; the sound plays at full volume", "layer",
                    "layers");
            return Animatable.of(1.0);
        }
        boolean different = differ(channels.at(0));
        for (Keyframe<double[]> keyframe : channels.keyframes()) {
            different |= differ(keyframe.value());
        }
        if (different) {
            warnings.add("Different audio levels for the left and right channel are not supported; the louder one"
                    + " is used", "layer", "layers");
        }
        return Keyframes.map(channels, LottieSounds::gain);
    }

    private static boolean differ(double[] levels) {
        return levels.length > 1 && levels[0] != levels[1];
    }

    private static double gain(double[] levels) {
        double decibels = levels.length > 1 ? Math.max(levels[0], levels[1]) : levels[0];
        double gain = Math.pow(10, decibels / 20);
        return Double.isNaN(gain) ? 1 : Math.clamp(gain, 0.0, 1.0);
    }

    private Sound sound(String reference) {
        Object asset = lottieAssets.get(reference);
        if (LottieJson.has(asset, "layers")) {
            return new Sound(null, NO_SOUND);
        }
        String source = LottieJson.string(asset, "p", null);
        if (source == null || source.isBlank()) {
            return new Sound(null, MISSING);
        }
        source = source.trim();
        if (!source.regionMatches(true, 0, "data:", 0, 5)) {
            return new Sound(null, EXTERNAL);
        }
        int comma = source.indexOf(',');
        String header = (comma < 0 ? source : source.substring(0, comma)).substring(5).toLowerCase(Locale.ROOT);
        String[] parts = header.split(";");
        String mime = parts[0].trim();
        if (!mime.startsWith("audio/")) {
            return new Sound(null, NO_SOUND);
        }
        String extension = EXTENSIONS.get(mime.substring("audio/".length()));
        if (extension == null) {
            return new Sound(null, UNKNOWN_FORMAT);
        }
        if (comma < 0 || !parts[parts.length - 1].trim().equals("base64")) {
            return new Sound(null, UNDECODABLE);
        }
        // four characters hold three bytes: decided before anything is decoded
        if ((source.length() - comma - 1) / 4L * 3 > MAX_BYTES + 3L) {
            return new Sound(null, TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(source.substring(comma + 1).replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return new Sound(null, UNDECODABLE);
        }
        if (bytes.length == 0) {
            return new Sound(null, UNDECODABLE);
        }
        if (bytes.length > MAX_BYTES) {
            return new Sound(null, TOO_LARGE);
        }
        String id = UUID.randomUUID().toString();
        String name = LottieJson.string(asset, "nm", "");
        assets.add(new Asset(id, Asset.Kind.AUDIO, "assets/" + id + "." + extension,
                name.isBlank() ? reference : name));
        assetBytes.put(id, bytes);
        return new Sound(id, null);
    }
}
