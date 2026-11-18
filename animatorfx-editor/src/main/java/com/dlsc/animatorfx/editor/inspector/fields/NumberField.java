package com.dlsc.animatorfx.editor.inspector.fields;

import com.dlsc.animatorfx.editor.doc.PropertyUnits;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * A monospaced field for one number. The value is kept in stored units; the text shows it in
 * display units with the unit suffix ({@code 100%}, {@code 45°}), which is never stored.
 *
 * <ul>
 * <li>Enter or losing the focus commits the typed text; Escape reverts it. Text that is not a
 * number reverts as well.</li>
 * <li>Arrow up and down change the value by 1 display unit, with Shift by 10, with Alt by 0.1.</li>
 * <li>{@link #installScrubbing} lets a horizontal drag on a label change the value.</li>
 * </ul>
 *
 * The change handler gets the new stored value and whether the change is part of a continuous
 * adjustment (arrow keys, scrubbing) that should become one undo step; the adjust-end handler
 * runs when such an adjustment is over. {@link #setValue} only updates the display: it never
 * calls the handler, and it leaves the text alone while the user is typing in the field.
 */
public final class NumberField extends TextField {

    private final PropertyUnits units;
    private int decimals = 2;
    private double min = Double.NEGATIVE_INFINITY;
    private double max = Double.POSITIVE_INFINITY;
    private Double value;
    private boolean typing;
    private boolean updatingText;
    private boolean adjusting;
    private BiConsumer<Double, Boolean> onChange = (v, a) -> {
    };
    private Runnable onAdjustEnd = () -> {
    };

    public NumberField(PropertyUnits units) {
        this.units = units;
        getStyleClass().addAll("number-field", "mono");
        setPromptText("mixed");
        setPrefColumnCount(5);
        textProperty().addListener(obs -> {
            if (!updatingText) {
                typing = true;
            }
        });
        setOnAction(e -> {
            commit();
            selectAll();
        });
        focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                Platform.runLater(this::selectAll);
            } else {
                commit();
                endAdjusting();
            }
        });
        addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
    }

    /** At most this many decimals are shown; 2 unless set. */
    public void setDecimals(int decimals) {
        this.decimals = decimals;
        showValue();
    }

    /** The range of the stored value; typed and adjusted values are limited to it. */
    public void setRange(double min, double max) {
        this.min = min;
        this.max = max;
    }

    /** The stored value; {@code null} when the selection has different values. */
    public Double getValue() {
        return value;
    }

    /**
     * Shows a stored value, or the "mixed" placeholder for {@code null}.
     */
    public void setValue(Double stored) {
        value = stored;
        if (!(isFocused() && typing)) {
            showValue();
        }
    }

    public void setOnChange(BiConsumer<Double, Boolean> handler) {
        onChange = handler;
    }

    public void setOnAdjustEnd(Runnable handler) {
        onAdjustEnd = handler;
    }

    /** Whether the user has typed text that is not committed yet. */
    public boolean isTyping() {
        return isFocused() && typing;
    }

    private void showValue() {
        updatingText = true;
        try {
            setText(value == null ? "" : PropertyUnits.number(value * units.factor(), decimals) + units.suffix());
        } finally {
            updatingText = false;
        }
        typing = false;
    }

    private Double parse() {
        String text = getText() == null ? "" : getText().strip();
        String suffix = units.suffix().strip();
        if (!suffix.isEmpty() && text.endsWith(suffix)) {
            text = text.substring(0, text.length() - suffix.length()).strip();
        }
        try {
            double number = Double.parseDouble(text.replace(',', '.'));
            return Double.isFinite(number) ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void commit() {
        if (!typing) {
            return;
        }
        Double number = parse();
        if (number == null) {
            showValue();
            return;
        }
        double stored = Math.clamp(number / units.factor(), min, max);
        boolean changed = value == null || Math.abs(stored - value) > 1e-12;
        value = stored;
        showValue();
        if (changed) {
            onChange.accept(stored, false);
        }
    }

    private void keyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            showValue();
            selectAll();
            event.consume();
        } else if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN) {
            Double base = typing ? parse() : value == null ? null : value * units.factor();
            if (base != null) {
                double step = event.isShiftDown() ? 10 : event.isAltDown() ? 0.1 : 1;
                adjust(base + (event.getCode() == KeyCode.UP ? step : -step));
                selectAll();
            }
            event.consume();
        }
    }

    /** Sets a display value as part of a continuous adjustment. */
    private void adjust(double display) {
        double stored = Math.clamp(round(display) / units.factor(), min, max);
        boolean changed = value == null || Math.abs(stored - value) > 1e-12;
        value = stored;
        showValue();
        if (changed) {
            adjusting = true;
            onChange.accept(stored, true);
        }
    }

    private static double round(double display) {
        return Math.round(display * 1000) / 1000.0;
    }

    private void endAdjusting() {
        if (adjusting) {
            adjusting = false;
            onAdjustEnd.run();
        }
    }

    /**
     * Makes a horizontal drag on the node, usually the label of the field, change the value:
     * one display unit per pixel, ten with Shift, a tenth with Alt. The whole drag is one
     * adjustment.
     */
    public void installScrubbing(Node handle) {
        double[] start = new double[2];
        boolean[] active = {false};
        handle.setCursor(Cursor.H_RESIZE);
        handle.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            active[0] = value != null && !isDisabled();
            start[0] = e.getScreenX();
            start[1] = value == null ? 0 : value * units.factor();
        });
        handle.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (active[0]) {
                double step = e.isShiftDown() ? 10 : e.isAltDown() ? 0.1 : 1;
                adjust(start[1] + Math.round(e.getScreenX() - start[0]) * step);
            }
        });
        handle.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            active[0] = false;
            endAdjusting();
        });
    }
}
