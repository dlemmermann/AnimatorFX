package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.editor.doc.Commands.Order;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dlsc.animatorfx.editor.doc.CommandsTest.doc;
import static com.dlsc.animatorfx.editor.doc.CommandsTest.group;
import static com.dlsc.animatorfx.editor.doc.CommandsTest.rect;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureCommandsTest {

    private static List<String> ids(List<Layer> layers) {
        return layers.stream().map(Layer::id).toList();
    }

    private static Layer layer(Animation animation, String id) {
        return Layers.find(animation, id).orElseThrow();
    }

    private static List<String> children(Animation animation, String groupId) {
        return ids(((GroupLayer) layer(animation, groupId)).children());
    }

    private static Layer at(Layer layer, double x, double y) {
        return layer.withBase(layer.base().withTransform(Transform.at(x, y)));
    }

    private static Layer withTransform(Layer layer, Transform transform) {
        return layer.withBase(layer.base().withTransform(transform));
    }

    private static Transform transform(Vec2 anchor, Vec2 position, Vec2 scale, double rotation) {
        return new Transform(Animatable.of(anchor), Animatable.of(position), Animatable.of(scale),
                Animatable.of(rotation), Animatable.of(Vec2.ZERO));
    }

    /** The matrix that takes the layer's local coordinates to document coordinates. */
    private static double[] toDocument(Animation animation, String id) {
        double[] matrix = layer(animation, id).base().transform().matrixAt(0);
        String current = id;
        while (true) {
            var parent = Layers.parentOf(animation, current);
            if (parent.isEmpty()) {
                return matrix;
            }
            matrix = Matrices.multiply(parent.get().base().transform().matrixAt(0), matrix);
            current = parent.get().id();
        }
    }

    @Test
    void allAppliesTheCommandsInOrderUnderOneName() {
        Command both = Commands.all("Both", Commands.addLayer(null, 0, rect("a")), Commands.addLayer(null, 1, rect("b")));

        Animation after = both.apply(doc());

        assertEquals("Both", both.name());
        assertEquals(List.of("a", "b"), ids(after.layers()));
        Animation unchanged = doc(rect("a"));
        assertSame(unchanged, Commands.all("None", Commands.removeLayers(List.of("x"))).apply(unchanged));
    }

    @Test
    void addLayersInsertsTheLayersInOrder() {
        Animation after = Commands.addLayers("Paste", "g", 1, List.of(rect("x"), rect("y")))
                .apply(doc(group("g", rect("a"), rect("b"))));

        assertEquals(List.of("a", "x", "y", "b"), children(after, "g"));
    }

    @Test
    void groupWrapsSiblingsAtTheTopMostSelectedIndex() {
        Animation before = doc(rect("a"), rect("b"), rect("c"), rect("d"));

        Command command = Commands.groupLayers(List.of("c", "a"), "g", "Group 1");
        Animation after = command.apply(before);

        assertEquals("Group", command.name());
        assertEquals(List.of("b", "g", "d"), ids(after.layers()));
        assertEquals(List.of("a", "c"), children(after, "g"));
        GroupLayer group = (GroupLayer) layer(after, "g");
        assertEquals("Group 1", group.name());
        assertEquals(Transform.IDENTITY, group.base().transform());
        assertEquals(0, group.base().inPoint());
        assertEquals(3, group.base().outPoint());
        assertSame(layer(before, "a"), layer(after, "a"));
    }

    @Test
    void groupWorksInsideAGroup() {
        Animation after = Commands.groupLayers(List.of("b", "c"), "inner", "Group 1")
                .apply(doc(group("g", rect("a"), rect("b"), rect("c"))));

        assertEquals(List.of("a", "inner"), children(after, "g"));
        assertEquals(List.of("b", "c"), children(after, "inner"));
    }

    @Test
    void groupRefusesLayersOfDifferentParentsAndSplitMasks() {
        Animation mixed = doc(rect("a"), group("g", rect("b")));
        assertSame(mixed, Commands.groupLayers(List.of("a", "b"), "x", "Group 1").apply(mixed));
        assertSame(mixed, Commands.groupLayers(List.of(), "x", "Group 1").apply(mixed));

        Layer masked = rect("b");
        masked = masked.withBase(masked.base().withMask(new Mask("a", Mask.Mode.CLIP)));
        Animation withMask = doc(rect("a"), masked, rect("c"));
        assertSame(withMask, Commands.groupLayers(List.of("b", "c"), "x", "Group 1").apply(withMask));
        assertEquals(List.of("x", "c"),
                ids(Commands.groupLayers(List.of("a", "b"), "x", "Group 1").apply(withMask).layers()));
    }

    @Test
    void ungroupPutsTheChildrenWhereTheGroupWas() {
        Animation before = doc(rect("a"), group("g", rect("b"), rect("c")), rect("d"));

        Command command = Commands.ungroupLayers(List.of("g"));
        Animation after = command.apply(before);

        assertEquals("Ungroup", command.name());
        assertEquals(List.of("a", "b", "c", "d"), ids(after.layers()));
        assertSame(layer(before, "b"), layer(after, "b"));
        assertSame(before, Commands.ungroupLayers(List.of("a", "nope")).apply(before));
    }

    @Test
    void ungroupComposesAStaticGroupTransformIntoStaticChildren() {
        Layer child = withTransform(rect("b"), transform(new Vec2(20, 15), new Vec2(60, 40), new Vec2(2, 1), 30));
        Layer grouped = withTransform(group("g", child, at(rect("c"), 5, 5)),
                transform(new Vec2(10, 10), new Vec2(300, 200), new Vec2(0.5, 1.5), 45));
        Animation before = doc(grouped);
        double[] placementB = toDocument(before, "b");
        double[] placementC = toDocument(before, "c");

        Animation after = Commands.ungroupLayers(List.of("g")).apply(before);

        assertEquals(List.of("b", "c"), ids(after.layers()));
        assertArrayEquals(placementB, toDocument(after, "b"), 1e-9);
        assertArrayEquals(placementC, toDocument(after, "c"), 1e-9);
        assertEquals(new Vec2(20, 15), layer(after, "b").base().transform().anchor().at(0));
    }

    @Test
    void ungroupMultipliesAStaticGroupOpacityAndClipsTheTimeRange() {
        Layer child = rect("b");
        child = child.withBase(child.base().withOpacity(Animatable.of(0.5)));
        Layer grouped = group("g", child);
        grouped = grouped.withBase(grouped.base().withOpacity(Animatable.of(0.5)).withInPoint(1).withOutPoint(2));

        Animation after = Commands.ungroupLayers(List.of("g")).apply(doc(grouped));

        Layer b = layer(after, "b");
        assertEquals(0.25, b.base().opacity().at(0), 1e-12);
        assertEquals(1, b.base().inPoint());
        assertEquals(2, b.base().outPoint());
    }

    @Test
    void ungroupRefusesAnAnimatedNonIdentityTransform() {
        Animatable<Vec2> moving = Animatable.keyframed(List.of(
                new Keyframe<>("k1", 0, Vec2.ZERO, Easing.LINEAR), new Keyframe<>("k2", 1, new Vec2(50, 0), Easing.LINEAR)));
        Layer animatedGroup = withTransform(group("g", rect("b")), Transform.IDENTITY.withPosition(moving));
        Animation groupAnimated = doc(animatedGroup);
        assertSame(groupAnimated, Commands.ungroupLayers(List.of("g")).apply(groupAnimated));
        assertTrue(Commands.ungroupObstacle(groupAnimated, List.of("g")).orElseThrow().contains("animated"));

        Layer animatedChild = withTransform(rect("b"), Transform.IDENTITY.withPosition(moving));
        Animation childAnimated = doc(at(group("g", animatedChild), 10, 10));
        assertSame(childAnimated, Commands.ungroupLayers(List.of("g")).apply(childAnimated));
        assertTrue(Commands.ungroupObstacle(childAnimated, List.of("g")).isPresent());

        // an identity group does not touch its children, so their animation is no obstacle
        Animation identity = doc(group("g", animatedChild));
        assertTrue(Commands.ungroupObstacle(identity, List.of("g")).isEmpty());
        assertEquals(List.of("b"), ids(Commands.ungroupLayers(List.of("g")).apply(identity).layers()));
    }

    @Test
    void ungroupClearsMasksThatPointedToTheGroup() {
        Layer masked = rect("a");
        masked = masked.withBase(masked.base().withMask(new Mask("g", Mask.Mode.ALPHA)));

        Animation after = Commands.ungroupLayers(List.of("g")).apply(doc(masked, group("g", rect("b"))));

        assertNull(layer(after, "a").base().mask());
    }

    @Test
    void reorderMovesLayersOneStepOrAllTheWay() {
        Animation before = doc(rect("a"), rect("b"), rect("c"), rect("d"));

        assertEquals(List.of("a", "c", "b", "d"), ids(Commands.reorderLayers(List.of("b"), Order.FORWARD).apply(before).layers()));
        assertEquals(List.of("b", "a", "c", "d"), ids(Commands.reorderLayers(List.of("b"), Order.BACKWARD).apply(before).layers()));
        assertEquals(List.of("a", "c", "d", "b"), ids(Commands.reorderLayers(List.of("b"), Order.FRONT).apply(before).layers()));
        assertEquals(List.of("b", "a", "c", "d"), ids(Commands.reorderLayers(List.of("b"), Order.BACK).apply(before).layers()));
    }

    @Test
    void reorderKeepsTheRelativeOrderOfSeveralLayers() {
        Animation before = doc(rect("a"), rect("b"), rect("c"), rect("d"));

        assertEquals(List.of("b", "a", "d", "c"), ids(Commands.reorderLayers(List.of("a", "c"), Order.FORWARD).apply(before).layers()));
        assertEquals(List.of("b", "d", "a", "c"), ids(Commands.reorderLayers(List.of("c", "a"), Order.FRONT).apply(before).layers()));
        assertEquals(List.of("b", "d", "a", "c"), ids(Commands.reorderLayers(List.of("b", "d"), Order.BACK).apply(before).layers()));
        assertEquals(List.of("a", "c", "d", "b"), ids(Commands.reorderLayers(List.of("c", "d"), Order.BACKWARD).apply(before).layers()));
    }

    @Test
    void reorderAtTheEndChangesNothingAndWorksInsideGroups() {
        Animation before = doc(rect("a"), group("g", rect("b"), rect("c")));

        assertSame(before, Commands.reorderLayers(List.of("g"), Order.FORWARD).apply(before));
        assertSame(before, Commands.reorderLayers(List.of("a"), Order.BACK).apply(before));
        assertEquals("Bring Forward", Commands.reorderLayers(List.of("b"), Order.FORWARD).name());
        assertEquals(List.of("c", "b"), children(Commands.reorderLayers(List.of("b"), Order.FORWARD).apply(before), "g"));
    }

    @Test
    void freshCopiesHaveNewLayerAndKeyframeIdsAndKeepMasksAmongThemselves() {
        Animatable<Double> opacity = Animatable.keyframed(List.of(new Keyframe<>("k1", 0, 0.0, Easing.LINEAR)));
        Layer a = rect("a");
        a = a.withBase(a.base().withOpacity(opacity).withMask(new Mask("b", Mask.Mode.CLIP)));
        Layer outside = rect("c");
        outside = outside.withBase(outside.base().withMask(new Mask("zzz", Mask.Mode.CLIP)));

        List<Layer> copies = Commands.freshCopies(List.of(a, rect("b"), outside));

        assertEquals(3, copies.size());
        assertNotEquals("a", copies.get(0).id());
        assertNotEquals("k1", copies.get(0).base().opacity().keyframes().get(0).id());
        assertEquals(copies.get(1).id(), copies.get(0).base().mask().layerId());
        assertNull(copies.get(2).base().mask());
        assertEquals("a", copies.get(0).name());
    }

    @Test
    void translatedShiftsAStaticAndAnAnimatedPosition() {
        Layer still = Commands.translated(at(rect("a"), 5, 5), new Vec2(10, 10));
        assertEquals(new Vec2(15, 15), still.base().transform().position().at(0));

        Animatable<Vec2> moving = Animatable.keyframed(List.of(
                new Keyframe<>("k1", 0, Vec2.ZERO, Easing.LINEAR), new Keyframe<>("k2", 1, new Vec2(50, 0), new Easing.Hold())));
        Layer moved = Commands.translated(withTransform(rect("a"), Transform.IDENTITY.withPosition(moving)), new Vec2(10, 10));
        List<Keyframe<Vec2>> keys = moved.base().transform().position().keyframes();
        assertEquals(new Vec2(10, 10), keys.get(0).value());
        assertEquals(new Vec2(60, 10), keys.get(1).value());
        assertEquals("k2", keys.get(1).id());
        assertFalse(keys.get(1).easing() instanceof Easing.Linear);
    }

    @Test
    void shapeLayersStayShapeLayersWhenGrouped() {
        Animation after = Commands.groupLayers(List.of("a"), "g", "Group 1").apply(doc(rect("a")));

        assertTrue(((GroupLayer) layer(after, "g")).children().get(0) instanceof ShapeLayer);
    }
}
