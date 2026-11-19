package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.dlsc.animatorfx.importer.LottieJson.get;
import static com.dlsc.animatorfx.importer.LottieJson.list;

/**
 * Converts the layers of a Lottie composition. One instance converts one document.
 */
final class LottieLayers {

    private static final int PRECOMPOSITION = 0;
    private static final int SOLID = 1;
    private static final int IMAGE = 2;
    private static final int NULL = 3;
    private static final int SHAPE = 4;
    private static final int TEXT = 5;
    private static final int AUDIO = 6;
    private static final int CAMERA = 13;

    /** As in the SVG importer: the ascent as a share of the font size. */
    private static final double ASCENT = 0.8;

    /** One layer of a composition on its way to the model. */
    private static final class Unit {
        Object json;
        String name;
        LottieTransforms.Placement placement;
        /** The converted layer; {@code null} if the layer is of a kind that is not converted. */
        Layer core;
        /** The layer for the layer's own mask, a sibling of {@code core}; or {@code null}. */
        Layer maskLayer;
        Unit parent;
        /** What stands for the layer among its siblings, bottom to top. */
        List<Layer> tops = List.of();
        boolean hiddenMatte;
        /** The unit whose alpha this one takes as its track matte, once that is settled. */
        Unit matteSource;
        /** A sound: it takes no part in masks and mattes and cannot be hidden as one. */
        boolean audio;
    }

    private final Warnings warnings;
    private final LottieProperties properties;
    private final LottieTransforms transforms;
    private final LottieShapes shapes;
    private final LottieEffects effects;
    private final LottieSounds sounds;
    private final Limits limits;
    private final double duration;
    private final Map<String, Object> lottieAssets;
    private final Map<String, Object> fonts;
    private final List<Asset> assets = new ArrayList<>();
    private final Map<String, byte[]> assetBytes = new LinkedHashMap<>();
    private final Map<String, String> imageAssetIds = new HashMap<>();
    private final Set<String> openCompositions = new HashSet<>();

    /**
     * @param lottieAssets the assets of the document by id
     * @param fonts        the fonts of the document by name
     */
    LottieLayers(Warnings warnings, LottieProperties properties, double duration, Map<String, Object> lottieAssets,
                 Map<String, Object> fonts) {
        this.warnings = warnings;
        this.properties = properties;
        this.transforms = new LottieTransforms(properties, warnings);
        this.limits = new Limits(warnings);
        this.shapes = new LottieShapes(properties, transforms, warnings, limits, duration);
        this.effects = new LottieEffects(properties, warnings);
        this.duration = duration;
        this.lottieAssets = lottieAssets;
        this.fonts = fonts;
        this.sounds = new LottieSounds(warnings, properties, duration, lottieAssets, assets, assetBytes);
    }

    List<Asset> assets() {
        return assets;
    }

    Map<String, byte[]> assetBytes() {
        return assetBytes;
    }

    /**
     * The layers of a composition, bottom to top; Lottie lists them top-most first.
     *
     * @param frameOffset added to every frame number of the composition, see {@link LottieProperties#time}
     */
    List<Layer> composition(List<Object> layers, double frameOffset) {
        List<Unit> units = new ArrayList<>();
        Map<Integer, Unit> byIndex = new HashMap<>();
        for (Object json : layers) {
            Unit unit = new Unit();
            unit.json = json;
            unit.name = LottieJson.string(json, "nm", "Layer " + (units.size() + 1));
            unit.placement = transforms.read(get(json, "ks"), frameOffset);
            units.add(unit);
            if (get(json, "ind") instanceof Number index) {
                byIndex.putIfAbsent(index.intValue(), unit);
            }
        }
        for (Unit unit : units) {
            unit.parent = parent(unit, byIndex);
        }
        for (Unit unit : units) {
            convert(unit, frameOffset);
        }
        matte(units, byIndex);

        List<Layer> result = new ArrayList<>();
        for (int i = units.size() - 1; i >= 0; i--) {
            Unit unit = units.get(i);
            for (Layer top : unit.tops) {
                result.add(unit.hiddenMatte && !unit.audio ? top.withBase(top.base().withVisible(false)) : top);
            }
        }
        return result;
    }

    /**
     * The parent layer, or {@code null} if the layer has none or its chain of ancestors is
     * broken: one of them is missing, or the chain runs in a circle.
     */
    private Unit parent(Unit unit, Map<Integer, Unit> byIndex) {
        Set<Unit> seen = new HashSet<>();
        seen.add(unit);
        Unit current = unit;
        while (get(current.json, "parent") instanceof Number index) {
            if (seen.size() > Limits.MAX_DEPTH) {
                warnings.add("Parent chains longer than " + Limits.MAX_DEPTH + " layers are not supported; the"
                        + " layers are treated as unparented", "layer", "layers");
                return null;
            }
            Unit next = byIndex.get(index.intValue());
            if (next == null || !seen.add(next)) {
                warnings.add("Layers whose parent is missing or circular are treated as unparented", "layer",
                        "layers");
                return null;
            }
            current = next;
        }
        return get(unit.json, "parent") instanceof Number index ? byIndex.get(index.intValue()) : null;
    }

