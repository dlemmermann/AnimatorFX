package com.dlsc.animatorfx.editor.inspector.fields;

import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import com.dlsc.animatorfx.model.Vec2;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * Two {@link NumberField}s for a vector: X and Y, or width and height. While the link toggle
 * (used for scale) is selected, a change of one component changes the other in proportion.
 *
 * <p>A change is reported as an operator on the vector rather than as a vector, because with
 * several layers selected an edit of X must keep every layer's own Y.
 */
public final class Vec2Field extends HBox {

    private final NumberField first;
    private final NumberField second;
    private final ToggleButton link = new ToggleButton(null, new FontIcon(Material2AL.LINK));
    private BiConsumer<UnaryOperator<Vec2>, Boolean> onChange = (fn, adjusting) -> {
    };

    /**
     * @param linkable whether the components change in proportion; the owner then places
     *                 {@link #getLinkToggle()} somewhere, so that the user can switch it off
     */
    public Vec2Field(PropertyUnits units, String firstName, String secondName, boolean linkable) {
        getStyleClass().add("vec2-field");
        first = new NumberField(units);
        second = new NumberField(units);
        first.setTooltip(new Tooltip(firstName));
        second.setTooltip(new Tooltip(secondName));
        for (NumberField field : new NumberField[]{first, second}) {
            HBox.setHgrow(field, Priority.ALWAYS);
            field.setMaxWidth(Double.MAX_VALUE);
            field.setMinWidth(0);
        }
        first.setOnChange((x, adjusting) -> onChange.accept(v -> {
            double y = link.isSelected() ? proportional(v.y(), v.x(), x) : v.y();
            return new Vec2(x, y);
        }, adjusting));
        second.setOnChange((y, adjusting) -> onChange.accept(v -> {
            double x = link.isSelected() ? proportional(v.x(), v.y(), y) : v.x();
            return new Vec2(x, y);
        }, adjusting));
        link.getStyleClass().setAll("icon-button", "link-toggle");
        link.setFocusTraversable(false);
        link.setTooltip(new Tooltip("Keep the proportions"));
        getChildren().addAll(first, second);
        link.setSelected(linkable);
    }

    /** The other component after this one went from {@code before} to {@code after}. */
    private static double proportional(double other, double before, double after) {
        return Math.abs(before) < 1e-9 ? after : other * after / before;
    }

    public NumberField getFirst() {
        return first;
    }

    public NumberField getSecond() {
        return second;
    }

    public ToggleButton getLinkToggle() {
        return link;
    }

    /** Shows the components; {@code null} for one that differs across the selection. */
    public void setValue(Double x, Double y) {
        first.setValue(x);
        second.setValue(y);
    }

    public void setValue(Vec2 value) {
        setValue(value == null ? null : value.x(), value == null ? null : value.y());
    }

    public void setRange(double min, double max) {
        first.setRange(min, max);
        second.setRange(min, max);
    }

    public void setDecimals(int decimals) {
        first.setDecimals(decimals);
        second.setDecimals(decimals);
    }

    public void setOnChange(BiConsumer<UnaryOperator<Vec2>, Boolean> handler) {
        onChange = handler;
    }

    public void setOnAdjustEnd(Runnable handler) {
        first.setOnAdjustEnd(handler);
        second.setOnAdjustEnd(handler);
    }

    /** A drag on the node scrubs the first component. */
    public void installScrubbing(Node handle) {
        first.installScrubbing(handle);
    }
}
