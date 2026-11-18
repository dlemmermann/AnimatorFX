package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.canvas.Snapper.Guide;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapperTest {

    private static final Box ARTBOARD = new Box(0, 0, 800, 600);

    @Test
    void anEdgeWithinTheToleranceSnapsToTheArtboardEdge() {
        Snapper.Result result = Snapper.snap(new Box(3, 210, 103, 290), List.of(ARTBOARD), 6);

        assertEquals(-3, result.dx(), 1e-9);
        assertEquals(0, result.dy(), 1e-9);
        assertEquals(1, result.guides().size());
        Guide guide = result.guides().get(0);
        assertTrue(guide.vertical());
        assertEquals(0, guide.position(), 1e-9);
        assertEquals(0, guide.from(), 1e-9);
        assertEquals(600, guide.to(), 1e-9);
    }

    @Test
    void theCentreSnapsToTheArtboardCentreOnBothAxes() {
        Snapper.Result result = Snapper.snap(new Box(352, 246, 452, 346), List.of(ARTBOARD), 6);

        assertEquals(-2, result.dx(), 1e-9);
        assertEquals(4, result.dy(), 1e-9);
        assertEquals(2, result.guides().size());
    }

    @Test
    void nothingSnapsOutsideTheTolerance() {
        Snapper.Result result = Snapper.snap(new Box(7, 210, 107, 290), List.of(ARTBOARD), 6);

        assertEquals(0, result.dx(), 1e-9);
        assertEquals(0, result.dy(), 1e-9);
        assertTrue(result.guides().isEmpty());
        assertTrue(Snapper.snap(new Box(3, 210, 103, 290), List.of(), 6).guides().isEmpty());
    }

    @Test
    void theNearestLineWins() {
        Box other = new Box(105, 0, 300, 100);
        // the right edge is 2 away from the other layer's left edge, the left edge 5 away from the artboard
        Snapper.Result result = Snapper.snap(new Box(5, 210, 103, 290), List.of(ARTBOARD, other), 6);

        assertEquals(2, result.dx(), 1e-9);
        assertEquals(105, result.guides().get(0).position(), 1e-9);
    }

    @Test
    void edgesSnapToOtherLayersAndTheGuideSpansBothBoxes() {
        Box other = new Box(300, 100, 400, 150);
        Snapper.Result result = Snapper.snap(new Box(500, 148, 560, 200), List.of(other), 6);

        assertEquals(2, result.dy(), 1e-9);
        Guide guide = result.guides().get(0);
        assertEquals(false, guide.vertical());
        assertEquals(150, guide.position(), 1e-9);
        assertEquals(300, guide.from(), 1e-9);
        assertEquals(560, guide.to(), 1e-9);
    }

    @Test
    void everyLineThatCoincidesAfterTheSnapGetsAGuide() {
        // same width as the artboard: left edge, centre and right edge all line up
        Snapper.Result result = Snapper.snap(new Box(2, 100, 802, 200), List.of(ARTBOARD), 6);

        assertEquals(-2, result.dx(), 1e-9);
        assertEquals(3, result.guides().stream().filter(Guide::vertical).count());
    }

    @Test
    void aSinglePointSnapsForHandleDrags() {
        Snapper.Result result = Snapper.snapPoint(797, 310, true, true, List.of(ARTBOARD), 6);
        assertEquals(3, result.dx(), 1e-9);
        assertEquals(0, result.dy(), 1e-9);

        Snapper.Result centre = Snapper.snapPoint(797, 302, false, true, List.of(ARTBOARD), 6);
        assertEquals(0, centre.dx(), 1e-9);
        assertEquals(-2, centre.dy(), 1e-9);
    }
}