    /**
     * Converts the layer where it will end up: below one carrier group per ancestor. The carriers
     * count as levels of nesting like any other group.
     */
    private void convert(Unit unit, double frameOffset) {
        if (!limits.layer()) {
            return;
        }
        int ancestors = 0;
        for (Unit ancestor = unit.parent; ancestor != null; ancestor = ancestor.parent) {
            ancestors++;
        }
        int entered = 0;
        try {
            // levels and layers for the carriers first: the content must not use them up
            while (entered < ancestors) {
                if (!limits.enter()) {
                    return;
                }
                entered++;
                if (!limits.layer()) {
                    return;
                }
            }
            build(unit, frameOffset);
        } finally {
            while (entered-- > 0) {
                limits.leave();
            }
        }
    }

    private void build(Unit unit, double frameOffset) {
        Object json = unit.json;
        int type = (int) LottieJson.number(json, "ty", -1);
        if (type == AUDIO) {
            unit.audio = true;
            Layer sound = sounds.layer(json, unit.name, frameOffset);
            if (sound != null) {
                unit.core = sound;
                unit.tops = List.of(sound);
            }
            return;
        }
        // The mask layer is asked for before the content: a layer that cannot have its mask is
        // left out as a whole, and must not have used up the budget by then.
        Object mask = chooseMask(json);
        if (mask != null && !limits.layer()) {
            return;
        }
        if (LottieJson.flag(json, "ddd")) {
            warnings.add("3D layers are not supported; they are treated as 2D", "layer", "layers");
        }
        if (LottieJson.flag(json, "ao")) {
            warnings.add("Auto-orient is not supported", "layer", "layers");
        }
        if (LottieJson.number(json, "bm", 0) != 0) {
            warnings.add("Blend modes are not supported; the layers are blended normally", "layer", "layers");
        }
        if (!list(json, "sy").isEmpty()) {
            warnings.add("Layer styles are not supported", "layer", "layers");
        }

        double in = LottieJson.has(json, "ip") ? clamp(properties.time(LottieJson.number(json, "ip", 0), frameOffset))
                : 0;
        double out = LottieJson.has(json, "op")
                ? clamp(properties.time(LottieJson.number(json, "op", 0), frameOffset)) : duration;
        LayerBase base = new LayerBase(UUID.randomUUID().toString(), unit.name, !LottieJson.flag(json, "hd"), false,
                in, Math.max(in, out), unit.placement.transform(), unit.placement.opacity(), null,
                effects.read(json, frameOffset), null);
        unit.core = switch (type) {
            case SHAPE -> new GroupLayer(base, shapeContent(json, frameOffset));
            case NULL -> new GroupLayer(base, List.of());
            case SOLID -> solid(json, base);
            case IMAGE -> image(json, base);
            case TEXT -> text(json, base);
            case PRECOMPOSITION -> precomposition(json, base, frameOffset);
            case CAMERA -> ignored("Camera layers are not supported");
            default -> ignored("Layers of type " + type + " are not supported");
        };
        if (unit.core == null) {
            return;
        }
        if (mask != null) {
            mask(unit, mask, frameOffset);
        }

        List<Layer> tops = new ArrayList<>();
        if (unit.maskLayer != null) {
            tops.add(unit.maskLayer);
        }
        tops.add(unit.core);
        // A child follows the transform of its parent, but neither its opacity nor its time
        // range: a group with a copy of the parent's transform carries it, one per ancestor.
        for (Unit ancestor = unit.parent; ancestor != null; ancestor = ancestor.parent) {
            LayerBase carrier = LayerBase.create(ancestor.name + " (parent transform)", duration)
                    .withTransform(ancestor.placement.transform());
            tops = new ArrayList<>(List.of(new GroupLayer(carrier, tops)));
        }
        unit.tops = tops;
    }

    private List<Layer> shapeContent(Object json, double frameOffset) {
        if (!limits.enter()) {
            return List.of();
        }
        try {
            return shapes.content(list(json, "shapes"), frameOffset);
        } finally {
            limits.leave();
        }
    }

    private double clamp(double time) {
        return Math.clamp(time, 0.0, duration);
    }

    private Layer ignored(String message) {
        warnings.add(message, "layer ignored", "layers ignored");
        return null;
    }

