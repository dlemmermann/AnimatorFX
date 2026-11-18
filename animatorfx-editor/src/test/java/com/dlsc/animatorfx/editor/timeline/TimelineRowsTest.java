package com.dlsc.animatorfx.editor.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.timeline.TimelineRow.LayerRow;
import com.dlsc.animatorfx.editor.timeline.TimelineRow.PropertyRow;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Vec2;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TimelineRowsTest {

    static ShapeLayer rect(String id) {
        return new ShapeLayer(LayerBase.create(id, 3).withId(id),
                new Geometry.Rect(Animatable.of(new Vec2(10, 10)), Animatable.of(0.0)),
                Animatable.of(new Paint.Solid(Rgba.WHITE)), null, null);
    }

    static Animation document() {
        Layer animated = rect("a");
        animated = animated.withBase(animated.base().withTransform(animated.base().transform().withPosition(
                Animatable.of(Vec2.ZERO).withKeyframe(0.5, Vec2.ZERO, Easing.LINEAR)
                        .withKeyframe(1.5, new Vec2(5, 5), Easing.LINEAR)))
                .withOpacity(Animatable.of(1.0).withKeyframe(1.5, 0.5, Easing.LINEAR)));
        GroupLayer group = new GroupLayer(LayerBase.create("g", 3).withId("g"), List.of(rect("c1"), rect("c2")));
        return Animation.create("doc", 100, 100, 3).withLayers(List.of(animated, group, rect("top")));
    }

    private static List<String> keys(List<TimelineRow> rows) {
        return rows.stream().map(r -> r instanceof PropertyRow p ? p.layerId() + ":" + p.key() : r.layerId()).toList();
    }

    @Test
    void theTopMostLayerComesFirstAndCollapsedLayersHideTheirRows() {
        List<TimelineRow> rows = TimelineRows.build(document(), Set.of(), false);
        assertEquals(List.of("top", "g", "a"), keys(rows));
        assertTrue(rows.stream().allMatch(r -> r.depth() == 0));
        assertEquals(false, ((LayerRow) rows.get(1)).expanded());
    }

    @Test
    void anExpandedLayerListsItsAnimatedProperties() {
        List<TimelineRow> rows = TimelineRows.build(document(), Set.of("a", "top"), false);
        assertEquals(List.of("top", "g", "a", "a:transform.position", "a:opacity"), keys(rows));
        PropertyRow position = (PropertyRow) rows.get(3);
        assertEquals("Position", position.property().label());
        assertEquals(1, position.depth());
    }

    @Test
    void showAllListsEveryProperty() {
        List<TimelineRow> rows = TimelineRows.build(document(), Set.of("top"), true);
        assertEquals(List.of("top", "top:transform.anchor", "top:transform.position", "top:transform.scale",
                "top:transform.rotation", "top:transform.skew", "top:opacity", "top:geometry.size",
                "top:geometry.cornerRadius", "top:fill", "g", "a"), keys(rows));
    }

    @Test
    void anExpandedGroupShowsItsPropertiesBeforeItsChildren() {
        Animation doc = Commands.addKeyframe("g", "opacity", 1).apply(document());
        List<TimelineRow> rows = TimelineRows.build(doc, Set.of("g", "c1"), false);
        assertEquals(List.of("top", "g", "g:opacity", "c2", "c1", "a"), keys(rows));
        assertEquals(1, rows.get(3).depth());
    }

    @Test
    void keyTimesAreTheDistinctTimesOfAllKeyframesOfALayer() {
        assertEquals(List.of(0.5, 1.5), TimelineRows.keyTimes(document().layers().get(0)));
        assertEquals(List.of(), TimelineRows.keyTimes(document().layers().get(2)));
    }

    @Test
    void neighboursOfATimeAreTheNearestKeyframesOnEitherSide() {
        Animatable<?> position = document().layers().get(0).base().transform().position();
        assertEquals(0.5, TimelineRows.previousKeyTime(position, 1.0).orElseThrow());
        assertEquals(1.5, TimelineRows.nextKeyTime(position, 1.0).orElseThrow());
        assertTrue(TimelineRows.previousKeyTime(position, 0.5).isEmpty());
        assertTrue(TimelineRows.nextKeyTime(position, 1.5).isEmpty());
        assertEquals(position.keyframes().get(0), TimelineRows.keyframeAt(position, 0.5).orElseThrow());
        assertTrue(TimelineRows.keyframeAt(position, 0.6).isEmpty());
    }
}
