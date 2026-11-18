package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerNamesTest {

    private static Layer named(String name) {
        return new GroupLayer(LayerBase.create(name, 3), List.of());
    }

    private static Layer slot(String slotName) {
        return new SlotLayer(LayerBase.create("Slot " + slotName, 3), slotName, 10, 10, Transform.IDENTITY);
    }

    private static Animation doc(Layer... layers) {
        return Animation.create("Test", 800, 600, 3).withLayers(List.of(layers));
    }

    @Test
    void theCounterContinuesAfterTheHighestExistingNumber() {
        assertEquals("Rectangle 1", LayerNames.next(doc(), "Rectangle"));
        assertEquals("Rectangle 4", LayerNames.next(doc(named("Rectangle 1"), named("Rectangle 3"), named("Ellipse 9")), "Rectangle"));
        assertEquals("Rectangle 1", LayerNames.next(doc(named("Rectangle"), named("Rectangle x"), named("Rectangle 2 copy")), "Rectangle"));
    }

    @Test
    void nestedLayersCount() {
        Layer group = new GroupLayer(LayerBase.create("Group 1", 3), List.of(named("Star 2")));

        assertEquals("Star 3", LayerNames.next(doc(group), "Star"));
        assertEquals("Group 2", LayerNames.next(doc(group), "Group"));
    }

    @Test
    void slotNamesCountUpAndAreUnique() {
        assertEquals("slot1", LayerNames.nextSlotName(doc()));
        assertEquals("slot3", LayerNames.nextSlotName(doc(slot("slot1"), slot("slot2"), slot("button"))));
        assertEquals("slot1", LayerNames.nextSlotName(doc(slot("slot2"))));
    }

    @Test
    void slotNamesMustBeUniqueJavaIdentifiers() {
        Animation animation = doc(slot("button"));

        assertTrue(LayerNames.slotNameError(animation, "title").isEmpty());
        assertTrue(LayerNames.slotNameError(animation, "_a1$").isEmpty());
        assertTrue(LayerNames.slotNameError(animation, "").orElseThrow().contains("name"));
        assertTrue(LayerNames.slotNameError(animation, "1st").orElseThrow().contains("identifier"));
        assertTrue(LayerNames.slotNameError(animation, "my slot").orElseThrow().contains("identifier"));
        assertTrue(LayerNames.slotNameError(animation, "class").orElseThrow().contains("keyword"));
        assertTrue(LayerNames.slotNameError(animation, "button").orElseThrow().contains("already"));
    }
}
