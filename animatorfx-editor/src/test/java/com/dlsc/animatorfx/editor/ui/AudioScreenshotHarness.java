package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.audio.AudioImport;
import com.dlsc.animatorfx.editor.audio.EditorAudio;
import com.dlsc.animatorfx.editor.audio.TestSounds;
import com.dlsc.animatorfx.editor.canvas.tools.NewLayers;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.session.RecoveryStore;
import com.dlsc.animatorfx.editor.session.SessionState;
import com.dlsc.animatorfx.editor.session.SessionStore;
import com.dlsc.animatorfx.editor.timeline.Markers;
import com.dlsc.animatorfx.editor.timeline.TimelineView;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.model.Vec2;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Not a test: builds a document with sound in a real window and renders the main window to PNG
 * files, for looking at the result. Three audio layers (one with a volume envelope, one looping
 * with a clip offset, one muted), three cues (one selected, one muted) and two markers. Nothing
 * sounds: the editor runs with its audio switched off. Run it like {@link ScreenshotHarness}:
 *
 * <pre>
 * mvn -pl animatorfx-editor test -Dtest=AudioScreenshotHarness -Dsurefire.failIfNoSpecifiedTests=false \
 *   -Dsurefire.jvm.args="--add-reads org.kordamp.ikonli.core=ALL-UNNAMED --enable-native-access=javafx.graphics
 *                        -Danimatorfx.audio=off -Danimatorfx.screenshots=DIR"
 * </pre>
 *
 * which writes {@code DIR/task-3-dark.png}, {@code task-3-light.png} (an audio layer selected,
 * the Audio section in the inspector), {@code task-3-cue-dark.png} (a cue selected, the Cue
 * section), {@code task-3-assets-dark.png}, close-ups of the timeline in both themes and
 * {@code editor-audio.png}, the picture of the README ({@code docs/images/editor-audio.png}). Only
 * the scene of the application is rendered, never the screen.
 */
class AudioScreenshotHarness {

    private static final String DIR_PROPERTY = "animatorfx.screenshots";
    private static final double SCALE = 2;

    private MainWindow window;
    private TimelineView timeline;
    private EditorDocument doc;

