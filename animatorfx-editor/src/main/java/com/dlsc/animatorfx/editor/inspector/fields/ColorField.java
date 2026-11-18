package com.dlsc.animatorfx.editor.inspector.fields;

import com.dlsc.animatorfx.model.Rgba;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * A colour as a swatch and a hex text ({@code #RRGGBB}, or {@code #RRGGBBAA} when not opaque).
 * A click on the swatch opens a {@link ColorPopup}. The change handler gets the colour and
 * whether the change is part of a drag in the picker, which should become one undo step; the
 * adjust-end handler runs when the drag is over. {@link #setValue} only updates the display.
 */
public final class ColorField extends HBox {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    private final Region swatchColor = new Region();
    private final Button swatch = new Button();
    private final TextField hex = new TextField();
    private ColorPopup popup;
    private Rgba value;
    private boolean typing;
    private boolean updatingText;
    private BiConsumer<Rgba, Boolean> onChange = (color, adjusting) -> {
    };
    private Runnable onAdjustEnd = () -> {
    };

    public ColorField() {
        getStyleClass().add("color-field");
        Region base = new Region();
        base.getStyleClass().add("swatch-base");
        swatchColor.getStyleClass().add("swatch-color");
        StackPane box = new StackPane(base, swatchColor);
        box.getStyleClass().add("swatch-box");
        swatch.setGraphic(box);
        swatch.getStyleClass().setAll("swatch");
        swatch.setFocusTraversable(false);
        swatch.setTooltip(new Tooltip("Pick a colour"));
        swatch.setOnAction(e -> showPicker());

        hex.getStyleClass().addAll("hex-field", "mono");
        hex.setPromptText("mixed");
        hex.setPrefColumnCount(8);
        hex.setMinWidth(0);
        hex.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(hex, Priority.ALWAYS);
        hex.textProperty().addListener(obs -> {
            if (!updatingText) {
                typing = true;
                hex.pseudoClassStateChanged(ERROR, HexColors.parse(hex.getText()).isEmpty());
            }
        });
        hex.setOnAction(e -> {
            commit();
            hex.selectAll();
        });
        hex.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                Platform.runLater(hex::selectAll);
            } else {
                commit();
            }
        });
        hex.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                showValue();
                e.consume();
            }
        });
        getChildren().addAll(swatch, hex);
    }

    public Rgba getValue() {
        return value;
    }

    /** Shows a colour, or the "mixed" placeholder for {@code null}. */
    public void setValue(Rgba color) {
        value = color;
        if (!(hex.isFocused() && typing)) {
            showValue();
        }
        if (popup != null && popup.isShowing() && color != null && !color.equals(popup.getColor())) {
            popup.setColor(color);
        }
    }

    public void setOnChange(BiConsumer<Rgba, Boolean> handler) {
        onChange = handler;
    }

    public void setOnAdjustEnd(Runnable handler) {
        onAdjustEnd = handler;
    }

    public TextField getHexField() {
        return hex;
    }

    public Button getSwatch() {
        return swatch;
    }

    /** The picker; created on first use. */
    public ColorPopup getPopup() {
        if (popup == null) {
            popup = new ColorPopup();
            popup.setOnChange((color, adjusting) -> {
                value = color;
                showValue();
                onChange.accept(color, adjusting);
            });
            popup.setOnAdjustEnd(() -> onAdjustEnd.run());
            popup.setOnHidden(e -> onAdjustEnd.run());
        }
        return popup;
    }

    public void showPicker() {
        getPopup().setColor(value == null ? Rgba.WHITE : value);
        getPopup().showBelow(swatch);
    }

    private void showValue() {
        updatingText = true;
        try {
            hex.setText(value == null ? "" : HexColors.format(value));
        } finally {
            updatingText = false;
        }
        typing = false;
        hex.pseudoClassStateChanged(ERROR, false);
        Color color = value == null ? Color.TRANSPARENT
                : Color.color(clamp(value.r()), clamp(value.g()), clamp(value.b()), clamp(value.a()));
        swatchColor.setBackground(new Background(new BackgroundFill(color, new CornerRadii(2), Insets.EMPTY)));
    }

    private static double clamp(double channel) {
        return Math.clamp(channel, 0.0, 1.0);
    }

    private void commit() {
        if (!typing) {
            return;
        }
        Rgba parsed = HexColors.parse(hex.getText()).orElse(null);
        if (parsed == null) {
            showValue();
            return;
        }
        boolean changed = !parsed.equals(value);
        value = parsed;
        showValue();
        if (changed) {
            onChange.accept(parsed, false);
        }
    }
}
