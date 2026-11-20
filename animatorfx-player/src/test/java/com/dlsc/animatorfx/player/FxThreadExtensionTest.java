package com.dlsc.animatorfx.player;

import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(FxThreadExtension.class)
class FxThreadExtensionTest {

    private final boolean constructedOnFxThread = Platform.isFxApplicationThread();

    @Test
    void constructsFixturesOnTheFxThread() {
        assertTrue(constructedOnFxThread, "JavaFX fixtures must be constructed on the FX thread");
    }
}
