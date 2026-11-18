package com.dlsc.animatorfx.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathDataTest {

    @Test
    void listsAreCopied() {
        List<PathData.Vertex> vertices = new ArrayList<>(List.of(Paths.corner(0, 0)));
        PathData.Contour contour = new PathData.Contour(vertices, false);
        List<PathData.Contour> contours = new ArrayList<>(List.of(contour));
        PathData path = new PathData(contours);
        vertices.clear();
        contours.clear();
        assertEquals(1, path.contours().size());
        assertEquals(1, path.contours().get(0).vertices().size());
        assertThrows(UnsupportedOperationException.class, () -> path.contours().clear());
        assertTrue(PathData.EMPTY.contours().isEmpty());

        List<Paint.Stop> stops = new ArrayList<>(List.of(new Paint.Stop(0, Rgba.BLACK)));
        Paint.LinearGradient gradient = new Paint.LinearGradient(Vec2.ZERO, Vec2.ONE, stops);
        stops.clear();
        assertEquals(1, gradient.stops().size());
    }
}
