package com.dlsc.animatorfx.player.render;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Trim;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.Evaluator;
import com.dlsc.animatorfx.player.FxThreadExtension;
import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static com.dlsc.animatorfx.player.TestDocs.at;
import static com.dlsc.animatorfx.player.TestDocs.doc;
import static com.dlsc.animatorfx.player.TestDocs.group;
import static com.dlsc.animatorfx.player.TestDocs.rect;
import static com.dlsc.animatorfx.player.TestDocs.stroke;
import static com.dlsc.animatorfx.player.TestDocs.transformed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks what the node tree actually paints. */
@ExtendWith(FxThreadExtension.class)
class RenderedPixelsTest {

    private static final Rgba RED = Rgba.parse("#FF0000FF");
    private static final Rgba BLUE = Rgba.parse("#0000FFFF");

    private static PixelReader paint(Animation animation) {
        Renderer renderer = new Renderer();
        renderer.setDocument(new AnimationFile(animation, Map.of()));
        renderer.apply(Evaluator.sample(animation, 0));
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        parameters.setViewport(new Rectangle2D(0, 0, animation.width(), animation.height()));
        WritableImage image = renderer.getRoot().snapshot(parameters, null);
        return image.getPixelReader();
    }

    private static ShapeLayer filled(String id, double width, double height, Rgba color) {
        return rect(id, width, height).withFill(Animatable.of(new Paint.Solid(color)));
    }

    @SuppressWarnings("unchecked")
    private static <L extends Layer> L masked(L layer, String maskId, Mask.Mode mode) {
        return (L) layer.withBase(layer.base().withMask(new Mask(maskId, mode)));
    }

    private static ShapeLayer withOpacity(ShapeLayer layer, double opacity) {
        return layer.withBase(layer.base().withOpacity(Animatable.of(opacity)));
    }

    private static void assertColor(Color expected, Color actual) {
        assertEquals(expected.getRed(), actual.getRed(), 0.02, "red of " + actual);
        assertEquals(expected.getGreen(), actual.getGreen(), 0.02, "green of " + actual);
        assertEquals(expected.getBlue(), actual.getBlue(), 0.02, "blue of " + actual);
        assertEquals(expected.getOpacity(), actual.getOpacity(), 0.02, "alpha of " + actual);
    }

    @Test
    void laterLayersPaintOverEarlierOnes() {
        PixelReader pixels = paint(doc(at(filled("red", 50, 50, RED), 10, 10), at(filled("blue", 50, 50, BLUE), 30, 30)));

        assertColor(Color.RED, pixels.getColor(20, 20));
        assertColor(Color.BLUE, pixels.getColor(40, 40));
        assertColor(Color.BLUE, pixels.getColor(70, 70));
        assertEquals(0, pixels.getColor(150, 20).getOpacity());
    }

    @Test
    void backgroundFillsTheDocument() {
        PixelReader pixels = paint(doc(at(filled("red", 10, 10, RED), 10, 10)).withBackground(BLUE));

        assertColor(Color.BLUE, pixels.getColor(1, 1));
        assertColor(Color.BLUE, pixels.getColor(198, 98));
        assertColor(Color.RED, pixels.getColor(15, 15));
    }

    @Test
    void groupTransformAndOpacityApplyToChildren() {
        Animation animation = doc(at(group("g", at(filled("red", 20, 20, RED), 10, 0)), 100, 50));

        PixelReader pixels = paint(animation);

        assertColor(Color.RED, pixels.getColor(120, 60));
        assertEquals(0, pixels.getColor(105, 60).getOpacity());
    }

    @Test
    void clipMaskCutsWithFullOpacity() {
        ShapeLayer mask = withOpacity(at(rect("mask", 20, 20), 40, 10), 0.25);
        PixelReader pixels = paint(doc(mask, masked(filled("content", 100, 100, RED), "mask", Mask.Mode.CLIP)));

        assertColor(Color.RED, pixels.getColor(50, 20));
        assertEquals(0, pixels.getColor(10, 10).getOpacity());
        assertEquals(0, pixels.getColor(70, 20).getOpacity());
    }

    @Test
    void alphaMaskMultipliesItsAlpha() {
        ShapeLayer mask = withOpacity(at(rect("mask", 20, 20), 40, 10), 0.5);
        PixelReader pixels = paint(doc(mask, masked(filled("content", 100, 100, RED), "mask", Mask.Mode.ALPHA)));

        assertEquals(0.5, pixels.getColor(50, 20).getOpacity(), 0.03);
        assertTrue(pixels.getColor(50, 20).getRed() > 0.9);
        assertEquals(0, pixels.getColor(10, 10).getOpacity());
    }

    @Test
    void maskStaysPutWhenTheMaskedLayerIsTransformed() {
        ShapeLayer content = transformed(filled("content", 100, 100, RED),
                Transform.at(100, 0).withRotation(Animatable.of(90.0)));
        PixelReader pixels = paint(doc(at(rect("mask", 20, 20), 40, 10), masked(content, "mask", Mask.Mode.CLIP)));

        // the rotated content covers x 0..100, the mask shows x 40..60, y 10..30 of it
        assertColor(Color.RED, pixels.getColor(50, 20));
        assertEquals(0, pixels.getColor(30, 20).getOpacity());
        assertEquals(0, pixels.getColor(50, 40).getOpacity());
    }

    @Test
    void trimStrokesOnlyPartOfTheOutline() {
        Trim trim = new Trim(Animatable.of(0.0), Animatable.of(0.5), Animatable.of(0.0));
        ShapeLayer layer = at(rect("r", 100, 50).withFill(Animatable.of(new Paint.None())).withStroke(stroke(6))
                .withTrim(trim), 10, 10);

        PixelReader pixels = paint(doc(layer));

        // half of the outline is the top and the right edge
        assertColor(Color.BLACK, pixels.getColor(60, 10));
        assertColor(Color.BLACK, pixels.getColor(110, 35));
        assertEquals(0, pixels.getColor(60, 60).getOpacity());
        assertEquals(0, pixels.getColor(10, 35).getOpacity());
    }

    @Test
    void gradientRunsInLayerCoordinates() {
        Paint gradient = new Paint.LinearGradient(new Vec2(0, 0), new Vec2(100, 0),
                List.of(new Paint.Stop(0, Rgba.BLACK), new Paint.Stop(1, Rgba.WHITE)));
        PixelReader pixels = paint(doc(at(rect("r", 100, 20).withFill(Animatable.of(gradient)), 50, 0)));

        assertEquals(0.5, pixels.getColor(100, 10).getRed(), 0.05);
        assertTrue(pixels.getColor(55, 10).getRed() < 0.1);
        assertTrue(pixels.getColor(145, 10).getRed() > 0.9);
    }

    @Test
    void dropShadowIsPaintedBesideTheLayer() {
        Effect shadow = new Effect.DropShadow(Animatable.of(BLUE), Animatable.of(0.0), Animatable.of(30.0),
                Animatable.of(0.0), Animatable.of(0.0));
        ShapeLayer layer = at(filled("r", 20, 20, RED), 10, 10);
        layer = layer.withBase(layer.base().withEffects(List.of(shadow)));

        PixelReader pixels = paint(doc(layer));

        assertColor(Color.RED, pixels.getColor(20, 20));
        assertColor(Color.BLUE, pixels.getColor(50, 20));
    }
}
