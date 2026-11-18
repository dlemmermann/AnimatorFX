package com.dlsc.animatorfx.sample;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationIO;
import com.dlsc.animatorfx.player.Evaluator;
import com.dlsc.animatorfx.player.Frame;
import com.dlsc.animatorfx.player.LayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The committed files of {@code samples/animations}: up to date with the generator, loadable,
 * and each showing the feature it is there for.
 */
class SampleFilesTest {

    private static final Path DIRECTORY = Path.of(System.getProperty("animatorfx.samples.dir", "../animations"));

    private static final boolean WRITE = Boolean.getBoolean("animatorfx.samples.write");

    @BeforeAll
    static void writeWhenAsked() throws IOException {
        if (WRITE) {
            SampleGenerator.writeAll(DIRECTORY);
        }
    }

    static Stream<String> names() {
        return SampleGenerator.all().keySet().stream();
    }

    private static Animation committed(String name) throws IOException {
        return AnimationIO.read(DIRECTORY.resolve(name + ".afx")).animation();
    }

    @ParameterizedTest
    @MethodSource("names")
    void theCommittedFileIsWhatTheGeneratorProduces(String name) throws IOException {
        assertEquals(SampleGenerator.all().get(name), committed(name),
                "Run the generator again, see SampleGenerator");
    }

    @ParameterizedTest
    @MethodSource("names")
    void theApplicationCarriesTheSampleAsAResource(String name) throws IOException {
        // the resources were copied before this run rewrote the files
        assumeFalse(WRITE, "the samples are being regenerated");
        AnimationFile resource = Samples.load(name);
        assertEquals(committed(name), resource.animation());
        assertTrue(resource.assets().isEmpty());
    }

    @Test
    void theBrowserShowsEverySampleButTheInPlaceOne() {
        Set<String> all = SampleGenerator.all().keySet();
        assertEquals(all.stream().filter(name -> !name.equals(Samples.ATTENTION)).toList(), Samples.SHOWCASE);
        assertTrue(all.contains(Samples.ATTENTION));
    }

    @ParameterizedTest
    @MethodSource("names")
    void everyFrameCanBeSampled(String name) throws IOException {
        Animation animation = committed(name);
        assertEquals(0, animation.background().a(), "samples are transparent");
        int layers = Layers.flatten(animation).size();
        for (int i = 0; i <= 60; i++) {
            Frame frame = Evaluator.sample(animation, animation.duration() * i / 60);
            assertEquals(layers, frame.layers().size());
            for (LayerState state : frame.layers().values()) {
                for (double value : state.matrix()) {
                    assertTrue(Double.isFinite(value));
                }
                assertTrue(state.opacity() >= 0 && state.opacity() <= 1);
            }
        }
    }

    @Test
    void theLoaderIsATrimmedArcWithRoundCapsThatLoopsSeamlessly() throws IOException {
        Animation loader = committed("loader");
        ShapeLayer arc = shape(loader, "loader-arc");
        assertTrue(arc.trim().start().isAnimated());
        assertTrue(arc.trim().end().isAnimated());
        assertTrue(arc.trim().offset().isAnimated());
        assertEquals(Stroke.Cap.ROUND, arc.stroke().cap());

        PathData first = Evaluator.sample(loader, 0).get("loader-arc").strokePath();
        PathData last = Evaluator.sample(loader, loader.duration()).get("loader-arc").strokePath();
        assertClose(ends(first), ends(last));
        PathData middle = Evaluator.sample(loader, loader.duration() / 2).get("loader-arc").strokePath();
        assertNotEquals(ends(first).get(1), ends(middle).get(1));
    }

    @Test
    void successHasBothMarkersAnOvershootingBadgeAndACheckThatDrawsOn() throws IOException {
        Animation success = committed("success");
        assertEquals(List.of("intro", "success"), success.markers().stream().map(Marker::name).toList());
        assertEquals(success.marker("intro").orElseThrow().end(), success.marker("success").orElseThrow().start());
        assertEquals(success.duration(), success.marker("success").orElseThrow().end());

        double largest = 0;
        for (int i = 0; i <= 55; i++) {
            largest = Math.max(largest, Evaluator.sample(success, i / 100.0).get("success-badge").scale().x());
        }
        assertTrue(largest > 1.05, "the badge overshoots, but its largest scale was " + largest);

        ShapeLayer check = shape(success, "success-check");
        assertTrue(check.trim().end().isAnimated());
        assertTrue(Evaluator.sample(success, 0.2).get("success-check").strokePath().contours().isEmpty());
        PathData drawn = Evaluator.sample(success, 1.3).get("success-check").strokePath();
        assertEquals(3, drawn.contours().get(0).vertices().size());

        // the loop of the settled state starts and ends alike
        Frame from = Evaluator.sample(success, 1.3);
        Frame to = Evaluator.sample(success, 2.6);
        assertEquals(from.get("success-badge").scale(), to.get("success-badge").scale());
        assertEquals(from.get("success-ring").opacity(), to.get("success-ring").opacity());
    }

