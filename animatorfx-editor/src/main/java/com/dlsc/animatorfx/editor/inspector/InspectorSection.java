package com.dlsc.animatorfx.editor.inspector;

import javafx.scene.Node;

/**
 * One section of the inspector. The inspector keeps an ordered list of sections
 * ({@link InspectorView#getSections()}); a section is shown while it applies to the selection.
 * Its node is built once; {@link #update} only brings the values in it up to date, also on
 * every playhead move, so it must be cheap and must not rebuild the node.
 *
 * <p>{@link SectionBase} provides the collapsible frame and rows of labelled fields.
 */
public interface InspectorSection {

    /** The heading. */
    String title();

    /** Whether the section has something to show for the selection of the context. */
    boolean appliesTo(InspectorContext context);

    /** The node of the section; always the same instance. */
    Node node();

    /** Shows the values of the selection at the playhead. Only called while the section applies. */
    void update(InspectorContext context);
}
