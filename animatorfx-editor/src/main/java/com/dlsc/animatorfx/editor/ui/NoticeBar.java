package com.dlsc.animatorfx.editor.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-modal notices below the toolbar. Each notice can be dismissed and may offer one action,
 * which usually dismisses it too. The bar takes no space while it is empty.
 */
public final class NoticeBar extends VBox {

    public NoticeBar() {
        getStyleClass().add("notice-bar");
        managedProperty().bind(visibleProperty());
        setVisible(false);
    }

    public void show(String message) {
        show(message, null, null);
    }

    /**
     * @param actionText the label of the action button, or {@code null} for a notice without an action
     * @return dismisses the notice; does nothing when the notice is gone already
     */
    public Runnable show(String message, String actionText, Runnable action) {
        return show(message, actionText, action, true);
    }

    /**
     * @param actionText      the label of the action button, or {@code null} for a notice without an action
     * @param dismissOnAction whether the action dismisses the notice; when not, it can be used again
     * @return dismisses the notice; does nothing when the notice is gone already
     */
    public Runnable show(String message, String actionText, Runnable action, boolean dismissOnAction) {
        HBox notice = new HBox();
        notice.getStyleClass().add("notice");

        FontIcon icon = new FontIcon(Material2AL.INFO);
        Label text = new Label(message);
        text.getStyleClass().add("notice-message");
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        notice.getChildren().addAll(icon, text);

        if (actionText != null) {
            Button button = new Button(actionText);
            button.getStyleClass().add("notice-action");
            button.setFocusTraversable(false);
            button.setOnAction(e -> {
                if (dismissOnAction) {
                    dismiss(notice);
                }
                action.run();
            });
            notice.getChildren().add(button);
        }
        Button dismiss = new Button(null, new FontIcon(Material2AL.CLOSE));
        dismiss.getStyleClass().addAll("icon-button", "notice-dismiss");
        dismiss.setFocusTraversable(false);
        dismiss.setOnAction(e -> dismiss(notice));
        notice.getChildren().add(dismiss);

        getChildren().add(notice);
        setVisible(true);
        return () -> dismiss(notice);
    }

    /**
     * The messages of the notices that are showing, oldest first.
     */
    public List<String> getMessages() {
        List<String> messages = new ArrayList<>();
        for (Node notice : getChildren()) {
            messages.add(((Label) notice.lookup(".notice-message")).getText());
        }
        return messages;
    }

    private void dismiss(Node notice) {
        getChildren().remove(notice);
        setVisible(!getChildren().isEmpty());
    }
}
