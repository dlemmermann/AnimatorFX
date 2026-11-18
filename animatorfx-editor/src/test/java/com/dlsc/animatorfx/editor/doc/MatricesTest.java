package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatricesTest {

    private static Transform transform(Vec2 anchor, Vec2 position, Vec2 scale, double rotation, Vec2 skew) {
        return new Transform(Animatable.of(anchor), Animatable.of(position), Animatable.of(scale),
                Animatable.of(rotation), Animatable.of(skew));
    }

    @Test
    void multiplyAppliesTheRightMatrixFirst() {
        double[] translate = {1, 0, 10, 0, 1, 20};
        double[] scale = {2, 0, 0, 0, 3, 0};

        assertEquals(new Vec2(12, 26), Matrices.apply(Matrices.multiply(translate, scale), new Vec2(1, 2)));
        assertEquals(new Vec2(22, 66), Matrices.apply(Matrices.multiply(scale, translate), new Vec2(1, 2)));
    }

    @Test
    void invertUndoesTheMatrixAndASingularMatrixHasNoInverse() {
        double[] m = transform(new Vec2(5, 5), new Vec2(100, 50), new Vec2(2, 0.5), 30, new Vec2(10, 0)).matrixAt(0);

        Vec2 back = Matrices.apply(Matrices.invert(m), Matrices.apply(m, new Vec2(7, -3)));

        assertEquals(7, back.x(), 1e-9);
        assertEquals(-3, back.y(), 1e-9);
        assertNull(Matrices.invert(new double[]{0, 0, 5, 0, 1, 5}));
    }

    @Test
    void applyLinearIgnoresTheTranslation() {
        assertEquals(new Vec2(2, 6), Matrices.applyLinear(new double[]{2, 0, 100, 0, 3, 100}, new Vec2(1, 2)));
    }

    @Test
    void isIdentityToleratesRoundingOnly() {
        assertTrue(Matrices.isIdentity(new double[]{1, 0, 0, 0, 1, 1e-12}));
        assertFalse(Matrices.isIdentity(new double[]{1, 0, 0.01, 0, 1, 0}));
    }

    @Test
    void decomposeGivesATransformWithTheSameMatrix() {
        Vec2 anchor = new Vec2(20, 10);
        double[] group = transform(new Vec2(3, 4), new Vec2(200, 100), new Vec2(1.5, -0.5), 40, Vec2.ZERO).matrixAt(0);
        double[] child = transform(anchor, new Vec2(30, 60), new Vec2(2, 3), -75, new Vec2(15, 0)).matrixAt(0);
        double[] composed = Matrices.multiply(group, child);

        Matrices.Decomposed d = Matrices.decompose(composed, anchor);

        double[] rebuilt = transform(anchor, d.position(), d.scale(), d.rotation(), d.skew()).matrixAt(0);
        assertArrayEquals(composed, rebuilt, 1e-9);
    }

    @Test
    void decomposeOfAPlainTransformReturnsItsValues() {
        Vec2 anchor = new Vec2(50, 50);
        double[] m = transform(anchor, new Vec2(120, 80), new Vec2(2, 0.5), 30, Vec2.ZERO).matrixAt(0);

        Matrices.Decomposed d = Matrices.decompose(m, anchor);

        assertEquals(120, d.position().x(), 1e-9);
        assertEquals(80, d.position().y(), 1e-9);
        assertEquals(2, d.scale().x(), 1e-9);
        assertEquals(0.5, d.scale().y(), 1e-9);
        assertEquals(30, d.rotation(), 1e-9);
        assertEquals(0, d.skew().x(), 1e-9);
        assertEquals(0, d.skew().y(), 1e-9);
    }
}
