package com.dlsc.animatorfx.importer;

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
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.dlsc.animatorfx.importer.ImportChecks.assertColor;
import static com.dlsc.animatorfx.importer.ImportChecks.assertMatrix;
import static com.dlsc.animatorfx.importer.ImportChecks.assertSolid;
import static com.dlsc.animatorfx.importer.ImportChecks.assertVec;
import static com.dlsc.animatorfx.importer.ImportChecks.children;
import static com.dlsc.animatorfx.importer.ImportChecks.lottie;
import static com.dlsc.animatorfx.importer.ImportChecks.named;
import static com.dlsc.animatorfx.importer.ImportChecks.names;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LottieImporterTest {

    private static final double EPS = 1e-9;

    private static ImportResult read(String json) throws IOException {
        ImportResult result = LottieImporter.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                "inline");
        ImportChecks.assertSavesAndRenders(result.file());
        return result;
    }

    private static <T> void assertKey(double time, Easing easing, Keyframe<T> keyframe) {
        assertEquals(time, keyframe.time(), EPS);
        if (easing instanceof Easing.CubicBezier expected) {
            Easing.CubicBezier actual = assertInstanceOf(Easing.CubicBezier.class, keyframe.easing());
            assertEquals(expected.x1(), actual.x1(), EPS);
            assertEquals(expected.y1(), actual.y1(), EPS);
            assertEquals(expected.x2(), actual.x2(), EPS);
            assertEquals(expected.y2(), actual.y2(), EPS);
        } else {
            assertEquals(easing, keyframe.easing());
        }
    }

    private static Easing bezier(double x1, double y1, double x2, double y2) {
        return new Easing.CubicBezier(x1, y1, x2, y2);
    }

    /** The matrix of a layer in document coordinates: those of the groups around it applied on top. */
    private static double[] worldMatrix(List<Layer> path, double time) {
        double[] matrix = Matrix.identity();
        for (Layer layer : path) {
            matrix = Matrix.multiply(matrix, layer.base().transform().matrixAt(time));
        }
        return matrix;
    }

    @Test
    void documentAndAnimatedTransform() throws IOException {
        ImportResult result = lottie("transform");
        Animation animation = result.file().animation();
        assertEquals("Transform demo", animation.name());
        assertEquals(200, animation.width());
        assertEquals(100, animation.height());
        assertEquals(30, animation.frameRate());
        assertEquals(2, animation.duration());
        assertEquals(Rgba.TRANSPARENT, animation.background());
        assertEquals(List.of("Mover"), names(animation.layers()));

        GroupLayer mover = named(animation, "Mover", GroupLayer.class);
        assertTrue(mover.base().visible());
        assertEquals(0, mover.base().inPoint());
        assertEquals(2, mover.base().outPoint());
        Transform transform = mover.base().transform();

        // percent becomes 0..1; h: 1 is a hold keyframe
        List<Keyframe<Double>> opacity = mover.base().opacity().keyframes();
        assertEquals(2, opacity.size());
        assertKey(0, Easing.HOLD, opacity.get(0));
        assertEquals(0, opacity.get(0).value());
        assertKey(0.5, Easing.LINEAR, opacity.get(1));
        assertEquals(1, opacity.get(1).value());

        // old style keyframes: the last one has only a time and takes the end value of its predecessor
        List<Keyframe<Double>> rotation = transform.rotation().keyframes();
        assertEquals(3, rotation.size());
        assertKey(0, bezier(0.25, 0, 0.5, 1), rotation.get(0));
        assertEquals(0, rotation.get(0).value());
        // handles on the diagonal are a linear easing
        assertKey(1, Easing.LINEAR, rotation.get(1));
        assertEquals(90, rotation.get(1).value());
        assertEquals(2, rotation.get(2).time(), EPS);
        assertEquals(360, rotation.get(2).value());

        // new style keyframes; CubicBezier(o.x, o.y, i.x, i.y)
        List<Keyframe<Vec2>> position = transform.position().keyframes();
        assertEquals(3, position.size());
        assertKey(0, bezier(0.333, 0, 0.667, 1), position.get(0));
        assertVec(10, 20, position.get(0).value());
        assertKey(1, bezier(0.6, 0, 0.2, 1), position.get(1));
        assertVec(110, 20, position.get(1).value());
        assertVec(110, 80, position.get(2).value());

        assertEquals(Animatable.of(new Vec2(5, 5)), transform.anchor());
        List<Keyframe<Vec2>> scale = transform.scale().keyframes();
        assertKey(0, Easing.LINEAR, scale.get(0));
        assertVec(1, 1, scale.get(0).value());
        assertVec(2, 0.5, scale.get(1).value());

        // a group that only wraps one shape is replaced by the shape
        assertEquals(List.of("Square"), names(mover.children()));
        ShapeLayer square = named(animation, "Square", ShapeLayer.class);
        Geometry.Rect rect = assertInstanceOf(Geometry.Rect.class, square.geometry());
        assertVec(10, 10, rect.size().at(0));
        // Lottie places a rectangle by its centre
        assertVec(0, 0, square.base().transform().position().at(0));
        assertVec(5, 5, square.base().transform().anchor().at(0));
        assertSolid(1, 0, 0, 1, square.fill().at(0));
        assertNull(square.stroke());

        assertEquals(List.of("Easing that differs per dimension is not supported; the first dimension is used"
                + " (1 keyframe)"), result.warnings());
    }

    @Test
    void transformMatrixMatchesTheLottieDefinition() throws IOException {
        Transform transform = named(lottie("transform").file().animation(), "Mover").base().transform();
        for (double time : new double[]{0, 0.4, 1, 1.7, 2}) {
            Vec2 p = transform.position().at(time);
            Vec2 s = transform.scale().at(time);
            double r = transform.rotation().at(time);
            // Lottie: translate(p) · rotate(r) · skew · scale(s) · translate(-a), where a skew of
            // sk degrees along x maps (x, y) to (x - tan(sk) · y, y)
            double[] skew = {1, -Math.tan(Math.toRadians(10)), 0, 0, 1, 0};
            double[] expected = Matrix.multiply(Matrix.translate(p.x(), p.y()), Matrix.multiply(Matrix.rotate(r),
                    Matrix.multiply(skew, Matrix.multiply(Matrix.scale(s.x(), s.y()), Matrix.translate(-5, -5)))));
            assertMatrix(expected, transform.matrixAt(time), 1e-9);
        }
        assertVec(-10, 0, transform.skew().at(0));
    }

    @Test
    void shapeGroupsStylesAndTrim() throws IOException {
        ImportResult result = lottie("shapes");
        Animation animation = result.file().animation();
        GroupLayer layer = named(animation, "Shapes", GroupLayer.class);
        assertVec(100, 50, layer.base().transform().position().at(0));
        // Lottie lists the top-most item first
        assertEquals(List.of("Sun", "Card", "Dot", "Badge", "Ring"), names(layer.children()));

        GroupLayer ring = named(animation, "Ring", GroupLayer.class);
        assertVec(10, 0, ring.base().transform().position().at(0));
        assertEquals(45, ring.base().transform().rotation().at(0));
        assertEquals(0.8, ring.base().opacity().at(0), EPS);
        ShapeLayer ellipse = assertInstanceOf(ShapeLayer.class, ring.children().get(0));
        assertEquals("Ellipse", ellipse.name());
        assertVec(60, 40, assertInstanceOf(Geometry.Ellipse.class, ellipse.geometry()).size().at(0));
        assertVec(50, 40, ellipse.base().transform().position().at(0));
        assertVec(30, 20, ellipse.base().transform().anchor().at(0));
        assertInstanceOf(Paint.None.class, ellipse.fill().at(0));
        Stroke stroke = ellipse.stroke();
        assertSolid(0, 0, 1, 0.5, stroke.paint().at(0));
        assertEquals(4, stroke.width().at(0));
        assertEquals(Stroke.Cap.ROUND, stroke.cap());
        assertEquals(Stroke.Join.BEVEL, stroke.join());
        assertEquals(5, stroke.miterLimit());
        assertEquals(List.of(6.0, 3.0), stroke.dashArray());
        assertEquals(2, stroke.dashOffset().at(0));
        // percent to 0..1, degrees to turns
        assertEquals(0.1, ellipse.trim().start().at(0), EPS);
        List<Keyframe<Double>> end = ellipse.trim().end().keyframes();
        assertEquals(2, end.size());
        assertEquals(0.1, end.get(0).value(), EPS);
        assertEquals(1, end.get(1).time(), EPS);
        assertEquals(1, end.get(1).value(), EPS);
        assertEquals(0.25, ellipse.trim().offset().at(0), EPS);

        // a fill above a stroke: the stroke of every geometry lies below the fill of every geometry
        GroupLayer badge = named(animation, "Badge", GroupLayer.class);
        assertEquals(Transform.IDENTITY, badge.base().transform());
        assertEquals(List.of("Hexagon", "Star", "Hexagon", "Star"), names(badge.children()));
        ShapeLayer hexagonStroke = assertInstanceOf(ShapeLayer.class, badge.children().get(0));
        assertInstanceOf(Paint.None.class, hexagonStroke.fill().at(0));
        assertEquals(Stroke.Cap.BUTT, hexagonStroke.stroke().cap());
        assertEquals(Stroke.Join.MITER, hexagonStroke.stroke().join());
        Geometry.Polygon hexagon = assertInstanceOf(Geometry.Polygon.class, hexagonStroke.geometry());
        assertEquals(6, hexagon.sides());
        assertEquals(10, hexagon.radius().at(0));
        assertVec(10, 10, hexagonStroke.base().transform().anchor().at(0));
        ShapeLayer starFill = assertInstanceOf(ShapeLayer.class, badge.children().get(3));
        assertSolid(1, 1, 0, 1, starFill.fill().at(0));
        assertNull(starFill.stroke());
        Geometry.Star star = assertInstanceOf(Geometry.Star.class, starFill.geometry());
        assertEquals(5, star.points());
        assertEquals(20, star.outerRadius().at(0));
        assertEquals(8, star.innerRadius().at(0));
        // centred on the Lottie position, turned by the star's rotation
        assertVec(20, 30, starFill.base().transform().position().at(0));
        assertVec(20, 20, starFill.base().transform().anchor().at(0));
        assertEquals(15, starFill.base().transform().rotation().at(0));

        // a stroke directly above a fill: one shape layer paints both
        ShapeLayer dot = named(animation, "Dot", ShapeLayer.class);
        assertSolid(1, 1, 1, 0.5, dot.fill().at(0));
        assertEquals(2, dot.stroke().width().at(0));
        assertEquals(Stroke.Cap.SQUARE, dot.stroke().cap());
        assertEquals(Stroke.Join.ROUND, dot.stroke().join());
        assertVec(4, 4, dot.base().transform().anchor().at(0));

        GroupLayer card = named(animation, "Card", GroupLayer.class);
        assertEquals(List.of("Tick", "Rect"), names(card.children()));
        ShapeLayer rect = assertInstanceOf(ShapeLayer.class, card.children().get(1));
        Geometry.Rect geometry = assertInstanceOf(Geometry.Rect.class, rect.geometry());
        assertVec(80, 40, geometry.size().at(0));
        assertEquals(6, geometry.cornerRadius().at(0));
        // gradient points are in group coordinates; the rectangle's own origin is its corner
        Paint.LinearGradient gradient = assertInstanceOf(Paint.LinearGradient.class, rect.fill().at(0));
        assertVec(0, 20, gradient.start());
        assertVec(80, 20, gradient.end());
        // alpha stops are merged into the colour stops
        assertEquals(3, gradient.stops().size());
        assertColor(1, 0, 0, 1, gradient.stops().get(0).color());
        assertEquals(0.5, gradient.stops().get(1).offset());
        assertColor(0, 1, 0, 0.5, gradient.stops().get(1).color());
        assertColor(0, 0, 1, 0, gradient.stops().get(2).color());

        ShapeLayer tick = assertInstanceOf(ShapeLayer.class, card.children().get(0));
        PathData.Contour contour = assertInstanceOf(Geometry.PathGeometry.class, tick.geometry()).path().at(0)
                .contours().get(0);
        assertFalse(contour.closed());
        assertEquals(3, contour.vertices().size());
        // Lottie tangents are relative to their vertex: i is the incoming, o the outgoing one
        assertVec(0, 10, contour.vertices().get(1).point());
        assertVec(-2, -2, contour.vertices().get(1).in());
        assertVec(2, 2, contour.vertices().get(1).out());
        Paint.LinearGradient unshifted = assertInstanceOf(Paint.LinearGradient.class, tick.fill().at(0));
        assertVec(-40, 0, unshifted.start());
        assertVec(40, 0, unshifted.end());

        ShapeLayer sun = named(animation, "Sun", ShapeLayer.class);
        assertInstanceOf(Paint.None.class, sun.fill().at(0));
        Paint.RadialGradient radial = assertInstanceOf(Paint.RadialGradient.class, sun.stroke().paint().at(0));
        assertVec(10, 10, radial.center());
        assertEquals(10, radial.radius(), EPS);
        assertColor(1, 1, 1, 0.5, radial.stops().get(0).color());
        assertColor(1, 0, 0, 0.5, radial.stops().get(1).color());
        assertEquals(3, sun.stroke().width().at(0));

        assertEquals(List.of(), result.warnings());
    }

    @Test
    void animatedPathsPaintsAndSizes() throws IOException {
        ImportResult result = lottie("morph");
        Animation animation = result.file().animation();
        assertEquals(List.of("Grow", "Morph"), names(animation.layers()));

        // shape items directly in the layer, without a group
        ShapeLayer blob = named(animation, "Blob", ShapeLayer.class);
        Animatable<PathData> path = assertInstanceOf(Geometry.PathGeometry.class, blob.geometry()).path();
        assertEquals(2, path.keyframes().size());
        assertKey(0, bezier(0.5, 0, 0.5, 1), path.keyframes().get(0));
        assertEquals(1, path.keyframes().get(1).time(), EPS);
        assertTrue(path.at(0).contours().get(0).closed());
        assertVec(10, 0, path.at(0).contours().get(0).vertices().get(1).point());
        assertVec(15, 0, path.at(0.5).contours().get(0).vertices().get(1).point(), 1e-6);
        assertVec(20, 20, path.at(1).contours().get(0).vertices().get(2).point());

        // colour and opacity animated in step: one paint track
        assertEquals(2, blob.fill().keyframes().size());
        assertSolid(1, 0, 0, 1, blob.fill().at(0));
        assertSolid(0.5, 0, 0.5, 0.5, blob.fill().at(0.5));
        assertSolid(0, 0, 1, 0, blob.fill().at(1));

        // an animated size: the anchor follows so that the rectangle stays centred on its position
        ShapeLayer bar = named(animation, "Bar", ShapeLayer.class);
        Geometry.Rect rect = assertInstanceOf(Geometry.Rect.class, bar.geometry());
        for (double time : new double[]{0, 0.25, 0.5, 1}) {
            Vec2 size = rect.size().at(time);
            Vec2 centre = Matrix.apply(bar.base().transform().matrixAt(time), size.x() / 2, size.y() / 2);
            Vec2 expected = new Vec2(10 + 40 * time, 10);
            assertVec(expected.x(), expected.y(), centre, 1e-9);
            assertEquals(20 + 80 * time, size.x(), 1e-9);
        }
        assertEquals(4, rect.cornerRadius().at(0));
        // static colour with animated opacity: the alpha of the paint is animated
        assertSolid(0, 1, 0, 1, bar.fill().at(0));
        assertSolid(0, 1, 0, 0.75, bar.fill().at(1));
        assertSolid(0, 1, 0, 0.5, bar.fill().at(2));

        assertEquals(List.of(), result.warnings());
    }

    @Test
    void parentingBecomesGroupsCarryingTheParentTransform() throws IOException {
        ImportResult result = lottie("parenting");
        Animation animation = result.file().animation();
        // z-order is that of the Lottie file: every layer stays where it is
        assertEquals(List.of("Loop B", "Loop A", "Orphan", "Root", "Root (parent transform)",
                "Root (parent transform)"), names(animation.layers()));

        GroupLayer root = assertInstanceOf(GroupLayer.class, animation.layers().get(3));
        assertFalse(root.base().visible());
        assertEquals(0.25, root.base().opacity().at(0));
        assertEquals(1, root.base().inPoint(), EPS);
        assertEquals(List.of(), root.children());

        // the child follows the parent's transform, but not its opacity, visibility or time range
        GroupLayer midCarrier = assertInstanceOf(GroupLayer.class, animation.layers().get(4));
        assertEquals(root.base().transform(), midCarrier.base().transform());
        assertTrue(midCarrier.base().visible());
        assertEquals(Animatable.of(1.0), midCarrier.base().opacity());
        assertEquals(0, midCarrier.base().inPoint());
        assertEquals(2, midCarrier.base().outPoint());
        assertEquals(List.of("Mid"), names(midCarrier.children()));
        Layer mid = midCarrier.children().get(0);
        assertEquals(0.5, mid.base().opacity().at(0));
        assertEquals(10 / 30.0, mid.base().inPoint(), EPS);
        assertEquals(20 / 30.0, mid.base().outPoint(), EPS);

        GroupLayer outer = assertInstanceOf(GroupLayer.class, animation.layers().get(5));
        assertEquals(root.base().transform(), outer.base().transform());
        assertEquals(List.of("Mid (parent transform)"), names(outer.children()));
        GroupLayer inner = assertInstanceOf(GroupLayer.class, outer.children().get(0));
        assertEquals(mid.base().transform(), inner.base().transform());
        assertEquals(Animatable.of(1.0), inner.base().opacity());
        assertEquals(0, inner.base().inPoint());
        assertEquals(List.of("Child"), names(inner.children()));
        Layer child = inner.children().get(0);
        assertEquals(0.4, child.base().opacity().at(0), EPS);

        // world matrix = root · mid · child, as Lottie composes parents
        for (double time : new double[]{0, 0.7, 2}) {
            double[] expected = Matrix.multiply(root.base().transform().matrixAt(time),
                    Matrix.multiply(mid.base().transform().matrixAt(time), child.base().transform().matrixAt(time)));
            assertMatrix(expected, worldMatrix(List.of(outer, inner, child), time), 1e-9);
        }
        // at the end: rotated by 90° around (100, 50); mid at (30,0) scaled by 2; child at (5,0)
        Vec2 origin = Matrix.apply(worldMatrix(List.of(outer, inner, child), 2), 0, 0);
        assertVec(100, 50 + (30 - 10) + 2 * 5, origin, 1e-9);

        assertVec(1, 2, named(animation, "Orphan").base().transform().position().at(0));
        assertEquals(List.of("Layers whose parent is missing or circular are treated as unparented (3 layers)"),
                result.warnings());
    }

    @Test
    void precompositionsTimeOffsetsAndMarkers() throws IOException {
        ImportResult result = lottie("precomp");
        Animation animation = result.file().animation();
        // without a name in the file, the given name is used
        assertEquals("precomp", animation.name());
        // (op - ip) / fr
        assertEquals(2, animation.duration());
        assertEquals(List.of("Slide", "Intro"), names(animation.layers()));

        // frame 0 is one second before the in point: the keyframe at -1 s is cut off at 0
        Layer slide = named(animation, "Slide");
        List<Keyframe<Vec2>> position = slide.base().transform().position().keyframes();
        assertEquals(2, position.size());
        assertKey(0, Easing.LINEAR, position.get(0));
        assertVec(50, 0, position.get(0).value());
        assertEquals(1, position.get(1).time(), EPS);
        assertVec(100, 0, position.get(1).value());
        assertEquals(0, slide.base().inPoint());
        assertEquals(1, slide.base().outPoint(), EPS);

        GroupLayer intro = named(animation, "Intro", GroupLayer.class);
        assertEquals(0.5, intro.base().inPoint(), EPS);
        assertEquals(1.5, intro.base().outPoint(), EPS);
        assertVec(20, 10, intro.base().transform().position().at(0));
        assertEquals(0.5, intro.base().opacity().at(0));
        assertEquals(List.of("Nested", "Pulse"), names(intro.children()));

        // the precomposition starts at frame 45 = 0.5 s after the in point: everything inside is shifted
        Layer pulse = named(animation, "Pulse");
        List<Keyframe<Vec2>> scale = pulse.base().transform().scale().keyframes();
        assertEquals(0.5, scale.get(0).time(), EPS);
        assertVec(1, 1, scale.get(0).value());
        assertEquals(1, scale.get(1).time(), EPS);
        assertVec(2, 2, scale.get(1).value());
        assertEquals(0.5, pulse.base().inPoint(), EPS);
        assertEquals(1, pulse.base().outPoint(), EPS);

        // offsets of nested precompositions add up
        GroupLayer nested = named(animation, "Nested", GroupLayer.class);
        assertEquals(0.7, nested.base().inPoint(), EPS);
        assertEquals(1.5, nested.base().outPoint(), EPS);
        Layer deep = named(animation, "Deep");
        List<Keyframe<Double>> rotation = deep.base().transform().rotation().keyframes();
        assertEquals(0.7, rotation.get(0).time(), EPS);
        assertEquals(1, rotation.get(1).time(), EPS);
        assertEquals(0.7, deep.base().inPoint(), EPS);
        assertEquals(1, deep.base().outPoint(), EPS);

        List<Marker> markers = animation.markers();
        assertEquals(List.of("intro", "loop", "intro 2", "Marker 4"), markers.stream().map(Marker::name).toList());
        assertEquals(0, markers.get(0).start(), EPS);
        assertEquals(0.5, markers.get(0).end(), EPS);
        assertEquals(1, markers.get(1).start(), EPS);
        assertEquals(1, markers.get(1).end(), EPS);
        assertEquals(1.5, markers.get(2).start(), EPS);
        assertEquals(2, markers.get(2).end(), EPS);
        assertEquals(50 / 30.0, markers.get(3).start(), EPS);
        assertEquals(2, markers.get(3).end(), EPS);

        assertEquals(List.of("The bounds of precompositions do not clip their content (2 precompositions)"),
                result.warnings());
    }

    @Test
    void masksAndAlphaTrackMattes() throws IOException {
        ImportResult result = lottie("masks");
        Animation animation = result.file().animation();
        List<Layer> layers = animation.layers();
        assertEquals(List.of("Clipped (parent transform)", "Both", "Matte 2", "Matted", "Matte", "Faded (mask)",
                "Faded", "Clipped (mask)", "Clipped"), names(layers));

        // a fully opaque mask clips; the mask path is in the coordinates of the masked layer
        Layer clipped = layers.get(8);
        ShapeLayer clipMask = assertInstanceOf(ShapeLayer.class, layers.get(7));
        assertEquals(new Mask(clipMask.id(), Mask.Mode.CLIP), clipped.base().mask());
        assertEquals(clipped.base().transform(), clipMask.base().transform());
        assertVec(10, 20, clipMask.base().transform().position().at(0));
        PathData.Contour triangle = assertInstanceOf(Geometry.PathGeometry.class, clipMask.geometry()).path().at(0)
                .contours().get(0);
        assertTrue(triangle.closed());
        assertEquals(3, triangle.vertices().size());

        // a translucent mask is an alpha mask of that opacity
        Layer faded = layers.get(6);
        assertEquals(new Mask(layers.get(5).id(), Mask.Mode.ALPHA), faded.base().mask());
        assertEquals(0.5, layers.get(5).base().opacity().at(0));

        // an alpha matte: the layer above (td: 1) is the mask
        Layer matte = layers.get(4);
        assertEquals(new Mask(matte.id(), Mask.Mode.ALPHA), layers.get(3).base().mask());
        assertTrue(matte.base().visible());
        assertNull(matte.base().mask());

        // mask and matte on one layer, and a matte that is masked itself: groups keep both
        GroupLayer both = assertInstanceOf(GroupLayer.class, layers.get(1));
        GroupLayer matte2 = assertInstanceOf(GroupLayer.class, layers.get(2));
        assertEquals(new Mask(matte2.id(), Mask.Mode.ALPHA), both.base().mask());
        assertEquals(List.of("Both (mask)", "Both"), names(both.children()));
        assertEquals(new Mask(both.children().get(0).id(), Mask.Mode.CLIP), both.children().get(1).base().mask());
        assertEquals(List.of("Matte 2 (mask)", "Matte 2"), names(matte2.children()));
        // an animated mask path
        ShapeLayer animatedMask = assertInstanceOf(ShapeLayer.class, both.children().get(0));
        assertEquals(2, ((Geometry.PathGeometry) animatedMask.geometry()).path().keyframes().size());

        // the matte named by index (tp); the masked layer is parented, so its carrier is masked
        GroupLayer carrier = assertInstanceOf(GroupLayer.class, layers.get(0));
        assertEquals(new Mask(matte.id(), Mask.Mode.ALPHA), carrier.base().mask());
        assertEquals(List.of("By index"), names(carrier.children()));
        assertNull(carrier.children().get(0).base().mask());

        assertEquals(List.of(), result.warnings());
    }

    @Test
    void solidImageTextLayersAndEffects() throws IOException {
        ImportResult result = lottie("layers");
        Animation animation = result.file().animation();
        assertEquals(List.of("Backdrop", "Picture", "Plain", "Title"), names(animation.layers()));

        ShapeLayer backdrop = named(animation, "Backdrop", ShapeLayer.class);
        assertVec(200, 100, assertInstanceOf(Geometry.Rect.class, backdrop.geometry()).size().at(0));
        assertSolid(0x33 / 255.0, 0x66 / 255.0, 0x99 / 255.0, 1, backdrop.fill().at(0));
        assertEquals(0.5, backdrop.base().opacity().at(0));

        assertEquals(1, animation.assets().size());
        Asset asset = animation.assets().get(0);
        assertEquals(Asset.Kind.IMAGE, asset.kind());
        assertEquals("assets/" + asset.id() + ".png", asset.path());
        assertEquals((byte) 0x89, result.file().assets().get(asset.id())[0]);
        ImageLayer picture = named(animation, "Picture", ImageLayer.class);
        assertEquals(asset.id(), picture.assetId());
        assertEquals(4, picture.width());
        assertEquals(2, picture.height());
        assertVec(50, 25, picture.base().transform().position().at(0));
        assertVec(2, 1, picture.base().transform().anchor().at(0));
        assertEquals(2, picture.base().opacity().keyframes().size());

        assertEquals(2, picture.base().effects().size());
        Effect.Blur blur = assertInstanceOf(Effect.Blur.class, picture.base().effects().get(0));
        assertEquals(10 * LottieEffects.BLURRINESS_TO_RADIUS, blur.radius().at(0), EPS);
        Effect.DropShadow shadow = assertInstanceOf(Effect.DropShadow.class, picture.base().effects().get(1));
        // opacity is 0..255
        assertColor(0, 0, 0, 0.5, shadow.color().at(0));
        assertEquals(8 * LottieEffects.SOFTNESS_TO_RADIUS, shadow.radius().at(0), EPS);
        // direction 135° points down and to the right; the distance is animated
        assertEquals(0, shadow.offsetX().at(0), EPS);
        assertEquals(10 * Math.sqrt(0.5), shadow.offsetX().at(1), EPS);
        assertEquals(10 * Math.sqrt(0.5), shadow.offsetY().at(1), EPS);
        assertEquals(5 * Math.sqrt(0.5), shadow.offsetY().at(0.5), EPS);
        assertEquals(0, shadow.spread().at(0));

        TextLayer title = named(animation, "Title", TextLayer.class);
        assertEquals("Hello\nWorld", title.text());
        assertEquals(20, title.fontSize().at(0));
        // the font is looked up in the font list
        assertEquals("Inter", title.fontFamily());
        assertEquals(TextLayer.Weight.BOLD, title.fontWeight());
        assertTrue(title.italic());
        assertEquals(TextLayer.Align.CENTER, title.alignment());
        assertSolid(0, 0.5, 1, 1, title.fill().at(0));
        assertSolid(1, 0, 0, 1, title.stroke().paint().at(0));
        assertEquals(2, title.stroke().width().at(0));
        assertVec(100, 50, title.base().transform().position().at(0));
        // the baseline lands on the position; the player centres the text on the layer origin
        assertVec(0, 0.8 * 20, title.base().transform().anchor().at(0));

        TextLayer plain = named(animation, "Plain", TextLayer.class);
        assertEquals("abc", plain.text());
        assertEquals("Unknown", plain.fontFamily());
        assertEquals(TextLayer.Weight.NORMAL, plain.fontWeight());
        assertFalse(plain.italic());
        assertEquals(TextLayer.Align.RIGHT, plain.alignment());
        assertNull(plain.stroke());
        assertVec(0, 8, plain.base().transform().anchor().at(0));

        assertEquals(List.of(
                "Text is placed approximately: the baseline is estimated without the font (2 texts)",
                "External images are not supported (1 layer ignored)"), result.warnings());
    }

    @Test
    void unsupportedFeaturesBecomeWarnings() throws IOException {
        ImportResult result = lottie("unsupported");
        Animation animation = result.file().animation();
        assertEquals(List.of(
                "Expressions are not supported (2 properties)",
                "Skew axes are not supported; the layers are skewed along x (1 transform)",
                "Separate x and y position keyframes with different timing are sampled at their key times (1 layer)",
                "Curved motion between position keyframes is not supported; the motion is straight (1 keyframe)",
                "Layers whose parent is missing or circular are treated as unparented (1 layer)",
                "3D layers are not supported; they are treated as 2D (1 layer)",
                "Auto-orient is not supported (1 layer)",
                "Blend modes are not supported; the layers are blended normally (1 layer)",
                "Layer styles are not supported (1 layer)",
                "Layer effects other than Gaussian blur and drop shadow are not supported (2 effects ignored)",
                "Rounded stars and polygons are not supported; their corners are sharp (1 shape)",
                "Shape items of type \"xx\" are not supported (1 shape item ignored)",
                "Highlights of radial gradients are not supported; the centre is used (1 gradient)",
                "Repeaters are not supported (1 shape item ignored)",
                "Merge paths are not supported (1 shape item ignored)",
                "Round corners are not supported (1 shape item ignored)",
                "Pucker and bloat is not supported (1 shape item ignored)",
                "Twist is not supported (1 shape item ignored)",
                "Offset paths are not supported (1 shape item ignored)",
                "Zig zag is not supported (1 shape item ignored)",
                "Several trim paths in one group are not supported; the first one applies (1 group)",
                "Animated dash lengths are not supported; the first value is used (1 stroke)",
                "Trimming several paths one after the other is not supported; they are trimmed together"
                        + " (1 trim path)",
                "The even-odd fill rule is not supported; the non-zero rule is used (1 fill)",
                "Trim paths apply to strokes only; trimmed fills are drawn whole (1 shape)",
                "Only one additive mask per layer is supported (3 masks ignored)",
                "Audio layers that reference external sound files were skipped (1 layer)",
                "Camera layers are not supported (1 layer ignored)",
                "Time remapping is not supported (1 layer)",
                "Time stretch is not supported; the layers play at normal speed (1 layer)",
                "The bounds of precompositions do not clip their content (1 precomposition)",
                "Precompositions that are missing from the file are ignored (1 layer ignored)",
                "Animated text is not supported; the first text is shown (1 layer)",
                "Text is placed approximately: the baseline is estimated without the font (1 text)",
                "Inverted and luminance track mattes are not supported (1 matte ignored)"), result.warnings());

        // what is supported around the unsupported parts is still converted
        assertEquals(List.of("Self", "Curved", "Animated text", "Remapped", "Luma matted (mask)", "Luma matted",
                "Luma source", "Expressive"), names(animation.layers()));
        // a luminance matte is not applied, and its source stays hidden as in Lottie
        assertFalse(named(animation, "Luma source").base().visible());
        Layer matted = named(animation, "Luma matted");
        assertEquals(new Mask(named(animation, "Luma matted (mask)").id(), Mask.Mode.CLIP), matted.base().mask());
        assertEquals("one", named(animation, "Animated text", TextLayer.class).text());
        assertEquals(List.of("Inner"), names(children(named(animation, "Remapped"))));
        // the skew axis is ignored, the skew itself is kept
        assertVec(-5, 0, named(animation, "Expressive").base().transform().skew().at(0));
        // the trim reaches the stroke; the first of the two trims wins
        List<Layer> repeated = children(named(animation, "Repeated"));
        assertEquals(2, repeated.size());
        ShapeLayer stroked = assertInstanceOf(ShapeLayer.class, repeated.get(0));
        assertEquals(0.5, stroked.trim().end().at(0), 1e-9);
        assertEquals(List.of(2.0, 2.0), stroked.stroke().dashArray());
        assertNull(assertInstanceOf(ShapeLayer.class, repeated.get(1)).trim());
        // a radial gradient on a star: the star becomes a path in the coordinates of the gradient
        ShapeLayer star = named(animation, "Rounded star", ShapeLayer.class);
        assertInstanceOf(Geometry.PathGeometry.class, star.geometry());
        Paint.RadialGradient radial = assertInstanceOf(Paint.RadialGradient.class, star.fill().at(0));
        assertVec(0, 0, radial.center());
        assertEquals(20, radial.radius(), 1e-9);
        assertVec(0, -20, ((Geometry.PathGeometry) star.geometry()).path().at(0).contours().get(0).vertices().get(0)
                .point(), 1e-9);
    }

    @Test
    void negativeKeyframeTimesKeepTheAnimationFromZeroOn() {
        Animatable<Double> source = Animatable.keyframed(List.of(
                Keyframes.key(-1, 0.0, bezier(0.8, 0, 0.2, 1)),
                Keyframes.key(1, 100.0, bezier(0.3, 0.5, 0.6, 1.4)),
                Keyframes.key(2, 50.0, Easing.LINEAR)));
        Animatable<Double> cut = Keyframes.fromZero(source);
        assertEquals(3, cut.keyframes().size());
        assertEquals(0, cut.keyframes().get(0).time());
        for (double time = 0; time <= 2.5; time += 0.05) {
            assertEquals(source.at(time), cut.at(time), 1e-6, "at " + time);
        }

        // a keyframe exactly at 0 stays as it is; hold and linear stay hold and linear
        Animatable<Double> exact = Keyframes.fromZero(Animatable.keyframed(List.of(
                Keyframes.key(-2, 1.0, Easing.LINEAR), Keyframes.key(0, 2.0, Easing.HOLD),
                Keyframes.key(1, 3.0, Easing.LINEAR))));
        assertEquals(2, exact.keyframes().size());
        assertEquals(2.0, exact.at(0.5));
        Animatable<Double> held = Keyframes.fromZero(Animatable.keyframed(List.of(
                Keyframes.key(-2, 1.0, Easing.HOLD), Keyframes.key(1, 3.0, Easing.LINEAR))));
        assertEquals(1.0, held.at(0.5));
        assertEquals(3.0, held.at(1));

        // everything before 0: the last value stays
        assertEquals(Animatable.of(7.0), Keyframes.fromZero(Animatable.keyframed(List.of(
                Keyframes.key(-2, 1.0, Easing.LINEAR), Keyframes.key(-1, 7.0, Easing.LINEAR)))));
    }

    @Test
    void propertiesWithDifferentTimingAreSampled() throws IOException {
        // split position: x and y keyed at the same frames combine exactly
        String layer = "{\"ty\":3,\"ind\":1,\"nm\":\"Null\",\"ip\":0,\"op\":60,\"ks\":{\"p\":{\"s\":true,"
                + "\"x\":{\"a\":1,\"k\":[{\"t\":0,\"s\":[0],\"i\":{\"x\":[0.5],\"y\":[1]},\"o\":{\"x\":[0.5],\"y\":[0]}},{\"t\":30,\"s\":[100]}]},"
                + "\"y\":{\"a\":1,\"k\":[{\"t\":0,\"s\":[10],\"i\":{\"x\":[0.5],\"y\":[1]},\"o\":{\"x\":[0.5],\"y\":[0]}},{\"t\":%d,\"s\":[50]}]}}}}";
        String document = "{\"fr\":30,\"ip\":0,\"op\":60,\"w\":10,\"h\":10,\"layers\":[" + layer + "]}";

        ImportResult aligned = read(String.format(document, 30));
        List<Keyframe<Vec2>> keys = aligned.file().animation().layers().get(0).base().transform().position().keyframes();
        assertEquals(2, keys.size());
        assertKey(0, bezier(0.5, 0, 0.5, 1), keys.get(0));
        assertVec(0, 10, keys.get(0).value());
        assertVec(100, 50, keys.get(1).value());
        assertEquals(List.of(), aligned.warnings());

        ImportResult sampled = read(String.format(document, 60));
        keys = sampled.file().animation().layers().get(0).base().transform().position().keyframes();
        assertEquals(3, keys.size());
        assertVec(0, 10, keys.get(0).value());
        assertEquals(1, keys.get(1).time(), EPS);
        assertEquals(100, keys.get(1).value().x(), EPS);
        assertEquals(30, keys.get(1).value().y(), EPS);
        assertVec(100, 50, keys.get(2).value());
        assertEquals(List.of("Separate x and y position keyframes with different timing are sampled at their key"
                + " times (1 layer)"), sampled.warnings());
    }

    @Test
    void gradientStopsMergeColourAndAlpha() {
        // alpha stops at offsets of their own add stops, with the colour interpolated there
        List<Paint.Stop> stops = LottieShapes.stops(
                new double[]{0, 1, 0, 0, 1, 0, 0, 1, 0, 1, 0.25, 0.5, 1, 0}, 2, 1);
        assertEquals(3, stops.size());
        assertColor(1, 0, 0, 1, stops.get(0).color());
        assertEquals(0.25, stops.get(1).offset());
        assertColor(0.75, 0, 0.25, 0.5, stops.get(1).color());
        assertColor(0, 0, 1, 0, stops.get(2).color());

        // without alpha stops the colours are opaque, times the opacity of the fill
        stops = LottieShapes.stops(new double[]{0, 1, 0, 0, 1, 0, 0, 1}, 2, 0.5);
        assertEquals(2, stops.size());
        assertColor(1, 0, 0, 0.5, stops.get(0).color());
        assertEquals(List.of(), LottieShapes.stops(new double[]{0, 1}, 2, 1));
    }

    @Test
    void unreadableInputFails() {
        assertThrows(IOException.class, () -> read("not json"));
        assertThrows(IOException.class, () -> read("[1, 2, 3]"));
        IOException noSize = assertThrows(IOException.class, () -> read("{\"layers\": [], \"fr\": 30}"));
        assertTrue(noSize.getMessage().contains("\"w\""), noSize.getMessage());
        assertThrows(IOException.class, () -> read("{\"w\": 1, \"h\": 1, \"fr\": 30}"));
        assertThrows(IOException.class, () -> read("{\"w\": 1, \"h\": 1, \"fr\": 0, \"layers\": []}"));
    }

    @Test
    void oddInputDoesNotFail() throws IOException {
        // wrong types, missing members, empty arrays: everything reads as its default
        ImportResult result = read("{\"w\": 10, \"h\": 10, \"fr\": 25, \"ip\": 10, \"op\": 5, \"markers\": [{}, 3],"
                + "\"assets\": [3, {\"id\": \"c\", \"layers\": [{\"ty\": 0, \"refId\": \"c\"}]}], \"layers\": ["
                + "{}, 7, null, {\"ty\": 4}, {\"ty\": 4, \"shapes\": [{\"ty\": \"gr\"}, {\"ty\": \"sh\"}, {\"ty\": \"fl\"},"
                + "{\"ty\": \"rc\"}, {\"ty\": \"sr\", \"pt\": {\"a\": 0, \"k\": 0}}, {\"ty\": \"gf\"}, {\"ty\": \"st\", \"d\": [{}]}, 5]},"
                + "{\"ty\": 0, \"refId\": \"c\"}, {\"ty\": 2}, {\"ty\": 5}, {\"ty\": 5, \"t\": {\"d\": {\"k\": [{}]}}},"
                + "{\"ty\": 4, \"ks\": {\"p\": {\"a\": 1, \"k\": [{\"t\": 0}, {\"t\": \"x\", \"s\": [1, 2]}, {\"s\": [1]}]}, \"o\": {\"a\": 1, \"k\": []}},"
                + " \"masksProperties\": [{}], \"tt\": 1, \"ef\": [{}, {\"ty\": 29}, {\"ty\": 25, \"ef\": [{}, {}, {}, {}, {}]}]},"
                + "{\"ty\": 1, \"sc\": \"nope\", \"parent\": 1, \"tt\": 1, \"tp\": 77}]}");
        assertEquals(0, result.file().animation().duration());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void audioLayersWithAnEmbeddedSoundBecomeAudioLayers() throws IOException {
        ImportResult result = lottie("audio");
        Animation animation = result.file().animation();
        assertEquals(2, animation.duration(), EPS);
        assertEquals(List.of("Box", "Nested", "Quiet", "Late", "Beep"), names(animation.layers()));
        assertEquals(List.of(
                "Different audio levels for the left and right channel are not supported; the louder one is used"
                        + " (1 layer)",
                "Audio layers that reference external sound files were skipped (2 layers)",
                "The bounds of precompositions do not clip their content (1 precomposition)"), result.warnings());

        // one asset per embedded sound, however many layers use it
        assertEquals(2, animation.assets().size());
        Asset beepAsset = animation.assets().get(0);
        assertEquals(Asset.Kind.AUDIO, beepAsset.kind());
        assertEquals("beep", beepAsset.name());
        assertEquals("assets/" + beepAsset.id() + ".wav", beepAsset.path());
        assertEquals("assets/" + animation.assets().get(1).id() + ".wav", animation.assets().get(1).path());
        byte[] bytes = result.file().assets().get(beepAsset.id());
        // the WAV file as it was embedded: 400 frames of 16 bit mono at 8 kHz
        assertEquals(44 + 800, bytes.length);
        assertEquals("RIFF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(8000, (bytes[24] & 0xFF) | (bytes[25] & 0xFF) << 8);

        AudioLayer beep = named(animation, "Beep", AudioLayer.class);
        assertEquals(beepAsset.id(), beep.assetId());
        assertEquals(0, beep.base().inPoint(), EPS);
        assertEquals(1, beep.base().outPoint(), EPS);
        assertEquals(0, beep.clipOffset(), EPS);
        assertEquals(Animatable.of(1.0), beep.volume());
        assertEquals(Animatable.of(0.0), beep.balance());
        assertFalse(beep.loop());
        assertFalse(beep.muted());
        assertTrue(beep.base().visible());

        // the sound starts 15 frames before the layer does; the levels are percentages
        AudioLayer late = named(animation, "Late", AudioLayer.class);
        assertEquals(beepAsset.id(), late.assetId());
        assertEquals(0.5, late.base().inPoint(), EPS);
        assertEquals(1.5, late.base().outPoint(), EPS);
        assertEquals(0.5, late.clipOffset(), EPS);
        assertEquals(2, late.volume().keyframes().size());
        assertKey(0.5, Easing.LINEAR, late.volume().keyframes().get(0));
        assertEquals(0.5, late.volume().keyframes().get(0).value(), EPS);
        assertKey(1.5, Easing.LINEAR, late.volume().keyframes().get(1));
        assertEquals(1, late.volume().keyframes().get(1).value(), EPS);

        // starts before the document does; hidden; parent, matte and mask mean nothing to a sound
        AudioLayer quiet = named(animation, "Quiet", AudioLayer.class);
        assertEquals(animation.assets().get(1).id(), quiet.assetId());
        assertEquals(0, quiet.base().inPoint(), EPS);
        assertEquals(2, quiet.base().outPoint(), EPS);
        assertEquals(1 / 3.0, quiet.clipOffset(), EPS);
        assertEquals(0.1, quiet.volume().at(0), EPS);
        assertFalse(quiet.base().visible());
        assertNull(quiet.base().mask());
        assertSame(quiet, animation.layers().get(2));
        assertTrue(late.base().visible());

        // inside a precomposition that starts 15 frames into the composition
        List<Layer> nested = children(named(animation, "Nested"));
        assertEquals(List.of("Nested beep"), names(nested));
        AudioLayer nestedBeep = assertInstanceOf(AudioLayer.class, nested.get(0));
        assertEquals(beepAsset.id(), nestedBeep.assetId());
        assertEquals(5 / 30.0, nestedBeep.base().inPoint(), EPS);
        assertEquals(35 / 30.0, nestedBeep.base().outPoint(), EPS);
        assertEquals(0, nestedBeep.clipOffset(), EPS);
    }

    @Test
    void theFileExtensionOfASoundFollowsItsMimeType() throws IOException {
        String[][] cases = {{"audio/mpeg", "mp3"}, {"audio/mp3", "mp3"}, {"audio/wav", "wav"}, {"audio/x-wav", "wav"},
                {"audio/wave", "wav"}, {"AUDIO/WAV", "wav"}, {"audio/aiff", "aiff"}, {"audio/x-aiff", "aiff"},
                {"audio/mp4", "m4a"}, {"audio/x-m4a", "m4a"}, {"audio/aac", "aac"}};
        for (String[] each : cases) {
            ImportResult result = read(soundDocument("data:" + each[0] + ";base64,AAECAw==", ""));
            Animation animation = result.file().animation();
            assertEquals(List.of(), result.warnings(), each[0]);
            assertEquals(1, animation.assets().size(), each[0]);
            assertTrue(animation.assets().get(0).path().endsWith("." + each[1]), each[0]);
            assertEquals(Asset.Kind.AUDIO, animation.assets().get(0).kind());
            assertEquals(4, result.file().assets().get(animation.assets().get(0).id()).length);
            // without a name of its own the asset is named after its id
            assertEquals("s", animation.assets().get(0).name());
        }
        ImportResult unknown = read(soundDocument("data:audio/ogg;base64,AAECAw==", ""));
        assertEquals(List.of(), unknown.file().animation().layers());
        assertEquals(List.of(), unknown.file().animation().assets());
        assertEquals(List.of("Embedded sounds in a format that is not supported were skipped (1 layer)"),
                unknown.warnings());
    }

    @Test
    void audioLevelsThatAreNotUnderstoodGiveFullVolume() throws IOException {
        ImportResult result = read(soundDocument("data:audio/wav;base64,AAECAw==",
                ",\"au\":{\"lv\":{\"a\":0,\"k\":\"loud\"}}"));
        AudioLayer layer = assertInstanceOf(AudioLayer.class, result.file().animation().layers().get(0));
        assertEquals(Animatable.of(1.0), layer.volume());
        assertEquals(List.of("Audio levels that are not understood are ignored; the sound plays at full volume"
                + " (1 layer)"), result.warnings());

        // percentages: the louder channel counts, more than 100 % is full volume, a single number serves both
        String[][] cases = {{"[50, 50]", "0.5"}, {"[100, 0]", "1"}, {"[0, 100]", "1"}, {"[150, 150]", "1"},
                {"[0, 0]", "0"}, {"[25]", "0.25"}, {"40", "0.4"}, {"[-30, -30]", "0"}};
        for (String[] each : cases) {
            result = read(soundDocument("data:audio/wav;base64,AAECAw==",
                    ",\"au\":{\"lv\":{\"a\":0,\"k\":" + each[0] + "}}"));
            assertEquals(Double.parseDouble(each[1]),
                    ((AudioLayer) result.file().animation().layers().get(0)).volume().at(0), EPS, each[0]);
        }
        // no levels at all: full volume, nothing to report
        result = read(soundDocument("data:audio/wav;base64,AAECAw==", ""));
        assertEquals(Animatable.of(1.0), ((AudioLayer) result.file().animation().layers().get(0)).volume());
        assertEquals(List.of(), result.warnings());
        result = read(soundDocument("data:audio/wav;base64,AAECAw==", ",\"au\":{}"));
        assertEquals(Animatable.of(1.0), ((AudioLayer) result.file().animation().layers().get(0)).volume());
    }

    @Test
    void aSoundDoesNotStartBeforeItsStartTime() throws IOException {
        // the layer is in from frame 0, its sound starts at frame 15: nothing can sound before that
        ImportResult result = read(soundDocument("data:audio/wav;base64,AAECAw==", "")
                .replace("\"st\":0", "\"st\":15"));
        AudioLayer layer = (AudioLayer) result.file().animation().layers().get(0);
        assertEquals(0.5, layer.base().inPoint(), EPS);
        assertEquals(2, layer.base().outPoint(), EPS);
        assertEquals(0, layer.clipOffset(), EPS);

        // a start after the out point leaves a layer without a duration, at the out point
        result = read(soundDocument("data:audio/wav;base64,AAECAw==", "")
                .replace("\"op\":60,\"st\":0", "\"op\":30,\"st\":45"));
        layer = (AudioLayer) result.file().animation().layers().get(0);
        assertEquals(1, layer.base().inPoint(), EPS);
        assertEquals(1, layer.base().outPoint(), EPS);
        assertEquals(0, layer.clipOffset(), EPS);
    }

    @Test
    void anImageLayerThatRefersToASoundSaysSo() throws IOException {
        ImportResult result = read(soundDocument("data:audio/wav;base64,AAECAw==", "")
                .replace("{\"ty\":6,", "{\"ty\":2,"));
        assertEquals(List.of(), result.file().animation().layers());
        assertEquals(List.of(), result.file().animation().assets());
        assertEquals(List.of("Image layers whose asset is a sound are ignored (1 layer ignored)"), result.warnings());
    }

    private static String soundDocument(String source, String layerMembers) {
        return "{\"fr\":30,\"ip\":0,\"op\":60,\"w\":100,\"h\":100,\"assets\":[{\"id\":\"s\",\"u\":\"\",\"e\":1,"
                + "\"p\":\"" + source + "\"}],\"layers\":[{\"ty\":6,\"nm\":\"Sound\",\"refId\":\"s\",\"ip\":0,"
                + "\"op\":60,\"st\":0" + layerMembers + "}]}";
    }
}
