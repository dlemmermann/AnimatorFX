package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.dlsc.animatorfx.importer.ImportChecks.names;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The safety net under every importer: whatever it is given, the result saves and reads back. */
class DocumentCheckTest {

    private static ShapeLayer shape(String name) {
        return new ShapeLayer(LayerBase.create(name, 1), new Geometry.Rect(Animatable.of(new Vec2(1, 1)),
                Animatable.of(0.0)), Animatable.of(new Paint.None()), null, null);
    }

    private static Layer masked(Layer layer, String maskId) {
        return layer.withBase(layer.base().withMask(new Mask(maskId, Mask.Mode.CLIP)));
    }

    private static Animation document(Layer... layers) {
        return Animation.create("test", 10, 10, 1).withLayers(List.of(layers));
    }

    private static Animation repaired(Animation animation, Warnings warnings) throws IOException {
        Animation result = DocumentCheck.repair(animation, warnings);
        ImportChecks.assertSavesAndRenders(new AnimationFile(result, Map.of()));
        return result;
    }

    @Test
    void aValidDocumentIsReturnedAsItIs() throws IOException {
        ShapeLayer mask = shape("mask");
        Animation animation = document(mask, masked(shape("a"), mask.id()),
                new GroupLayer(LayerBase.create("g", 1), List.of(shape("b"))));
        Warnings warnings = new Warnings();
        assertSame(animation, repaired(animation, warnings));
        assertEquals(List.of(), warnings.lines());
    }

    @Test
    void masksThatDoNotNameASiblingAreRemoved() throws IOException {
        ShapeLayer outside = shape("outside");
        ShapeLayer self = shape("self");
        Animation animation = document(outside,
                new GroupLayer(LayerBase.create("g", 1), List.of(masked(shape("inner"), outside.id()))),
                masked(self, self.id()), masked(shape("lost"), "no-such-layer"));
        Warnings warnings = new Warnings();
        Animation result = repaired(animation, warnings);
        assertEquals(List.of("outside", "g", "self", "lost"), names(result.layers()));
        assertNull(((GroupLayer) result.layers().get(1)).children().get(0).base().mask());
        assertNull(result.layers().get(2).base().mask());
        assertNull(result.layers().get(3).base().mask());
        assertEquals(List.of("Masks that do not refer to a layer beside the masked one were removed (3 masks)"),
                warnings.lines());
    }

    @Test
    void layersWithValuesThatAreNotFiniteAreDroppedAndTheirMasksWithThem() throws IOException {
        ShapeLayer nan = shape("nan");
        nan = nan.withBase(nan.base().withTransform(Transform.at(Double.NaN, 0)));
        ShapeLayer infinite = shape("infinite").withGeometry(new Geometry.Rect(
                Animatable.of(new Vec2(Double.POSITIVE_INFINITY, 1)), Animatable.of(0.0)));
        ShapeLayer large = shape("large");
        large = large.withBase(large.base().withOpacity(Animatable.of(1e12)));
        Animation animation = document(nan, masked(shape("kept"), nan.id()),
                new GroupLayer(LayerBase.create("g", 1), List.of(infinite, shape("child"))), large);
        Warnings warnings = new Warnings();
        Animation result = repaired(animation, warnings);
        assertEquals(List.of("kept", "g"), names(result.layers()));
        assertEquals(List.of("child"), names(((GroupLayer) result.layers().get(1)).children()));
        assertNull(result.layers().get(0).base().mask());
        assertEquals(List.of("Layers with values that are not finite or too large were dropped (3 layers)",
                "Masks that do not refer to a layer beside the masked one were removed (1 mask)"), warnings.lines());
    }

    @Test
    void repeatedIdsAndExcessiveNestingAreRepaired() throws IOException {
        ShapeLayer first = shape("first");
        Layer twin = shape("twin").withBase(shape("twin").base().withId(first.id()));
        Layer nested = shape("bottom");
        for (int i = 0; i < 70; i++) {
            nested = new GroupLayer(LayerBase.create("level", 1), List.of(nested));
        }
        Warnings warnings = new Warnings();
        Animation result = repaired(document(first, twin, nested), warnings);
        assertNotEquals(result.layers().get(0).id(), result.layers().get(1).id());
        int depth = 0;
        for (Layer layer = result.layers().get(2); layer instanceof GroupLayer group && !group.children().isEmpty();
             layer = group.children().get(0)) {
            depth++;
        }
        assertEquals(Limits.MAX_DEPTH - 1, depth);
        assertEquals(List.of("Nesting deeper than 64 levels is not supported; the deeper content was skipped",
                "Layers with an id that another layer has got a new id (1 layer)"), warnings.lines());
    }

