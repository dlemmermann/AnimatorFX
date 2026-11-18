package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Geometries;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerProperties;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.MotionPath;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Trim;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dlsc.animatorfx.editor.doc.CommandsTest.doc;
import static com.dlsc.animatorfx.editor.doc.CommandsTest.group;
import static com.dlsc.animatorfx.editor.doc.CommandsTest.rect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The commands behind masks, effects, trim, motion paths and convert-to-path.
 */
class FeatureCommandsTest {

    private static final PathData LINE = new PathData(List.of(new PathData.Contour(List.of(
            new PathData.Vertex(new Vec2(10, 10), Vec2.ZERO, Vec2.ZERO),
            new PathData.Vertex(new Vec2(110, 10), Vec2.ZERO, Vec2.ZERO)), false)));

    private static Layer layer(Animation animation, String id) {
        return Layers.find(animation, id).orElseThrow();
    }

    private static Layer masked(Layer layer, String maskId) {
        return layer.withBase(layer.base().withMask(new Mask(maskId, Mask.Mode.CLIP)));
    }

    private static Effect.Blur blur(double radius) {
        return new Effect.Blur(Animatable.of(radius));
    }

    private static Effect.Glow glow(double level) {
        return new Effect.Glow(Animatable.of(level));
    }

    // ------------------------------------------------------------------ mask

    @Test
    void setMaskPointsALayerAtASiblingAndRemovesTheMaskAgain() {
        Animation start = doc(rect("a"), rect("b"));

        Animation masked = Commands.setMask("a", new Mask("b", Mask.Mode.ALPHA)).apply(start);

        assertEquals(new Mask("b", Mask.Mode.ALPHA), layer(masked, "a").base().mask());
        assertNull(layer(Commands.setMask("a", null).apply(masked), "a").base().mask());
        assertSame(masked, Commands.setMask("a", new Mask("b", Mask.Mode.ALPHA)).apply(masked));
    }

    @Test
    void setMaskRefusesALayerThatIsNotASiblingOrIsTheLayerItself() {
        Animation start = doc(rect("a"), group("g", rect("inner")));

        assertSame(start, Commands.setMask("a", new Mask("inner", Mask.Mode.CLIP)).apply(start));
        assertSame(start, Commands.setMask("a", new Mask("a", Mask.Mode.CLIP)).apply(start));
        assertSame(start, Commands.setMask("a", new Mask("gone", Mask.Mode.CLIP)).apply(start));
        assertSame(start, Commands.setMask("inner", new Mask("a", Mask.Mode.CLIP)).apply(start));
    }

    @Test
    void movingAMaskedLayerToAnotherParentClearsItsMask() {
        Animation start = doc(masked(rect("a"), "b"), rect("b"), group("g"));

        Animation after = Commands.moveLayer("a", "g", 0).apply(start);

        assertNull(layer(after, "a").base().mask());
    }

    @Test
    void movingAMaskLayerToAnotherParentClearsTheMasksThatPointedToIt() {
        Animation start = doc(masked(rect("a"), "b"), rect("b"), masked(rect("c"), "b"), group("g"));

        Animation after = Commands.moveLayer("b", "g", 0).apply(start);

        assertNull(layer(after, "a").base().mask());
        assertNull(layer(after, "c").base().mask());
    }

    @Test
    void movingWithinTheSameParentKeepsMasks() {
        Animation start = doc(masked(rect("a"), "b"), rect("b"), rect("c"));

        Animation after = Commands.moveLayer("b", null, 2).apply(start);

        assertEquals(new Mask("b", Mask.Mode.CLIP), layer(after, "a").base().mask());
        Animation moved = Commands.moveLayer("a", null, 2).apply(start);
        assertEquals(new Mask("b", Mask.Mode.CLIP), layer(moved, "a").base().mask());
    }

