package com.dlsc.animatorfx.player;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;

import java.util.ArrayList;
import java.util.List;

/** Small documents for evaluator and renderer tests. */
public final class TestDocs {

    public static final double DURATION = 2;

    private TestDocs() {
    }

    public static Animation doc(Layer... layers) {
        return Animation.create("test", 200, 100, DURATION).withLayers(List.of(layers));
    }

    public static LayerBase base(String id) {
        return new LayerBase(id, id, true, false, 0, DURATION, Transform.IDENTITY, Animatable.of(1.0), null,
                List.of(), null);
    }

    /** A filled, unstroked rectangle of the given size. */
    public static ShapeLayer rect(String id, double width, double height) {
        return new ShapeLayer(base(id), new Geometry.Rect(Animatable.of(new Vec2(width, height)), Animatable.of(0.0)),
                Animatable.of(new Paint.Solid(Rgba.WHITE)), null, null);
    }

    public static GroupLayer group(String id, Layer... children) {
        return new GroupLayer(base(id), List.of(children));
    }

    public static Stroke stroke(double width) {
        return new Stroke(Animatable.of(new Paint.Solid(Rgba.BLACK)), Animatable.of(width), Stroke.Cap.BUTT,
                Stroke.Join.MITER, 10, List.of(), Animatable.of(0.0));
    }

    public static <L extends Layer> L at(L layer, double x, double y) {
        return transformed(layer, Transform.at(x, y));
    }

    @SuppressWarnings("unchecked")
    public static <L extends Layer> L transformed(L layer, Transform transform) {
        return (L) layer.withBase(layer.base().withTransform(transform));
    }

    /** Linear keyframes from alternating time and value arguments. */
    @SuppressWarnings("unchecked")
    public static <T> Animatable<T> keys(Object... timesAndValues) {
        List<Keyframe<T>> keyframes = new ArrayList<>();
        for (int i = 0; i < timesAndValues.length; i += 2) {
            keyframes.add(new Keyframe<>("k" + i / 2, ((Number) timesAndValues[i]).doubleValue(),
                    (T) timesAndValues[i + 1], Easing.LINEAR));
        }
        return Animatable.keyframed(keyframes);
    }
}
