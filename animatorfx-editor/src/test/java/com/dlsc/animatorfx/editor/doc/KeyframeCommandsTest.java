package com.dlsc.animatorfx.editor.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dlsc.animatorfx.editor.doc.Commands.KeyframeCopy;
import com.dlsc.animatorfx.editor.doc.Selection.KeyframeRef;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.LayerProperties;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Vec2;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyframeCommandsTest {

    private static ShapeLayer rect(String id) {
        return new ShapeLayer(LayerBase.create(id, 3).withId(id),
                new Geometry.Rect(Animatable.of(new Vec2(10, 10)), Animatable.of(0.0)),
                Animatable.of(new Paint.Solid(Rgba.WHITE)), null, null);
    }

    private static Animation document() {
        return Animation.create("doc", 100, 100, 3).withLayers(List.of(rect("a"), rect("b")));
    }

    private static Animatable<?> value(Animation animation, String id, String key) {
        return LayerProperties.get(Layers.find(animation, id).orElseThrow(), key);
    }

    @Test
    void batchRunsEveryCommandAsOneNamedCommand() {
        Command batch = Commands.batch("Change Opacity", List.of(
                Commands.setValueAt("a", "opacity", 0, 0.5, false),
                Commands.setValueAt("b", "opacity", 0, 0.5, false)));
        assertEquals("Change Opacity", batch.name());
        Animation after = batch.apply(document());
        assertEquals(0.5, value(after, "a", "opacity").at(0));
        assertEquals(0.5, value(after, "b", "opacity").at(0));
    }

    @Test
    void aBatchIsOneUndoStep() {
        EditorDocument doc = EditorDocument.untitled();
        doc.execute(Commands.addLayer(null, 0, rect("a")));
        doc.execute(Commands.addLayer(null, 1, rect("b")));
        Animation before = doc.getAnimation();
        doc.execute(Commands.batch("Change Opacity", List.of(
                Commands.setValueAt("a", "opacity", 0, 0.25, false),
                Commands.setValueAt("b", "opacity", 0, 0.25, false))));
        assertEquals("Change Opacity", doc.getUndoName());
        doc.undo();
        assertSame(before, doc.getAnimation());
    }

    @Test
    void aBatchThatChangesNothingReturnsTheSameAnimation() {
        Animation doc = document();
        assertSame(doc, Commands.batch("Nothing", List.of(
                Commands.setValueAt("a", "opacity", 0, 1.0, false),
                Commands.setValueAt("missing", "opacity", 0, 0.5, false))).apply(doc));
        assertSame(doc, Commands.batch("Empty", List.of()).apply(doc));
    }

    @Test
    void pastedKeyframesKeepTheirSpacingValuesAndEasingAndGetNewIds() {
        Easing ease = Easing.presets().get("Ease Out");
        Animation doc = Commands.setProperty("a", "opacity", Animatable.keyframed(List.of(
                new Keyframe<>("k1", 0.5, 0.2, ease), new Keyframe<>("k2", 1.0, 0.8, Easing.HOLD)))).apply(document());
        List<KeyframeCopy> copies = Commands.copyKeyframes(doc,
                List.of(new KeyframeRef("a", "opacity", "k2"), new KeyframeRef("a", "opacity", "k1")));
        assertEquals(2, copies.size());
        assertEquals(0.0, copies.get(0).offset(), 1e-12);
        assertEquals(0.5, copies.get(1).offset(), 1e-12);

        Animation after = Commands.pasteKeyframes(copies, 2.0).apply(doc);
        List<? extends Keyframe<?>> keyframes = value(after, "a", "opacity").keyframes();
        assertEquals(4, keyframes.size());
        assertEquals(2.0, keyframes.get(2).time(), 1e-12);
        assertEquals(0.2, keyframes.get(2).value());
        assertEquals(ease, keyframes.get(2).easing());
        assertNotEquals("k1", keyframes.get(2).id());
        assertEquals(2.5, keyframes.get(3).time(), 1e-12);
        assertEquals(Easing.HOLD, keyframes.get(3).easing());
        assertEquals("Paste Keyframes", Commands.pasteKeyframes(copies, 2.0).name());
    }

    @Test
    void pastingOntoAKeyframeReplacesItsValueAndAStaticPropertyBecomesAnimated() {
        Animation doc = Commands.setProperty("a", "opacity", Animatable.keyframed(List.of(
                new Keyframe<>("k1", 0.5, 0.2, Easing.LINEAR), new Keyframe<>("k2", 1.0, 0.8, Easing.LINEAR))))
                .apply(document());
        List<KeyframeCopy> copies = Commands.copyKeyframes(doc, List.of(new KeyframeRef("a", "opacity", "k1")));
        Animation after = Commands.pasteKeyframes(copies, 1.0).apply(doc);
        List<? extends Keyframe<?>> keyframes = value(after, "a", "opacity").keyframes();
        assertEquals(2, keyframes.size());
        assertEquals("k2", keyframes.get(1).id());
        assertEquals(0.2, keyframes.get(1).value());

        // the property was made static in the meantime
        Animation flat = Commands.setProperty("a", "opacity", Animatable.of(1.0)).apply(doc);
        assertTrue(value(Commands.pasteKeyframes(copies, 1.0).apply(flat), "a", "opacity").isAnimated());
    }

    @Test
    void pastingSkipsLayersAndPropertiesThatAreGone() {
        Animation doc = Commands.addKeyframe("a", "geometry.cornerRadius", 0.5).apply(document());
        Layer a = Layers.find(doc, "a").orElseThrow();
        String id = LayerProperties.get(a, "geometry.cornerRadius").keyframes().get(0).id();
        List<KeyframeCopy> copies = Commands.copyKeyframes(doc,
                List.of(new KeyframeRef("a", "geometry.cornerRadius", id), new KeyframeRef("a", "opacity", "nope")));
        assertEquals(1, copies.size());

        Animation withoutLayer = Commands.removeLayers(List.of("a")).apply(doc);
        assertSame(withoutLayer, Commands.pasteKeyframes(copies, 1).apply(withoutLayer));
        Animation ellipse = Commands.updateLayer("Shape", "a",
                l -> ((ShapeLayer) l).withGeometry(new Geometry.Ellipse(Animatable.of(new Vec2(4, 4))))).apply(doc);
        assertSame(ellipse, Commands.pasteKeyframes(copies, 1).apply(ellipse));
    }
}