    @Test
    void morphAnimatesAPathAndItsGradient() throws IOException {
        Animation morph = committed("morph");
        ShapeLayer icon = shape(morph, "morph-icon");
        Geometry.PathGeometry geometry = assertInstanceOf(Geometry.PathGeometry.class, icon.geometry());
        assertEquals(3, geometry.path().keyframes().stream().map(k -> k.value()).distinct().count());
        assertTrue(icon.fill().isAnimated());
        assertInstanceOf(Paint.LinearGradient.class, icon.fill().at(0));

        PathData play = Evaluator.sample(morph, 0).get("morph-icon").fillPath();
        PathData between = Evaluator.sample(morph, 0.85).get("morph-icon").fillPath();
        PathData pause = Evaluator.sample(morph, 1.4).get("morph-icon").fillPath();
        assertNotEquals(play, between);
        assertNotEquals(pause, between);
        assertEquals(play, Evaluator.sample(morph, morph.duration()).get("morph-icon").fillPath());
        assertNotEquals(icon.fill().at(0), icon.fill().at(0.85));
        assertNotEquals(icon.fill().at(1.4), icon.fill().at(0.85));
    }

    @Test
    void cardHasTwoSlotsAShadowAndAMaskedShine() throws IOException {
        Animation card = committed("card");
        assertEquals(List.of("title", "action"), Layers.slots(card).stream().map(SlotLayer::slotName).toList());
        for (SlotLayer slot : Layers.slots(card)) {
            assertTrue(slot.base().transform().position().isAnimated());
            assertTrue(slot.base().opacity().isAnimated());
            // the slot settles on its rest transform
            assertEquals(slot.restTransform().position().at(0), slot.base().transform().position().at(card.duration()));
        }
        assertTrue(Layers.find(card, "card-panel").orElseThrow().base().effects().stream()
                .anyMatch(Effect.DropShadow.class::isInstance));
        ShapeLayer shine = shape(card, "card-shine");
        assertEquals(new Mask("card-shine-mask", Mask.Mode.CLIP), shine.base().mask());
        assertInstanceOf(Paint.LinearGradient.class, shine.fill().at(0));
        assertTrue(shine.base().transform().position().isAnimated());
        // staggered: the title starts before the action
        assertTrue(Evaluator.sample(card, 0.5).get("card-title").opacity() > 0);
        assertEquals(0, Evaluator.sample(card, 0.5).get("card-action").opacity());
    }

    @Test
    void orbitMovesBodiesAlongOrientedMotionPathsAroundABlurredGlow() throws IOException {
        Animation orbit = committed("orbit");
        for (String id : List.of("orbit-outer-craft", "orbit-inner-craft")) {
            Layer craft = Layers.find(orbit, id).orElseThrow();
            assertNotNull(craft.base().motionPath());
            assertTrue(craft.base().motionPath().orientToPath());
            assertTrue(craft.base().motionPath().progress().isAnimated());
            LayerState start = Evaluator.sample(orbit, 0).get(id);
            LayerState quarter = Evaluator.sample(orbit, 1).get(id);
            LayerState end = Evaluator.sample(orbit, orbit.duration()).get(id);
            assertNotEquals(start.position(), quarter.position());
            assertNotEquals(start.rotation(), quarter.rotation());
            assertClose(List.of(start.position()), List.of(end.position()));
        }
        assertTrue(Layers.find(orbit, "orbit-glow").orElseThrow().base().effects().stream()
                .anyMatch(Effect.Blur.class::isInstance));
        // each trail ends at its craft
        for (String name : List.of("outer", "inner")) {
            for (double time : new double[]{0.3, 1.7, 3.1}) {
                Frame frame = Evaluator.sample(orbit, time);
                List<PathData.Contour> trail = frame.get("orbit-" + name + "-trail").strokePath().contours();
                List<PathData.Vertex> last = trail.get(trail.size() - 1).vertices();
                assertClose(List.of(frame.get("orbit-" + name + "-craft").position()),
                        List.of(last.get(last.size() - 1).point()));
            }
        }
    }

    @Test
    void attentionIsASingleSlotThatReturnsToRest() throws IOException {
        Animation attention = committed("attention");
        SlotLayer slot = Layers.slots(attention).get(0);
        assertEquals("target", slot.slotName());
        assertEquals(1, Layers.flatten(attention).size());
        LayerState moving = Evaluator.sample(attention, 0.1).get(slot.id());
        assertNotEquals(0, moving.rotation());
        assertNotEquals(Vec2.ONE, moving.scale());
        for (double time : new double[]{0, attention.duration()}) {
            LayerState rest = Evaluator.sample(attention, time).get(slot.id());
            assertEquals(0, rest.rotation());
            assertEquals(Vec2.ONE, rest.scale());
            assertEquals(slot.restTransform().position().at(0), rest.position());
        }
    }

    private static ShapeLayer shape(Animation animation, String id) {
        return assertInstanceOf(ShapeLayer.class, Layers.find(animation, id).orElseThrow());
    }

    /** The first and the last point of a path. */
    private static List<Vec2> ends(PathData path) {
        List<PathData.Vertex> first = path.contours().get(0).vertices();
        List<PathData.Vertex> last = path.contours().get(path.contours().size() - 1).vertices();
        return List.of(first.get(0).point(), last.get(last.size() - 1).point());
    }

    private static void assertClose(List<Vec2> expected, List<Vec2> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).x(), actual.get(i).x(), 1e-6);
            assertEquals(expected.get(i).y(), actual.get(i).y(), 1e-6);
        }
    }
}
