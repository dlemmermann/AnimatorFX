package com.dlsc.animatorfx.player.render;

import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathConverterTest {

    private static PathData.Vertex corner(double x, double y) {
        return new PathData.Vertex(new Vec2(x, y), Vec2.ZERO, Vec2.ZERO);
    }

    private static PathData triangle(double size, boolean closed) {
        return new PathData(List.of(new PathData.Contour(
                List.of(corner(0, 0), corner(size, 0), corner(size, size)), closed)));
    }

    @Test
    void closedCornerContourBecomesLinesAndClosePath() {
        List<PathElement> elements = new ArrayList<>();

        PathConverter.write(triangle(10, true), elements);

        assertEquals(4, elements.size());
        MoveTo move = assertInstanceOf(MoveTo.class, elements.get(0));
        assertEquals(0, move.getX());
        LineTo line = assertInstanceOf(LineTo.class, elements.get(2));
        assertEquals(10, line.getX());
        assertEquals(10, line.getY());
        assertInstanceOf(ClosePath.class, elements.get(3));
        assertTrue(move.isAbsolute());
    }

    @Test
    void openContourHasNoClosePath() {
        List<PathElement> elements = new ArrayList<>();

        PathConverter.write(triangle(10, false), elements);

        assertEquals(3, elements.size());
        assertInstanceOf(LineTo.class, elements.get(2));
    }

    @Test
    void handlesBecomeAbsoluteControlPoints() {
        PathData curve = new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(0, 0), Vec2.ZERO, new Vec2(5, 0)),
                new PathData.Vertex(new Vec2(20, 10), new Vec2(0, -4), Vec2.ZERO)), true)));
        List<PathElement> elements = new ArrayList<>();

        PathConverter.write(curve, elements);

        // the straight closing segment is left to the ClosePath
        assertEquals(3, elements.size());
        CubicCurveTo cubic = assertInstanceOf(CubicCurveTo.class, elements.get(1));
        assertEquals(5, cubic.getControlX1());
        assertEquals(0, cubic.getControlY1());
        assertEquals(20, cubic.getControlX2());
        assertEquals(6, cubic.getControlY2());
        assertEquals(20, cubic.getX());
        assertEquals(10, cubic.getY());
        assertInstanceOf(ClosePath.class, elements.get(2));
    }

    @Test
    void severalContoursEachStartWithAMove() {
        PathData two = new PathData(List.of(triangle(10, false).contours().get(0),
                triangle(20, true).contours().get(0)));
        List<PathElement> elements = new ArrayList<>();

        PathConverter.write(two, elements);

        assertEquals(7, elements.size());
        assertInstanceOf(MoveTo.class, elements.get(3));
    }

    @Test
    void rewritingReusesElementsOfTheSameKind() {
        List<PathElement> elements = new ArrayList<>();
        PathConverter.write(triangle(10, true), elements);
        List<PathElement> before = List.copyOf(elements);

        PathConverter.write(triangle(30, true), elements);

        assertEquals(4, elements.size());
        for (int i = 0; i < 4; i++) {
            assertSame(before.get(i), elements.get(i));
        }
        assertEquals(30, ((LineTo) elements.get(2)).getY());
    }

    @Test
    void rewritingAShorterPathDropsTheRest() {
        List<PathElement> elements = new ArrayList<>();
        PathConverter.write(triangle(10, true), elements);

        PathConverter.write(PathData.EMPTY, elements);

        assertTrue(elements.isEmpty());
    }
}
