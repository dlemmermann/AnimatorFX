package com.dlsc.animatorfx.editor.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dlsc.animatorfx.editor.timeline.LayerDrops.Drop;
import com.dlsc.animatorfx.editor.timeline.LayerDrops.Where;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Mask;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayerDropsTest {

    /** Bottom to top: a, g[c1, c2], top. */
    private final Animation doc = TimelineRowsTest.document();

    private static List<String> ids(List<Layer> layers) {
        return layers.stream().map(Layer::id).toList();
    }

    private Animation drop(List<String> ids, String anchor, Where where) {
        return LayerDrops.command(doc, ids, new Drop(anchor, where)).orElseThrow().apply(doc);
    }

    @Test
    void aMaskedLayerDroppedIntoAGroupTogetherWithItsMaskKeepsTheMask() {
        Animation masked = Commands.setMask("a", new Mask("top", Mask.Mode.CLIP)).apply(doc);

        Animation after = LayerDrops.command(masked, List.of("a", "top"), new Drop("g", Where.INTO)).orElseThrow()
                .apply(masked);

        assertEquals(List.of("c1", "c2", "a", "top"), ids(((GroupLayer) Layers.find(after, "g").orElseThrow()).children()));
        assertEquals(new Mask("top", Mask.Mode.CLIP), Layers.find(after, "a").orElseThrow().base().mask());
    }

    @Test
    void droppingAboveARowPutsTheLayerOverIt() {
        assertEquals(List.of("g", "top", "a"), ids(drop(List.of("a"), "top", Where.ABOVE).layers()));
        assertEquals(List.of("g", "a", "top"), ids(drop(List.of("a"), "g", Where.ABOVE).layers()));
    }

    @Test
    void droppingBelowARowPutsTheLayerUnderIt() {
        assertEquals(List.of("top", "a", "g"), ids(drop(List.of("top"), "a", Where.BELOW).layers()));
    }

    @Test
    void droppingOnAGroupMovesTheLayerIntoItOnTop() {
        Animation after = drop(List.of("a"), "g", Where.INTO);
        assertEquals(List.of("g", "top"), ids(after.layers()));
        assertEquals(List.of("c1", "c2", "a"), ids(((GroupLayer) after.layers().get(0)).children()));
    }

    @Test
    void aChildCanLeaveItsGroup() {
        Animation after = drop(List.of("c1"), "top", Where.ABOVE);
        assertEquals(List.of("a", "g", "top", "c1"), ids(after.layers()));
        assertEquals(List.of("c2"), ids(((GroupLayer) after.layers().get(1)).children()));
    }

    @Test
    void severalLayersKeepTheirOrder() {
        Animation after = drop(List.of("top", "a"), "c1", Where.ABOVE);
        assertEquals(List.of("g"), ids(after.layers()));
        assertEquals(List.of("c1", "a", "top", "c2"), ids(((GroupLayer) after.layers().get(0)).children()));
    }

    @Test
    void impossibleDropsAreRefused() {
        assertTrue(LayerDrops.command(doc, List.of("g"), new Drop("c1", Where.ABOVE)).isEmpty(), "into itself");
        assertTrue(LayerDrops.command(doc, List.of("g"), new Drop("g", Where.INTO)).isEmpty());
        assertTrue(LayerDrops.command(doc, List.of("a"), new Drop("a", Where.ABOVE)).isEmpty(), "onto itself");
        assertTrue(LayerDrops.command(doc, List.of("a"), new Drop("top", Where.INTO)).isEmpty(), "not a group");
        assertTrue(LayerDrops.command(doc, List.of(), new Drop("top", Where.ABOVE)).isEmpty());
    }

    @Test
    void aDropThatChangesNothingLeavesTheDocumentAlone() {
        Animation after = LayerDrops.command(doc, List.of("a"), new Drop("g", Where.BELOW)).orElseThrow().apply(doc);
        assertEquals(doc, after);
    }
}
