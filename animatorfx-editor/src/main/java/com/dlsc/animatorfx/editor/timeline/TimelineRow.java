package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.PropertyRef;

/**
 * One row of the timeline: a layer, or one of the properties of an expanded layer.
 */
public sealed interface TimelineRow permits TimelineRow.LayerRow, TimelineRow.PropertyRow {

    Layer layer();

    /** The nesting depth: 0 for top-level layers; a property row is one deeper than its layer. */
    int depth();

    default String layerId() {
        return layer().id();
    }

    /**
     * @param expanded whether the property rows (and, for a group, the children) are listed
     */
    record LayerRow(Layer layer, int depth, boolean expanded) implements TimelineRow {
    }

    record PropertyRow(Layer layer, PropertyRef property, int depth) implements TimelineRow {

        public String key() {
            return property.key();
        }
    }
}