    @Test
    void movingAMaskedLayerTogetherWithItsMaskKeepsTheMask() {
        Animation start = doc(masked(rect("a"), "b"), rect("b"), masked(rect("c"), "b"), group("g"));

        Animation after = Commands.moveLayers(List.of(new Commands.LayerMove("a", "g", 0),
                new Commands.LayerMove("b", "g", 1))).apply(start);

        assertEquals(List.of("a", "b"), ((GroupLayer) layer(after, "g")).children().stream().map(Layer::id).toList());
        assertEquals(new Mask("b", Mask.Mode.CLIP), layer(after, "a").base().mask(), "the pair is together again");
        assertNull(layer(after, "c").base().mask(), "c stayed behind and lost its mask layer");
        assertSame(start, Commands.moveLayers(List.of(new Commands.LayerMove("gone", "g", 0))).apply(start));
    }

    @Test
    void twoLayersCannotMaskEachOther() {
        Animation start = doc(masked(rect("a"), "b"), rect("b"), rect("c"));

        assertSame(start, Commands.setMask("b", new Mask("a", Mask.Mode.CLIP)).apply(start));
        assertEquals(new Mask("c", Mask.Mode.CLIP),
                layer(Commands.setMask("b", new Mask("c", Mask.Mode.CLIP)).apply(start), "b").base().mask());
    }

    // ------------------------------------------------------------------ effects

    @Test
    void effectsAreAddedAtTheEndAndRemovedByIndex() {
        Animation one = Commands.addEffect("a", blur(8)).apply(doc(rect("a")));
        Animation two = Commands.addEffect("a", glow(0.5)).apply(one);

        assertEquals(List.of(blur(8), glow(0.5)), layer(two, "a").base().effects());
        assertEquals(List.of(glow(0.5)), layer(Commands.removeEffect("a", 0).apply(two), "a").base().effects());
        assertSame(two, Commands.removeEffect("a", 5).apply(two));
    }

    @Test
    void reorderingEffectsKeepsTheKeyframesWithTheirEffect() {
        Animation start = Commands.all("setup", Commands.addEffect("a", blur(8)), Commands.addEffect("a", glow(0.5)),
                Commands.setValueAt("a", "effects[0].radius", 1, 20.0, true)).apply(doc(rect("a")));

        Animation after = Commands.moveEffect("a", 0, 1).apply(start);

        assertEquals(glow(0.5), layer(after, "a").base().effects().get(0));
        Animatable<?> radius = LayerProperties.get(layer(after, "a"), "effects[1].radius");
        assertTrue(radius.isAnimated());
        assertEquals(20.0, radius.at(1));
        assertSame(start, Commands.moveEffect("a", 0, 0).apply(start));
        assertSame(start, Commands.moveEffect("a", 0, 2).apply(start));
    }

    @Test
    void removingAnEffectKeepsTheKeyframesOfTheOthers() {
        Animation start = Commands.all("setup", Commands.addEffect("a", blur(8)), Commands.addEffect("a", glow(0.5)),
                Commands.setValueAt("a", "effects[1].level", 1, 0.9, true)).apply(doc(rect("a")));

        Animation after = Commands.removeEffect("a", 0).apply(start);

        assertEquals(0.9, LayerProperties.get(layer(after, "a"), "effects[0].level").at(1));
        assertTrue(LayerProperties.get(layer(after, "a"), "effects[0].level").isAnimated());
    }

    // ------------------------------------------------------------------ trim

    @Test
    void trimIsEnabledWithTheWholePathAndDisabledAgain() {
        Animation start = doc(rect("a"));

        Animation enabled = Commands.setTrimEnabled("a", true).apply(start);

        assertEquals(new Trim(Animatable.of(0.0), Animatable.of(1.0), Animatable.of(0.0)),
                ((ShapeLayer) layer(enabled, "a")).trim());
        assertSame(enabled, Commands.setTrimEnabled("a", true).apply(enabled));
        assertNull(((ShapeLayer) layer(Commands.setTrimEnabled("a", false).apply(enabled), "a")).trim());
        Animation withGroup = doc(group("g"));
        assertSame(withGroup, Commands.setTrimEnabled("g", true).apply(withGroup));
    }

    // ------------------------------------------------------------------ motion path

