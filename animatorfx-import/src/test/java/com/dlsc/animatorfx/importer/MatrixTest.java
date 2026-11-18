package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;

import static com.dlsc.animatorfx.importer.ImportChecks.assertMatrix;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixTest {

    private static void assertRoundTrip(double[] matrix) {
        Transform transform = Matrix.decompose(matrix);
        assertEquals(Vec2.ZERO, transform.anchor().at(0));
        assertEquals(0, transform.skew().at(0).y());
        assertMatrix(matrix, transform.matrixAt(0), 1e-9);
    }

    @Test
    void decompositionReproducesTheMatrix() {
        assertRoundTrip(Matrix.identity());
        assertRoundTrip(SvgTransforms.parse("translate(10 20)"));
        assertRoundTrip(SvgTransforms.parse("rotate(30)"));
        assertRoundTrip(SvgTransforms.parse("rotate(200 10 20) scale(2 0.5)"));
        assertRoundTrip(SvgTransforms.parse("scale(3 0.25) rotate(40)"));
        assertRoundTrip(SvgTransforms.parse("skewX(30)"));
        assertRoundTrip(SvgTransforms.parse("skewY(30)"));
        assertRoundTrip(SvgTransforms.parse("translate(5,6) skewY(-20) skewX(50) rotate(-70) scale(1.5, 4)"));
        assertRoundTrip(SvgTransforms.parse("matrix(0.3 -1.2 2.5 0.7 11 -13)"));
    }

    @Test
    void reflectionsHaveANegativeDeterminantAndStillRoundTrip() {
        double[] flipX = SvgTransforms.parse("scale(-1 1)");
        assertTrue(Matrix.determinant(flipX) < 0);
        assertRoundTrip(flipX);
        assertRoundTrip(SvgTransforms.parse("scale(1 -1)"));
        assertRoundTrip(SvgTransforms.parse("matrix(0 1 1 0 0 0)"));
        assertRoundTrip(SvgTransforms.parse("rotate(33) scale(-2 3) skewX(10) translate(1 2)"));
        assertRoundTrip(SvgTransforms.parse("matrix(-0.3 -1.2 2.5 -0.7 11 -13)"));
    }

    @Test
    void simpleMatricesDecomposeToCleanValues() {
        Transform transform = Matrix.decompose(SvgTransforms.parse("translate(20 10) rotate(90) scale(2)"));
        assertEquals(new Vec2(20, 10), transform.position().at(0));
        assertEquals(90, transform.rotation().at(0));
        assertEquals(new Vec2(2, 2), transform.scale().at(0));
        assertEquals(Vec2.ZERO, transform.skew().at(0));

        // a mirror is a negative y scale turned half around
        transform = Matrix.decompose(SvgTransforms.parse("scale(-1 1)"));
        assertEquals(new Vec2(1, -1), transform.scale().at(0));
        assertEquals(180, transform.rotation().at(0));
    }

    @Test
    void transformListGrammar() {
        assertArrayEquals(new double[]{1, 0, 10, 0, 1, 0}, SvgTransforms.parse("translate(10)"));
        assertArrayEquals(new double[]{2, 0, 0, 0, 2, 0}, SvgTransforms.parse(" scale( 2 ) "));
        assertArrayEquals(new double[]{1, 3, 5, 2, 4, 6}, SvgTransforms.parse("matrix(1,2,3,4,5,6)"));
        assertArrayEquals(new double[]{1, 3, 5, 2, 4, 6}, SvgTransforms.parse("matrix(1 2 3e0 4 5,6)"));
        // chained left to right: the last one applies to the point first
        assertArrayEquals(new double[]{2, 0, 10, 0, 2, 20}, SvgTransforms.parse("translate(10,20),scale(2)"));
        assertArrayEquals(new double[]{2, 0, 20, 0, 2, 40}, SvgTransforms.parse("scale(2)translate(10 20)"));
        // rotation around a centre keeps the centre
        assertMatrix(new double[]{0, -1, 30, 1, 0, 10}, SvgTransforms.parse("rotate(90 10 20)"), 1e-12);
        assertMatrix(new double[]{1, 1, 0, 0, 1, 0}, SvgTransforms.parse("skewX(45)"), 1e-12);
        assertMatrix(new double[]{1, 0, 0, 1, 1, 0}, SvgTransforms.parse("skewY(45)"), 1e-12);
        assertArrayEquals(Matrix.identity(), SvgTransforms.parse(""));
        assertArrayEquals(Matrix.identity(), SvgTransforms.parse(null));
    }

    @Test
    void invalidTransformListsAreRejected() {
        assertNull(SvgTransforms.parse("rotate(oops)"));
        assertNull(SvgTransforms.parse("spin(3)"));
        assertNull(SvgTransforms.parse("translate(1 2 3)"));
        assertNull(SvgTransforms.parse("scale(2"));
        assertNull(SvgTransforms.parse("matrix(1 2 3)"));
    }
}
