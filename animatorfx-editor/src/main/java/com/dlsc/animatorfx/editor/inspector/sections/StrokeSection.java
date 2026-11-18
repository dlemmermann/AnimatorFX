package com.dlsc.animatorfx.editor.inspector.sections;

import com.dlsc.animatorfx.editor.canvas.tools.NewLayers;
import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.editor.inspector.FieldRow;
import com.dlsc.animatorfx.editor.inspector.InspectorContext;
import com.dlsc.animatorfx.editor.inspector.PropertyRows;
import com.dlsc.animatorfx.editor.inspector.SectionBase;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.editor.inspector.fields.TextValueField;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.TextLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * The stroke of the selected shapes and texts: paint, width, cap, join, miter limit, dash array
 * (comma-separated lengths) and dash offset. Layers without a stroke get one with the Add
 * button; the Remove button takes it away again.
 */
public final class StrokeSection extends SectionBase {

    public static final String ADD_ID = "inspector-stroke-add";
    public static final String REMOVE_ID = "inspector-stroke-remove";
    public static final String DASH_ID = "inspector-stroke-dash";
    public static final String CAP_ID = "inspector-stroke-cap";
    public static final String JOIN_ID = "inspector-stroke-join";
    public static final String MITER_ID = "inspector-stroke-miter";

    private final PropertyRows.PaintRow paint;
    private final PropertyRows.NumberRow width;
    private final ComboBox<Stroke.Cap> cap = new ComboBox<>();
    private final ComboBox<Stroke.Join> join = new ComboBox<>();
    private final NumberField miter = new NumberField(PropertyUnits.NONE);
    private final TextValueField dash = new TextValueField();
    private final Button add = new Button("Add Stroke");
    private final Button remove = new Button(null, new FontIcon(Material2AL.DELETE_OUTLINE));
    private final List<FieldRow> plainRows = new ArrayList<>();
    private boolean updating;

    public StrokeSection() {
        super("Stroke");
        paint = paint("stroke.paint", "Paint");
        width = number("stroke.width", "Width");
        width.field().setRange(0, Double.MAX_VALUE);

        cap.setId(CAP_ID);
        cap.getItems().addAll(Stroke.Cap.values());
        join.setId(JOIN_ID);
        join.getItems().addAll(Stroke.Join.values());
        cap.setConverter(names());
        join.setConverter(names());
        for (ComboBox<?> box : List.of(cap, join)) {
            box.getStyleClass().add("compact-combo");
            box.setPromptText("Mixed");
            box.setMaxWidth(Double.MAX_VALUE);
            box.setMinWidth(0);
            HBox.setHgrow(box, Priority.ALWAYS);
        }
        cap.setTooltip(new Tooltip("Line cap"));
        join.setTooltip(new Tooltip("Line join"));
        cap.valueProperty().addListener((obs, old, value) -> {
            if (!updating && value != null) {
                editStroke("Change Line Cap", s -> s.withCap(value), null);
            }
        });
        join.valueProperty().addListener((obs, old, value) -> {
            if (!updating && value != null) {
                editStroke("Change Line Join", s -> s.withJoin(value), null);
            }
        });
        HBox ends = new HBox(cap, join);
        ends.getStyleClass().add("vec2-field");
        plainRows.add(row("Cap / Join", ends));

        miter.setId(MITER_ID);
        miter.setRange(1, 1000);
        miter.setMaxWidth(Double.MAX_VALUE);
        miter.setOnChange((limit, adjusting) -> editStroke("Change Miter Limit", s -> s.withMiterLimit(limit),
                adjusting ? miter : null));
        miter.setOnAdjustEnd(() -> context().endGesture());
        FieldRow miterRow = row("Miter", miter);
        miter.installScrubbing(miterRow.getLabel());
        plainRows.add(miterRow);

        dash.setId(DASH_ID);
        dash.getStyleClass().add("mono");
        dash.setPromptText("solid");
        dash.setValidator(text -> parseDashes(text).isPresent() ? Optional.empty()
                : Optional.of("Lengths separated by commas, such as 8, 4"));
        dash.setOnCommit(text -> editStroke("Change Dashes", s -> s.withDashArray(parseDashes(text).orElseThrow()), null));
        plainRows.add(row("Dashes", dash));
        number("stroke.dashOffset", "Offset");

        add.setId(ADD_ID);
        add.getStyleClass().add("section-button");
        add.setOnAction(e -> setStroke("Add Stroke", true));
        add(add);
        remove.setId(REMOVE_ID);
        remove.getStyleClass().setAll("icon-button", "section-toggle");
        remove.setFocusTraversable(false);
        remove.setTooltip(new Tooltip("Remove the stroke"));
        remove.setOnAction(e -> setStroke("Remove Stroke", false));
        actions().getChildren().add(remove);
    }