    private Layer solid(Object json, LayerBase base) {
        Rgba color = SvgColors.parse(LottieJson.string(json, "sc", "#000000"));
        Vec2 size = new Vec2(LottieJson.number(json, "sw", 0), LottieJson.number(json, "sh", 0));
        return new ShapeLayer(base, new Geometry.Rect(Animatable.of(size), Animatable.of(0.0)),
                Animatable.of(new Paint.Solid(color == null ? Rgba.BLACK : color)), null, null);
    }

    private Layer image(Object json, LayerBase base) {
        String reference = LottieJson.string(json, "refId", "");
        Object asset = lottieAssets.get(reference);
        ImageData data = ImageData.fromDataUri(LottieJson.string(asset, "p", null));
        if (data == null) {
            warnings.add("External images are not supported", "layer ignored", "layers ignored");
            return null;
        }
        String assetId = imageAssetIds.computeIfAbsent(reference, key -> {
            String id = UUID.randomUUID().toString();
            assets.add(new Asset(id, Asset.Kind.IMAGE, "assets/" + id + "." + data.extension(),
                    LottieJson.string(asset, "nm", reference)));
            assetBytes.put(id, data.bytes());
            return id;
        });
        return new ImageLayer(base, assetId, LottieJson.number(asset, "w", data.width()),
                LottieJson.number(asset, "h", data.height()));
    }

    /**
     * The first text document of the layer. The justification becomes the alignment, which
     * the player applies about the layer origin as Lottie does. Lottie places text by its
     * baseline, the model by its top: the difference goes into the anchor, with the ascent
     * estimated for want of the font.
     */
    private Layer text(Object json, LayerBase base) {
        Object text = get(json, "t");
        List<Object> documents = list(get(text, "d"), "k");
        if (documents.isEmpty()) {
            return ignored("Text layers without text are ignored");
        }
        if (documents.size() > 1 || !list(text, "a").isEmpty()) {
            warnings.add("Animated text is not supported; the first text is shown", "layer", "layers");
        }
        Object document = get(documents.get(0), "s");
        String content = LottieJson.string(document, "t", "").replace("\r\n", "\n").replace('\r', '\n')
                .replace('\u0003', '\n');
        double size = LottieJson.number(document, "s", 12);
        String fontName = LottieJson.string(document, "f", "");
        Object font = fonts.get(fontName);
        String family = LottieJson.string(font, "fFamily", fontName.isEmpty() ? "System" : fontName);
        String style = LottieJson.string(font, "fStyle", "").toLowerCase(Locale.ROOT);
        String weight = LottieJson.string(font, "fWeight", "");
        boolean bold = style.contains("bold") || style.contains("black") || style.contains("heavy")
                || SvgLength.fraction(weight, 400, null) >= 600;
        TextLayer.Align align = switch ((int) LottieJson.number(document, "j", 0)) {
            case 1 -> TextLayer.Align.RIGHT;
            case 2 -> TextLayer.Align.CENTER;
            default -> TextLayer.Align.LEFT;
        };
        Vec2 origin = new Vec2(0, ASCENT * size);
        warnings.add("Text is placed approximately: the baseline is estimated without the font",
                "text", "texts");

        Rgba fill = LottieProperties.color(LottieJson.numbers(get(document, "fc")), null);
        Rgba strokeColor = LottieProperties.color(LottieJson.numbers(get(document, "sc")), null);
        double strokeWidth = LottieJson.number(document, "sw", 0);
        Stroke stroke = strokeColor == null || !(strokeWidth > 0) ? null
                : new Stroke(Animatable.of(new Paint.Solid(strokeColor)), Animatable.of(strokeWidth), Stroke.Cap.BUTT,
                Stroke.Join.MITER, 4, List.of(), Animatable.of(0.0));
        Transform transform = base.transform();
        LayerBase placed = base.withTransform(
                transform.withAnchor(Keyframes.map(transform.anchor(), anchor -> anchor.plus(origin))));
        return new TextLayer(placed, content, family, null, Animatable.of(size),
                bold ? TextLayer.Weight.BOLD : TextLayer.Weight.NORMAL,
                style.contains("italic") || style.contains("oblique"), align,
                Animatable.of(fill == null ? new Paint.None() : new Paint.Solid(fill)), stroke);
    }

    /** A precomposition is flattened into a group; its start time shifts everything inside. */
    private Layer precomposition(Object json, LayerBase base, double frameOffset) {
        String reference = LottieJson.string(json, "refId", "");
        Object asset = lottieAssets.get(reference);
        if (!LottieJson.has(asset, "layers")) {
            return ignored("Precompositions that are missing from the file are ignored");
        }
        if (!openCompositions.add(reference)) {
            return ignored("Precompositions that contain themselves are ignored");
        }
        if (!limits.enter()) {
            openCompositions.remove(reference);
            return new GroupLayer(base, List.of());
        }
        try {
            if (LottieJson.has(json, "tm")) {
                warnings.add("Time remapping is not supported", "layer", "layers");
            }
            if (LottieJson.number(json, "sr", 1) != 1) {
                warnings.add("Time stretch is not supported; the layers play at normal speed", "layer", "layers");
            }
            warnings.add("The bounds of precompositions do not clip their content", "precomposition",
                    "precompositions");
            double start = LottieJson.number(json, "st", 0);
            return new GroupLayer(base, composition(list(asset, "layers"), frameOffset + start));
        } finally {
            limits.leave();
            openCompositions.remove(reference);
        }
    }

