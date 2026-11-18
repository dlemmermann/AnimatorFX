package com.dlsc.animatorfx.sample;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationIO;
import com.dlsc.animatorfx.player.AnimationPlayer;
import com.dlsc.animatorfx.player.AnimationView;
import com.dlsc.animatorfx.player.Evaluator;
import com.dlsc.animatorfx.player.Fit;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the README honest: its format example loads, its Java snippets only call methods that
 * exist, and the embedding code it shows runs (the same statements, executed here).
 */
class ReadmeTest {

    private static final Path README = Path.of(System.getProperty("animatorfx.readme", "../../README.md"));

    private static String readme() throws IOException {
        return Files.readString(README);
    }

    private static List<String> blocks(String language) throws IOException {
        List<String> blocks = new ArrayList<>();
        Matcher matcher = Pattern.compile("```" + language + "\\R(.*?)```", Pattern.DOTALL).matcher(readme());
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }
        return blocks;
    }

    @Test
    void theFormatExampleLoadsAndAnimates() throws IOException {
        List<String> json = blocks("json");
        assertEquals(1, json.size(), "the README has one JSON example");
        AnimationFile file = AnimationIO.read(new ByteArrayInputStream(json.get(0).getBytes(StandardCharsets.UTF_8)));
        Animation animation = file.animation();
        assertEquals("pulse", animation.name());
        assertEquals(1, animation.layers().size());
        ShapeLayer shape = assertInstanceOf(ShapeLayer.class, animation.layers().get(0));
        Geometry.Ellipse ellipse = assertInstanceOf(Geometry.Ellipse.class, shape.geometry());
        assertEquals(new Vec2(60, 60), ellipse.size().at(0));
        assertEquals(new Paint.Solid(Rgba.parse("#6366F1")), shape.fill().at(0));
        assertEquals(new Vec2(30, 30), shape.base().transform().anchor().at(0));
        assertEquals(new Vec2(60, 60), shape.base().transform().position().at(0));
        String dot = shape.id();
        assertEquals(Vec2.ONE, Evaluator.sample(animation, 0).get(dot).scale());
        assertEquals(new Vec2(1.5, 1.5), Evaluator.sample(animation, 0.5).get(dot).scale());
        assertEquals(Vec2.ONE, Evaluator.sample(animation, 1).get(dot).scale());
        // a quarter into the first segment the easing is well behind linear (1.125)
        double eased = 1 + 0.5 * new Easing.CubicBezier(0.37, 0, 0.63, 1).apply(0.25);
        assertEquals(eased, Evaluator.sample(animation, 0.125).get(dot).scale().x(), 1e-9);
        assertTrue(eased < 1.08, "eased: " + eased);
    }

    @Test
    void theJavaSnippetsOnlyCallMethodsThatExist() throws IOException {
        Map<String, Class<?>> receivers = Map.of("view", AnimationView.class, "player", AnimationPlayer.class,
                "AnimationIO", AnimationIO.class);
        List<String> java = blocks("java");
        assertFalse(java.isEmpty());
        int calls = 0;
        for (String block : java) {
            Matcher matcher = Pattern.compile("\\b(view|player|AnimationIO)\\.(\\w+)\\(").matcher(block);
            while (matcher.find()) {
                Set<String> methods = Arrays.stream(receivers.get(matcher.group(1)).getMethods())
                        .map(Method::getName).collect(Collectors.toSet());
                assertTrue(methods.contains(matcher.group(2)), "No such method: " + matcher.group());
                calls++;
            }
        }
        assertTrue(calls >= 15, "the snippets were found, calls: " + calls);
    }

    @Test
    void theEmbeddingCodeOfTheReadmeRuns() throws Exception {
        URL url = Samples.class.getResource("animations/success.afx");
        URL cardUrl = Samples.class.getResource("animations/card.afx");
        URL attentionUrl = Samples.class.getResource("animations/attention.afx");
        Fx.run(() -> {
            try {
                // loading and playing
                AnimationFile file = AnimationIO.read(url);
                AnimationView view = new AnimationView(file);
                view.setFit(Fit.CONTAIN);
                view.setCycleCount(AnimationPlayer.INDEFINITE);
                view.setRate(1.5);
                view.play();
                view.pause();
                view.seek(0.5);
                view.stop();
                view.setAutoPlay(true);
                assertEquals(0, view.getCurrentTime());

                // markers
                view.playMarker("intro");
                view.getPlayer().setOnFinished(event -> view.playMarker("success"));
                view.getPlayer().setOnMarkerReached(marker -> System.out.println("reached " + marker.name()));
                assertEquals("intro", view.getPlayer().getActiveMarker());

                // CSS
                StackPane pane = new StackPane(view);
                new Scene(pane);
                view.setStyle("-fx-fit: cover; -fx-auto-play: false;");
                pane.applyCss();
                assertEquals(Fit.COVER, view.getFit());
                assertFalse(view.isAutoPlay());

                // embedded slots
                AnimationView card = new AnimationView(AnimationIO.read(cardUrl));
                Label titleLabel = new Label("Hello");
                Button actionButton = new Button("Open");
                card.bind("title", titleLabel);
                card.bind("action", actionButton);
                new Scene(new StackPane(card));
                card.applyCss();
                card.layout();
                assertSame(card.getLayerNode("card-title"), titleLabel.getParent());
                card.unbind("title");
                assertNull(titleLabel.getParent());

                // in place, without a view
                Button button = new Button("Save");
                VBox root = new VBox(button);
                AnimationPlayer player = new AnimationPlayer(AnimationIO.read(attentionUrl));
                player.bindInPlace("target", button);
                player.setOnFinished(event -> player.unbind("target"));
                player.play();
                player.seek(0.1);
                assertEquals(-7, button.getRotate(), 1e-9);
                assertSame(root, button.getParent());
                player.stop();
                player.unbind("target");
                assertEquals(0, button.getRotate());

                // in place by node id, and by FXML namespace
                AnimationView attention = new AnimationView(AnimationIO.read(attentionUrl));
                button.setId("target");
                attention.autoBind(root);
                assertEquals(Set.of("target"), attention.getPlayer().getBoundSlots());
                attention.unbind("target");
                button.setId(null);
                attention.autoBind(root, Map.of("target", button));
                assertEquals(Set.of("target"), attention.getPlayer().getBoundSlots());
                attention.unbind("target");
                view.stop();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });
    }
}
