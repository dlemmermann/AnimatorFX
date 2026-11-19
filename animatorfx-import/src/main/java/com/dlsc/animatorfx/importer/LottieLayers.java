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

import com.lottie4j.core.definition.BlendMode;
import com.lottie4j.core.definition.LayerType;
import com.lottie4j.core.definition.MatteMode;
import com.lottie4j.core.helper.LayerHierarchy;
import com.lottie4j.core.model.text.Font;
import com.lottie4j.core.model.text.FontList;
import com.lottie4j.core.model.text.TextData;
import com.lottie4j.core.model.text.TextKeyframe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts the layers of a Lottie composition. One instance converts one document.
 */
final class LottieLayers {

    /** As in the SVG importer: the ascent as a share of the font size. */
    private static final double ASCENT = 0.8;

    /** One layer of a composition on its way to the model. */
    private static final class Unit {
        com.lottie4j.core.model.layer.Layer source;
        String name;
        LottieTransforms.Placement placement;
        /** The converted layer; {@code null} if the layer is of a kind that is not converted. */
        Layer core;
        /** The layer for the layer's own mask, a sibling of {@code core}; or {@code null}. */
        Layer maskLayer;
        /** The layers this one follows, the closest first; empty for a layer without (usable) parent. */
        List<Unit> ancestors = List.of();
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
    private final Map<String, com.lottie4j.core.model.asset.Asset> lottieAssets;
    private final FontList fonts;
    private final List<Asset> assets = new ArrayList<>();
    private final Map<String, byte[]> assetBytes = new LinkedHashMap<>();
    private final Map<String, String> imageAssetIds = new HashMap<>();
    private final Set<String> openCompositions = new HashSet<>();