    @Test
    void addingAMotionPathAnimatesProgressFromTheInPointToTheOutPoint() {
        Layer late = rect("a");
        late = late.withBase(late.base().withInPoint(0.5).withOutPoint(2));

        Animation after = Commands.addMotionPath("a", LINE).apply(doc(late));

        MotionPath motion = layer(after, "a").base().motionPath();
        assertEquals(LINE, motion.path());
        assertFalse(motion.orientToPath());
        List<Keyframe<Double>> keyframes = motion.progress().keyframes();
        assertEquals(2, keyframes.size());
        assertEquals(0.5, keyframes.get(0).time());
        assertEquals(0.0, keyframes.get(0).value());
        assertEquals(Easing.presets().get("Ease In Out"), keyframes.get(0).easing());
        assertEquals(2.0, keyframes.get(1).time());
        assertEquals(1.0, keyframes.get(1).value());
    }

    @Test
    void aLayerWithoutATimeRangeGetsAStaticProgress() {
        Layer flat = rect("a");
        flat = flat.withBase(flat.base().withInPoint(1).withOutPoint(1));

        MotionPath motion = layer(Commands.addMotionPath("a", LINE).apply(doc(flat)), "a").base().motionPath();

        assertEquals(Animatable.of(0.0), motion.progress());
    }

    @Test
    void setMotionPathReplacesAndRemoves() {
        MotionPath motion = new MotionPath(LINE, Animatable.of(0.25), true);

        Animation with = Commands.setMotionPath("a", motion).apply(doc(rect("a")));

        assertEquals(motion, layer(with, "a").base().motionPath());
        assertSame(with, Commands.setMotionPath("a", motion).apply(with));
        assertNull(layer(Commands.setMotionPath("a", null).apply(with), "a").base().motionPath());
    }

    @Test
    void aTranslatedLayerTakesItsMotionPathAlong() {
        Layer layer = rect("a").withBase(rect("a").base().withMotionPath(new MotionPath(LINE, Animatable.of(0.0), false)));

        Layer moved = Commands.translated(layer, new Vec2(5, 7));

        assertEquals(new Vec2(15, 17), moved.base().motionPath().path().contours().get(0).vertices().get(0).point());
        assertEquals(new Vec2(5, 7), moved.base().transform().position().at(0));
    }

    // ------------------------------------------------------------------ convert to path

    @Test
    void convertToPathReplacesAPrimitiveWithItsOutline() {
        Animation start = doc(rect("a"));
        Geometry before = ((ShapeLayer) layer(start, "a")).geometry();

        Animation after = Commands.convertToPath(List.of("a"), 0).apply(start);

        ShapeLayer shape = (ShapeLayer) layer(after, "a");
        assertEquals(new Geometry.PathGeometry(Animatable.of(Geometries.toPath(before, 0))), shape.geometry());
        assertEquals(((ShapeLayer) layer(start, "a")).fill(), shape.fill());
        assertEquals(layer(start, "a").base(), shape.base());
        assertSame(after, Commands.convertToPath(List.of("a"), 0).apply(after));
        assertTrue(Commands.convertObstacle(start, List.of("a")).isEmpty());
    }

    @Test
    void convertToPathLeavesAnimatedGeometryAndOtherLayersAlone() {
        Animation start = Commands.setValueAt("a", "geometry.size", 1, new Vec2(80, 80), true)
                .apply(doc(rect("a"), group("g"), rect("b")));

        Animation after = Commands.convertToPath(List.of("a", "g", "b"), 0).apply(start);

        assertTrue(((ShapeLayer) layer(after, "a")).geometry() instanceof Geometry.Rect);
        assertTrue(((ShapeLayer) layer(after, "b")).geometry() instanceof Geometry.PathGeometry);
        assertSame(layer(start, "g"), layer(after, "g"));
        String obstacle = Commands.convertObstacle(start, List.of("a", "b")).orElseThrow();
        assertTrue(obstacle.contains("a") && obstacle.contains("keyframes"), obstacle);
    }
}