    /** Shows enum constants as words: "Round" for {@code ROUND}. */
    private static <E extends Enum<E>> StringConverter<E> names() {
        return new StringConverter<>() {
            @Override
            public String toString(E value) {
                return value == null ? "" : value.name().charAt(0) + value.name().substring(1).toLowerCase(Locale.ROOT);
            }

            @Override
            public E fromString(String text) {
                return null;
            }
        };
    }

    public PropertyRows.PaintRow paint() {
        return paint;
    }

    public PropertyRows.NumberRow width() {
        return width;
    }

    /** Comma- or space-separated non-negative lengths; an empty text is a solid line. */
    public static Optional<List<Double>> parseDashes(String text) {
        List<Double> lengths = new ArrayList<>();
        String body = text == null ? "" : text.strip();
        if (body.isEmpty() || body.equalsIgnoreCase("solid")) {
            return Optional.of(lengths);
        }
        for (String part : body.split("[\\s,;]+")) {
            try {
                double length = Double.parseDouble(part);
                if (!Double.isFinite(length) || length < 0) {
                    return Optional.empty();
                }
                lengths.add(length);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return lengths.stream().allMatch(length -> length == 0) ? Optional.empty() : Optional.of(lengths);
    }

    public static String formatDashes(List<Double> lengths) {
        return lengths.stream().map(length -> PropertyUnits.number(length, 2)).collect(Collectors.joining(", "));
    }

    private static Stroke stroke(Layer layer) {
        return switch (layer) {
            case ShapeLayer shape -> shape.stroke();
            case TextLayer text -> text.stroke();
            default -> null;
        };
    }

    private static Layer withStroke(Layer layer, Stroke stroke) {
        return switch (layer) {
            case ShapeLayer shape -> shape.withStroke(stroke);
            case TextLayer text -> text.withStroke(stroke);
            default -> layer;
        };
    }

    private void editStroke(String name, UnaryOperator<Stroke> change, Object gestureKey) {
        context().edit(name, Layer.class, layer -> stroke(layer) == null ? layer : withStroke(layer, change.apply(stroke(layer))),
                gestureKey);
    }

    private void setStroke(String name, boolean present) {
        Stroke fresh = new Stroke(Animatable.of(new Paint.Solid(NewLayers.SHAPE_COLOR)), Animatable.of(2.0),
                Stroke.Cap.BUTT, Stroke.Join.MITER, 10, List.of(), Animatable.of(0.0));
        context().edit(name, Layer.class, layer -> present
                ? (stroke(layer) == null ? withStroke(layer, fresh) : layer) : withStroke(layer, null), null);
    }

    @Override
    public boolean appliesTo(InspectorContext context) {
        return !context.layers().isEmpty()
                && context.layers().stream().allMatch(l -> l instanceof ShapeLayer || l instanceof TextLayer);
    }

    @Override
    protected void updateFields(InspectorContext context) {
        boolean all = context.layers().stream().allMatch(l -> stroke(l) != null);
        add.setVisible(!all);
        add.setManaged(!all);
        remove.setVisible(all);
        remove.setManaged(all);
        plainRows.forEach(row -> row.show(all));
        if (!all) {
            return;
        }
        paint.field().setExtent(Extents.of(context.layers().get(0), context.time()));
        updating = true;
        try {
            cap.setValue(context.common(Layer.class, l -> stroke(l).cap()));
            join.setValue(context.common(Layer.class, l -> stroke(l).join()));
        } finally {
            updating = false;
        }
        miter.setValue(context.common(Layer.class, l -> stroke(l).miterLimit()));
        List<Double> dashes = context.common(Layer.class, l -> stroke(l).dashArray());
        dash.setValue(dashes == null ? null : formatDashes(dashes));
    }
}
