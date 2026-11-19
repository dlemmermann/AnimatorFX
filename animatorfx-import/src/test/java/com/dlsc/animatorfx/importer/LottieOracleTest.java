package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.player.AnimationView;
import com.dlsc.animatorfx.player.Fit;
import com.lottie4j.core.file.LottieFileLoader;
import com.lottie4j.fxplayer.LottiePlayer;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the Lottie fixtures twice, with our importer and the {@code AnimationView}, and with
 * the Lottie4J player, which reads the same file without any conversion, and compares the two
 * pictures.
 * <p>
 * Our Lottie conventions (parenting, mattes, easing, time mapping, the order of painting) were
 * written against the specification and never against a second implementation. This is that
 * second opinion: a frame where the two disagree is either a bug in the importer or a place where
 * Lottie4J is the one that is wrong, and either is worth a look. The library is a test
 * dependency only; nothing in the importer or the runtime depends on it.
 * <p>
 * The limits below are not "correct enough" thresholds. Each is the divergence measured when the
 * test was written plus some room, so that a change that makes the import <em>worse</em> fails
 * and a known difference is written down with its reason instead of being forgotten.
 */
class LottieOracleTest {

    /** A pixel counts as different when a channel (0..255), composited over white, is off by more. */
    private static final int CHANNEL_TOLERANCE = 24;

    /**
     * The share of pixels (0..1) that may differ, per fixture; 1% for a fixture not named here. What
     * is measured today, rounded up:
     * <ul>
     *   <li>{@code shapes}: the gradient fill and the gradient stroke look different (the colors
     *       towards the end of a gradient with alpha stops, and the stroke of an ellipse with a
     *       gradient). Which side is right has not been established; the Lottie specification
     *       needs to be read against both outputs.</li>
     *   <li>{@code layers}: its text layer, which the two draw with their own idea of the font and
     *       of where the baseline is.</li>
     *   <li>{@code parenting}, {@code precomp}, {@code morph}: edges that differ by a pixel or
     *       two, plus offsets that have not been traced yet.</li>
     * </ul>
     */
    private static final Map<String, Double> LIMITS = Map.of(
            "shapes", 0.16,
            "layers", 0.09,
            "parenting", 0.04,
            "precomp", 0.025,
            "morph", 0.015);

    /** Set the environment variable {@code LOTTIE_ORACLE_DUMP} to a directory to get both pictures of every frame as PNG files. */
    private static final String DUMP_VARIABLE = "LOTTIE_ORACLE_DUMP";

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    private static <T> T onFxThread(Callable<T> action) throws Exception {
        try {
            return WaitForAsyncUtils.asyncFx(action).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static File fixture(String name) throws URISyntaxException {
        return new File(Objects.requireNonNull(LottieOracleTest.class.getResource("lottie/" + name + ".json"), name)
                .toURI());
    }

    /** The picture of one frame as our player draws it. */
    private static WritableImage ours(com.dlsc.animatorfx.model.io.AnimationFile file, double time) {
        Animation animation = file.animation();
        AnimationView view = new AnimationView(file);
        view.setFit(Fit.CONTAIN);
        view.setAutoPlay(false);
        Scene scene = new Scene(view, animation.width(), animation.height());
        view.setCurrentTime(time);
        view.applyCss();
        view.layout();
        return scene.getRoot().snapshot(transparent(), null);
    }

    /** The picture of one frame as Lottie4J draws it. */
    private static WritableImage reference(com.lottie4j.core.model.animation.Animation animation, double frame) {
        LottiePlayer player = new LottiePlayer(animation, animation.width(), animation.height());
        player.setBackgroundColor(Color.TRANSPARENT);
        // a picture of its own canvas, without the adaptive scaling that trades resolution for speed
        return player.snapshotFrame(frame);
    }

    private static void dump(String name, int step, WritableImage ours, WritableImage reference) throws IOException {
        String directory = System.getenv(DUMP_VARIABLE);
        if (directory == null) {
            return;
        }
        Path target = Files.createDirectories(Path.of(directory));
        Files.write(target.resolve(name + "-" + step + "-ours.png"), PngWriter.encode(ours));
        Files.write(target.resolve(name + "-" + step + "-lottie4j.png"), PngWriter.encode(reference));
    }

    private static SnapshotParameters transparent() {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return parameters;
    }

    /** The share of pixels (0..1) the two pictures draw differently, both composited over white. */
    static double difference(WritableImage a, WritableImage b) {
        int width = (int) Math.min(a.getWidth(), b.getWidth());
        int height = (int) Math.min(a.getHeight(), b.getHeight());
        PixelReader pa = a.getPixelReader();
        PixelReader pb = b.getPixelReader();
        int different = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (differs(pa.getColor(x, y), pb.getColor(x, y))) {
                    different++;
                }
            }
        }
        return different / (double) (width * height);
    }

    private static boolean differs(Color a, Color b) {
        return channel(a.getRed(), a.getOpacity(), b.getRed(), b.getOpacity())
                || channel(a.getGreen(), a.getOpacity(), b.getGreen(), b.getOpacity())
                || channel(a.getBlue(), a.getOpacity(), b.getBlue(), b.getOpacity());
    }

    private static boolean channel(double ca, double aa, double cb, double ab) {
        double over = 1 - aa;
        double under = 1 - ab;
        return Math.abs((ca * aa + over) - (cb * ab + under)) * 255 > CHANNEL_TOLERANCE;
    }

    @ParameterizedTest
    @ValueSource(strings = {"layers", "shapes", "transform", "parenting", "masks", "morph", "precomp"})
    void importMatchesTheReferenceRenderer(String name) throws Exception {
        var reference = LottieFileLoader.load(fixture(name));
        ImportResult imported;
        try (var in = LottieOracleTest.class.getResourceAsStream("lottie/" + name + ".json")) {
            imported = LottieImporter.read(Objects.requireNonNull(in), name);
        }
        Animation animation = imported.file().animation();
        int steps = 6;
        double worst = 0;
        StringBuilder report = new StringBuilder();
        for (int i = 0; i <= steps; i++) {
            // the out point is exclusive in Lottie: Lottie4J clamps to the frame before it, so the last picture is that one
            double time = (animation.duration() - 1 / animation.frameRate()) * i / steps;
            double frame = reference.inPoint() + time * animation.frameRate();
            int step = i;
            double share = onFxThread(() -> {
                WritableImage mine = ours(imported.file(), time);
                WritableImage theirs = reference(reference, frame);
                dump(name, step, mine, theirs);
                return difference(mine, theirs);
            });
            report.append(String.format("  t=%.2fs frame %.1f: %.2f%% of the pixels differ%n", time, frame,
                    share * 100));
            worst = Math.max(worst, share);
        }
        System.out.printf("LOTTIE-ORACLE %s worst %.2f%%%n%s", name, worst * 100, report);
        double limit = LIMITS.getOrDefault(name, 0.01);
        assertTrue(worst <= limit, name + ": the importer and Lottie4J disagree by more than "
                + limit * 100 + "% of the pixels\n" + report);
    }
}
