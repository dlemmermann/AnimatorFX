package com.dlsc.animatorfx.player.render;

import com.dlsc.animatorfx.player.FxThreadExtension;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(FxThreadExtension.class)
class HeadlessToolkitTest {

    @Test
    void createsNodesOnTheFxThread() {
        assertTrue(Platform.isFxApplicationThread());
        Group group = new Group(new Rectangle(10, 20));
        assertEquals(10, group.getLayoutBounds().getWidth());
        assertEquals("Monocle", System.getProperty("glass.platform"));
    }
}