    /**
     * @param lottieAssets the assets of the document by id
     * @param fonts        the fonts of the document; {@code null} if it has none
     */
    LottieLayers(Warnings warnings, LottieProperties properties, double duration,
                 Map<String, com.lottie4j.core.model.asset.Asset> lottieAssets, FontList fonts) {
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
    List<Layer> composition(List<com.lottie4j.core.model.layer.Layer> layers, double frameOffset) {
        List<Unit> units = new ArrayList<>();
        Map<com.lottie4j.core.model.layer.Layer, Unit> unitOf = new IdentityHashMap<>();
        for (com.lottie4j.core.model.layer.Layer source : layers) {
            if (source == null) {
                continue;
            }
            Unit unit = new Unit();
            unit.source = source;
            unit.name = source.name() != null ? source.name() : "Layer " + (units.size() + 1);
            unit.placement = transforms.read(source.transform(), frameOffset);
            units.add(unit);
            unitOf.put(source, unit);
        }
        List<com.lottie4j.core.model.layer.Layer> sources = units.stream().map(unit -> unit.source).toList();
        Map<Integer, com.lottie4j.core.model.layer.Layer> byIndex = LayerHierarchy.byIndex(sources);
        for (Unit unit : units) {
            unit.ancestors = ancestors(unit, byIndex, unitOf);
        }
        for (Unit unit : units) {
            convert(unit, frameOffset);
        }
        matte(units, sources, byIndex, unitOf);

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
     * The layers a layer follows, the closest first. Empty if the layer has none, or if its
     * chain of ancestors is broken: one of them is missing, the chain runs in a circle, or it is
     * longer than a document may nest.
     */
    private List<Unit> ancestors(Unit unit, Map<Integer, com.lottie4j.core.model.layer.Layer> byIndex,
                                 Map<com.lottie4j.core.model.layer.Layer, Unit> unitOf) {
        if (unit.source.indexParent() == null) {
            return List.of();
        }
        // outermost first; empty for a circle, and ending early at an ancestor that does not exist
        List<com.lottie4j.core.model.layer.Layer> chain = LayerHierarchy.parentChain(unit.source, byIndex);
        if (chain.isEmpty() || chain.get(0).indexParent() != null) {
            warnings.add("Layers whose parent is missing or circular are treated as unparented", "layer", "layers");
            return List.of();
        }
        if (chain.size() > Limits.MAX_DEPTH) {
            warnings.add("Parent chains longer than " + Limits.MAX_DEPTH + " layers are not supported; the"
                    + " layers are treated as unparented", "layer", "layers");
            return List.of();
        }
        List<Unit> ancestors = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            ancestors.add(unitOf.get(chain.get(i)));
        }
        return ancestors;
    }

    /**
     * Converts the layer where it will end up: below one carrier group per ancestor. The carriers
     * count as levels of nesting like any other group.
     */
    private void convert(Unit unit, double frameOffset) {
        if (!limits.layer()) {
            return;
        }
        int ancestors = unit.ancestors.size();
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
        com.lottie4j.core.model.layer.Layer source = unit.source;
        if (source.layerType() == LayerType.AUDIO) {
            unit.audio = true;
            Layer sound = sounds.layer(source, unit.name, frameOffset);
            if (sound != null) {
                unit.core = sound;
                unit.tops = List.of(sound);
            }
            return;
        }
        // The mask layer is asked for before the content: a layer that cannot have its mask is
        // left out as a whole, and must not have used up the budget by then.
        com.lottie4j.core.model.layer.Mask mask = chooseMask(source);
        if (mask != null && !limits.layer()) {
            return;
        }
        if (flag(source.has3dLayers())) {
            warnings.add("3D layers are not supported; they are treated as 2D", "layer", "layers");
        }
        if (flag(source.autoRotate())) {
            warnings.add("Auto-orient is not supported", "layer", "layers");
        }
        if (source.blendMode() != null && source.blendMode() != BlendMode.NORMAL) {
            warnings.add("Blend modes are not supported; the layers are blended normally", "layer", "layers");
        }
        if (source.styles() != null && !source.styles().isEmpty()) {
            warnings.add("Layer styles are not supported", "layer", "layers");
        }

        double in = source.inPoint() != null ? clamp(properties.time(source.inPoint(), frameOffset)) : 0;
        double out = source.outPoint() != null ? clamp(properties.time(source.outPoint(), frameOffset)) : duration;
        LayerBase base = new LayerBase(UUID.randomUUID().toString(), unit.name, !source.isHidden(), false,
                in, Math.max(in, out), unit.placement.transform(), unit.placement.opacity(), null,
                effects.read(source, frameOffset), null);
        LayerType type = source.layerType();
        unit.core = switch (type) {
            case SHAPE -> new GroupLayer(base, shapeContent(source, frameOffset));
            case NULL -> new GroupLayer(base, List.of());
            case SOLD_COLOR -> solid(source, base);
            case IMAGE -> image(source, base);
            case TEXT -> text(source, base);
            case PRECOMPOSITION -> precomposition(source, base, frameOffset);
            case CAMERA -> ignored("Camera layers are not supported");
            case null -> ignored("Layers of an unknown type are not supported");
            default -> ignored("Layers of type " + type.value() + " are not supported");
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
        for (Unit ancestor : unit.ancestors) {
            LayerBase carrier = LayerBase.create(ancestor.name + " (parent transform)", duration)
                    .withTransform(ancestor.placement.transform());
            tops = new ArrayList<>(List.of(new GroupLayer(carrier, tops)));
        }
        unit.tops = tops;
    }

    private static boolean flag(Integer value) {
        return value != null && value != 0;
    }

    private List<Layer> shapeContent(com.lottie4j.core.model.layer.Layer source, double frameOffset) {
        if (source.shapes() == null || !limits.enter()) {
            return List.of();
        }
        try {
            return shapes.content(source.shapes(), frameOffset);
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

    private Layer solid(com.lottie4j.core.model.layer.Layer source, LayerBase base) {
        Rgba color = SvgColors.parse(source.solidColor() != null ? source.solidColor() : "#000000");
        Vec2 size = new Vec2(source.solidWidth() != null ? source.solidWidth() : 0,
                source.solidHeight() != null ? source.solidHeight() : 0);
        return new ShapeLayer(base, new Geometry.Rect(Animatable.of(size), Animatable.of(0.0)),
                Animatable.of(new Paint.Solid(color == null ? Rgba.BLACK : color)), null, null);
    }

    private Layer image(com.lottie4j.core.model.layer.Layer source, LayerBase base) {
        String reference = source.referenceId() != null ? source.referenceId() : "";
        com.lottie4j.core.model.asset.Asset asset = lottieAssets.get(reference);
        String uri = asset != null && asset.fileName() instanceof String name ? name : null;
        ImageData data = ImageData.fromDataUri(uri);
        if (data == null && uri != null && uri.trim().regionMatches(true, 0, "data:audio/", 0, 11)) {
            return ignored("Image layers whose asset is a sound are ignored");
        }
        if (data == null) {
            warnings.add("External images are not supported", "layer ignored", "layers ignored");
            return null;
        }
        String assetId = imageAssetIds.computeIfAbsent(reference, key -> {
            String id = UUID.randomUUID().toString();
            assets.add(new Asset(id, Asset.Kind.IMAGE, "assets/" + id + "." + data.extension(),
                    asset.name() != null ? asset.name() : reference));
            assetBytes.put(id, data.bytes());
            return id;
        });
        return new ImageLayer(base, assetId, asset.width() != null ? asset.width() : data.width(),
                asset.height() != null ? asset.height() : data.height());
    }

    /**
     * The first text document of the layer. The justification becomes the alignment, which
     * the player applies about the layer origin as Lottie does. Lottie places text by its
     * baseline, the model by its top: the difference goes into the anchor, with the ascent
     * estimated for want of the font.
     */
    private Layer text(com.lottie4j.core.model.layer.Layer source, LayerBase base) {
        TextData text = source.textData();
        List<TextKeyframe> documents = text == null || text.document() == null
                || text.document().keyframes() == null ? List.of() : text.document().keyframes();
        if (documents.isEmpty()) {
            return ignored("Text layers without text are ignored");
        }
        if (documents.size() > 1 || (text.animators() != null && !text.animators().isEmpty())) {
            warnings.add("Animated text is not supported; the first text is shown", "layer", "layers");
        }
        TextKeyframe document = documents.get(0);
        String content = document.getText().replace("\r\n", "\n").replace('\r', '\n').replace('\u0003', '\n');
        double size = document.getFontSize() != null ? document.getFontSize() : 12;
        String fontName = document.getFontFamily() != null ? document.getFontFamily() : "";
        Font font = fonts == null ? null : fonts.byName(fontName);
        String family = font != null && font.family() != null ? font.family()
                : fontName.isEmpty() ? "System" : fontName;
        Integer justification = document.getJustification();
        TextLayer.Align align = switch (justification == null ? 0 : justification) {
            case 1 -> TextLayer.Align.RIGHT;
            case 2 -> TextLayer.Align.CENTER;
            default -> TextLayer.Align.LEFT;
        };
        Vec2 origin = new Vec2(0, ASCENT * size);
        warnings.add("Text is placed approximately: the baseline is estimated without the font",
                "text", "texts");

        Rgba fill = LottieProperties.color(document.getFontColor(), null);
        Rgba strokeColor = LottieProperties.color(document.getStrokeColor(), null);
        double strokeWidth = document.getStrokeWidth() != null ? document.getStrokeWidth() : 0;
        Stroke stroke = strokeColor == null || !(strokeWidth > 0) ? null
                : new Stroke(Animatable.of(new Paint.Solid(strokeColor)), Animatable.of(strokeWidth), Stroke.Cap.BUTT,
                Stroke.Join.MITER, 4, List.of(), Animatable.of(0.0));
        Transform transform = base.transform();
        LayerBase placed = base.withTransform(
                transform.withAnchor(Keyframes.map(transform.anchor(), anchor -> anchor.plus(origin))));
        return new TextLayer(placed, content, family, null, Animatable.of(size),
                font != null && font.isBold() ? TextLayer.Weight.BOLD : TextLayer.Weight.NORMAL,
                font != null && font.isItalic(), align,
                Animatable.of(fill == null ? new Paint.None() : new Paint.Solid(fill)), stroke);
    }

    /** A precomposition is flattened into a group; its start time shifts everything inside. */
    private Layer precomposition(com.lottie4j.core.model.layer.Layer source, LayerBase base, double frameOffset) {
        String reference = source.referenceId() != null ? source.referenceId() : "";
        com.lottie4j.core.model.asset.Asset asset = lottieAssets.get(reference);
        if (asset == null || asset.layers() == null) {
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
            if (source.timeRemapping() != null) {
                warnings.add("Time remapping is not supported", "layer", "layers");
            }
            if (source.timeStretch() != null && source.timeStretch() != 1) {
                warnings.add("Time stretch is not supported; the layers play at normal speed", "layer", "layers");
            }
            warnings.add("The bounds of precompositions do not clip their content", "precomposition",
                    "precompositions");
            double start = source.startTime() != null ? source.startTime() : 0;
            return new GroupLayer(base, composition(asset.layers(), frameOffset + start));
        } finally {
            limits.leave();
            openCompositions.remove(reference);
        }
    }

    /** The mask of a layer that is converted: the first additive one, or {@code null}. */
    private com.lottie4j.core.model.layer.Mask chooseMask(com.lottie4j.core.model.layer.Layer source) {
        com.lottie4j.core.model.layer.Mask chosen = null;
        if (source.masks() == null) {
            return null;
        }
        for (com.lottie4j.core.model.layer.Mask mask : source.masks()) {
            String mode = mask.mode() != null ? mask.mode() : "a";
            if (mode.equals("n")) {
                continue;
            }
            if (chosen == null && mode.equals("a") && !Boolean.TRUE.equals(mask.inverted())) {
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
    private void mask(Unit unit, com.lottie4j.core.model.layer.Mask chosen, double frameOffset) {
        Animatable<PathData> path = properties.path(chosen.path(), frameOffset);
        Animatable<Double> opacity = transforms.opacity(chosen.opacity(), frameOffset);
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
    private void matte(List<Unit> units, List<com.lottie4j.core.model.layer.Layer> sources,
                       Map<Integer, com.lottie4j.core.model.layer.Layer> byIndex,
                       Map<com.lottie4j.core.model.layer.Layer, Unit> unitOf) {
        Set<Unit> used = new HashSet<>();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            MatteMode mode = unit.source.matteMode();
            if (mode == null || mode == MatteMode.NORMAL) {
                continue;
            }
            Unit source = matteSource(sources, i, byIndex, unitOf);
            if (source == null || source == unit || unit.audio || source.audio) {
                continue;
            }
            if (mode != MatteMode.ALPHA) {
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
            if (flag(unit.source.matteTarget())) {
                unit.hiddenMatte = true;
            }
            // a layer that masks another one is left out by the renderer; it need not be hidden
            if (used.contains(unit)) {
                unit.hiddenMatte = false;
            }
        }
    }

    /**
     * The layer a layer takes its matte from: the one it names with {@code tp}, or else the one
     * listed before it, whether or not that one is marked as a matte.
     */
    private static Unit matteSource(List<com.lottie4j.core.model.layer.Layer> sources, int position,
                                    Map<Integer, com.lottie4j.core.model.layer.Layer> byIndex,
                                    Map<com.lottie4j.core.model.layer.Layer, Unit> unitOf) {
        com.lottie4j.core.model.layer.Layer named = LayerHierarchy.matteSource(sources, position, byIndex);
        if (named != null) {
            return unitOf.get(named);
        }
        boolean names = sources.get(position).tp() != null;
        return names || position == 0 ? null : unitOf.get(sources.get(position - 1));
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
