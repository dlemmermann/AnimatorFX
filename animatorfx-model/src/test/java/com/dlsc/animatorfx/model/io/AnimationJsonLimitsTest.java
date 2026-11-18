package com.dlsc.animatorfx.model.io;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Documents at the edge of what the format takes: many masks, deep nesting. */
class AnimationJsonLimitsTest {

    private static ShapeLayer shape(String name) {
        return new ShapeLayer(LayerBase.create(name, 1), new Geometry.Rect(Animatable.of(new Vec2(1, 1)),
                Animatable.of(0.0)), Animatable.of(new Paint.None()), null, null);
    }

    private static Animation nested(int levels) {
        Layer layer = shape("bottom");
        for (int i = 1; i < levels; i++) {
            layer = new GroupLayer(LayerBase.create("level", 1), List.of(layer));
        }
        return Animation.create("deep", 10, 10, 1).withLayers(List.of(layer));
    }

    /** Runs the action on a thread with a small stack, where unbounded recursion overflows for certain. */
    private static void onSmallStack(Runnable action) throws InterruptedException {
        onStack(256 * 1024, action);
    }

    private static void onStack(long bytes, Runnable action) throws InterruptedException {
        Throwable[] failure = new Throwable[1];
        Thread thread = new Thread(null, () -> {
            try {
                action.run();
            } catch (Throwable e) {
                failure[0] = e;
            }
        }, "small-stack", bytes);
        thread.start();
        thread.join();
        if (failure[0] != null) {
            throw new AssertionError("Failed on a small stack: " + failure[0], failure[0]);
        }
    }

    @Test
    void readingManyMaskedLayersTakesLinearTime() throws Exception {
        List<Layer> layers = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            ShapeLayer mask = shape("mask");
            ShapeLayer masked = shape("masked");
            layers.add(mask);
            layers.add(masked.withBase(masked.base().withMask(new Mask(mask.id(), Mask.Mode.CLIP))));
        }
        Animation animation = Animation.create("masks", 10, 10, 1).withLayers(layers);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AnimationIO.write(new AnimationFile(animation, Map.of()), out);
        byte[] bytes = out.toByteArray();
        // warm up, then measure: the time must not grow with the square of the number of siblings
        AnimationIO.read(new ByteArrayInputStream(bytes));
        Animation read = assertTimeout(Duration.ofSeconds(2),
                () -> AnimationIO.read(new ByteArrayInputStream(bytes)).animation());
        assertEquals(40_000, read.layers().size());
        assertEquals(read.layers().get(0).id(), read.layers().get(1).base().mask().layerId());
    }

    @Test
    void nestingUpToTheLimitIsWrittenAndRead() throws Exception {
        Animation animation = nested(AnimationJson.MAX_LAYER_DEPTH);
        // a quarter of the default stack of a thread: the limit leaves room
        onStack(512 * 1024, () -> {
            try {
                Map<String, Object> json = AnimationJson.toJson(animation);
                assertEquals(json, AnimationJson.toJson(AnimationJson.fromJson(json)));
                // what is written can also be parsed again: the JSON text is not nested too deeply
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                AnimationIO.write(new AnimationFile(animation, Map.of()), out);
                AnimationIO.read(new ByteArrayInputStream(out.toByteArray()));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void writingDeeperNestingIsRefusedInsteadOfOverflowingTheStack() throws Exception {
        onSmallStack(() -> {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> AnimationJson.toJson(nested(AnimationJson.MAX_LAYER_DEPTH + 1)));
            assertTrue(refused.getMessage().contains("nested"), refused.getMessage());
            assertThrows(IllegalArgumentException.class, () -> AnimationJson.toJson(nested(50_000)));
            assertThrows(IllegalArgumentException.class, () -> AnimationIO.write(
                    new AnimationFile(nested(50_000), Map.of()), new ByteArrayOutputStream()));
        });
    }

    @Test
    void readingDeeperNestingIsRejectedInsteadOfOverflowingTheStack() throws Exception {
        // built by hand: no parser and no writer limits the depth of a tree in memory
        Map<String, Object> innermost = new LinkedHashMap<>();
        innermost.put("type", "group");
        Map<String, Object> layer = innermost;
        for (int i = 1; i < 50_000; i++) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("type", "group");
            group.put("children", List.of(layer));
            layer = group;
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("formatVersion", 1.0);
        document.put("width", 10.0);
        document.put("height", 10.0);
        document.put("duration", 1.0);
        document.put("layers", List.of(layer));
        onSmallStack(() -> {
            AnimationFormatException rejected = assertThrows(AnimationFormatException.class,
                    () -> AnimationJson.fromJson(document));
            assertTrue(rejected.getMessage().contains("nested"), rejected.getMessage());
            assertTrue(rejected.jsonPath().startsWith("layers[0].children[0]"), rejected.jsonPath());
        });
    }
}
