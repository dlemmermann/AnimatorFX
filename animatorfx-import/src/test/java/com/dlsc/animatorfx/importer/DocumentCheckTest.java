package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.dlsc.animatorfx.importer.ImportChecks.names;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
