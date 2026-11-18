package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerPropertiesTest {

    private static final List<String> BASE_KEYS = List.of("transform.anchor", "transform.position",
            "transform.scale", "transform.rotation", "transform.skew", "opacity");

    private static final Animatable<Paint> RED = Animatable.of(new Paint.Solid(new Rgba(1, 0, 0, 1)));

    private static Animatable<Double> num(double value) {
        return Animatable.of(value);
    }

    private static Animatable<Vec2> vec(double x, double y) {
        return Animatable.of(new Vec2(x, y));
    }

    private static Stroke stroke() {
        return new Stroke(Animatable.of(new Paint.Solid(Rgba.BLACK)), num(2), Stroke.Cap.ROUND, Stroke.Join.MITER,
                10, List.of(4.0, 2.0), num(0));
    }

    private static ShapeLayer shape(LayerBase base, Geometry geometry, Stroke stroke, Trim trim) {
        return new ShapeLayer(base, geometry, RED, stroke, trim);
    }

    private static LayerBase fullBase() {
        return LayerBase.create("Layer", 5)
                .withEffects(List.of(
                        new Effect.Blur(num(4)),
                        new Effect.DropShadow(Animatable.of(Rgba.BLACK), num(8), num(1), num(2), num(0.1)),
                        new Effect.Glow(num(0.5))))
                .withMotionPath(new MotionPath(Paths.square(), num(0), true));
    }

    private static ShapeLayer rectLayer() {
        return shape(LayerBase.create("Rect", 5).withEffects(List.of(new Effect.Blur(num(4)))),
                new Geometry.Rect(vec(100, 50), num(8)), stroke(), new Trim(num(0), num(1), num(0)));
    }

    private static List<String> keys(Layer layer) {
        return LayerProperties.list(layer).stream().map(PropertyRef::key).toList();
    }

    private static List<String> concat(List<String> first, String... more) {
        return java.util.stream.Stream.concat(first.stream(), java.util.stream.Stream.of(more)).toList();
    }

    /** A value of the property's type that differs from anything the fixtures use. */
    private static Animatable<?> sample(PropertyRef.ValueType type) {
        return switch (type) {
            case NUMBER -> Animatable.of(0.0).withKeyframe(0, 12.5, Easing.LINEAR).withKeyframe(1, 99.0, Easing.HOLD);
            case VEC2 -> Animatable.of(new Vec2(-7, 13));
            case COLOR -> Animatable.of(new Rgba(0.1, 0.2, 0.3, 0.4));
            case PAINT -> Animatable.<Paint>of(new Paint.LinearGradient(Vec2.ZERO, Vec2.ONE,
                    List.of(new Paint.Stop(0, Rgba.WHITE), new Paint.Stop(1, Rgba.BLACK))));
            case PATH -> Animatable.of(Paths.circle(3));
        };
    }

    /** Sets every listed property in turn and checks that only that property changed. */
    private static void assertRoundTrip(Layer layer) {
        List<PropertyRef> refs = LayerProperties.list(layer);
        for (PropertyRef ref : refs) {
            assertSame(ref.value(), LayerProperties.get(layer, ref.key()), ref.key());
            Animatable<?> value = sample(ref.type());
            Layer changed = LayerProperties.set(layer, ref.key(), value);

            assertSame(layer.getClass(), changed.getClass(), ref.key());
            assertEquals(layer.id(), changed.id(), ref.key());
            assertSame(value, LayerProperties.get(changed, ref.key()), ref.key());
            List<PropertyRef> after = LayerProperties.list(changed);
            assertEquals(refs.size(), after.size(), ref.key());
            for (int i = 0; i < refs.size(); i++) {
                PropertyRef before = refs.get(i);
                assertEquals(before.key(), after.get(i).key());
                if (!before.key().equals(ref.key())) {
                    assertSame(before.value(), after.get(i).value(), ref.key() + " changed " + before.key());
                }
            }
            assertEquals(layer, LayerProperties.set(changed, ref.key(), ref.value()), ref.key());
        }
    }

    @Test
    void listForRectShapeWithStrokeTrimAndBlur() {
        assertEquals(concat(BASE_KEYS,
                "geometry.size", "geometry.cornerRadius", "fill",
                "stroke.paint", "stroke.width", "stroke.dashOffset",
                "trim.start", "trim.end", "trim.offset",
                "effects[0].radius"), keys(rectLayer()));
    }

    @Test
    void listTypesAndLabels() {
        List<PropertyRef> refs = LayerProperties.list(rectLayer());
        assertEquals(new PropertyRef("transform.position", "Position", PropertyRef.ValueType.VEC2,
                Transform.IDENTITY.position()), refs.get(1));
        assertEquals("Stroke Width", ref(refs, "stroke.width").label());
        assertEquals("Blur Radius", ref(refs, "effects[0].radius").label());
        assertEquals(PropertyRef.ValueType.NUMBER, ref(refs, "transform.rotation").type());
        assertEquals(PropertyRef.ValueType.NUMBER, ref(refs, "opacity").type());
        assertEquals(PropertyRef.ValueType.PAINT, ref(refs, "fill").type());
        assertEquals(PropertyRef.ValueType.PAINT, ref(refs, "stroke.paint").type());
        assertEquals(PropertyRef.ValueType.VEC2, ref(refs, "geometry.size").type());

        List<PropertyRef> full = LayerProperties.list(shape(fullBase(),
                new Geometry.PathGeometry(Animatable.of(Paths.square())), null, null));
        assertEquals(PropertyRef.ValueType.PATH, ref(full, "geometry.path").type());
        assertEquals(PropertyRef.ValueType.COLOR, ref(full, "effects[1].color").type());
        for (PropertyRef ref : full) {
            assertTrue(!ref.label().isBlank() && Character.isUpperCase(ref.label().charAt(0)), ref.key());
        }
    }

    private static PropertyRef ref(List<PropertyRef> refs, String key) {
        return refs.stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void listForEveryGeometry() {
        assertEquals(concat(BASE_KEYS, "geometry.size", "fill"),
                keys(shape(LayerBase.create("l", 1), new Geometry.Ellipse(vec(10, 10)), null, null)));
        assertEquals(concat(BASE_KEYS, "geometry.start", "geometry.end", "fill"),
                keys(shape(LayerBase.create("l", 1), new Geometry.Line(vec(0, 0), vec(10, 10)), null, null)));
        assertEquals(concat(BASE_KEYS, "geometry.radius", "fill"),
                keys(shape(LayerBase.create("l", 1), new Geometry.Polygon(6, num(10)), null, null)));
        assertEquals(concat(BASE_KEYS, "geometry.outerRadius", "geometry.innerRadius", "fill"),
                keys(shape(LayerBase.create("l", 1), new Geometry.Star(5, num(10), num(4)), null, null)));
        assertEquals(concat(BASE_KEYS, "geometry.path", "fill"),
                keys(shape(LayerBase.create("l", 1), new Geometry.PathGeometry(Animatable.of(Paths.square())),
                        null, null)));
    }

    @Test
    void listForOtherLayerTypes() {
        LayerBase base = fullBase();
        String[] tail = {"motionPath.progress", "effects[0].radius", "effects[1].color", "effects[1].radius",
                "effects[1].offsetX", "effects[1].offsetY", "effects[1].spread", "effects[2].level"};

        assertEquals(concat(BASE_KEYS, tail), keys(new GroupLayer(base, List.of())));
        assertEquals(concat(BASE_KEYS, tail), keys(new ImageLayer(base, "asset", 10, 10)));
        assertEquals(concat(BASE_KEYS, tail), keys(new SlotLayer(base, "slot", 10, 10, Transform.IDENTITY)));
        assertEquals(concat(concat(BASE_KEYS, "fontSize", "fill", "stroke.paint", "stroke.width",
                "stroke.dashOffset"), tail), keys(textLayer(base, stroke())));
        assertEquals(concat(BASE_KEYS, "fontSize", "fill"), keys(textLayer(LayerBase.create("t", 1), null)));
    }

    private static TextLayer textLayer(LayerBase base, Stroke stroke) {
        return new TextLayer(base, "Hello", "System", null, num(24), TextLayer.Weight.BOLD, false,
                TextLayer.Align.CENTER, RED, stroke);
    }

    @Test
    void getAndSetRoundTripForEveryKey() {
        LayerBase base = fullBase();
        Trim trim = new Trim(num(0), num(1), num(0));
        List<Layer> layers = List.of(
                shape(base, new Geometry.Rect(vec(100, 50), num(8)), stroke(), trim),
                shape(base, new Geometry.Ellipse(vec(10, 10)), stroke(), trim),
                shape(base, new Geometry.Line(vec(0, 0), vec(10, 10)), stroke(), trim),
                shape(base, new Geometry.Polygon(6, num(10)), stroke(), trim),
                shape(base, new Geometry.Star(5, num(10), num(4)), stroke(), trim),
                shape(base, new Geometry.PathGeometry(Animatable.of(Paths.square())), stroke(), trim),
                textLayer(base, stroke()),
                new ImageLayer(base, "asset", 10, 10),
                new GroupLayer(base, List.of(rectLayer())),
                new SlotLayer(base, "slot", 10, 10, Transform.at(1, 2)));

        java.util.Set<String> seen = new java.util.TreeSet<>();
        for (Layer layer : layers) {
            assertRoundTrip(layer);
            keys(layer).forEach(k -> seen.add(k.replaceAll("\\[\\d+]", "[i]")));
        }
        assertEquals(new java.util.TreeSet<>(List.of(
                "transform.anchor", "transform.position", "transform.scale", "transform.rotation", "transform.skew",
                "opacity", "fill", "stroke.paint", "stroke.width", "stroke.dashOffset",
                "trim.start", "trim.end", "trim.offset",
                "geometry.size", "geometry.cornerRadius", "geometry.start", "geometry.end", "geometry.radius",
                "geometry.outerRadius", "geometry.innerRadius", "geometry.path",
                "fontSize", "motionPath.progress",
                "effects[i].radius", "effects[i].color", "effects[i].offsetX", "effects[i].offsetY",
                "effects[i].spread", "effects[i].level")), seen);
    }

    @Test
    void setKeepsNonAnimatableMembers() {
        ShapeLayer layer = rectLayer();
        ShapeLayer changed = (ShapeLayer) LayerProperties.set(layer, "stroke.width", num(7));
        assertEquals(7.0, changed.stroke().width().at(0));
        assertEquals(Stroke.Cap.ROUND, changed.stroke().cap());
        assertEquals(List.of(4.0, 2.0), changed.stroke().dashArray());
        assertSame(layer.geometry(), changed.geometry());
        assertSame(layer.base(), changed.base());

        GroupLayer group = new GroupLayer(LayerBase.create("g", 1), List.of(layer));
        GroupLayer moved = (GroupLayer) LayerProperties.set(group, "transform.position", vec(3, 4));
        assertSame(layer, moved.children().get(0));
        assertEquals(new Vec2(3, 4), moved.base().transform().position().at(0));
    }

    @Test
    void unknownOrAbsentKeyThrows() {
        ShapeLayer plain = shape(LayerBase.create("l", 1), new Geometry.Ellipse(vec(10, 10)), null, null);
        assertNull(plain.stroke());
        assertNotNull(plain.fill());

        assertThrows(IllegalArgumentException.class, () -> LayerProperties.get(plain, "nope"));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.set(plain, "nope", num(1)));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.get(plain, "stroke.width"));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.set(plain, "stroke.width", num(1)));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.set(plain, "trim.start", num(0)));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.set(plain, "geometry.cornerRadius", num(1)));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.set(plain, "motionPath.progress", num(1)));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.set(plain, "effects[0].radius", num(1)));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.get(rectLayer(), "effects[1].radius"));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.get(rectLayer(), "effects[0].level"));
        assertThrows(IllegalArgumentException.class,
                () -> LayerProperties.get(new GroupLayer(LayerBase.create("g", 1), List.of()), "fill"));
    }

    @Test
    void mismatchedValueTypeThrows() {
        ShapeLayer layer = rectLayer();
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.set(layer, "opacity", vec(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.set(layer, "transform.position", num(1)));
        assertThrows(IllegalArgumentException.class,
                () -> LayerProperties.set(layer, "fill", Animatable.of(Rgba.BLACK)));
        assertThrows(IllegalArgumentException.class, () -> LayerProperties.set(layer, "opacity",
                Animatable.of(Vec2.ZERO).withKeyframe(0, Vec2.ONE, Easing.LINEAR)));
        // every Paint implementation is accepted for a paint property
        assertEquals(new Paint.None(),
                LayerProperties.get(LayerProperties.set(layer, "fill", Animatable.of(new Paint.None())), "fill").at(0));
    }
}
