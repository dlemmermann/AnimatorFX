package com.dlsc.animatorfx.player.render;

import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts model paints to JavaFX paints. Gradients keep their layer-local coordinates.
 */
public final class PaintConverter {

    private PaintConverter() {
    }

    /**
     * @return the JavaFX paint, or {@code null} for no paint ({@code null} or {@link Paint.None})
     */
    public static javafx.scene.paint.Paint toFx(Paint paint) {
        return switch (paint) {
            case null -> null;
            case Paint.None none -> null;
            case Paint.Solid solid -> toFx(solid.color());
            case Paint.LinearGradient linear -> new LinearGradient(linear.start().x(), linear.start().y(),
                    linear.end().x(), linear.end().y(), false, CycleMethod.NO_CYCLE, stops(linear.stops()));
            case Paint.RadialGradient radial -> new RadialGradient(0, 0, radial.center().x(), radial.center().y(),
                    radial.radius(), false, CycleMethod.NO_CYCLE, stops(radial.stops()));
        };
    }

    /**
     * Components outside {@code 0..1} are clamped.
     */
    public static Color toFx(Rgba color) {
        return Color.color(unit(color.r()), unit(color.g()), unit(color.b()), unit(color.a()));
    }

    private static List<Stop> stops(List<Paint.Stop> stops) {
        List<Stop> result = new ArrayList<>(stops.size());
        for (Paint.Stop stop : stops) {
            result.add(new Stop(unit(stop.offset()), toFx(stop.color())));
        }
        return result;
    }

    private static double unit(double value) {
        return Double.isNaN(value) ? 0 : Math.clamp(value, 0.0, 1.0);
    }
}
