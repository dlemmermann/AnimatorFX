package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.session.SessionState.WindowState;
import javafx.geometry.Rectangle2D;

import java.util.List;

/**
 * Decides where the window opens: at its saved bounds when they are still reachable on a screen,
 * otherwise centred at the default size on the primary screen.
 */
public final class WindowPlacement {

    public static final double DEFAULT_WIDTH = 1440;
    public static final double DEFAULT_HEIGHT = 900;

    private static final double MIN_WIDTH = 320;
    private static final double MIN_HEIGHT = 200;
    /** The strip at the top of the window that has to be on a screen so the window can be dragged. */
    private static final double GRAB_HEIGHT = 40;
    private static final double MIN_VISIBLE_WIDTH = 100;
    private static final double MIN_VISIBLE_HEIGHT = 20;

    public record Placement(double x, double y, double width, double height, boolean maximized) {
    }

    private WindowPlacement() {
    }

    /**
     * @param saved   the saved window state, or {@code null}
     * @param screens the visual bounds of the current screens, primary screen first
     */
    public static Placement resolve(WindowState saved, List<Rectangle2D> screens) {
        if (saved != null && isVisible(saved, screens)) {
            return new Placement(saved.x(), saved.y(), saved.width(), saved.height(), saved.maximized());
        }
        Rectangle2D primary = screens.get(0);
        double width = Math.min(DEFAULT_WIDTH, primary.getWidth());
        double height = Math.min(DEFAULT_HEIGHT, primary.getHeight());
        return new Placement(primary.getMinX() + (primary.getWidth() - width) / 2,
                primary.getMinY() + (primary.getHeight() - height) / 2, width, height, false);
    }

    /**
     * Whether the window has a usable size and enough of its top edge lies on one of the screens.
     */
    public static boolean isVisible(WindowState saved, List<Rectangle2D> screens) {
        if (!(saved.width() >= MIN_WIDTH) || !(saved.height() >= MIN_HEIGHT)
                || !Double.isFinite(saved.x()) || !Double.isFinite(saved.y())) {
            return false;
        }
        for (Rectangle2D screen : screens) {
            double visibleWidth = Math.min(saved.x() + saved.width(), screen.getMaxX())
                    - Math.max(saved.x(), screen.getMinX());
            double visibleHeight = Math.min(saved.y() + GRAB_HEIGHT, screen.getMaxY())
                    - Math.max(saved.y(), screen.getMinY());
            if (visibleWidth >= MIN_VISIBLE_WIDTH && visibleHeight >= MIN_VISIBLE_HEIGHT) {
                return true;
            }
        }
        return false;
    }
}