    @Test
    void whatOnlyTheReaderOfTheFormatFindsIsDroppedToo() throws IOException {
        // an image layer without its asset: nothing the first pass looks for
        ImageLayer orphan = new ImageLayer(LayerBase.create("orphan", 1), "no-such-asset", 1, 1);
        ShapeLayer kept = shape("kept");
        Warnings warnings = new Warnings();
        Animation result = repaired(document(masked(kept, orphan.id()),
                new GroupLayer(LayerBase.create("g", 1), List.of(orphan))), warnings);
        assertEquals(List.of("kept", "g"), names(result.layers()));
        assertEquals(List.of(), ((GroupLayer) result.layers().get(1)).children());
        assertEquals(List.of("Masks that do not refer to a layer beside the masked one were removed (1 mask)",
                "Layers that the document format does not accept were dropped (1 layer)"), warnings.lines());
    }

    @Test
    void aDocumentThatCannotBeRepairedByDroppingLayersFails() {
        Animation animation = Animation.create("test", Double.NaN, 10, 1);
        assertThrows(IOException.class, () -> DocumentCheck.repair(animation, new Warnings()));
    }

    @Test
    void aTreeOfAnyDepthIsCutWithoutRecursion() throws IOException {
        Layer nested = shape("bottom");
        for (int i = 0; i < 50_000; i++) {
            nested = new GroupLayer(LayerBase.create("level", 1), List.of(nested));
        }
        Animation deep = document(shape("beside"), nested);
        Warnings warnings = new Warnings();
        Animation result = ImportChecks.onSmallStack(() -> DocumentCheck.repair(deep, warnings));
        assertEquals(Limits.MAX_DEPTH, ImportChecks.measure(result)[1]);
        assertEquals(Limits.MAX_DEPTH + 1, ImportChecks.measure(result)[0]);
        ImportChecks.assertSavesAndRenders(new AnimationFile(result, Map.of()));
        assertEquals(List.of("Nesting deeper than 64 levels is not supported; the deeper content was skipped"),
                warnings.lines());
    }

    @Test
    void layersBeyondTheLimitAreCutWhateverTheyAreThereFor() throws IOException {
        // 15,000 pairs of a mask and its layer: 30,000 layers
        List<Layer> layers = new java.util.ArrayList<>();
        for (int i = 0; i < 15_000; i++) {
            ShapeLayer mask = shape("mask");
            layers.add(masked(shape("masked"), mask.id()));
            layers.add(mask);
        }
        // and a group whose children would cross the limit
        Warnings warnings = new Warnings();
        Animation result = repaired(Animation.create("test", 10, 10, 1).withLayers(layers), warnings);
        assertEquals(Limits.MAX_LAYERS, ImportChecks.measure(result)[0]);
        // the limit falls between two pairs: every layer that stays has its mask
        for (int i = 0; i < result.layers().size(); i += 2) {
            assertEquals(result.layers().get(i + 1).id(), result.layers().get(i).base().mask().layerId());
        }
        assertEquals(List.of("Import stopped at 20000 layers; the rest of the file was skipped"), warnings.lines());

        List<Layer> children = new java.util.ArrayList<>();
        for (int i = 0; i < 25_000; i++) {
            children.add(shape("child"));
        }
        result = repaired(document(new GroupLayer(LayerBase.create("g", 1), children), shape("after")),
                new Warnings());
        assertEquals(Limits.MAX_LAYERS, ImportChecks.measure(result)[0]);
        assertEquals(List.of("g"), names(result.layers()));
    }

    @Test
    void aLayerThatOnlyMaskedDroppedLayersGoesWithThem() throws IOException {
        ShapeLayer source = shape("source");
        ShapeLayer shared = shape("shared");
        ShapeLayer chained = shape("chained");
        Layer broken = masked(shape("broken"), source.id());
        broken = broken.withBase(broken.base().withOpacity(Animatable.of(Double.NaN)));
        Layer alsoBroken = masked(shape("also broken"), shared.id());
        alsoBroken = alsoBroken.withBase(alsoBroken.base().withOpacity(Animatable.of(Double.NaN)));
        // source masks only the broken layer, and is itself masked by "chained", which nothing else uses
        Animation animation = document(chained, masked(source, chained.id()), broken, shared, alsoBroken,
                masked(shape("user"), shared.id()), shape("plain"));
        Warnings warnings = new Warnings();
        Animation result = repaired(animation, warnings);
        assertEquals(List.of("shared", "user", "plain"), names(result.layers()));
        assertEquals(shared.id(), result.layers().get(1).base().mask().layerId());
        assertEquals(List.of("Layers with values that are not finite or too large were dropped (2 layers)"),
                warnings.lines());
    }

    @Test
    void audioLayersAreCheckedLikeOtherLeafLayers() throws IOException {
        Asset sound = new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "snd.wav");
        AudioLayer good = AudioLayer.create("good", "snd", 0, 1);
        ShapeLayer shape = shape("shape");
        Animation valid = document(good, shape).withAssets(List.of(sound));
        Warnings warnings = new Warnings();
        Animation same = DocumentCheck.repair(valid, warnings);
        assertSame(valid, same);
        ImportChecks.assertSavesAndRenders(new AnimationFile(same, Map.of("snd", new byte[]{1, 2, 3})));
        assertEquals(List.of(), warnings.lines());

