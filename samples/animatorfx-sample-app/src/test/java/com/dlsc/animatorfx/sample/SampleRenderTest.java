package com.dlsc.animatorfx.sample;

import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.AnimationView;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders every sample through {@link AnimationView} at several times. With
 * {@code -Danimatorfx.screenshots=DIR} the frames are also written as PNG: one contact sheet per
 * sample (a dark and a light row) and single frames.
 */
class SampleRenderTest {

    private static final int FRAMES = 6;
    private static final Color DARK = Color.web("#0f172a");
    private static final Color LIGHT = Color.web("#f8fafc");

    /** Samples whose content scales or fades in from nothing. */
    private static final Set<String> BUILDS_UP_FROM_NOTHING = Set.of("success", "card");

    static Stream<String> names() {
        return Samples.SHOWCASE.stream();
    }

    @ParameterizedTest
    @MethodSource("names")
    void everySampleRendersAcrossItsDuration(String name) throws Exception {
        AnimationFile file = Samples.load(name);
        double duration = file.animation().duration();
        WritableImage sheet = Fx.call(() -> {
            VBox rows = new VBox();
            for (Color background : new Color[]{DARK, LIGHT}) {
                HBox row = new HBox();
                row.setBackground(Background.fill(background));
                for (int i = 0; i < FRAMES; i++) {
                    row.getChildren().add(frame(name, file, duration * i / (FRAMES - 1)));
                }
                rows.getChildren().add(row);
            }
            new Scene(rows);
            rows.applyCss();
            rows.layout();
            return Fx.snapshot(rows, Color.TRANSPARENT);
        });
        double width = file.animation().width();
        double height = file.animation().height();
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < FRAMES; i++) {
                int painted = painted(sheet, (int) (i * width), (int) (row * height), (int) width, (int) height);
                if (i == 0 && BUILDS_UP_FROM_NOTHING.contains(name)) {
                    assertEquals(0, painted, "the first frame is empty");
                } else {
                    assertTrue(painted > 100, "frame " + i + " of row " + row + " draws, painted: " + painted);
                }
            }
        }

        Path directory = Fx.screenshots();
        if (directory != null) {
            Fx.writePng(sheet, directory.resolve(name + "-sheet.png"));
            for (double fraction : new double[]{0.2, 0.47, 1}) {
                double time = duration * fraction;
                WritableImage single = Fx.call(() -> {
                    StackPane pane = frame(name, file, time);
                    pane.setPrefSize(file.animation().width() * 2, file.animation().height() * 2);
                    new Scene(pane);
                    pane.applyCss();
                    pane.layout();
                    return Fx.snapshot(pane, Color.TRANSPARENT);
                });
                Fx.writePng(single, directory.resolve(String.format("%s-%02.0f.png", name, fraction * 100)));
            }
        }
    }

    private static StackPane frame(String name, AnimationFile file, double time) {
        AnimationView view = new AnimationView(file);
        if (name.equals("card")) {
            Label title = new Label("Hello, AnimatorFX");
            title.setStyle(SampleShowcase.TITLE_STYLE);
            view.bind("title", title);
            view.bind("action", new Button("Replay"));
        }
        view.seek(time);
        StackPane pane = new StackPane(view);
        pane.setPrefSize(file.animation().width(), file.animation().height());
        return pane;
    }

    /** The number of pixels of a cell (every second one is looked at) that differ from its corner. */
    private static int painted(WritableImage image, int left, int top, int width, int height) {
        int background = image.getPixelReader().getArgb(left, top);
        int count = 0;
        for (int y = top; y < top + height; y += 2) {
            for (int x = left; x < left + width; x += 2) {
                if (image.getPixelReader().getArgb(x, y) != background) {
                    count++;
                }
            }
        }
        return count;
    }
}
