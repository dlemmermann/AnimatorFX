package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.canvas.TransformMath.Values;
import com.dlsc.animatorfx.editor.doc.Matrices;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransformMathTest {

    private static final Box LOCAL = new Box(0, 0, 100, 50);
    /** A 100 x 50 box with its anchor in the centre, centred on (200, 100): it covers 150..250 x 75..125. */
    private static final Values PLAIN = new Values(new Vec2(50, 25), new Vec2(200, 100), Vec2.ONE, 0, Vec2.ZERO);

    private static void assertVec(double x, double y, Vec2 actual) {
        assertEquals(x, actual.x(), 1e-9, "x of " + actual);
        assertEquals(y, actual.y(), 1e-9, "y of " + actual);
    }

    private static Values with(Values start, TransformMath.Scaled scaled) {
        return new Values(start.anchor(), scaled.position(), scaled.scale(), start.rotation(), start.skew());
    }

    @Test
    void aCornerHandleScalesAboutTheOppositeCorner() {
        TransformMath.Scaled scaled = TransformMath.scale(PLAIN, LOCAL, Handle.SE, new Vec2(350, 175), false, false);

        assertVec(2, 2, scaled.scale());
        assertVec(250, 125, scaled.position());
        assertVec(150, 75, Matrices.apply(with(PLAIN, scaled).matrix(), new Vec2(0, 0)));
    }

    @Test
    void anEdgeHandleChangesOneAxisOnly() {
        TransformMath.Scaled scaled = TransformMath.scale(PLAIN, LOCAL, Handle.E, new Vec2(300, 999), false, false);

        assertVec(1.5, 1, scaled.scale());
        assertVec(225, 100, scaled.position());
    }

    @Test
    void altScalesAboutTheAnchorAndKeepsThePosition() {
        TransformMath.Scaled scaled = TransformMath.scale(PLAIN, LOCAL, Handle.SE, new Vec2(300, 125), true, false);

        assertVec(2, 1, scaled.scale());
        assertVec(200, 100, scaled.position());
    }

    @Test
    void shiftKeepsTheAspectRatioUsingTheLargerChange() {
        Values start = new Values(new Vec2(50, 25), new Vec2(200, 100), new Vec2(1, 2), 0, Vec2.ZERO);
        // the box covers 150..250 x 50..150; the pointer doubles the width and leaves the height
        TransformMath.Scaled scaled = TransformMath.scale(start, LOCAL, Handle.SE, new Vec2(350, 150), false, true);

        assertVec(2, 4, scaled.scale());
        assertVec(150, 50, Matrices.apply(with(start, scaled).matrix(), new Vec2(0, 0)));

        TransformMath.Scaled edge = TransformMath.scale(PLAIN, LOCAL, Handle.E, new Vec2(350, 0), false, true);
        assertVec(2, 2, edge.scale());
    }

    @Test
    void draggingPastTheFixedCornerMirrors() {
        TransformMath.Scaled scaled = TransformMath.scale(PLAIN, LOCAL, Handle.SE, new Vec2(50, 125), false, false);

        assertVec(-1, 1, scaled.scale());
    }

    @Test
    void theOppositeCornerStaysFixedForARotatedSkewedAndScaledLayer() {
        Values start = new Values(new Vec2(30, 10), new Vec2(200, 100), new Vec2(1.5, 0.5), 30, new Vec2(10, 0));
        Vec2 fixedBefore = Matrices.apply(start.matrix(), new Vec2(100, 50));
        Vec2 pointer = new Vec2(120, 40);

        TransformMath.Scaled scaled = TransformMath.scale(start, LOCAL, Handle.NW, pointer, false, false);

        double[] after = with(start, scaled).matrix();
        Vec2 fixedAfter = Matrices.apply(after, new Vec2(100, 50));
        assertVec(fixedBefore.x(), fixedBefore.y(), fixedAfter);
        assertVec(pointer.x(), pointer.y(), Matrices.apply(after, new Vec2(0, 0)));
    }

    @Test
    void aBoxWithoutHeightKeepsItsVerticalScale() {
        Box line = new Box(0, 0, 100, 0);
        Values start = new Values(new Vec2(50, 0), new Vec2(200, 100), Vec2.ONE, 0, Vec2.ZERO);

        TransformMath.Scaled scaled = TransformMath.scale(start, line, Handle.SE, new Vec2(350, 130), false, true);

        assertVec(2, 2, scaled.scale());
        assertVec(1, 1, TransformMath.scale(start, line, Handle.S, new Vec2(0, 130), false, false).scale());
    }

    @Test
    void rotationFollowsThePointerAroundThePivot() {
        Vec2 pivot = PLAIN.position();

        assertEquals(90, TransformMath.rotation(0, pivot, new Vec2(300, 100), new Vec2(200, 200)), 1e-9);
        assertEquals(100, TransformMath.rotation(10, pivot, new Vec2(300, 100), new Vec2(200, 200)), 1e-9);
        assertEquals(-80, TransformMath.rotation(10, pivot, new Vec2(300, 100), new Vec2(200, 0)), 1e-9);
    }

    @Test
    void snapAngleRoundsToTheStep() {
        assertEquals(45, TransformMath.snapAngle(50, 15), 1e-9);
        assertEquals(-15, TransformMath.snapAngle(-11, 15), 1e-9);
    }

    private static Vec2 onCircle(double degrees) {
        return new Vec2(200 + 100 * Math.cos(Math.toRadians(degrees)), 100 + 100 * Math.sin(Math.toRadians(degrees)));
    }

    @Test
    void rotationDoesNotJumpWhenThePointerCrossesTheHalfTurn() {
        assertEquals(190, TransformMath.rotation(170, PLAIN.position(), onCircle(170), onCircle(190)), 1e-9);
    }

    @Test
    void rotationAccumulatesPastHalfATurnAndPastAFullTurn() {
        double rotation = 0;
        for (int angle = 30; angle <= 420; angle += 30) {
            rotation = TransformMath.rotation(rotation, PLAIN.position(), onCircle(angle - 30), onCircle(angle));
        }
        assertEquals(420, rotation, 1e-6);

        for (int angle = 390; angle >= -210; angle -= 30) {
            rotation = TransformMath.rotation(rotation, PLAIN.position(), onCircle(angle + 30), onCircle(angle));
        }
        assertEquals(-210, rotation, 1e-6);
    }

    @Test
    void movingTheAnchorKeepsTheLayerInPlace() {
        Values start = new Values(new Vec2(30, 10), new Vec2(200, 100), new Vec2(1.5, 0.5), 30, new Vec2(10, 0));
        Vec2 pointer = new Vec2(260, 90);

        TransformMath.Anchored moved = TransformMath.moveAnchor(start, pointer);

        Values after = new Values(moved.anchor(), moved.position(), start.scale(), start.rotation(), start.skew());
        assertVec(pointer.x(), pointer.y(), moved.position());
        for (Vec2 probe : new Vec2[]{new Vec2(0, 0), new Vec2(100, 50), new Vec2(-7, 3)}) {
            Vec2 expected = Matrices.apply(start.matrix(), probe);
            assertVec(expected.x(), expected.y(), Matrices.apply(after.matrix(), probe));
        }
    }

    @Test
    void constraintsForShiftDrags() {
        assertVec(0, -30, TransformMath.constrainAxis(new Vec2(10, -30)));
        assertVec(40, 0, TransformMath.constrainAxis(new Vec2(40, 30)));
        assertVec(50, -50, TransformMath.constrainSquare(new Vec2(50, -20)));
        assertVec(-50, 50, TransformMath.constrainSquare(new Vec2(-10, 50)));

        Vec2 diagonal = TransformMath.constrainAngle(new Vec2(100, 90), 45);
        assertEquals(diagonal.x(), diagonal.y(), 1e-9);
        assertEquals(95, diagonal.x(), 1e-9);
        assertVec(100, 0, TransformMath.constrainAngle(new Vec2(100, 5), 45));
    }

    @Test
    void quadAndHandles() {
        Quad quad = Quad.of(LOCAL, PLAIN.matrix());

        assertVec(150, 75, quad.handle(Handle.NW));
        assertVec(250, 100, quad.handle(Handle.E));
        assertVec(200, 125, quad.handle(Handle.S));
        assertVec(200, 100, quad.center());
        assertVec(200, 53, quad.rotateHandle(22));
        assertEquals(new Box(150, 75, 250, 125), quad.bounds());
        assertEquals(Handle.NW, Handle.SE.opposite());
        assertEquals(Handle.W, Handle.E.opposite());

        // turned by 90 degrees the top edge points to the right
        Values turned = new Values(PLAIN.anchor(), PLAIN.position(), Vec2.ONE, 90, Vec2.ZERO);
        assertVec(247, 100, Quad.of(LOCAL, turned.matrix()).rotateHandle(22));
        assertEquals(true, quad.contains(new Vec2(151, 76)));
        assertEquals(false, quad.contains(new Vec2(149, 76)));

        // a box scaled to nothing covers no point, not even the one it sits on
        Values collapsed = new Values(PLAIN.anchor(), PLAIN.position(), Vec2.ZERO, 0, Vec2.ZERO);
        Quad point = Quad.of(LOCAL, collapsed.matrix());
        assertEquals(false, point.contains(new Vec2(200, 100)));
        assertEquals(false, point.contains(new Vec2(10, 10)));
        Values flat = new Values(PLAIN.anchor(), PLAIN.position(), new Vec2(1, 0), 0, Vec2.ZERO);
        assertEquals(false, Quad.of(LOCAL, flat.matrix()).contains(new Vec2(200, 100)));
    }
}
