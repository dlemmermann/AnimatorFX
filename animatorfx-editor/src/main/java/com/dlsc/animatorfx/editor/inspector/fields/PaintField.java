package com.dlsc.animatorfx.editor.inspector.fields;

import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.Vec2;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Edits a paint: a type selector (None, Solid, Linear, Radial) and, depending on the type, a
 * {@link ColorField} or a gradient editor with a {@link GradientStopBar}, the colour and offset
 * of the selected stop, and the start and end points (linear) or the centre and radius (radial)
 * in layer coordinates.
 *
 * <p>The change handler gets the new paint and whether the change is part of a drag that should
 * become one undo step; the adjust-end handler runs when the drag is over. {@link #setValue}
 * only updates the display.
 */
public final class PaintField extends VBox {

    public static final String NONE = "None";
    public static final String SOLID = "Solid";
    public static final String LINEAR = "Linear";
    public static final String RADIAL = "Radial";

    private static final Rgba DEFAULT_COLOR = Rgba.parse("#4C9AFF");

    private final ComboBox<String> type = new ComboBox<>();
    private final ColorField solid = new ColorField();
    private final GradientStopBar stopBar = new GradientStopBar();
    private final ColorField stopColor = new ColorField();
    private final NumberField stopOffset = new NumberField(PropertyUnits.PERCENT);
    private final Vec2Field start = new Vec2Field(PropertyUnits.NONE, "Start X", "Start Y", false);
    private final Vec2Field end = new Vec2Field(PropertyUnits.NONE, "End X", "End Y", false);
    private final Vec2Field center = new Vec2Field(PropertyUnits.NONE, "Centre X", "Centre Y", false);
    private final NumberField radius = new NumberField(PropertyUnits.NONE);
    private final VBox gradientBox = new VBox();
    private final HBox startRow = row("Start", start);
    private final HBox endRow = row("End", end);
    private final HBox centerRow = row("Centre", center);
    private final HBox radiusRow = row("Radius", radius);

    private Paint value;
    private Rgba lastColor = DEFAULT_COLOR;
    private Vec2 extent = new Vec2(100, 100);
    private boolean updating;
    private BiConsumer<Paint, Boolean> onChange = (paint, adjusting) -> {
    };
    private Runnable onAdjustEnd = () -> {
    };

    public PaintField() {
        getStyleClass().add("paint-field");
        type.getItems().addAll(NONE, SOLID, LINEAR, RADIAL);
        type.getStyleClass().add("compact-combo");
        type.setPromptText("Mixed");
        type.setMinWidth(72);
        type.setPrefWidth(72);
        type.valueProperty().addListener((obs, oldType, newType) -> {
            if (!updating && newType != null) {
                fire(converted(newType), false);
            }
        });
        HBox.setHgrow(solid, Priority.ALWAYS);
        HBox head = new HBox(type, solid);
        head.getStyleClass().add("paint-head");

        stopOffset.setRange(0, 1);
        stopOffset.setDecimals(0);
        stopOffset.setMaxWidth(58);
        stopOffset.setMinWidth(58);
        HBox.setHgrow(stopColor, Priority.ALWAYS);
        HBox stopRow = row("Stop", stopColor);
        stopRow.getChildren().add(stopOffset);
        radius.setRange(0, Double.MAX_VALUE);
        gradientBox.getStyleClass().add("gradient-editor");
        gradientBox.getChildren().addAll(stopBar, stopRow, startRow, endRow, centerRow, radiusRow);
        getChildren().addAll(head, gradientBox);

        solid.setOnChange((color, adjusting) -> fire(new Paint.Solid(color), adjusting));
        solid.setOnAdjustEnd(() -> onAdjustEnd.run());
        stopBar.setOnChange((stops, adjusting) -> fire(withStops(stops), adjusting));
        stopBar.setOnAdjustEnd(() -> onAdjustEnd.run());
        stopBar.setOnSelect(this::showSelectedStop);
        stopColor.setOnChange((color, adjusting) -> editStop(color, null, adjusting));
        stopColor.setOnAdjustEnd(() -> onAdjustEnd.run());
        stopOffset.setOnChange((offset, adjusting) -> editStop(null, offset, adjusting));
        stopOffset.setOnAdjustEnd(() -> onAdjustEnd.run());
        start.setOnChange((fn, adjusting) -> {
            if (value instanceof Paint.LinearGradient g) {
                fire(new Paint.LinearGradient(fn.apply(g.start()), g.end(), g.stops()), adjusting);
            }
        });
        end.setOnChange((fn, adjusting) -> {
            if (value instanceof Paint.LinearGradient g) {
                fire(new Paint.LinearGradient(g.start(), fn.apply(g.end()), g.stops()), adjusting);
            }
        });
        center.setOnChange((fn, adjusting) -> {
            if (value instanceof Paint.RadialGradient g) {
                fire(new Paint.RadialGradient(fn.apply(g.center()), g.radius(), g.stops()), adjusting);
            }
        });
        radius.setOnChange((r, adjusting) -> {
            if (value instanceof Paint.RadialGradient g) {
                fire(new Paint.RadialGradient(g.center(), r, g.stops()), adjusting);
            }
        });
        for (Vec2Field field : List.of(start, end, center)) {
            field.setOnAdjustEnd(() -> onAdjustEnd.run());
        }
        radius.setOnAdjustEnd(() -> onAdjustEnd.run());
        show();
    }

    private static HBox row(String name, Node editor) {
        Label label = new Label(name);
        label.getStyleClass().add("sub-label");
        HBox.setHgrow(editor, Priority.ALWAYS);
        HBox row = new HBox(label, editor);
        row.getStyleClass().add("paint-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    public Paint getValue() {
        return value;
    }

    /** Shows a paint, or the "Mixed" placeholder for {@code null}. */
    public void setValue(Paint paint) {
        value = paint;
        show();
    }

    /**
     * The size of the layer content: a paint that is turned into a gradient runs across it.
     */
    public void setExtent(Vec2 size) {
        extent = size;
    }

    public void setOnChange(BiConsumer<Paint, Boolean> handler) {
        onChange = handler;
    }

    public void setOnAdjustEnd(Runnable handler) {
        onAdjustEnd = handler;
    }

    public ComboBox<String> getTypeBox() {
        return type;
    }

    public ColorField getSolidField() {
        return solid;
    }

    public GradientStopBar getStopBar() {
        return stopBar;
    }

    public ColorField getStopColorField() {
        return stopColor;
    }

    public NumberField getStopOffsetField() {
        return stopOffset;
    }

    public Vec2Field getStartField() {
        return start;
    }

    public Vec2Field getEndField() {
        return end;
    }

    public Vec2Field getCenterField() {
        return center;
    }

    public NumberField getRadiusField() {
        return radius;
    }

    private void fire(Paint paint, boolean adjusting) {
        value = paint;
        show();
        onChange.accept(paint, adjusting);
    }

    private static String typeOf(Paint paint) {
        return switch (paint) {
            case null -> null;
            case Paint.None none -> NONE;
            case Paint.Solid s -> SOLID;
            case Paint.LinearGradient g -> LINEAR;
            case Paint.RadialGradient g -> RADIAL;
        };
    }

    private static List<Paint.Stop> stopsOf(Paint paint) {
        return switch (paint) {
            case Paint.LinearGradient g -> g.stops();
            case Paint.RadialGradient g -> g.stops();
            case null, default -> List.of();
        };
    }

    /** The current paint turned into another type, keeping colours and stops where there are any. */
    private Paint converted(String newType) {
        List<Paint.Stop> stops = stopsOf(value);
        Rgba color = value instanceof Paint.Solid s ? s.color() : stops.isEmpty() ? lastColor : stops.get(0).color();
        if (stops.size() < 2) {
            stops = List.of(new Paint.Stop(0, color), new Paint.Stop(1, Rgba.WHITE.equals(color) ? Rgba.BLACK : Rgba.WHITE));
        }
        return switch (newType) {
            case SOLID -> new Paint.Solid(color);
            case LINEAR -> new Paint.LinearGradient(new Vec2(0, extent.y() / 2), new Vec2(extent.x(), extent.y() / 2), stops);
            case RADIAL -> new Paint.RadialGradient(new Vec2(extent.x() / 2, extent.y() / 2),
                    Math.max(extent.x(), extent.y()) / 2, stops);
            default -> new Paint.None();
        };
    }

    private Paint withStops(List<Paint.Stop> stops) {
        return switch (value) {
            case Paint.LinearGradient g -> new Paint.LinearGradient(g.start(), g.end(), stops);
            case Paint.RadialGradient g -> new Paint.RadialGradient(g.center(), g.radius(), stops);
            case null, default -> value;
        };
    }

    private void editStop(Rgba color, Double offset, boolean adjusting) {
        List<Paint.Stop> stops = new ArrayList<>(stopsOf(value));
        int index = stopBar.getSelectedIndex();
        if (index >= stops.size()) {
            return;
        }
        Paint.Stop stop = stops.get(index);
        Paint.Stop edited = new Paint.Stop(offset == null ? stop.offset() : offset, color == null ? stop.color() : color);
        stops.set(index, edited);
        stops.sort(Comparator.comparingDouble(Paint.Stop::offset));
        fire(withStops(stops), adjusting);
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i) == edited && i != index) {
                stopBar.select(i);
            }
        }
    }

    private void showSelectedStop() {
        List<Paint.Stop> stops = stopsOf(value);
        int index = stopBar.getSelectedIndex();
        if (index < stops.size()) {
            stopColor.setValue(stops.get(index).color());
            stopOffset.setValue(stops.get(index).offset());
        }
    }

    private void show() {
        updating = true;
        try {
            type.setValue(typeOf(value));
        } finally {
            updating = false;
        }
        boolean isSolid = value instanceof Paint.Solid;
        boolean linear = value instanceof Paint.LinearGradient;
        boolean radial = value instanceof Paint.RadialGradient;
        visible(solid, isSolid);
        visible(gradientBox, linear || radial);
        visible(startRow, linear);
        visible(endRow, linear);
        visible(centerRow, radial);
        visible(radiusRow, radial);
        if (value instanceof Paint.Solid s) {
            lastColor = s.color();
            solid.setValue(s.color());
        }
        if (linear || radial) {
            stopBar.setStops(stopsOf(value));
            showSelectedStop();
        }
        if (value instanceof Paint.LinearGradient g) {
            start.setValue(g.start());
            end.setValue(g.end());
        } else if (value instanceof Paint.RadialGradient g) {
            center.setValue(g.center());
            radius.setValue(g.radius());
        }
    }

    private static void visible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
