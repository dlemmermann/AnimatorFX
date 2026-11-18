package com.dlsc.animatorfx.player.render;

import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaintConverterTest {

    private static final List<Paint.Stop> STOPS = List.of(new Paint.Stop(0, Rgba.BLACK), new Paint.Stop(1, Rgba.WHITE));

    @Test
    void noPaintIsNull() {
        assertNull(PaintConverter.toFx(new Paint.None()));
        assertNull(PaintConverter.toFx((Paint) null));
    }

    @Test
    void solidBecomesColor() {
        assertEquals(Color.color(0.25, 0.5, 0.75, 0.5), PaintConverter.toFx(new Paint.Solid(new Rgba(0.25, 0.5, 0.75, 0.5))));
    }

    @Test
    void outOfRangeComponentsAreClamped() {
        assertEquals(Color.color(1, 0, 1, 1), PaintConverter.toFx(new Rgba(1.5, -0.5, 1, 2)));
    }

    @Test
    void linearGradientUsesAbsoluteLayerCoordinates() {
        LinearGradient gradient = (LinearGradient) PaintConverter.toFx(
                new Paint.LinearGradient(new Vec2(1, 2), new Vec2(30, 40), STOPS));

        assertFalse(gradient.isProportional());
        assertEquals(1, gradient.getStartX());
        assertEquals(2, gradient.getStartY());
        assertEquals(30, gradient.getEndX());
        assertEquals(40, gradient.getEndY());
        assertEquals(CycleMethod.NO_CYCLE, gradient.getCycleMethod());
        assertEquals(List.of(new Stop(0, Color.BLACK), new Stop(1, Color.WHITE)), gradient.getStops());
    }

    @Test
    void radialGradientUsesAbsoluteLayerCoordinates() {
        RadialGradient gradient = (RadialGradient) PaintConverter.toFx(
                new Paint.RadialGradient(new Vec2(5, 6), 20, STOPS));

        assertFalse(gradient.isProportional());
        assertEquals(5, gradient.getCenterX());
        assertEquals(6, gradient.getCenterY());
        assertEquals(20, gradient.getRadius());
        assertEquals(0, gradient.getFocusDistance());
        assertEquals(List.of(new Stop(0, Color.BLACK), new Stop(1, Color.WHITE)), gradient.getStops());
    }
}
