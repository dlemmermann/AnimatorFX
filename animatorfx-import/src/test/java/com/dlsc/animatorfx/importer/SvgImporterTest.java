package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.ImageLayer;
import com.dlsc.animatorfx.model.Layer;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.dlsc.animatorfx.importer.ImportChecks.assertColor;
import static com.dlsc.animatorfx.importer.ImportChecks.assertSolid;
import static com.dlsc.animatorfx.importer.ImportChecks.assertVec;
import static com.dlsc.animatorfx.importer.ImportChecks.children;
import static com.dlsc.animatorfx.importer.ImportChecks.named;
import static com.dlsc.animatorfx.importer.ImportChecks.names;
import static com.dlsc.animatorfx.importer.ImportChecks.svg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgImporterTest {

    private static ImportResult read(String svg) throws IOException {
        ImportResult result = SvgImporter.read(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)), "inline");
        ImportChecks.assertSavesAndRenders(result.file());
        return result;
    }

    private static Vec2 position(Layer layer) {
        return layer.base().transform().position().at(0);
    }

    private static Paint fill(Layer layer) {
        return assertInstanceOf(ShapeLayer.class, layer).fill().at(0);
    }

    private static Vec2 size(Layer layer) {
        Geometry geometry = assertInstanceOf(ShapeLayer.class, layer).geometry();
        return switch (geometry) {
            case Geometry.Rect rect -> rect.size().at(0);
            case Geometry.Ellipse ellipse -> ellipse.size().at(0);
            default -> throw new AssertionError("Neither rect nor ellipse: " + geometry);
        };
    }

    private static PathData path(Layer layer) {
        Geometry geometry = assertInstanceOf(ShapeLayer.class, layer).geometry();
        return assertInstanceOf(Geometry.PathGeometry.class, geometry).path().at(0);
    }

    @Test
    void documentAndBasicShapes() throws IOException {
        ImportResult result = svg("basic-shapes");
        Animation animation = result.file().animation();
        assertEquals("basic-shapes", animation.name());
        assertEquals(200, animation.width());
        assertEquals(100, animation.height());
        assertEquals(3, animation.duration());
        assertEquals(60, animation.frameRate());
        assertEquals(Rgba.TRANSPARENT, animation.background());

        // later siblings are on top: the model lists layers bottom to top, like the document
        assertEquals(List.of("box", "uneven", "dot", "ellipse 1", "line 1", "polyline 1", "Triangle"),
                names(animation.layers()));
        Set<String> ids = new HashSet<>();
        animation.layers().forEach(layer -> assertTrue(ids.add(layer.id())));

        ShapeLayer box = named(animation, "box", ShapeLayer.class);
        Geometry.Rect rect = assertInstanceOf(Geometry.Rect.class, box.geometry());
        assertVec(30, 40, rect.size().at(0));
        assertEquals(5, rect.cornerRadius().at(0));
        assertVec(10, 20, position(box));
        assertSolid(1, 0, 0, 1, box.fill().at(0));
        assertNull(box.stroke());
        assertEquals(0, box.base().inPoint());
        assertEquals(3, box.base().outPoint());

        ShapeLayer uneven = named(animation, "uneven", ShapeLayer.class);
        assertEquals(4, ((Geometry.Rect) uneven.geometry()).cornerRadius().at(0));
        assertInstanceOf(Paint.None.class, uneven.fill().at(0));
        assertSolid(0, 0, 0, 1, uneven.stroke().paint().at(0));
        assertEquals(1, uneven.stroke().width().at(0));
        assertEquals(Stroke.Cap.BUTT, uneven.stroke().cap());
        assertEquals(Stroke.Join.MITER, uneven.stroke().join());
        assertEquals(4, uneven.stroke().miterLimit());
        assertEquals(List.of(), uneven.stroke().dashArray());

        ShapeLayer dot = named(animation, "dot", ShapeLayer.class);
        assertVec(40, 40, size(dot));
        assertVec(80, 30, position(dot));
        assertSolid(0, 0, 1, 1, dot.fill().at(0));
        assertEquals(2, dot.stroke().width().at(0));

        ShapeLayer ellipse = named(animation, "ellipse 1", ShapeLayer.class);
        assertVec(60, 20, size(ellipse));
        assertVec(120, 40, position(ellipse));
        assertSolid(0, 128 / 255.0, 0, 1, ellipse.fill().at(0));

        ShapeLayer line = named(animation, "line 1", ShapeLayer.class);
        Geometry.Line geometry = assertInstanceOf(Geometry.Line.class, line.geometry());
        assertVec(0, 90, geometry.start().at(0));
        assertVec(200, 95, geometry.end().at(0));
        assertVec(0, 0, position(line));
        assertSolid(1, 0, 0, 0.5, line.stroke().paint().at(0));
        assertEquals(3, line.stroke().width().at(0));
        assertEquals(Stroke.Cap.ROUND, line.stroke().cap());
        // an odd dash list repeats
        assertEquals(List.of(4.0, 2.0, 1.0, 4.0, 2.0, 1.0), line.stroke().dashArray());
        assertEquals(1.5, line.stroke().dashOffset().at(0));

        ShapeLayer polyline = named(animation, "polyline 1", ShapeLayer.class);
        PathData.Contour open = path(polyline).contours().get(0);
        assertFalse(open.closed());
        assertEquals(3, open.vertices().size());
        assertVec(20, 0, open.vertices().get(2).point());
        assertEquals(Stroke.Join.BEVEL, polyline.stroke().join());
        assertEquals(7, polyline.stroke().miterLimit());
        assertSolid(0x11 / 255.0, 0x22 / 255.0, 0x33 / 255.0, 1, polyline.stroke().paint().at(0));

        ShapeLayer triangle = named(animation, "Triangle", ShapeLayer.class);
        PathData.Contour closed = path(triangle).contours().get(0);
        assertTrue(closed.closed());
        assertEquals(3, closed.vertices().size());
        assertSolid(0x11 / 255.0, 0x22 / 255.0, 0x33 / 255.0, 0x80 / 255.0, triangle.fill().at(0));

        assertEquals(List.of("Rectangles with different horizontal and vertical corner radii use the smaller radius"),
                result.warnings());
    }

    @Test
    void nestedGroupsTransformsGradientClipPathAndFilter() throws IOException {
        ImportResult result = svg("nested");
        Animation animation = result.file().animation();
        assertEquals(List.of("outer"), names(animation.layers()));

        GroupLayer outer = named(animation, "outer", GroupLayer.class);
        assertVec(20, 10, position(outer));
        assertEquals(0.5, outer.base().opacity().at(0));
        assertEquals(List.of("inner", "window", "clipped"), names(outer.children()));

        GroupLayer inner = named(animation, "inner", GroupLayer.class);
        assertEquals(90, inner.base().transform().rotation().at(0));
        assertVec(2, 2, inner.base().transform().scale().at(0));
        assertVec(0, 0, position(inner));
        assertEquals(List.of("box"), names(inner.children()));

        ShapeLayer box = named(animation, "box", ShapeLayer.class);
        assertVec(40, 20, size(box));
        assertVec(10, 5, position(box));
        // the gradient spans the bounding box, in the coordinates of the layer
        Paint.LinearGradient gradient = assertInstanceOf(Paint.LinearGradient.class, box.fill().at(0));
        assertVec(0, 0, gradient.start());
        assertVec(40, 0, gradient.end());
        assertEquals(2, gradient.stops().size());
        assertEquals(0, gradient.stops().get(0).offset());
        assertColor(1, 0, 0, 1, gradient.stops().get(0).color());
        assertEquals(1, gradient.stops().get(1).offset());
        assertColor(0, 0, 1, 0.5, gradient.stops().get(1).color());

        // the clip path becomes a sibling layer that the clipped layer references
        ShapeLayer clipped = named(animation, "clipped", ShapeLayer.class);
        ShapeLayer window = named(animation, "window", ShapeLayer.class);
        assertEquals(new Mask(window.id(), Mask.Mode.CLIP), clipped.base().mask());
        assertVec(60, 60, size(window));
        assertVec(20, 20, position(window));
        assertSolid(0, 1, 0, 1, clipped.fill().at(0));

        assertEquals(List.of("Filters are not supported (1 element ignored)"), result.warnings());
    }

    @Test
    void pathsWithEveryCommandAndADoctype() throws IOException {
        ImportResult result = svg("paths");
        Animation animation = result.file().animation();
        // a path without segments draws nothing
        assertEquals(List.of("everything", "broken"), names(animation.layers()));

        PathData everything = path(named(animation, "everything"));
        assertEquals(2, everything.contours().size());
        assertTrue(everything.contours().get(0).closed());
        assertVec(10, 10, everything.contours().get(0).vertices().get(0).point());
        PathData.Contour square = everything.contours().get(1);
        assertTrue(square.closed());
        assertEquals(3, square.vertices().size());
        // relative to the start of the closed subpath
        assertVec(90, 90, square.vertices().get(0).point());
        assertTrue(everything.contours().get(0).vertices().size() > 15);

        ShapeLayer broken = named(animation, "broken", ShapeLayer.class);
        assertEquals(2, path(broken).contours().get(0).vertices().size());
        assertSolid(0, 0, 0, 1, broken.fill().at(0));
        assertSolid(1, 0, 0, 1, broken.stroke().paint().at(0));
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void gradients() throws IOException {
        ImportResult result = svg("gradients");
        Animation animation = result.file().animation();

        Paint.LinearGradient boxed = assertInstanceOf(Paint.LinearGradient.class, fill(named(animation, "boxed")));
        assertVec(0, 0, boxed.start());
        assertVec(100, 0, boxed.end());
        List<Paint.Stop> stops = boxed.stops();
        assertEquals(4, stops.size());
        assertColor(1, 1, 1, 1, stops.get(0).color());
        assertEquals(0.25, stops.get(1).offset());
        assertColor(1, 0, 0, 0.5, stops.get(1).color());
        // an offset below its predecessor is raised to it
        assertEquals(0.25, stops.get(2).offset());
        assertColor(0, 0, 0, 1, stops.get(2).color());
        assertColor(0, 0, 0, 0, stops.get(3).color());

        // user space coordinates are shifted by the origin of the rectangle; stops are inherited
        Paint.LinearGradient user = assertInstanceOf(Paint.LinearGradient.class, fill(named(animation, "userSpace")));
        assertVec(10, 10, user.start());
        assertVec(110, 10, user.end());
        assertEquals(4, user.stops().size());
        assertColor(1, 1, 1, 0.5, user.stops().get(0).color());
        assertColor(1, 0, 0, 0.25, user.stops().get(1).color());

        // attributes are inherited along the href chain, and the gradient transform applies
        ShapeLayer inherited = named(animation, "inherited", ShapeLayer.class);
        assertInstanceOf(Paint.None.class, inherited.fill().at(0));
        Paint.LinearGradient vertical = assertInstanceOf(Paint.LinearGradient.class, inherited.stroke().paint().at(0));
        assertVec(-5, -10, vertical.start());
        assertVec(-5, 90, vertical.end());

        // a diagonal in a non-square box: the vector is perpendicular to the lines of equal colour
        Paint.LinearGradient skewed = assertInstanceOf(Paint.LinearGradient.class, fill(named(animation, "skewed")));
        assertVec(0, 0, skewed.start());
        assertVec(80, 160, skewed.end());

        Paint.RadialGradient round = assertInstanceOf(Paint.RadialGradient.class, fill(named(animation, "round")));
        assertVec(20, 20, round.center());
        assertEquals(20, round.radius(), 1e-9);

        Paint.RadialGradient spot = assertInstanceOf(Paint.RadialGradient.class, fill(named(animation, "spotted")));
        assertVec(300, 200, spot.center());
        assertEquals(80, spot.radius(), 1e-9);

        Paint.RadialGradient oval = assertInstanceOf(Paint.RadialGradient.class, fill(named(animation, "oval")));
        assertVec(100, 50, oval.center());
        assertEquals(0.5 * Math.sqrt(200 * 100), oval.radius(), 1e-9);

        assertSolid(0, 1, 0, 1, fill(named(animation, "solid")));
        assertInstanceOf(Paint.None.class, fill(named(animation, "unpainted")));
        assertSolid(0, 0, 1, 1, fill(named(animation, "fallback")));
        // a bounding box gradient on a shape without area paints nothing
        assertNull(named(animation, "flat", ShapeLayer.class).stroke());

        assertEquals(List.of(
                "Gradient spread methods other than pad are not supported; the gradients are padded",
                "Focal points of radial gradients are not supported; the centre is used",
                "Radial gradients that are stretched to an ellipse are drawn as circles",
                "Paint references that cannot be resolved are ignored"), result.warnings());
    }

    @Test
    void clipPathsBecomeSiblingMaskLayers() throws IOException {
        ImportResult result = svg("clip");
        Animation animation = result.file().animation();
        List<Layer> layers = animation.layers();
        assertEquals(List.of("one", "a", "two", "b", "one", "c", "d", "e"), names(layers));

        // the clip path lives in the user space of the clipped element, including its transform
        ShapeLayer a = assertInstanceOf(ShapeLayer.class, layers.get(1));
        assertVec(15, 25, position(a));
        assertEquals(new Mask(layers.get(0).id(), Mask.Mode.CLIP), a.base().mask());
        assertVec(50, 50, size(layers.get(0)));
        assertVec(20, 30, position(layers.get(0)));

        // several shapes: a group, placed by the transform of the clip path
        assertEquals(new Mask(layers.get(2).id(), Mask.Mode.CLIP), layers.get(3).base().mask());
        List<Layer> shapes = children(layers.get(2));
        assertEquals(2, shapes.size());
        assertVec(40, 40, size(shapes.get(0)));
        assertVec(100, 0, position(shapes.get(0)));
        assertVec(100, 0, position(shapes.get(1)));
        assertVec(0.5, 0.5, shapes.get(1).base().transform().scale().at(0));
        assertEquals(1, path(shapes.get(1)).contours().size());

        // every use of a clip path gets its own copy
        assertEquals(new Mask(layers.get(4).id(), Mask.Mode.CLIP), layers.get(5).base().mask());
        assertNotEquals(layers.get(0).id(), layers.get(4).id());
        assertVec(10, 10, position(layers.get(4)));

        assertNull(layers.get(6).base().mask());
        assertNull(layers.get(7).base().mask());
        assertEquals(List.of(
                "Clip paths in bounding box units are not supported (1 clip path ignored)",
                "Clip paths that cannot be resolved are ignored"), result.warnings());
    }

    @Test
    void useElementsAreExpanded() throws IOException {
        ImportResult result = svg("use");
        Animation animation = result.file().animation();
        List<Layer> layers = animation.layers();
        assertEquals(List.of("first", "use 1", "symbolic"), names(layers.subList(0, 3)));

        GroupLayer first = assertInstanceOf(GroupLayer.class, layers.get(0));
        assertVec(30, 40, position(first));
        assertEquals(List.of("tile"), names(first.children()));
        // the referenced element inherits from the use element
        assertSolid(0, 0, 1, 1, fill(first.children().get(0)));
        assertVec(10, 10, size(first.children().get(0)));

        GroupLayer second = assertInstanceOf(GroupLayer.class, layers.get(1));
        assertVec(10, 10, position(second));
        assertVec(2, 2, second.base().transform().scale().at(0));
        assertEquals(List.of("pair"), names(second.children()));
        List<Layer> pair = children(second.children().get(0));
        assertEquals(2, pair.size());
        assertSolid(0, 0, 0, 1, fill(children(pair.get(0)).get(0)));
        assertVec(20, 0, position(pair.get(1)));
        assertSolid(1, 0, 0, 1, fill(children(pair.get(1)).get(0)));

        GroupLayer symbolic = assertInstanceOf(GroupLayer.class, layers.get(2));
        assertVec(100, 0, position(symbolic));
        assertEquals(List.of("icon"), names(symbolic.children()));
        assertVec(10, 10, size(children(symbolic.children().get(0)).get(0)));

        // every copy has its own ids
        Set<String> ids = new HashSet<>();
        com.dlsc.animatorfx.model.Layers.flatten(animation).forEach(layer -> assertTrue(ids.add(layer.id())));

        assertEquals(List.of(
                "References to other files are not supported (1 <use> element ignored)",
                "References that cannot be resolved are ignored (1 <use> element)",
                "Circular references are ignored (1 <use> element)"), result.warnings());
    }

    @Test
    void text() throws IOException {
        ImportResult result = svg("text");
        Animation animation = result.file().animation();
        assertEquals(List.of("plain", "styled", "g 1"), names(animation.layers()));

        TextLayer plain = named(animation, "plain", TextLayer.class);
        assertEquals("Hello big world", plain.text());
        assertEquals("Helvetica Neue", plain.fontFamily());
        assertEquals(20, plain.fontSize().at(0));
        assertEquals(TextLayer.Weight.NORMAL, plain.fontWeight());
        assertFalse(plain.italic());
        assertEquals(TextLayer.Align.LEFT, plain.alignment());
        assertSolid(0, 0, 128 / 255.0, 1, plain.fill().at(0));
        assertNull(plain.stroke());
        // y is the baseline in SVG and the top in the model
        assertVec(10, 40 - 0.8 * 20, position(plain));

        TextLayer styled = named(animation, "styled", TextLayer.class);
        assertEquals("Centred", styled.text());
        assertEquals("Serif", styled.fontFamily());
        assertEquals(16, styled.fontSize().at(0), 1e-9);
        assertEquals(TextLayer.Weight.BOLD, styled.fontWeight());
        assertTrue(styled.italic());
        assertEquals(TextLayer.Align.CENTER, styled.alignment());
        assertEquals(0.5, styled.stroke().width().at(0));
        assertVec(150 - 0.55 * 16 * 7 / 2, 100 - 0.8 * 16, position(styled));

        TextLayer right = named(animation, "right", TextLayer.class);
        assertEquals(10, right.fontSize().at(0));
        assertEquals(TextLayer.Align.RIGHT, right.alignment());
        assertVec(200 - 0.55 * 10 * 3, 50 - 8, position(right));

        assertEquals(List.of("Text is placed approximately: the baseline and the width are estimated without the font"
                + " (3 texts)"), result.warnings());
    }

    @Test
    void embeddedImages() throws IOException {
        ImportResult result = svg("image");
        Animation animation = result.file().animation();
        assertEquals(List.of("embedded", "stretched", "natural"), names(animation.layers()));

        // the same data is stored once
        assertEquals(1, animation.assets().size());
        Asset asset = animation.assets().get(0);
        assertEquals(Asset.Kind.IMAGE, asset.kind());
        assertEquals("assets/" + asset.id() + ".png", asset.path());
        byte[] bytes = result.file().assets().get(asset.id());
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals('P', bytes[1]);

        // the 4x2 image is fitted into 40x40 keeping its aspect ratio, centred
        ImageLayer embedded = named(animation, "embedded", ImageLayer.class);
        assertEquals(asset.id(), embedded.assetId());
        assertEquals(40, embedded.width());
        assertEquals(20, embedded.height());
        assertVec(10, 30, position(embedded));

        ImageLayer stretched = named(animation, "stretched", ImageLayer.class);
        assertEquals(40, stretched.width());
        assertEquals(40, stretched.height());
        assertVec(50, 0, position(stretched));

        ImageLayer natural = named(animation, "natural", ImageLayer.class);
        assertEquals(4, natural.width());
        assertEquals(2, natural.height());
        assertVec(5, 80, position(natural));
        assertEquals(0.5, natural.base().opacity().at(0));

        assertEquals(List.of(
                "External images are not supported (1 image ignored)",
                "Embedded images other than PNG and JPEG are not supported (1 image ignored)"), result.warnings());
    }

    @Test
    void presentationAttributesStyleAndInheritance() throws IOException {
        ImportResult result = svg("style");
        Animation animation = result.file().animation();
        assertEquals(List.of("group", "defaults", "evenodd", "evenodd2"), names(animation.layers()));
        assertEquals(0.8, named(animation, "group").base().opacity().at(0));

        ShapeLayer inherits = named(animation, "inherits", ShapeLayer.class);
        assertSolid(0, 128 / 255.0, 0, 0.5, inherits.fill().at(0));
        assertSolid(0, 0, 1, 0.25, inherits.stroke().paint().at(0));
        assertEquals(4, inherits.stroke().width().at(0));
        // opacity is not inherited: it belongs to the group layer
        assertEquals(1, inherits.base().opacity().at(0));

        // the style attribute wins over presentation attributes
        ShapeLayer overrides = named(animation, "overrides", ShapeLayer.class);
        assertSolid(1, 1, 0, 1, overrides.fill().at(0));
        assertNull(overrides.stroke());

        ShapeLayer current = named(animation, "current", ShapeLayer.class);
        assertSolid(0, 0, 128 / 255.0, 0.5, current.fill().at(0));
        assertSolid(0, 0, 128 / 255.0, 0.25, current.stroke().paint().at(0));

        assertFalse(named(animation, "hidden").base().visible());
        // visibility is inherited by the shapes, and a shape can switch itself back on
        assertTrue(named(animation, "invisible").base().visible());
        assertFalse(named(animation, "ghost").base().visible());
        assertTrue(named(animation, "shown").base().visible());

        ShapeLayer explicit = named(animation, "explicit", ShapeLayer.class);
        assertSolid(0, 128 / 255.0, 0, 0.5, explicit.fill().at(0));
        assertEquals(4, explicit.stroke().width().at(0));
        assertEquals(0.8, explicit.base().opacity().at(0));

        assertSolid(0, 128 / 255.0, 0, 1, fill(named(animation, "defaults")));
        assertEquals(List.of("The even-odd fill rule is not supported; the non-zero rule is used (2 shapes)"),
                result.warnings());
    }

    @Test
    void unsupportedFeaturesBecomeWarnings() throws IOException {
        ImportResult result = svg("unsupported");
        Animation animation = result.file().animation();
        assertEquals(List.of(
                "CSS stylesheets are not supported (1 <style> element ignored)",
                "Filters are not supported (3 elements ignored)",
                "Patterns are not supported (1 element ignored)",
                "Masks are not supported (1 element ignored)",
                "Markers are not supported (1 element ignored)",
                "SMIL animation is not supported (4 elements ignored)",
                "Foreign objects are not supported (1 element ignored)",
                "Text on a path is not supported (1 element ignored)",
                "preserveAspectRatio values other than xMidYMid meet and none are not supported; the default is used",
                "References to other files are not supported (1 <use> element ignored)",
                "Text is placed approximately: the baseline and the width are estimated without the font (1 text)",
                "Element <blink> is not supported (2 elements ignored)",
                "Element <hatch> is not supported (1 element ignored)",
                "Transforms that cannot be parsed are ignored"), result.warnings());

        // the view box is half the size of the document
        assertEquals(1, animation.layers().size());
        GroupLayer root = assertInstanceOf(GroupLayer.class, animation.layers().get(0));
        assertVec(2, 2, root.base().transform().scale().at(0));
        assertEquals(List.of("animated", "circle 1", "text 1", "rect 1", "choice"), names(root.children()));
        assertInstanceOf(Paint.None.class, fill(root.children().get(0)));
        assertNull(root.children().get(0).base().mask());
        assertEquals("On", named(animation, "text 1", TextLayer.class).text());
        assertEquals(Transform.IDENTITY, named(animation, "rect 1").base().transform());
        // a switch shows its first child
        assertEquals(List.of("chosen"), names(children(named(animation, "choice"))));
    }

    @Test
    void sizeUnitsAndViewBox() throws IOException {
        ImportResult result = svg("viewbox");
        Animation animation = result.file().animation();
        assertEquals(192, animation.width(), 1e-9);
        assertEquals(50 * 96 / 25.4, animation.height(), 1e-9);

        GroupLayer root = assertInstanceOf(GroupLayer.class, animation.layers().get(0));
        assertVec(1.92, 1.92, root.base().transform().scale().at(0));
        double height = 50 * 96 / 25.4;
        assertVec(-19.2, (height - 96) / 2 - 20 * 1.92, position(root));
        // percentages refer to the view box
        ShapeLayer full = named(animation, "full", ShapeLayer.class);
        assertVec(100, 50, size(full));
        assertVec(10, 20, position(full));
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void sizeFallsBackToTheViewBoxAndThenToTheDefault() throws IOException {
        Animation fromViewBox = read("<svg xmlns='http://www.w3.org/2000/svg' width='100%' viewBox='0 0 64 32'/>")
                .file().animation();
        assertEquals(64, fromViewBox.width());
        assertEquals(32, fromViewBox.height());
        assertEquals(List.of(), fromViewBox.layers());

        Animation fallback = read("<svg xmlns='http://www.w3.org/2000/svg'/>").file().animation();
        assertEquals(300, fallback.width());
        assertEquals(150, fallback.height());

        // stretched without keeping the aspect ratio
        Animation stretched = read("<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100' "
                + "viewBox='0 0 50 25' preserveAspectRatio='none'><rect width='1' height='1'/></svg>").file().animation();
        assertVec(2, 4, stretched.layers().get(0).base().transform().scale().at(0));
    }

    @Test
    void elementsWithoutNamespaceAreAccepted() throws IOException {
        Animation animation = read("<svg width='10' height='10'><rect width='5' height='5'/></svg>").file().animation();
        assertEquals(List.of("rect 1"), names(animation.layers()));
    }

    @Test
    void inkscapeLabelsNameLayers() throws IOException {
        Animation animation = read("<svg xmlns='http://www.w3.org/2000/svg' "
                + "xmlns:inkscape='http://www.inkscape.org/namespaces/inkscape'>"
                + "<g inkscape:label='Background'/><g id='g12' inkscape:label='Ignored'/></svg>").file().animation();
        assertEquals(List.of("Background", "g12"), names(animation.layers()));
    }

    @Test
    void transformedShapesMatchTheSvgMatrix() throws IOException {
        Animation animation = read("<svg xmlns='http://www.w3.org/2000/svg'>"
                + "<rect id='r' x='10' y='20' width='30' height='40' transform='matrix(0.3 -1.2 2.5 0.7 11 -13)'/>"
                + "<circle id='c' cx='10' cy='20' r='5' transform='scale(-1 1) skewX(20)'/></svg>").file().animation();
        double[] expected = Matrix.multiply(SvgTransforms.parse("matrix(0.3 -1.2 2.5 0.7 11 -13)"),
                Matrix.translate(10, 20));
        ImportChecks.assertMatrix(expected, named(animation, "r").base().transform().matrixAt(0), 1e-9);
        expected = Matrix.multiply(SvgTransforms.parse("scale(-1 1) skewX(20)"), Matrix.translate(5, 15));
        ImportChecks.assertMatrix(expected, named(animation, "c").base().transform().matrixAt(0), 1e-9);
    }

    @Test
    void externalEntitiesAreNotResolved() throws IOException {
        Path directory = Path.of("target", "xxe");
        Files.createDirectories(directory);
        Path secret = directory.resolve("secret.txt");
        Files.writeString(secret, "TOPSECRET");
        Path dtd = directory.resolve("evil.dtd");
        Files.writeString(dtd, "<!ENTITY leaked \"TOPSECRET\">");

        String general = "<?xml version='1.0'?><!DOCTYPE svg [<!ENTITY xxe SYSTEM '" + secret.toUri() + "'>]>"
                + "<svg xmlns='http://www.w3.org/2000/svg'><text id='t' x='0' y='10'>safe&xxe;</text></svg>";
        assertNoSecret(general);

        String external = "<?xml version='1.0'?><!DOCTYPE svg SYSTEM '" + dtd.toUri() + "'>"
                + "<svg xmlns='http://www.w3.org/2000/svg'><text id='t' x='0' y='10'>safe&leaked;</text></svg>";
        assertNoSecret(external);

        String parameter = "<?xml version='1.0'?><!DOCTYPE svg [<!ENTITY % remote SYSTEM '" + dtd.toUri()
                + "'> %remote;]><svg xmlns='http://www.w3.org/2000/svg'><text id='t' x='0' y='10'>safe&leaked;</text></svg>";
        assertNoSecret(parameter);
    }

    private static void assertNoSecret(String svg) {
        try {
            ImportResult result = read(svg);
            for (Layer layer : result.file().animation().layers()) {
                if (layer instanceof TextLayer text) {
                    assertFalse(text.text().contains("TOPSECRET"), text.text());
                }
            }
        } catch (IOException e) {
            // rejecting the document is as good as leaving the entity out
            assertFalse(String.valueOf(e.getMessage()).contains("TOPSECRET"));
        }
    }

    @Test
    void internalEntitiesStillWork() throws IOException {
        Animation animation = read("<?xml version='1.0'?><!DOCTYPE svg [<!ENTITY ns_svg 'http://www.w3.org/2000/svg'>"
                + "<!ENTITY greeting 'Hi'>]><svg xmlns='&ns_svg;'><text id='t' y='10'>&greeting;</text></svg>")
                .file().animation();
        assertEquals("Hi", named(animation, "t", TextLayer.class).text());
    }

    @Test
    void unreadableInputFails() {
        assertThrows(IOException.class, () -> read("this is not xml"));
        assertThrows(IOException.class, () -> read("<svg xmlns='http://www.w3.org/2000/svg'><g></svg>"));
        IOException notSvg = assertThrows(IOException.class, () -> read("<html><body/></html>"));
        assertTrue(notSvg.getMessage().contains("html"), notSvg.getMessage());
        assertThrows(IOException.class, () -> read(""));
    }
}
