package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationIO;
import com.dlsc.animatorfx.model.io.AnimationJson;
import com.dlsc.animatorfx.player.Evaluator;
import com.dlsc.animatorfx.player.Frame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared helpers of the importer tests. */
final class ImportChecks {

    static final double EPS = 1e-9;

    private ImportChecks() {
    }

    static InputStream resource(String path) {
        return java.util.Objects.requireNonNull(ImportChecks.class.getResourceAsStream(path), path);
    }

    static ImportResult svg(String fixture) throws IOException {
        try (InputStream in = resource("svg/" + fixture + ".svg")) {
            ImportResult result = SvgImporter.read(in, fixture);
            assertSavesAndRenders(result.file());
            return result;
        }
    }

    static ImportResult lottie(String fixture) throws IOException {
        try (InputStream in = resource("lottie/" + fixture + ".json")) {
            ImportResult result = LottieImporter.read(in, fixture);
            assertSavesAndRenders(result.file());
            return result;
        }
    }

    /**
     * The document must be accepted by the writer and the reader, read back as what was written,
     * and evaluate at any time.
     */
    static void assertSavesAndRenders(AnimationFile file) throws IOException {
        assertWithinLimits(file.animation());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AnimationIO.write(file, out);
        AnimationFile reread = AnimationIO.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(AnimationJson.toJson(file.animation()), AnimationJson.toJson(reread.animation()));
        assertEquals(file.assets().keySet(), reread.assets().keySet());
        for (String id : file.assets().keySet()) {
            assertArrayEquals(file.assets().get(id), reread.assets().get(id));
        }
        Animation animation = file.animation();
        List<Layer> all = Layers.flatten(animation);
        double duration = animation.duration();
        for (double time : new double[]{0, duration / 3, duration / 2, duration, duration + 1}) {
            Frame frame = Evaluator.sample(animation, time);
            for (Layer layer : all) {
                assertNotNull(frame.get(layer.id()), layer.name());
            }
        }
    }

    /**
     * What every import promises about its result, however the layers came about: at most
     * {@link Limits#MAX_LAYERS} layers in total and groups nested at most {@link Limits#MAX_DEPTH}
     * deep. Walks the tree without recursion, so it is safe on a tree that breaks the promise.
     */
    static void assertWithinLimits(Animation animation) {
        int[] measured = measure(animation);
        assertTrue(measured[0] <= Limits.MAX_LAYERS, "layers: " + measured[0]);
        assertTrue(measured[1] <= Limits.MAX_DEPTH, "nesting: " + measured[1]);
    }

    /** {@code {number of layers, deepest nesting}}; a top-level layer is at depth 1. */
    static int[] measure(Animation animation) {
        int count = 0;
        int deepest = 0;
        java.util.ArrayDeque<Layer> layers = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<Integer> depths = new java.util.ArrayDeque<>();
        for (Layer layer : animation.layers()) {
            layers.push(layer);
            depths.push(1);
        }
        while (!layers.isEmpty()) {
            Layer layer = layers.pop();
            int depth = depths.pop();
            count++;
            deepest = Math.max(deepest, depth);
            if (layer instanceof GroupLayer group) {
                for (Layer child : group.children()) {
                    layers.push(child);
                    depths.push(depth + 1);
                }
            }
        }
        return new int[]{count, deepest};
    }

    /**
     * Runs an import on a new thread with a small stack and hands on whatever it throws, so that
     * recursion without a bound fails for certain, whatever the JIT and the default stack size.
     */
    static <T> T onSmallStack(java.util.concurrent.Callable<T> action) throws IOException {
        Object[] outcome = new Object[2];
        Thread thread = new Thread(null, () -> {
            try {
                outcome[0] = action.call();
            } catch (Throwable e) {
                outcome[1] = e;
            }
        }, "small-stack", 256 * 1024);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        if (outcome[1] instanceof IOException declared) {
            throw declared;
        }
        if (outcome[1] != null) {
            throw new AssertionError("Failed on a small stack: " + outcome[1], (Throwable) outcome[1]);
        }
        @SuppressWarnings("unchecked")
        T result = (T) outcome[0];
        return result;
    }

    static Layer named(Animation animation, String name) {
        return Layers.flatten(animation).stream().filter(layer -> layer.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No layer named " + name + " in "
                        + Layers.flatten(animation).stream().map(Layer::name).toList()));
    }

    static <T extends Layer> T named(Animation animation, String name, Class<T> type) {
        return assertInstanceOf(type, named(animation, name));
    }

    static List<String> names(List<Layer> layers) {
        return layers.stream().map(Layer::name).toList();
    }

    static List<Layer> children(Layer group) {
        return assertInstanceOf(GroupLayer.class, group).children();
    }

    static void assertVec(double x, double y, Vec2 actual) {
        assertEquals(x, actual.x(), EPS, "x of " + actual);
        assertEquals(y, actual.y(), EPS, "y of " + actual);
    }

    static void assertVec(double x, double y, Vec2 actual, double tolerance) {
        assertEquals(x, actual.x(), tolerance, "x of " + actual);
        assertEquals(y, actual.y(), tolerance, "y of " + actual);
    }

    static void assertColor(double r, double g, double b, double a, Rgba actual) {
        assertEquals(r, actual.r(), 1e-6, "r of " + actual);
        assertEquals(g, actual.g(), 1e-6, "g of " + actual);
        assertEquals(b, actual.b(), 1e-6, "b of " + actual);
        assertEquals(a, actual.a(), 1e-6, "a of " + actual);
    }

    static void assertSolid(double r, double g, double b, double a, Paint actual) {
        assertColor(r, g, b, a, assertInstanceOf(Paint.Solid.class, actual).color());
    }

    static void assertMatrix(double[] expected, double[] actual, double tolerance) {
        for (int i = 0; i < 6; i++) {
            assertEquals(expected[i], actual[i], tolerance, "element " + i + " of " + java.util.Arrays.toString(actual)
                    + ", expected " + java.util.Arrays.toString(expected));
        }
    }
}
