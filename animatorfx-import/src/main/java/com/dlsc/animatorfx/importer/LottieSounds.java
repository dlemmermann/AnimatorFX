package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;

import com.lottie4j.core.model.layer.AudioSettings;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
    private static final double[] FULL = {100, 100};
    /** Stands for levels that are no numbers. */
    private static final double[] UNKNOWN = {};

    /** What became of one Lottie asset: the id of the audio asset, or why there is none. */
    private record Sound(String assetId, String problem) {
    }

    private final Warnings warnings;
    private final LottieProperties properties;
    private final double duration;
    private final Map<String, com.lottie4j.core.model.asset.Asset> lottieAssets;
    private final List<Asset> assets;
    private final Map<String, byte[]> assetBytes;
    private final Map<String, Sound> sounds = new HashMap<>();

    /**
     * @param assets     the assets of the document that is built; sounds are added to it
     * @param assetBytes their content by asset id
     */
    LottieSounds(Warnings warnings, LottieProperties properties, double duration, Map<String, com.lottie4j.core.model.asset.Asset> lottieAssets,
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
     * layer: what lies between that and the in point is the clip offset, and a layer that is in
     * before its sound starts begins at the start time instead. Transform, parent, masks and
     * mattes mean nothing to a sound and are left out.
     *
     * @return {@code null}, with a warning, if the sound is not embedded in the file or cannot be used
     */
    Layer layer(com.lottie4j.core.model.layer.Layer json, String name, double frameOffset) {
        String reference = json.referenceId() == null ? "" : json.referenceId();
        Sound sound = sounds.computeIfAbsent(reference, this::sound);
        if (sound.assetId() == null) {
            warnings.add(sound.problem(), "layer", "layers");
            return null;
        }
        double in = json.inPoint() != null ? clamp(properties.time(json.inPoint(), frameOffset)) : 0;
        double out = json.outPoint() != null ? clamp(properties.time(json.outPoint(), frameOffset)) : duration;
        double start = properties.time(json.startTime() != null ? json.startTime() : 0, frameOffset);
        double clipOffset = 0;
        if (Limits.usable(start)) {
            // the sound cannot be heard before it starts
            in = Math.max(in, Math.min(start, Math.max(in, out)));
            clipOffset = Math.max(0, in - start);
        }
        LayerBase base = LayerBase.create(name, duration)
                .withVisible(!json.isHidden()).withInPoint(in).withOutPoint(Math.max(in, out));
        return new AudioLayer(base, sound.assetId(), clipOffset, volume(json.audioSettings(), frameOffset), Animatable.of(0.0), false,
                false);
    }

    private double clamp(double time) {
        return Math.clamp(time, 0.0, duration);
    }

    /**
     * The audio levels {@code au.lv} are {@code [left, right]} in percent, 100 being the sound
     * as it is, which is how lottie-web reads them. The model has one volume from 0 to 1: the
     * louder channel decides, and levels above 100 are limited to full volume.
     */
    private Animatable<Double> volume(AudioSettings settings, double frameOffset) {
        if (settings == null || settings.level() == null) {
            return Animatable.of(1.0);
        }
        // levels that are no numbers are left out when the file is read: nothing remains of them
        List<?> values = settings.level().keyframes();
        Animatable<double[]> channels = properties.read(settings.level(), frameOffset, FULL,
                numbers -> numbers.length == 0 ? UNKNOWN : numbers);
        boolean unknown = values == null || values.isEmpty() || channels.at(0) == UNKNOWN;
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
        double percent = levels.length > 1 ? Math.max(levels[0], levels[1]) : levels[0];
        return Double.isNaN(percent) ? 1 : Math.clamp(percent / 100, 0.0, 1.0);
    }

    private Sound sound(String reference) {
        com.lottie4j.core.model.asset.Asset asset = lottieAssets.get(reference);
        if (asset != null && asset.layers() != null) {
            return new Sound(null, NO_SOUND);
        }
        if (asset == null || !(asset.fileName() instanceof String source) || source.isBlank()) {
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
        // four characters hold three bytes, white space aside: decided before anything is decoded
        int characters = 0;
        for (int i = comma + 1; i < source.length(); i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                characters++;
            }
        }
        if (characters / 4L * 3 > MAX_BYTES + 3L) {
            return new Sound(null, TOO_LARGE);
        }
        String payload = source.substring(comma + 1);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(characters == payload.length() ? payload
                    : payload.replaceAll("\\s", ""));
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
        String name = asset.name() == null ? "" : asset.name();
        assets.add(new Asset(id, Asset.Kind.AUDIO, "assets/" + id + "." + extension,
                name.isBlank() ? reference : name));
        assetBytes.put(id, bytes);
        return new Sound(id, null);
    }
}