    @Test
    @EnabledIfSystemProperty(named = DIR_PROPERTY, matches = ".+")
    void render() throws Exception {
        System.setProperty(EditorAudio.PROPERTY, "off");
        Path out = Path.of(System.getProperty(DIR_PROPERTY));
        Files.createDirectories(out);
        Path config = Files.createTempDirectory(out, "config-");

        FxToolkit.registerPrimaryStage();
        window = fx(() -> {
            MainWindow w = new MainWindow(new Stage(), new SessionStore(config),
                    new RecoveryStore(config.resolve("recovery")));
            w.getStage().setWidth(1360);
            w.getStage().setHeight(960);
            w.show();
            return w;
        });
        timeline = window.getTimelineView();
        doc = window.getWorkspace().getActiveDocument();
        try {
            settle();
            fx(() -> {
                window.applyLayout(new SessionState.Layout(0.775, 0.56, false, false, SidePanel.INSPECTOR));
                return null;
            });
            settle();
            List<String> ids = buildScene(config);
            String chimes = ids.get(0);
            String selectedCue = ids.get(1);

            fx(() -> {
                doc.getSelection().layerIds().setAll(chimes);
                return null;
            });
            for (Theme.Mode mode : new Theme.Mode[]{Theme.Mode.DARK, Theme.Mode.LIGHT}) {
                theme(mode);
                String name = mode.name().toLowerCase();
                snapshot(window.getScene().getRoot(), out.resolve("task-3-" + name + ".png"));
                if (mode == Theme.Mode.DARK) {
                    // the picture of the README, docs/images/editor-audio.png
                    snapshot(window.getScene().getRoot(), out.resolve("editor-audio.png"));
                }
                snapshot(timeline, out.resolve("task-3-timeline-" + name + ".png"));
            }
            theme(Theme.Mode.DARK);
            fx(() -> {
                timeline.selectCue(selectedCue);
                return null;
            });
            settle();
            snapshot(window.getScene().getRoot(), out.resolve("task-3-cue-dark.png"));
            theme(Theme.Mode.LIGHT);
            snapshot(window.getScene().getRoot(), out.resolve("task-3-cue-light.png"));
            theme(Theme.Mode.DARK);
            fx(() -> {
                window.getSidePanel().setActiveTab(SidePanel.ASSETS);
                return null;
            });
            settle();
            snapshot(window.getScene().getRoot(), out.resolve("task-3-assets-dark.png"));
        } finally {
            fx(() -> {
                window.shutdown();
                window.getStage().hide();
                return null;
            });
            try (var files = Files.walk(config)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** @return the id of the first audio layer and of the cue to select */
    private List<String> buildScene(Path dir) throws Exception {
        Path chimesFile = dir.resolve("chimes.wav");
        Files.write(chimesFile, TestSounds.chimes(1.6));
        Path padFile = dir.resolve("pad.wav");
        double[] pad = TestSounds.sine(2.4, 22050, 110, 1);
        for (int i = 0; i < pad.length; i++) {
            double t = i / (double) pad.length;
            pad[i] *= 0.75 * Math.sin(Math.PI * t) * (0.6 + 0.4 * Math.sin(2 * Math.PI * 5 * t));
        }
        Files.write(padFile, TestSounds.wav(pad, 22050, 1, 16, false));

        fx(() -> {
            doc.execute(Commands.setDocument("Set Duration", a -> a.withDuration(4)));
            ShapeLayer card = NewLayers.rectangle(doc.getAnimation(), new Vec2(250, 190), new Vec2(300, 220));
            doc.execute(Commands.addLayer(null, 0, card.withBase(card.base().withName("Card"))
                    .withFill(Animatable.of(new Paint.Solid(Rgba.parse("#4C9AFF"))))));
            ShapeLayer dot = NewLayers.ellipse(doc.getAnimation(), new Vec2(500, 150), new Vec2(90, 90));
            Layer dotLayer = dot.withBase(dot.base().withName("Dot")).withFill(Animatable.of(new Paint.Solid(Rgba.parse("#FFB454"))));
            doc.execute(Commands.addLayer(null, 1, dotLayer));
            doc.execute(Commands.setProperty(dotLayer.id(), "opacity", Animatable.keyframed(List.of(
                    new Keyframe<>("o1", 0.4, 0.0, Easing.LINEAR), new Keyframe<>("o2", 1.2, 1.0, Easing.LINEAR)))));
            return null;
        });

        fx(() -> {
            doc.setPlayhead(0.4);
            return null;
        });
        Optional<String> chimes = fx(() -> window.getImportActions().importAudio(doc, chimesFile)).get();
        settle();
        String chimesAsset = ((AudioLayer) doc.getAnimation().layers().get(doc.getAnimation().layers().size() - 1)).assetId();
        String loopId = fx(() -> {
            doc.setPlayhead(0.2);
            String id = AudioImport.place(doc, chimesAsset).orElseThrow();
            doc.execute(Commands.updateLayer("Edit", id, l -> ((AudioLayer) l).withLoop(true).withClipOffset(0.35)
                    .withBase(l.base().withName("chimes loop").withInPoint(0.2).withOutPoint(3.7))));
            doc.setPlayhead(1.0);
            return id;
        });
        Optional<String> padLayer = fx(() -> window.getImportActions().importAudio(doc, padFile)).get();
        fx(() -> {
            doc.execute(Commands.updateLayer("Mute Layer", padLayer.orElseThrow(), l -> ((AudioLayer) l).withMuted(true)));
            // longer than its sound: the end of the bar is empty
            doc.execute(Commands.updateLayer("Trim Layer", chimes.orElseThrow(), l -> l.withBase(l.base().withOutPoint(2.5))));
            doc.execute(Commands.setProperty(chimes.orElseThrow(), "volume", Animatable.keyframed(List.of(
                    new Keyframe<>("v1", 0.4, 0.15, Easing.LINEAR), new Keyframe<>("v2", 0.8, 1.0, Easing.LINEAR),
                    new Keyframe<>("v3", 1.5, 1.0, Easing.LINEAR), new Keyframe<>("v4", 2.0, 0.2, Easing.LINEAR)))));
            doc.execute(Markers.add(0.4));
            doc.execute(Markers.add(2.2));
            doc.execute(Markers.setRange("Resize Marker", "marker1", 0.4, 1.4));
            doc.execute(Markers.rename("marker1", "intro"));
            doc.execute(Markers.setRange("Resize Marker", "marker2", 2.2, 3.6));
            doc.execute(Markers.rename("marker2", "loop"));
            doc.execute(Commands.addCue(new SoundCue("cue-tick", "tick", 0.6, chimesAsset, 1, false)));
            doc.execute(Commands.addCue(new SoundCue("cue-whoosh", "whoosh", 1.7, chimesAsset, 0.6, true)));
            doc.execute(Commands.addCue(new SoundCue("cue-hit", "impact", 2.9, chimesAsset, 0.8, false)));
            doc.getViewState().getExpandedLayerIds().add(chimes.orElseThrow());
            doc.getViewState().setShowAllProperties(true);
            doc.setPlayhead(1.25);
            return null;
        });
        // the waveforms are decoded in the background
        Thread.sleep(1500);
        settle();
        return List.of(chimes.orElseThrow(), "cue-tick", loopId);
    }

    private void theme(Theme.Mode mode) throws Exception {
        fx(() -> {
            window.getTheme().setMode(mode);
            return null;
        });
        Thread.sleep(1200);
        settle();
    }

    private void snapshot(Node node, Path file) throws Exception {
        WritableImage image = fx(() -> {
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setTransform(Transform.scale(SCALE, SCALE));
            return node.snapshot(parameters, null);
        });
        Files.write(file, PngEncoder.encode(image));
        System.out.println("Wrote " + file);
    }

    private void settle() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(150);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        return WaitForAsyncUtils.asyncFx(action).get();
    }
}
