package com.dlsc.animatorfx.editor.inspector.fields;

import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * The stops of a gradient on a bar that previews it. A click on the bar adds a stop with the
 * colour the gradient has there; a stop is dragged to move it and dragged off the bar (or
 * removed with Delete while the bar has the focus) to delete it. A gradient keeps at least two
 * stops. One stop is selected; its colour is edited next to the bar.
 */
public final class GradientStopBar extends Region {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final double BAR_HEIGHT = 14;
    private static final double HANDLE_WIDTH = 10;
    private static final double HANDLE_HEIGHT = 10;
    private static final double REMOVE_DISTANCE = 26;

    private final Region base = new Region();
    private final Region gradient = new Region();
    private final List<Region> handles = new ArrayList<>();
    private List<Paint.Stop> stops = List.of();
    private int selected;
    private int dragged = -1;
    private boolean removing;
    private boolean moved;
    private List<Paint.Stop> beforeDrag;
    private BiConsumer<List<Paint.Stop>, Boolean> onChange = (list, adjusting) -> {
    };
    private Runnable onAdjustEnd = () -> {
    };
    private Runnable onSelect = () -> {
    };

    public GradientStopBar() {
        getStyleClass().add("gradient-bar");
        setFocusTraversable(true);
        base.getStyleClass().add("alpha-bar");
        gradient.getStyleClass().add("gradient-preview");
        getChildren().addAll(base, gradient);
        setMinHeight(BAR_HEIGHT + HANDLE_HEIGHT);
        setPrefHeight(BAR_HEIGHT + HANDLE_HEIGHT);
        setMaxHeight(BAR_HEIGHT + HANDLE_HEIGHT);
        setOnMousePressed(this::pressed);
        setOnMouseDragged(this::draggedTo);
        setOnMouseReleased(e -> released());
        addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                removeSelected();
                // never let the key reach the canvas, where it would delete the layer
                e.consume();
            }
        });
    }

    public List<Paint.Stop> getStops() {
        return stops;
    }

    /** Shows the stops without reporting a change. */
    public void setStops(List<Paint.Stop> newStops) {
        if (dragged >= 0 && removing) {
            return;
        }
        stops = List.copyOf(newStops);
        selected = Math.clamp(selected, 0, Math.max(0, stops.size() - 1));
        requestLayout();
    }

    public int getSelectedIndex() {
        return selected;
    }

    public void select(int index) {
        selected = Math.clamp(index, 0, Math.max(0, stops.size() - 1));
        requestLayout();
        onSelect.run();
    }

    /** The handler gets the new stops, ordered by offset, and whether a drag is in progress. */
    public void setOnChange(BiConsumer<List<Paint.Stop>, Boolean> handler) {
        onChange = handler;
    }

    public void setOnAdjustEnd(Runnable handler) {
        onAdjustEnd = handler;
    }

    public void setOnSelect(Runnable handler) {
        onSelect = handler;
    }

    /** The x coordinate of an offset. */
    public double offsetToX(double offset) {
        return HANDLE_WIDTH / 2 + offset * (getWidth() - HANDLE_WIDTH);
    }

    private double xToOffset(double x) {
        return Math.clamp((x - HANDLE_WIDTH / 2) / Math.max(1, getWidth() - HANDLE_WIDTH), 0, 1);
    }

    /** The colour of the gradient at an offset. */
    public static Rgba colorAt(List<Paint.Stop> stops, double offset) {
        if (stops.isEmpty()) {
            return Rgba.WHITE;
        }
        Paint.Stop before = stops.get(0);
        for (Paint.Stop stop : stops) {
            if (stop.offset() >= offset) {
                double span = stop.offset() - before.offset();
                return span <= 0 ? stop.color() : before.color().lerp(stop.color(), (offset - before.offset()) / span);
            }
            before = stop;
        }
        return before.color();
    }

    // ------------------------------------------------------------------ pointer

    private void pressed(MouseEvent event) {
        requestFocus();
        dragged = -1;
        moved = false;
        removing = false;
        beforeDrag = stops;
        for (int i = stops.size() - 1; i >= 0; i--) {
            if (Math.abs(offsetToX(stops.get(i).offset()) - event.getX()) <= HANDLE_WIDTH / 2 + 1) {
                dragged = i;
                break;
            }
        }
        if (dragged < 0) {
            double offset = Math.round(xToOffset(event.getX()) * 100) / 100.0;
            Paint.Stop added = new Paint.Stop(offset, colorAt(stops, offset));
            List<Paint.Stop> list = new ArrayList<>(stops);
            list.add(added);
            apply(list, added, false);
            dragged = selected;
            beforeDrag = stops;
        } else {
            select(dragged);
        }
        event.consume();
    }

    private void draggedTo(MouseEvent event) {
        if (dragged < 0 || beforeDrag == null) {
            return;
        }
        moved = true;
        boolean off = beforeDrag.size() > 2 && (event.getY() < -REMOVE_DISTANCE || event.getY() > getHeight() + REMOVE_DISTANCE);
        List<Paint.Stop> list = new ArrayList<>(beforeDrag);
        Paint.Stop original = list.remove(dragged);
        removing = off;
        if (off) {
            stops = List.copyOf(list);
            selected = Math.clamp(selected, 0, stops.size() - 1);
            requestLayout();
            onChange.accept(stops, true);
        } else {
            Paint.Stop movedStop = new Paint.Stop(Math.round(xToOffset(event.getX()) * 100) / 100.0, original.color());
            list.add(movedStop);
            apply(list, movedStop, true);
        }
        event.consume();
    }

    private void released() {
        if (dragged >= 0 && moved) {
            onAdjustEnd.run();
        }
        dragged = -1;
        removing = false;
        beforeDrag = null;
        onSelect.run();
    }

    private void removeSelected() {
        if (stops.size() > 2) {
            List<Paint.Stop> list = new ArrayList<>(stops);
            list.remove(selected);
            stops = List.copyOf(list);
            selected = Math.clamp(selected, 0, stops.size() - 1);
            requestLayout();
            onChange.accept(stops, false);
            onSelect.run();
        }
    }

    /** Orders the stops, keeps the given one selected and reports the change. */
    private void apply(List<Paint.Stop> list, Paint.Stop keep, boolean adjusting) {
        list.sort(Comparator.comparingDouble(Paint.Stop::offset));
        stops = List.copyOf(list);
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i) == keep) {
                selected = i;
            }
        }
        requestLayout();
        onChange.accept(stops, adjusting);
        onSelect.run();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        base.resizeRelocate(HANDLE_WIDTH / 2, 0, width - HANDLE_WIDTH, BAR_HEIGHT);
        gradient.resizeRelocate(HANDLE_WIDTH / 2, 0, width - HANDLE_WIDTH, BAR_HEIGHT);
        List<Stop> fxStops = new ArrayList<>();
        for (Paint.Stop stop : stops) {
            fxStops.add(new Stop(Math.clamp(stop.offset(), 0, 1), color(stop.color())));
        }
        gradient.setBackground(fxStops.isEmpty() ? Background.EMPTY : new Background(new BackgroundFill(
                new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, fxStops), new CornerRadii(3), Insets.EMPTY)));
        while (handles.size() < stops.size()) {
            Region handle = new Region();
            handle.getStyleClass().add("stop-handle");
            handle.setMouseTransparent(true);
            handle.setManaged(false);
            handles.add(handle);
            getChildren().add(handle);
        }
        for (int i = 0; i < handles.size(); i++) {
            Region handle = handles.get(i);
            handle.setVisible(i < stops.size());
            if (i < stops.size()) {
                handle.pseudoClassStateChanged(SELECTED, i == selected);
                handle.setBackground(new Background(new BackgroundFill(color(stops.get(i).color()), new CornerRadii(2),
                        new Insets(1.5))));
                handle.resizeRelocate(Math.round(offsetToX(stops.get(i).offset()) - HANDLE_WIDTH / 2), BAR_HEIGHT - 2,
                        HANDLE_WIDTH, HANDLE_HEIGHT + 2);
            }
        }
    }

    private static Color color(Rgba c) {
        return Color.color(Math.clamp(c.r(), 0.0, 1.0), Math.clamp(c.g(), 0.0, 1.0), Math.clamp(c.b(), 0.0, 1.0),
                Math.clamp(c.a(), 0.0, 1.0));
    }
}
