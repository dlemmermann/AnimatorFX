package com.dlsc.animatorfx.editor.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.Vec2;
import java.util.List;
import org.junit.jupiter.api.Test;

class PropertyUnitsTest {

    @Test
    void unitsFollowTheProperty() {
        assertEquals(new PropertyUnits(100, "%"), PropertyUnits.of("transform.scale"));
        assertEquals(new PropertyUnits(100, "%"), PropertyUnits.of("opacity"));
        assertEquals(new PropertyUnits(100, "%"), PropertyUnits.of("trim.end"));
        assertEquals(new PropertyUnits(1, "°"), PropertyUnits.of("transform.rotation"));
        assertEquals(new PropertyUnits(1, "°"), PropertyUnits.of("transform.skew"));
        assertEquals(new PropertyUnits(1, "px"), PropertyUnits.of("transform.position"));
        assertEquals(new PropertyUnits(1, "px"), PropertyUnits.of("effects[0].radius"));
        assertEquals(new PropertyUnits(100, "%"), PropertyUnits.of("effects[1].level"));
    }

    @Test
    void readOutsAreShortAndCarryTheUnit() {
        assertEquals("120, 80.5", PropertyUnits.text("transform.position", new Vec2(120, 80.5)));
        assertEquals("100, 50%", PropertyUnits.text("transform.scale", new Vec2(1, 0.5)));
        assertEquals("45°", PropertyUnits.text("transform.rotation", 45.0));
        assertEquals("33.3%", PropertyUnits.text("opacity", 0.3333));
        assertEquals("12", PropertyUnits.text("geometry.cornerRadius", 12.0));
        assertEquals("#4C9AFF", PropertyUnits.text("fill", new Paint.Solid(Rgba.parse("#4C9AFF"))));
        assertEquals("#4C9AFF80", PropertyUnits.text("fill", new Paint.Solid(Rgba.parse("#4C9AFF80"))));
        assertEquals("none", PropertyUnits.text("fill", new Paint.None()));
        assertEquals("linear 2", PropertyUnits.text("fill", new Paint.LinearGradient(Vec2.ZERO, Vec2.ONE,
                List.of(new Paint.Stop(0, Rgba.BLACK), new Paint.Stop(1, Rgba.WHITE)))));
        assertEquals("radial 1", PropertyUnits.text("fill", new Paint.RadialGradient(Vec2.ZERO, 1,
                List.of(new Paint.Stop(0, Rgba.BLACK)))));
        assertEquals("#000000", PropertyUnits.text("effects[0].color", Rgba.BLACK));
    }

    @Test
    void numbersDropTrailingZerosAndNegativeZero() {
        assertEquals("0", PropertyUnits.number(-0.0001, 2));
        assertEquals("1.5", PropertyUnits.number(1.5, 2));
        assertEquals("0.333", PropertyUnits.number(1 / 3.0, 3));
        assertEquals("-12.25", PropertyUnits.number(-12.25, 2));
    }
}
