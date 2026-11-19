package com.dlsc.animatorfx.model.io;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.MotionPath;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Trim;
import com.dlsc.animatorfx.model.Vec2;

import java.util.List;

/** A document that uses every feature of the model. Colours are exact in 8 bits per channel. */
final class KitchenSink {

    static final double DURATION = 4;

    private KitchenSink() {
    }

    static Animation animation() {
        List<Asset> assets = List.of(
                new Asset("img-1", Asset.Kind.IMAGE, "assets/img-1.png", "logo.png"),
                new Asset("font-1", Asset.Kind.FONT, "assets/font-1.ttf", ""),
                new Asset("snd-1", Asset.Kind.AUDIO, "assets/snd-1.wav", "chime.wav"));
        List<Marker> markers = List.of(new Marker("intro", 0, 1.5), new Marker("loop", 1.5, 4));
        List<SoundCue> cues = List.of(
                new SoundCue("cue-1", "tick", 1.5, "snd-1", 0.25, true),
                new SoundCue("cue-2", "", 0, "snd-1", 1, false));
        return new Animation(Animation.CURRENT_FORMAT, "Kitchen \"sink\" ✓", 640, 360.5, 30, DURATION,
                Rgba.parse("#102030FF"), markers, assets,
                List.of(rect(), ellipse(), line(), polygon(), star(), path(), text(), image(), group(), slot(),
                        audio()),
                cues);
    }

    @SafeVarargs
    private static <T> Animatable<T> keys(Keyframe<T>... keyframes) {
        return Animatable.keyframed(List.of(keyframes));
    }

    private static <T> Keyframe<T> key(String id, double time, T value, Easing easing) {
        return new Keyframe<>(id, time, value, easing);
    }

    private static LayerBase base(String id) {
        return new LayerBase(id, id + " layer", true, false, 0, DURATION, Transform.IDENTITY, Animatable.of(1.0), null,
                List.of(), null);
    }

