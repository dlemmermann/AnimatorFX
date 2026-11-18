package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.canvas.tools.SlotTool;
import com.dlsc.animatorfx.editor.canvas.tools.TextTool;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.session.RecoveryStore;
import com.dlsc.animatorfx.editor.session.SessionStore;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Vec2;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Not a test: builds a small document with the canvas tools in a real window, as a user would
 * with the mouse, and renders the main window in both themes to PNG files, for looking at the
 * result. It is not picked up by a normal build. Run it without the Monocle flags:
 *
 * <pre>
 * mvn -pl animatorfx-editor test -Dtest=ScreenshotHarness -Dsurefire.failIfNoSpecifiedTests=false \
 *   -Dsurefire.jvm.args="--add-reads org.kordamp.ikonli.core=ALL-UNNAMED --enable-native-access=javafx.graphics
 *                        -Danimatorfx.screenshots=DIR [-Danimatorfx.screenshots.prefix=task-7]"
 * </pre>
 *
 * which writes {@code DIR/task-7-dark.png} and {@code DIR/task-7-light.png}.
 */
class ScreenshotHarness {

    private static final String DIR_PROPERTY = "animatorfx.screenshots";
    private static final double SCALE = 2;

    private MainWindow window;
    private CanvasView canvas;
    private EditorDocument doc;

