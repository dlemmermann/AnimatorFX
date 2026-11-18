package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.session.SessionState;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three tab titles of the side panel are readable in full at the default layout.
 */
class SidePanelTabsTest extends EditorUiTest {

    @Test
    void theTabTitlesFitIntoTheSidePanelInBothThemes() {
        for (Theme.Mode mode : new Theme.Mode[]{Theme.Mode.DARK, Theme.Mode.LIGHT}) {
            run(() -> window.getTheme().setMode(mode));
            layout();
            run(() -> {
                SidePanel panel = window.getSidePanel();
                assertEquals(SessionState.Layout.DEFAULT.sideDivider(), window.getLayoutState().sideDivider(), 0.005);
                Bounds panelBounds = panel.localToScene(panel.getLayoutBounds());
                Set<Node> tabs = panel.getTabPane().lookupAll(".tab-header-area > .headers-region > .tab");
                assertEquals(3, tabs.size());
                for (Node tab : tabs) {
                    Bounds bounds = tab.localToScene(tab.getLayoutBounds());
                    assertTrue(bounds.getMinX() >= panelBounds.getMinX() && bounds.getMaxX() <= panelBounds.getMaxX(),
                            mode + ": tab " + bounds + " leaves the panel " + panelBounds);
                    Label label = (Label) tab.lookup(".tab-label");
                    assertTrue(label.getWidth() >= Math.floor(label.prefWidth(-1)),
                            mode + ": '" + label.getText() + "' is cut off at " + label.getWidth() + " of "
                                    + label.prefWidth(-1));
                }
                assertEquals(List.of("Inspector", "Assets", "Document"),
                        tabs.stream().map(tab -> ((Label) tab.lookup(".tab-label")).getText()).sorted(
                                Comparator.comparingInt(List.of("Inspector", "Assets", "Document")::indexOf))
                                .toList());
                Node overflow = panel.getTabPane().lookup(".control-buttons-tab");
                assertFalse(overflow != null && overflow.isVisible() && overflow.getLayoutBounds().getWidth() >= 1
                        && overflow.getOpacity() > 0, mode + ": the tab overflow button shows");
                // the headers are not cut off either
                Node headers = panel.getTabPane().lookup(".tab-header-area > .headers-region");
                Bounds clip = headers.localToScene(headers.getClip().getBoundsInParent());
                for (Node tab : tabs) {
                    Bounds bounds = tab.localToScene(tab.getLayoutBounds());
                    assertTrue(bounds.getMaxX() <= clip.getMaxX() + 0.5,
                            mode + ": tab " + bounds + " is clipped at " + clip.getMaxX());
                }
            });
        }
    }
}
