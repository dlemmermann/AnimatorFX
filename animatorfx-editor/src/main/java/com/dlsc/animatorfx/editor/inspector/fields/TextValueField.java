package com.dlsc.animatorfx.editor.inspector.fields;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.css.PseudoClass;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * A text field for a plain text value: Enter or losing the focus commits, Escape reverts. An
 * optional validator turns the field red and explains why while the text is not allowed; such
 * text is never committed. {@link #setValue} only updates the display, and leaves the text
 * alone while the user is typing.
 */
public final class TextValueField extends TextField {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    private String value;
    private boolean typing;
    private boolean updatingText;
    private Consumer<String> onCommit = text -> {
    };
    private Function<String, Optional<String>> validator = text -> Optional.empty();
    private final Tooltip errorTip = new Tooltip();

    public TextValueField() {
        getStyleClass().add("text-value-field");
        setPromptText("mixed");
        textProperty().addListener(obs -> {
            if (!updatingText) {
                typing = true;
                validate();
            }
        });
        setOnAction(e -> {
            commit();
            selectAll();
        });
        focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                commit();
                showValue();
            }
        });
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                showValue();
                selectAll();
                e.consume();
            }
        });
    }

    public String getValue() {
        return value;
    }

    /** Shows a text, or the "mixed" placeholder for {@code null}. */
    public void setValue(String text) {
        value = text;
        if (!(isFocused() && typing)) {
            showValue();
        }
    }

    public void setOnCommit(Consumer<String> handler) {
        onCommit = handler;
    }

    /** The validator returns the reason why a text is not allowed, or empty when it is. */
    public void setValidator(Function<String, Optional<String>> validator) {
        this.validator = validator;
    }

    /** The reason why the typed text is not allowed; empty when it is, or when nothing was typed. */
    public Optional<String> getError() {
        return typing ? validator.apply(getText() == null ? "" : getText()) : Optional.empty();
    }

    private void validate() {
        Optional<String> error = getError();
        pseudoClassStateChanged(ERROR, error.isPresent());
        errorTip.setText(error.orElse(""));
        setTooltip(error.isPresent() ? errorTip : null);
    }

    private void showValue() {
        updatingText = true;
        try {
            setText(value == null ? "" : value);
        } finally {
            updatingText = false;
        }
        typing = false;
        validate();
    }

    private void commit() {
        if (!typing || getError().isPresent()) {
            return;
        }
        String text = getText() == null ? "" : getText();
        boolean changed = !text.equals(value);
        value = text;
        typing = false;
        if (changed) {
            onCommit.accept(text);
        }
    }
}