    /** The mask of a layer that is converted: the first additive one, or {@code null}. */
    private Object chooseMask(Object json) {
        Object chosen = null;
        for (Object mask : list(json, "masksProperties")) {
            String mode = LottieJson.string(mask, "mode", "a");
            if (mode.equals("n")) {
                continue;
            }
            if (chosen == null && mode.equals("a") && !LottieJson.flag(mask, "inv")) {
                chosen = mask;
            } else {
                warnings.add("Only one additive mask per layer is supported", "mask ignored", "masks ignored");
            }
        }
        return chosen;
    }

    /**
     * The mask path is in the coordinates of the layer, so the mask layer, a sibling as the
     * model wants it, gets the same transform.
     */
    private void mask(Unit unit, Object chosen, double frameOffset) {
        Animatable<PathData> path = properties.path(get(chosen, "pt"), frameOffset);
        Animatable<Double> opacity = transforms.opacity(get(chosen, "o"), frameOffset);
        boolean opaque = !opacity.isAnimated() && opacity.at(0) >= 1;
        LayerBase base = LayerBase.create(unit.name + " (mask)", duration)
                .withTransform(unit.placement.transform()).withOpacity(opacity);
        unit.maskLayer = new ShapeLayer(base, new Geometry.PathGeometry(path),
                Animatable.of(new Paint.Solid(Rgba.WHITE)), null, null);
        unit.core = unit.core.withBase(unit.core.base()
                .withMask(new Mask(unit.maskLayer.id(), opaque ? Mask.Mode.CLIP : Mask.Mode.ALPHA)));
    }

    /**
     * Track mattes: a layer with {@code tt} takes its alpha from the layer above it (or the one
     * named by {@code tp}), which is not drawn itself. Masked layer and matte end up as two
     * layers of the same list, which is what the model asks of a mask. A matte may be matted
     * itself; mattes that lead back to their own layer are declined.
     */
    private void matte(List<Unit> units, Map<Integer, Unit> byIndex) {
        Set<Unit> used = new HashSet<>();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            int mode = (int) LottieJson.number(unit.json, "tt", 0);
            if (mode == 0) {
                continue;
            }
            Unit source = get(unit.json, "tp") instanceof Number index ? byIndex.get(index.intValue())
                    : i > 0 ? units.get(i - 1) : null;
            if (source == null || source == unit || unit.audio || source.audio) {
                continue;
            }
            if (mode != 1) {
                source.hiddenMatte = true;
                warnings.add("Inverted and luminance track mattes are not supported", "matte ignored",
                        "mattes ignored");
                continue;
            }
            if (source.tops.isEmpty() || unit.tops.isEmpty()) {
                source.hiddenMatte = true;
                continue;
            }
            boolean circular = false;
            for (Unit matte = source; matte != null && !circular; matte = matte.matteSource) {
                circular = matte == unit;
            }
            if (circular) {
                warnings.add("Track mattes that refer to each other are not supported", "matte ignored",
                        "mattes ignored");
                continue;
            }
            if (!single(source) || !single(unit)) {
                continue;
            }
            Layer masked = unit.tops.get(0);
            if (masked.base().mask() != null) {
                // cannot happen as units are built; a second mask would orphan the first
                warnings.add("Layers that are masked already take no track matte", "matte ignored", "mattes ignored");
                continue;
            }
            unit.tops = List.of(masked.withBase(masked.base()
                    .withMask(new Mask(source.tops.get(0).id(), Mask.Mode.ALPHA))));
            unit.matteSource = source;
            source.hiddenMatte = true;
            used.add(source);
        }
        for (Unit unit : units) {
            if (LottieJson.flag(unit.json, "td")) {
                unit.hiddenMatte = true;
            }
            // a layer that masks another one is left out by the renderer; it need not be hidden
            if (used.contains(unit)) {
                unit.hiddenMatte = false;
            }
        }
    }

    /**
     * Makes one layer stand for the unit, so that it can mask or be masked. A unit that is two
     * layers (the layer and its own mask) goes into a group; the mask inside stays beside its layer.
     */
    private boolean single(Unit unit) {
        if (unit.tops.size() > 1) {
            if (!limits.layer()) {
                return false;
            }
            unit.tops = List.of(new GroupLayer(LayerBase.create(unit.name, duration), unit.tops));
        }
        return true;
    }
}
