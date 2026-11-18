package com.dlsc.animatorfx.sample;

import com.dlsc.animatorfx.player.AnimationPlayer;
import com.dlsc.animatorfx.player.AnimationView;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample window under the headless toolkit.
 */
class SampleAppTest {

    private Stage stage;
    private SampleBrowser browser;

    @BeforeEach
    void show() throws Exception {
        Fx.run(() -> {
            stage = new Stage();
            new SampleApp().start(stage);
            browser = (SampleBrowser) stage.getScene().getRoot();
        });
    }

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> {
            browser.getInPlaceDemo().getPlayer().stop();
            stage.close();
        });
    }

    @Test
    void theWindowShowsTheFirstSample() throws Exception {
        Fx.run(() -> {
            assertTrue(stage.isShowing());
            assertEquals(Samples.SHOWCASE, browser.getList().getItems());
            assertEquals("loader", browser.getList().getSelectionModel().getSelectedItem());
            assertSame(browser.getShowcase("loader"), browser.getCenter());
        });
    }

    @Test
    void everySamplePlaysWhenSelected() throws Exception {
        for (String name : Samples.SHOWCASE) {
            Fx.run(() -> browser.getList().getSelectionModel().select(name));
            AnimationView view = browser.getShowcase(name).getView();
            // auto play starts the view once it is part of the showing window
            await(() -> view.getStatus() == AnimationPlayer.Status.PLAYING && view.getCurrentTime() > 0.05);
            double before = Fx.call(view::getCurrentTime);
            await(() -> view.getCurrentTime() != before);
            Fx.run(() -> {
                assertSame(browser.getShowcase(name), browser.getCenter());
                assertNotNull(view.getScene());
                assertEquals(AnimationPlayer.INDEFINITE, view.getCycleCount());
            });
        }
    }

    @Test
    void theTransportControlsDriveTheView() throws Exception {
        Fx.run(() -> browser.getList().getSelectionModel().select("success"));
        SampleShowcase showcase = browser.getShowcase("success");
        AnimationView view = showcase.getView();
        await(() -> view.getStatus() == AnimationPlayer.Status.PLAYING);
        Fx.run(() -> {
            Button play = lookup(showcase, "#play", Button.class);
            assertEquals("Pause", play.getText());
            play.fire();
            assertEquals(AnimationPlayer.Status.PAUSED, view.getStatus());
            assertEquals("Play", play.getText());

            Slider time = lookup(showcase, "#time", Slider.class);
            assertEquals(2.6, time.getMax());
            time.setValue(1.0);
            assertEquals(1.0, view.getCurrentTime());
            view.seek(2.0);
            assertEquals(2.0, time.getValue());
            assertEquals("2.00 / 2.60 s", lookup(showcase, "#timecode", Label.class).getText());

            lookup(showcase, "#loop", CheckBox.class).setSelected(false);
            assertEquals(1, view.getCycleCount());

            @SuppressWarnings("unchecked")
            ChoiceBox<Double> rate = lookup(showcase, "#rate", ChoiceBox.class);
            rate.setValue(2.0);
            assertEquals(2.0, view.getRate());

            // one button per marker of the document
            lookup(showcase, "#marker-success", Button.class).fire();
            assertEquals(AnimationPlayer.Status.PLAYING, view.getStatus());
            assertEquals("success", view.getPlayer().getActiveMarker());
            assertEquals(1.3, view.getCurrentTime());
            lookup(showcase, "#marker-intro", Button.class).fire();
            assertEquals("intro", view.getPlayer().getActiveMarker());
            assertEquals(0, view.getCurrentTime());
        });
        // not looping: the marker range ends and the view stops at its end
        await(() -> view.getStatus() == AnimationPlayer.Status.STOPPED);
        Fx.run(() -> assertEquals(1.3, view.getCurrentTime(), 1e-9));
    }

    @Test
    void theCardEmbedsARealLabelAndARealButton() throws Exception {
        Fx.run(() -> browser.getList().getSelectionModel().select("card"));
        SampleShowcase showcase = browser.getShowcase("card");
        AnimationView view = showcase.getView();
        await(() -> view.getCurrentTime() > 0.05);
        Fx.run(() -> {
            view.pause();
            view.seek(2.6);
            showcase.applyCss();
            showcase.layout();
            Label title = lookup(view, "#card-title", Label.class);
            Button action = lookup(view, "#card-action", Button.class);
            assertEquals("Hello, AnimatorFX", title.getText());
            assertSame(view.getLayerNode("card-title"), title.getParent());
            assertSame(view.getLayerNode("card-action"), action.getParent());
            // resized to the slot's reference size
            assertEquals(104, action.getWidth());
            assertEquals(32, action.getHeight());
            assertEquals(1, action.getParent().getOpacity());

            view.seek(0.6);
            double early = action.getParent().getOpacity();
            assertTrue(early > 0 && early < 1, "the slot fades in, opacity " + early);

            // the embedded button is live
            view.seek(2.0);
            action.fire();
            assertEquals(AnimationPlayer.Status.PLAYING, view.getStatus());
            assertEquals(0, view.getCurrentTime());
        });
    }

    @Test
    void theInPlaceButtonIsAnimatedAndGivenBack() throws Exception {
        InPlaceDemo demo = browser.getInPlaceDemo();
        Button button = demo.getButton();
        AnimationPlayer player = demo.getPlayer();
        Parent parent = Fx.call(button::getParent);
        Fx.run(() -> {
            assertSame(demo, parent);
            button.setRotate(3);
            button.fire();
            assertEquals(AnimationPlayer.Status.PLAYING, player.getStatus());
            assertEquals(java.util.Set.of("target"), player.getBoundSlots());
            player.pause();
            player.seek(0.1);
            assertEquals(-7, button.getRotate(), 1e-9);
            assertTrue(button.getScaleX() > 1.01);
            // still in the application's own layout
            assertSame(parent, button.getParent());
            player.play();
        });
        await(() -> player.getStatus() == AnimationPlayer.Status.STOPPED);
        Fx.run(() -> {
            assertTrue(player.getBoundSlots().isEmpty());
            assertEquals(3, button.getRotate());
            assertEquals(1, button.getScaleX());
            assertEquals(0, button.getTranslateX());
            assertNull(button.getEffect());
            assertFalse(button.isDisabled());
        });
    }

    @Test
    void theBackdropCanBeSwitched() throws Exception {
        Path directory = Fx.screenshots();
        Fx.run(() -> browser.getList().getSelectionModel().select("card"));
        AnimationView view = browser.getShowcase("card").getView();
        await(() -> view.getCurrentTime() > 0.05);
        WritableImage dark = Fx.call(() -> {
            view.pause();
            view.seek(1.25);
            return stage.getScene().snapshot(null);
        });
        WritableImage light = Fx.call(() -> {
            lookup(browser, "#dark", CheckBox.class).setSelected(false);
            browser.applyCss();
            return stage.getScene().snapshot(null);
        });
        int x = 400;
        int y = 40;
        assertEquals(Color.web("#0f172a"), dark.getPixelReader().getColor(x, y));
        assertNotEquals(dark.getPixelReader().getColor(x, y), light.getPixelReader().getColor(x, y));
        if (directory != null) {
            Fx.writePng(dark, directory.resolve("sample-app-dark.png"));
            Fx.writePng(light, directory.resolve("sample-app-light.png"));
        }
    }

    private static <T extends Node> T lookup(Node root, String selector, Class<T> type) {
        return type.cast(assertInstanceOf(type, root.lookup(selector), selector));
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!Fx.call(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not reached within 10 s");
            }
            Thread.sleep(20);
        }
    }
}