        AudioLayer orphan = AudioLayer.create("orphan", "no-such-asset", 0, 1);
        AudioLayer loud = AudioLayer.create("loud", "snd", 0, 1).withVolume(Animatable.of(2.0))
                .withBalance(Animatable.keyframed(List.of(new Keyframe<>("k1", 0, -3.0, Easing.LINEAR),
                        new Keyframe<>("k2", 1, 0.5, Easing.HOLD)))).withClipOffset(-4);
        AudioLayer nan = AudioLayer.create("nan", "snd", 0, 1).withClipOffset(Double.NaN);
        Animation result = DocumentCheck.repair(document(good, masked(shape, good.id()), orphan,
                new GroupLayer(LayerBase.create("g", 1), List.of(loud, nan))).withAssets(List.of(sound)), warnings);
        assertEquals(List.of("good", "shape", "g"), names(result.layers()));
        assertNull(result.layers().get(1).base().mask());
        AudioLayer limited = (AudioLayer) ((GroupLayer) result.layers().get(2)).children().get(0);
        assertEquals(loud.id(), limited.id());
        assertEquals(Animatable.of(1.0), limited.volume());
        assertEquals(List.of(new Keyframe<>("k1", 0, -1.0, Easing.LINEAR), new Keyframe<>("k2", 1, 0.5, Easing.HOLD)),
                limited.balance().keyframes());
        assertEquals(0, limited.clipOffset());
        assertEquals(1, ((GroupLayer) result.layers().get(2)).children().size());
        assertEquals(List.of("Audio layers without a sound were dropped (1 layer)",
                "Audio layers with values out of range were limited to their range (1 layer)",
                "Layers with values that are not finite or too large were dropped (1 layer)",
                "Masks that the document format does not accept were removed (1 layer)"), warnings.lines());
    }

    @Test
    void manyAudioLayersOutOfRangeDoNotFailTheImport() throws IOException {
        Asset sound = new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "snd.wav");
        List<Layer> layers = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            layers.add(AudioLayer.create("a" + i, i % 2 == 0 ? "snd" : "gone", 0, 1)
                    .withVolume(Animatable.of(-1.0)));
        }
        Warnings warnings = new Warnings();
        Animation result = DocumentCheck.repair(document(layers.toArray(Layer[]::new)).withAssets(List.of(sound)),
                warnings);
        assertEquals(20, result.layers().size());
        ImportChecks.assertSavesAndRenders(new AnimationFile(result, Map.of("snd", new byte[]{1})));
    }

    @Test
    void cuesThatTheFormatDoesNotAcceptAreRepairedOrDropped() throws IOException {
        Asset sound = new Asset("snd", Asset.Kind.AUDIO, "assets/snd.wav", "snd.wav");
        Asset image = new Asset("img", Asset.Kind.IMAGE, "assets/img.png", "img.png");
        SoundCue good = new SoundCue("c1", "good", 0.5, "snd", 0.8, false);
        Animation valid = document(shape("shape")).withAssets(List.of(sound, image)).withCues(List.of(good));
        Warnings warnings = new Warnings();
        assertSame(valid, DocumentCheck.repair(valid, warnings));
        assertEquals(List.of(), warnings.lines());

        List<SoundCue> cues = new ArrayList<>(List.of(good,
                new SoundCue("c1", "twin", 0.25, "snd", 1, true),
                new SoundCue("c3", "early", -2, "snd", 1, false),
                new SoundCue("c4", "late and loud", 7, "snd", 3, false),
                new SoundCue("c5", "silent", 0, "snd", Double.NaN, false),
                new SoundCue("c6", "never", Double.NaN, "snd", 1, false),
                new SoundCue("c7", "endless", Double.POSITIVE_INFINITY, "snd", 1, false),
                new SoundCue("c8", "picture", 0, "img", 1, false),
                new SoundCue("c9", "orphan", 0, "gone", 1, false)));
        for (int i = 0; i < 40; i++) {
            cues.add(new SoundCue("x" + i, "x", 0, "gone", 1, false));
        }
        Animation result = DocumentCheck.repair(valid.withCues(cues), warnings);
        assertEquals(List.of("good", "twin", "early", "late and loud", "silent"),
                result.cues().stream().map(SoundCue::name).toList());
        assertSame(good, result.cues().get(0));
        assertNotEquals("c1", result.cues().get(1).id());
        assertTrue(result.cues().get(1).muted());
        assertEquals(0, result.cues().get(2).time());
        assertEquals(1, result.cues().get(3).time());
        assertEquals(1, result.cues().get(3).volume());
        assertEquals(1, result.cues().get(4).volume());
        assertEquals(List.of("Sound cues with an id that another cue has got a new id (1 cue)",
                "Sound cues with a time or volume out of range were limited to their range (3 cues)",
                "Sound cues without a time were dropped (2 cues)",
                "Sound cues without a sound were dropped (42 cues)"), warnings.lines());
        ImportChecks.assertSavesAndRenders(new AnimationFile(result, Map.of("snd", new byte[]{1}, "img", new byte[]{2})));
    }
}
