package com.dlsc.animatorfx.editor.doc;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.util.LinkedHashSet;

/**
 * The per-document state of the canvas and the timeline that is kept in the session. The views
 * bind to it; the session saves and restores it.
 */
public final class ViewState {

    private final DoubleProperty canvasZoom = new SimpleDoubleProperty(this, "canvasZoom", 1);
    private final DoubleProperty canvasPanX = new SimpleDoubleProperty(this, "canvasPanX");
    private final DoubleProperty canvasPanY = new SimpleDoubleProperty(this, "canvasPanY");
    private final DoubleProperty timelineZoom = new SimpleDoubleProperty(this, "timelineZoom", 1);
    private final DoubleProperty timelineScroll = new SimpleDoubleProperty(this, "timelineScroll");
    private final BooleanProperty showAllProperties = new SimpleBooleanProperty(this, "showAllProperties");
    private final ObservableSet<String> expandedLayerIds = FXCollections.observableSet(new LinkedHashSet<>());

    /**
     * The canvas zoom factor, where 1.0 shows the document at 100 %.
     *
     * @return the canvas zoom property
     */
    public final DoubleProperty canvasZoomProperty() {
        return canvasZoom;
    }

    public final double getCanvasZoom() {
        return canvasZoom.get();
    }

    public final void setCanvasZoom(double value) {
        canvasZoom.set(value);
    }

    /**
     * The horizontal canvas pan in view pixels.
     *
     * @return the canvas pan x property
     */
    public final DoubleProperty canvasPanXProperty() {
        return canvasPanX;
    }

    public final double getCanvasPanX() {
        return canvasPanX.get();
    }

    public final void setCanvasPanX(double value) {
        canvasPanX.set(value);
    }

    /**
     * The vertical canvas pan in view pixels.
     *
     * @return the canvas pan y property
     */
    public final DoubleProperty canvasPanYProperty() {
        return canvasPanY;
    }

    public final double getCanvasPanY() {
        return canvasPanY.get();
    }

    public final void setCanvasPanY(double value) {
        canvasPanY.set(value);
    }

    /**
     * The timeline zoom factor, where 1.0 is the default time scale.
     *
     * @return the timeline zoom property
     */
    public final DoubleProperty timelineZoomProperty() {
        return timelineZoom;
    }

    public final double getTimelineZoom() {
        return timelineZoom.get();
    }

    public final void setTimelineZoom(double value) {
        timelineZoom.set(value);
    }

    /**
     * The horizontal scroll position of the timeline in seconds.
     *
     * @return the timeline scroll property
     */
    public final DoubleProperty timelineScrollProperty() {
        return timelineScroll;
    }

    public final double getTimelineScroll() {
        return timelineScroll.get();
    }

    public final void setTimelineScroll(double value) {
        timelineScroll.set(value);
    }

    /**
     * Whether expanded layers show every property in the timeline or only the animated ones.
     *
     * @return the show-all-properties property
     */
    public final BooleanProperty showAllPropertiesProperty() {
        return showAllProperties;
    }

    public final boolean isShowAllProperties() {
        return showAllProperties.get();
    }

    public final void setShowAllProperties(boolean value) {
        showAllProperties.set(value);
    }

    /**
     * The ids of the layers that are expanded in the timeline.
     */
    public ObservableSet<String> getExpandedLayerIds() {
        return expandedLayerIds;
    }
}