    private static PathData triangle(double size) {
        return new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(0, 0), Vec2.ZERO, new Vec2(size / 3, -4.25)),
                new PathData.Vertex(new Vec2(size, 0), new Vec2(-1, -2), new Vec2(3, 4)),
                new PathData.Vertex(new Vec2(size / 2, size), Vec2.ZERO, Vec2.ZERO)), true)));
    }

    private static PathData openCurve() {
        return new PathData(List.of(
                new PathData.Contour(List.of(
                        new PathData.Vertex(new Vec2(0, 100), Vec2.ZERO, new Vec2(50, -80)),
                        new PathData.Vertex(new Vec2(300, 100), new Vec2(-50, -80), Vec2.ZERO)), false),
                new PathData.Contour(List.of(), false)));
    }

    private static Stroke stroke() {
        return new Stroke(Animatable.of(new Paint.Solid(Rgba.parse("#FF000080"))),
                keys(key("w0", 0, 1.0, Easing.LINEAR), key("w1", 2, 6.5, Easing.LINEAR)),
                Stroke.Cap.ROUND, Stroke.Join.BEVEL, 4, List.of(8.0, 2.5), Animatable.of(3.0));
    }

    private static ShapeLayer rect() {
        Transform transform = new Transform(
                Animatable.of(new Vec2(50, 25)),
                keys(key("p0", 0, new Vec2(10, 20), new Easing.CubicBezier(0.42, 0, 0.58, 1)),
                        key("p1", 1.25, new Vec2(300, 20.5), Easing.HOLD),
                        key("p2", 3, new Vec2(10, 200), Easing.LINEAR)),
                Animatable.of(new Vec2(1.5, -1)),
                keys(key("r0", 0, 0.0, new Easing.CubicBezier(0.3, -0.6, 0.7, 1.6)), key("r1", 4, 360.0, Easing.LINEAR)),
                Animatable.of(new Vec2(10, 0)));
        Paint.LinearGradient from = new Paint.LinearGradient(new Vec2(0, 0), new Vec2(100, 0),
                List.of(new Paint.Stop(0, Rgba.WHITE), new Paint.Stop(0.75, Rgba.parse("#3366CC"))));
        Paint.LinearGradient to = new Paint.LinearGradient(new Vec2(0, 0), new Vec2(0, 50),
                List.of(new Paint.Stop(0.25, Rgba.BLACK), new Paint.Stop(1, Rgba.parse("#00FF0040"))));
        LayerBase base = new LayerBase("rect", "", true, false, 0, DURATION, transform,
                keys(key("o0", 0.5, 0.0, Easing.LINEAR), key("o1", 1, 1.0, Easing.LINEAR)), null,
                List.of(new Effect.Blur(keys(key("b0", 0, 0.0, Easing.LINEAR), key("b1", 1, 12.0, Easing.LINEAR))),
                        new Effect.DropShadow(Animatable.of(Rgba.parse("#00000099")), Animatable.of(8.0),
                                Animatable.of(2.0), Animatable.of(-3.0), Animatable.of(0.25)),
                        new Effect.Glow(Animatable.of(0.6))),
                new MotionPath(openCurve(), keys(key("m0", 0, 0.0, Easing.LINEAR), key("m1", 4, 1.0, Easing.LINEAR)),
                        true));
        return new ShapeLayer(base,
                new Geometry.Rect(keys(key("s0", 0, new Vec2(100, 50), Easing.LINEAR),
                        key("s1", 2, new Vec2(120, 60), Easing.LINEAR)), Animatable.of(8.0)),
                keys(key("f0", 0, (Paint) from, Easing.LINEAR), key("f1", 2, (Paint) to, Easing.HOLD)),
                stroke(),
                new Trim(Animatable.of(0.1), keys(key("t0", 0, 0.0, Easing.LINEAR), key("t1", 1, 1.0, Easing.LINEAR)),
                        Animatable.of(0.5)));
    }

    private static ShapeLayer ellipse() {
        return new ShapeLayer(base("ellipse").withMask(new Mask("rect", Mask.Mode.ALPHA)),
                new Geometry.Ellipse(Animatable.of(new Vec2(80, 40))),
                Animatable.of(new Paint.RadialGradient(new Vec2(40, 20), 40,
                        List.of(new Paint.Stop(0, Rgba.WHITE), new Paint.Stop(1, Rgba.TRANSPARENT)))),
                null, null);
    }

    private static ShapeLayer line() {
        return new ShapeLayer(base("line"),
                new Geometry.Line(Animatable.of(Vec2.ZERO), keys(key("e0", 0, new Vec2(0, 0), Easing.LINEAR),
                        key("e1", 1, new Vec2(100, 100), Easing.LINEAR))),
                Animatable.of(new Paint.None()),
                new Stroke(Animatable.of(new Paint.Solid(Rgba.BLACK)), Animatable.of(1.0), Stroke.Cap.BUTT,
                        Stroke.Join.MITER, 10, List.of(), Animatable.of(0.0)),
                new Trim(Animatable.of(0.0), Animatable.of(1.0), Animatable.of(0.0)));
    }

    private static ShapeLayer polygon() {
        LayerBase base = base("polygon").withVisible(false).withLocked(true).withInPoint(0.5).withOutPoint(3.5);
        return new ShapeLayer(base, new Geometry.Polygon(5, Animatable.of(30.0)),
                Animatable.of(new Paint.Solid(Rgba.BLACK)), null, null);
    }

    private static ShapeLayer star() {
        return new ShapeLayer(base("star").withTransform(Transform.at(100, 100)),
                new Geometry.Star(7, Animatable.of(40.0), keys(key("i0", 0, 10.0, Easing.LINEAR),
                        key("i1", 2, 25.0, Easing.LINEAR))),
                Animatable.of(new Paint.Solid(Rgba.parse("#FFCC00"))), null, null);
    }

    private static ShapeLayer path() {
        return new ShapeLayer(base("path"),
                new Geometry.PathGeometry(keys(key("d0", 0, triangle(60), new Easing.CubicBezier(0, 0, 1, 1)),
                        key("d1", 2, openCurve(), Easing.LINEAR))),
                Animatable.of(new Paint.Solid(Rgba.parse("#336699"))), null, null);
    }

    private static TextLayer text() {
        return new TextLayer(base("text"), "Hello\n\"World\" – ✓", "Inter", "font-1",
                keys(key("z0", 0, 12.0, Easing.LINEAR), key("z1", 1, 48.0, Easing.LINEAR)),
                TextLayer.Weight.BOLD, true, TextLayer.Align.CENTER, Animatable.of(new Paint.Solid(Rgba.WHITE)),
                stroke());
    }

    private static ImageLayer image() {
        return new ImageLayer(base("image").withOpacity(Animatable.of(0.5)), "img-1", 64, 48.5);
    }

    private static GroupLayer group() {
        ShapeLayer child = new ShapeLayer(base("child"), new Geometry.PathGeometry(Animatable.of(triangle(20))),
                Animatable.of(new Paint.Solid(Rgba.BLACK)), null, null);
        SlotLayer slot = new SlotLayer(base("inner-slot").withTransform(Transform.at(5, 6)), "content", 200, 100,
                Transform.IDENTITY);
        GroupLayer nested = new GroupLayer(base("nested").withMask(new Mask("child", Mask.Mode.CLIP)),
                List.of(new TextLayer(base("plain-text"), "", "System", null, Animatable.of(12.0),
                        TextLayer.Weight.NORMAL, false, TextLayer.Align.LEFT,
                        Animatable.of(new Paint.Solid(Rgba.BLACK)), null)));
        return new GroupLayer(base("group").withTransform(Transform.at(20, 30)), List.of(child, slot, nested));
    }

    private static AudioLayer audio() {
        return new AudioLayer(base("audio").withInPoint(0.5).withOutPoint(3), "snd-1", 0.75,
                keys(key("v0", 0.5, 0.0, Easing.LINEAR), key("v1", 1, 1.0, Easing.HOLD)), Animatable.of(-0.5), true,
                true);
    }

    private static SlotLayer slot() {
        Transform transform = Transform.at(400, 300).withScale(Animatable.of(new Vec2(2, 2)));
        return new SlotLayer(base("slot").withTransform(transform), "footer", 120, 32, transform);
    }
}
