package com.dlsc.animatorfx.player;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.MotionPath;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.PathMeasure;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Trim;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dlsc.animatorfx.player.TestDocs.DURATION;
import static com.dlsc.animatorfx.player.TestDocs.at;
import static com.dlsc.animatorfx.player.TestDocs.base;
import static com.dlsc.animatorfx.player.TestDocs.doc;
import static com.dlsc.animatorfx.player.TestDocs.group;
import static com.dlsc.animatorfx.player.TestDocs.keys;
import static com.dlsc.animatorfx.player.TestDocs.rect;
import static com.dlsc.animatorfx.player.TestDocs.stroke;
import static com.dlsc.animatorfx.player.TestDocs.transformed;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatorTest {

    private static final double EPS = 1e-9;

    private static PathData line(double x0, double y0, double x1, double y1) {
        return new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(x0, y0), Vec2.ZERO, Vec2.ZERO),
                new PathData.Vertex(new Vec2(x1, y1), Vec2.ZERO, Vec2.ZERO)), false)));
    }

    @Test
    void samplesPositionKeyframesAtThreeTimes() {
        Animatable<Vec2> position = keys(0, new Vec2(0, 0), 2, new Vec2(100, 40));
        Animation animation = doc(transformed(rect("r", 10, 10), Transform.IDENTITY.withPosition(position)));

        assertArrayEquals(new double[]{1, 0, 0, 0, 1, 0}, Evaluator.sample(animation, 0).get("r").matrix(), EPS);
        assertArrayEquals(new double[]{1, 0, 50, 0, 1, 20}, Evaluator.sample(animation, 1).get("r").matrix(), EPS);
        assertArrayEquals(new double[]{1, 0, 100, 0, 1, 40}, Evaluator.sample(animation, 2).get("r").matrix(), EPS);
        assertEquals(new Vec2(50, 20), Evaluator.sample(animation, 1).get("r").position());
        assertEquals(1, Evaluator.sample(animation, 1).time());
    }

    @Test
    void layerIsVisibleFromInPointUpToButExcludingOutPoint() {
        ShapeLayer layer = rect("r", 10, 10);
        Animation animation = doc(layer.withBase(layer.base().withInPoint(0.5).withOutPoint(1.5)));

        assertFalse(Evaluator.sample(animation, 0.25).get("r").visible());
        assertTrue(Evaluator.sample(animation, 0.5).get("r").visible());
        assertTrue(Evaluator.sample(animation, 1).get("r").visible());
        assertFalse(Evaluator.sample(animation, 1.5).get("r").visible());
        assertFalse(Evaluator.sample(animation, 2).get("r").visible());
    }

    @Test
    void layerEndingWithTheDocumentIsVisibleOnTheLastFrame() {
        Animation animation = doc(rect("r", 10, 10));

        assertTrue(Evaluator.sample(animation, DURATION).get("r").visible());
    }

    @Test
    void hiddenLayerIsNeverVisible() {
        ShapeLayer layer = rect("r", 10, 10);
        Animation animation = doc(layer.withBase(layer.base().withVisible(false)));

        assertFalse(Evaluator.sample(animation, 1).get("r").visible());
    }

    @Test
    void groupTransformIsNotBakedIntoChildren() {
        Animation animation = doc(at(group("g", at(rect("child", 10, 10), 5, 7)), 100, 50));

        Frame frame = Evaluator.sample(animation, 0);

        assertArrayEquals(new double[]{1, 0, 100, 0, 1, 50}, frame.get("g").matrix(), EPS);
        assertArrayEquals(new double[]{1, 0, 5, 0, 1, 7}, frame.get("child").matrix(), EPS);
        assertEquals(2, frame.layers().size());
    }

    @Test
    void motionPathOverridesPositionAndAddsTangentRotation() {
        MotionPath motion = new MotionPath(line(10, 10, 10, 110), keys(0, 0.0, 2, 1.0), true);
        Transform transform = Transform.at(500, 500).withRotation(Animatable.of(10.0));
        ShapeLayer layer = transformed(rect("r", 10, 10), transform);
        Animation animation = doc(layer.withBase(layer.base().withMotionPath(motion)));

        LayerState state = Evaluator.sample(animation, 1).get("r");

        assertEquals(10, state.position().x(), 1e-6);
        assertEquals(60, state.position().y(), 1e-6);
        assertEquals(100, state.rotation(), 1e-6);
        double[] expected = Transform.at(10, 60).withRotation(Animatable.of(100.0)).matrixAt(0);
        assertArrayEquals(expected, state.matrix(), 1e-6);
    }

    @Test
    void motionPathWithoutOrientationKeepsTheRotation() {
        MotionPath motion = new MotionPath(line(10, 10, 10, 110), Animatable.of(0.5), false);
        ShapeLayer layer = transformed(rect("r", 10, 10), Transform.at(500, 500).withRotation(Animatable.of(10.0)));
        Animation animation = doc(layer.withBase(layer.base().withMotionPath(motion)));

        LayerState state = Evaluator.sample(animation, 1).get("r");

        assertEquals(10, state.rotation(), EPS);
        assertEquals(60, state.position().y(), 1e-6);
    }

    @Test
    void trimHalvesTheStrokePathAndLeavesTheFillPathAlone() {
        Trim trim = new Trim(Animatable.of(0.0), keys(0, 0.0, 2, 1.0), Animatable.of(0.0));
        Animation animation = doc(rect("r", 100, 50).withStroke(stroke(2)).withTrim(trim));

        LayerState state = Evaluator.sample(animation, 1).get("r");

        assertEquals(300, new PathMeasure(state.fillPath()).length(), 1e-6);
        assertEquals(150, new PathMeasure(state.strokePath()).length(), 1e-6);
        assertNotSame(state.fillPath(), state.strokePath());
    }

    @Test
    void untrimmedShapeUsesOnePathForFillAndStroke() {
        Animation animation = doc(rect("r", 100, 50).withStroke(stroke(2)));

        LayerState state = Evaluator.sample(animation, 1).get("r");

        assertSame(state.fillPath(), state.strokePath());
        assertEquals(300, new PathMeasure(state.fillPath()).length(), 1e-6);
    }

    @Test
    void samplesGeometryFillAndStroke() {
        Geometry geometry = new Geometry.Rect(keys(0, new Vec2(10, 10), 2, new Vec2(30, 10)), Animatable.of(0.0));
        Animatable<Paint> fill = keys(0, new Paint.Solid(Rgba.BLACK), 2, new Paint.Solid(Rgba.WHITE));
        ShapeLayer layer = rect("r", 1, 1).withGeometry(geometry).withFill(fill).withStroke(stroke(1)
                .withWidth(keys(0, 2.0, 2, 6.0)).withDashOffset(keys(0, 0.0, 2, 8.0)));

        LayerState state = Evaluator.sample(doc(layer), 1).get("r");

        assertEquals(60, new PathMeasure(state.fillPath()).length(), 1e-6);
        assertEquals(new Paint.Solid(new Rgba(0.5, 0.5, 0.5, 1)), state.fill());
        assertEquals(new Paint.Solid(Rgba.BLACK), state.strokePaint());
        assertEquals(4, state.strokeWidth(), EPS);
        assertEquals(4, state.dashOffset(), EPS);
    }

    @Test
    void samplesOpacityAndFontSize() {
        LayerBase base = base("t").withOpacity(keys(0, 0.0, 2, 1.0));
        TextLayer text = new TextLayer(base, "Hi", "System", null, keys(0, 10.0, 2, 30.0), TextLayer.Weight.NORMAL,
                false, TextLayer.Align.LEFT, Animatable.of(new Paint.Solid(Rgba.BLACK)), null);

        LayerState state = Evaluator.sample(doc(text), 1).get("t");

        assertEquals(0.5, state.opacity(), EPS);
        assertEquals(20, state.fontSize(), EPS);
        assertNull(state.fillPath());
        assertNull(state.strokePaint());
    }

    @Test
    void samplesEffectValues() {
        Effect.Blur blur = new Effect.Blur(keys(0, 0.0, 2, 10.0));
        Effect.DropShadow shadow = new Effect.DropShadow(keys(0, Rgba.BLACK, 2, Rgba.WHITE), Animatable.of(4.0),
                keys(0, 0.0, 2, 6.0), Animatable.of(3.0), Animatable.of(0.25));
        Effect.Glow glow = new Effect.Glow(Animatable.of(0.75));
        ShapeLayer layer = rect("r", 10, 10);
        Animation animation = doc(layer.withBase(layer.base().withEffects(List.of(blur, shadow, glow))));

        List<LayerState.EffectState> effects = Evaluator.sample(animation, 1).get("r").effects();

        assertEquals(3, effects.size());
        assertSame(blur, effects.get(0).source());
        assertArrayEquals(new double[]{5}, effects.get(0).values(), EPS);
        assertNull(effects.get(0).color());
        assertSame(shadow, effects.get(1).source());
        assertArrayEquals(new double[]{4, 3, 3, 0.25}, effects.get(1).values(), EPS);
        assertEquals(new Rgba(0.5, 0.5, 0.5, 1), effects.get(1).color());
        assertArrayEquals(new double[]{0.75}, effects.get(2).values(), EPS);
    }

    @Test
    void staticLayerYieldsTheSameStateForEveryFrame() {
        ShapeLayer moving = transformed(rect("moving", 10, 10),
                Transform.IDENTITY.withPosition(keys(0, new Vec2(0, 0), 2, new Vec2(100, 40))));
        Animation animation = doc(rect("still", 10, 10).withStroke(stroke(2)), moving);

        Frame first = Evaluator.sample(animation, 0.5);
        Frame second = Evaluator.sample(animation, 1.5);

        assertSame(first.get("still"), second.get("still"));
        assertSame(first.get("moving").fillPath(), second.get("moving").fillPath());
        assertSame(first.get("moving").fill(), second.get("moving").fill());
        assertNotSame(first.get("moving"), second.get("moving"));
    }

    @Test
    void statesCompareByValueIncludingTheMatrix() {
        Animation animation = doc(at(rect("r", 10, 10), 5, 5));
        Animation copy = doc(at(rect("r", 10, 10), 5, 5));
        Animation moved = doc(at(rect("r", 10, 10), 6, 5));

        LayerState state = Evaluator.sample(animation, 0).get("r");

        assertNotSame(state, Evaluator.sample(copy, 0).get("r"));
        assertEquals(state, Evaluator.sample(copy, 0).get("r"));
        assertEquals(state.hashCode(), Evaluator.sample(copy, 0).get("r").hashCode());
        assertNotEquals(state, Evaluator.sample(moved, 0).get("r"));
    }

    @Test
    void unknownLayerIsNull() {
        assertNull(Evaluator.sample(doc(rect("r", 10, 10)), 0).get("nope"));
    }

    @Test
    void duplicateLayerIdsAreRejected() {
        Animation animation = doc(rect("r", 10, 10), rect("r", 20, 20));

        assertThrows(IllegalArgumentException.class, () -> Evaluator.sample(animation, 0));
    }
}