    @Test
    @EnabledIfSystemProperty(named = DIR_PROPERTY, matches = ".+")
    void renderBothThemes() throws Exception {
        Path out = Path.of(System.getProperty(DIR_PROPERTY));
        String prefix = System.getProperty(DIR_PROPERTY + ".prefix", "task-8");
        Files.createDirectories(out);
        Path config = Files.createTempDirectory(out, "config-");

        FxToolkit.registerPrimaryStage();
        window = fx(() -> {
            MainWindow w = new MainWindow(new Stage(), new SessionStore(config),
                    new RecoveryStore(config.resolve("recovery")));
            w.getStage().setWidth(1280);
            w.getStage().setHeight(820);
            w.show();
            return w;
        });
        canvas = window.getCanvasView();
        doc = window.getWorkspace().getActiveDocument();
        try {
            settle();
            buildScene();
            for (Theme.Mode mode : new Theme.Mode[]{Theme.Mode.DARK, Theme.Mode.LIGHT}) {
                fx(() -> {
                    window.getTheme().setMode(mode);
                    return null;
                });
                Thread.sleep(1200);
                settle();
                WritableImage image = fx(() -> {
                    SnapshotParameters parameters = new SnapshotParameters();
                    parameters.setTransform(Transform.scale(SCALE, SCALE));
                    return window.getScene().getRoot().snapshot(parameters, null);
                });
                Path file = out.resolve(prefix + "-" + mode.name().toLowerCase() + ".png");
                Files.write(file, PngEncoder.encode(image));
                System.out.println("Wrote " + file + " zoom " + canvas.getZoom());
            }
        } finally {
            fx(() -> {
                window.shutdown();
                window.getStage().hide();
                return null;
            });
            try (var files = Files.walk(config)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** A card with a badge, a title, decorations and a slot; the badge is selected and turned. */
    private void buildScene() throws Exception {
        drag(ToolKind.RECTANGLE, 80, 90, 720, 510);
        recolor(0, "#1C232D");
        fx(() -> {
            doc.execute(Commands.setProperty(doc.getAnimation().layers().get(0).id(), "geometry.cornerRadius",
                    Animatable.of(18.0)));
            return null;
        });
        drag(ToolKind.ELLIPSE, 130, 190, 350, 410);
        fx(() -> {
            Layer badge = doc.getAnimation().layers().get(1);
            doc.execute(Commands.setProperty(badge.id(), "fill", Animatable.of(new Paint.LinearGradient(
                    new Vec2(0, 0), new Vec2(220, 220),
                    List.of(new Paint.Stop(0, Rgba.parse("#4C9AFF")), new Paint.Stop(1, Rgba.parse("#A56BFF")))))));
            return null;
        });
        drag(ToolKind.STAR, 190, 250, 290, 350);
        recolor(2, "#FFFFFF");
        drag(ToolKind.POLYGON, 600, 130, 660, 190);
        recolor(3, "#FFB454");
        drag(ToolKind.LINE, 400, 330, 660, 330);

        click(ToolKind.TEXT, 400, 262);
        settle();
        fx(() -> {
            TextField editor = (TextField) canvas.lookup("#" + TextTool.EDITOR_ID);
            editor.setText("Payment complete");
            Event.fireEvent(editor, new ActionEvent());
            return null;
        });

        drag(ToolKind.SLOT, 400, 370, 580, 426);
        settle();
        fx(() -> {
            TextField name = (TextField) canvas.lookup("#" + SlotTool.NAME_FIELD_ID);
            name.setText("button");
            Event.fireEvent(name, new ActionEvent());
            return null;
        });

        // select the badge and turn it with the rotate handle, then hover the title
        click(ToolKind.SELECT, 150, 300);
        settle();
        Vec2 handle = fx(() -> canvas.getGeometry().quad(doc.getSelection().layerIds().get(0)).orElseThrow()
                .rotateHandle(22));
        Vec2 centre = fx(() -> canvas.documentToView(new Vec2(240, 300)));
        double angle = Math.toRadians(-90 + 15);
        Vec2 target = new Vec2(centre.x() + 140 * Math.cos(angle), centre.y() + 140 * Math.sin(angle));
        mouse(MouseEvent.MOUSE_PRESSED, handle);
        mouse(MouseEvent.MOUSE_DRAGGED, target);
        mouse(MouseEvent.MOUSE_RELEASED, target);
        mouse(MouseEvent.MOUSE_MOVED, fx(() -> canvas.documentToView(new Vec2(470, 275))));
        fx(() -> {
            doc.setPlayhead(1.14);
            return null;
        });
        settle();
        animate();
    }

    /** Task 8: keyframes, a marker and an expanded layer, through the timeline and the inspector. */
    private void animate() throws Exception {
        String badge = doc.getAnimation().layers().get(1).id();
        fx(() -> {
            doc.getSelection().layerIds().setAll(badge);
            doc.setPlayhead(0.2);
            doc.execute(com.dlsc.animatorfx.editor.doc.Commands.addKeyframe(badge, "transform.position", 0.2));
            doc.execute(com.dlsc.animatorfx.editor.doc.Commands.setValueAt(badge, "transform.position", 1.2, new Vec2(300, 300), true));
            doc.execute(com.dlsc.animatorfx.editor.doc.Commands.setValueAt(badge, "transform.scale", 0.2, new Vec2(0.6, 0.6), true));
            doc.execute(com.dlsc.animatorfx.editor.doc.Commands.setValueAt(badge, "transform.scale", 0.8, new Vec2(1, 1), true));
            doc.execute(com.dlsc.animatorfx.editor.doc.Commands.setValueAt(badge, "opacity", 0.0, 0.0, true));
            doc.execute(com.dlsc.animatorfx.editor.doc.Commands.setValueAt(badge, "opacity", 0.5, 1.0, true));
            doc.getViewState().getExpandedLayerIds().add(badge);
            doc.execute(com.dlsc.animatorfx.editor.timeline.Markers.add(0.0));
            doc.execute(com.dlsc.animatorfx.editor.timeline.Markers.rename("marker1", "intro"));
            doc.execute(com.dlsc.animatorfx.editor.timeline.Markers.add(1.6));
            doc.execute(com.dlsc.animatorfx.editor.timeline.Markers.rename("marker1", "success"));
            doc.execute(com.dlsc.animatorfx.editor.timeline.Markers.setRange("Resize Marker", "success", 1.6, 2.8));
            doc.setPlayhead(1.14);
            return null;
        });
        settle();
        var timeline = window.getTimelineView();
        var tracks = timeline.getTrackPane();
        double x = fx(() -> tracks.timeToX(0.2));
        double y = fx(() -> tracks.rowCenter(tracks.rowOf(badge, "transform.position")));
        fx(() -> {
            Point2D scene = tracks.localToScene(x, y);
            for (EventType<MouseEvent> type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
                Event.fireEvent(tracks, new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                        MouseButton.PRIMARY, 1, false, false, false, false, type == MouseEvent.MOUSE_PRESSED, false,
                        false, false, false, false, null));
            }
            timeline.getKeyframeActions().setEasing(com.dlsc.animatorfx.model.Easing.presets().get("Ease Out"));
            return null;
        });
        settle();
    }

    private void recolor(int index, String hex) throws Exception {
        fx(() -> {
            ShapeLayer shape = (ShapeLayer) doc.getAnimation().layers().get(index);
            assert shape.geometry() instanceof Geometry;
            doc.execute(Commands.setProperty(shape.id(), "fill", Animatable.of(new Paint.Solid(Rgba.parse(hex)))));
            return null;
        });
    }

    private void drag(ToolKind tool, double fromX, double fromY, double toX, double toY) throws Exception {
        fx(() -> {
            canvas.setActiveTool(tool);
            return null;
        });
        Vec2 from = fx(() -> canvas.documentToView(new Vec2(fromX, fromY)));
        Vec2 to = fx(() -> canvas.documentToView(new Vec2(toX, toY)));
        mouse(MouseEvent.MOUSE_PRESSED, from);
        for (int i = 1; i <= 4; i++) {
            mouse(MouseEvent.MOUSE_DRAGGED, from.lerp(to, i / 4.0));
        }
        mouse(MouseEvent.MOUSE_RELEASED, to);
        settle();
    }

    private void click(ToolKind tool, double x, double y) throws Exception {
        fx(() -> {
            canvas.setActiveTool(tool);
            return null;
        });
        Vec2 at = fx(() -> canvas.documentToView(new Vec2(x, y)));
        mouse(MouseEvent.MOUSE_PRESSED, at);
        mouse(MouseEvent.MOUSE_RELEASED, at);
    }

    private void mouse(EventType<MouseEvent> type, Vec2 view) throws Exception {
        fx(() -> {
            Point2D scene = canvas.localToScene(view.x(), view.y());
            boolean down = type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_DRAGGED;
            Event.fireEvent(canvas, new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                    type == MouseEvent.MOUSE_MOVED ? MouseButton.NONE : MouseButton.PRIMARY, 1,
                    false, false, false, false, down, false, false, false, false, false, null));
            return null;
        });
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
