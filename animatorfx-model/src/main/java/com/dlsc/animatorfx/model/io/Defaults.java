package com.dlsc.animatorfx.model.io;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.TextLayer;

/**
 * The values of optional members that are absent from a file. The writer leaves out members that
 * have these values, so changing one changes the meaning of existing files.
 */
final class Defaults {

    static final String DOCUMENT_NAME = "Untitled";
    static final double FRAME_RATE = 60;
    static final Rgba BACKGROUND = Rgba.TRANSPARENT;

    static final Animatable<Double> ZERO = Animatable.of(0.0);
    static final Animatable<Double> OPACITY = Animatable.of(1.0);
    static final Animatable<Paint> FILL = Animatable.of(new Paint.Solid(Rgba.BLACK));

    static final Animatable<Paint> STROKE_PAINT = Animatable.of(new Paint.Solid(Rgba.BLACK));
    static final Animatable<Double> STROKE_WIDTH = Animatable.of(1.0);
    static final Stroke.Cap CAP = Stroke.Cap.BUTT;
    static final Stroke.Join JOIN = Stroke.Join.MITER;
    static final double MITER_LIMIT = 10;
    static final Animatable<Double> TRIM_END = Animatable.of(1.0);

    static final String FONT_FAMILY = "System";
    static final Animatable<Double> FONT_SIZE = Animatable.of(12.0);
    static final TextLayer.Weight FONT_WEIGHT = TextLayer.Weight.NORMAL;
    static final TextLayer.Align ALIGNMENT = TextLayer.Align.LEFT;

    static final Animatable<Rgba> SHADOW_COLOR = Animatable.of(Rgba.BLACK);

    private Defaults() {
    }
}
