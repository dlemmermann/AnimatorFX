package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.canvas.tools.SlotTool;
import com.dlsc.animatorfx.editor.canvas.tools.TextTool;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.inspector.EasingEditor;
import com.dlsc.animatorfx.editor.inspector.InspectorView;
import com.dlsc.animatorfx.editor.inspector.fields.PaintField;
import com.dlsc.animatorfx.editor.inspector.sections.FillSection;
import com.dlsc.animatorfx.editor.inspector.sections.GeometrySection;
import com.dlsc.animatorfx.editor.inspector.sections.TransformSection;
import com.dlsc.animatorfx.editor.session.RecoveryStore;
import com.dlsc.animatorfx.editor.session.SessionStore;
import com.dlsc.animatorfx.editor.timeline.LayerTreeColumn;
import com.dlsc.animatorfx.editor.timeline.MarkerLane;
import com.dlsc.animatorfx.editor.timeline.TimelineView;
import com.dlsc.animatorfx.editor.timeline.TrackPane;
import com.dlsc.animatorfx.model.Vec2;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
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

/**
 * Not a test: builds a small animation in a real window the way a user would — shapes with the
 * canvas tools, colours, gradient and corner radius in the inspector, keyframes with the
 * keyframe diamonds, auto-key and the timeline, markers in the marker lane, an eased segment in
 * the easing editor — and renders the main window in both themes to PNG files, for looking at
 * the result. It is not picked up by a normal build. Run it without the Monocle flags:
 *
 * <pre>
 * mvn -pl animatorfx-editor test -Dtest=ScreenshotHarness -Dsurefire.failIfNoSpecifiedTests=false \
 *   -Dsurefire.jvm.args="--add-reads org.kordamp.ikonli.core=ALL-UNNAMED --enable-native-access=javafx.graphics
 *                        -Danimatorfx.screenshots=DIR [-Danimatorfx.screenshots.prefix=task-8]"
 * </pre>
 *
 * which writes {@code DIR/task-8-dark.png} and {@code DIR/task-8-light.png}.
 */
class ScreenshotHarness {

    private static final String DIR_PROPERTY = "animatorfx.screenshots";
    private static final double SCALE = 2;

    private MainWindow window;
    private CanvasView canvas;
    private TimelineView timeline;
    private InspectorView inspector;
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
            w.getStage().setWidth(1360);
            w.getStage().setHeight(960);
            w.show();
            return w;
        });
        canvas = window.getCanvasView();
        timeline = window.getTimelineView();
        inspector = window.getSidePanel().getInspector();
        doc = window.getWorkspace().getActiveDocument();
        try {
            settle();
            fx(() -> {
                window.applyLayout(new com.dlsc.animatorfx.editor.session.SessionState.Layout(0.775, 0.615, false, false,
                        SidePanel.INSPECTOR));
                return null;
            });
            settle();
            buildScene();
            animate();
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
            if (Boolean.getBoolean(DIR_PROPERTY + ".extra")) {
                extras(out);
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

    /** A card with a badge, a title, decorations and a slot, styled in the inspector. */
    private void buildScene() throws Exception {
        drag(ToolKind.RECTANGLE, 80, 90, 720, 510);
        fillHex("#1C232D");
        type(section(GeometrySection.class).cornerRadius().field(), "18");

        drag(ToolKind.ELLIPSE, 130, 190, 350, 410);
        PaintField fill = section(FillSection.class).fill().field();
        fx(() -> {
            ((ComboBox<String>) fill.getTypeBox()).setValue(PaintField.LINEAR);
            return null;
        });
        settle();
        type(fill.getStopColorField().getHexField(), "#4C9AFF");
        Point2D lastStop = fx(() -> new Point2D(fill.getStopBar().offsetToX(1), 18));
        mouse(fill.getStopBar(), MouseEvent.MOUSE_PRESSED, lastStop.getX(), lastStop.getY(), 1);
        mouse(fill.getStopBar(), MouseEvent.MOUSE_RELEASED, lastStop.getX(), lastStop.getY(), 1);
        type(fill.getStopColorField().getHexField(), "#A56BFF");
        type(fill.getStartField().getFirst(), "0");
        type(fill.getStartField().getSecond(), "0");
        type(fill.getEndField().getFirst(), "220");
        type(fill.getEndField().getSecond(), "220");

        drag(ToolKind.STAR, 190, 250, 290, 350);
        fillHex("#FFFFFF");
        drag(ToolKind.POLYGON, 600, 130, 660, 190);
        fillHex("#FFB454");
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
    }

    /** The badge slides, grows and fades in; two markers; the first position segment is eased. */
    private void animate() throws Exception {
        click(ToolKind.SELECT, 150, 300);
        settle();
        String badge = fx(() -> doc.getSelection().layerIds().get(0));
        TransformSection transform = section(TransformSection.class);

        // the end pose first, with the diamond; the rest with auto-key
        scrub(1.2);
        fire(transform.position().toggle());
        fire((ButtonBase) window.getScene().lookup("#" + LayerTreeColumn.AUTO_KEY_ID));
        scrub(0.8);
        fire(transform.scale().toggle());
        scrub(0.5);
        fire(transform.opacity().toggle());
        scrub(0.2);
        type(transform.position().field().getFirst(), "170");
        type(transform.scale().field().getFirst(), "60");
        scrub(0.0);
        type(transform.opacity().field(), "0");

        // markers: one with the button, renamed in the lane; one by double-click
        scrub(0.0);
        fire((ButtonBase) window.getScene().lookup("#" + LayerTreeColumn.ADD_MARKER_ID));
        MarkerLane lane = timeline.getMarkerLane();
        double x = fx(() -> timeline.getScale().toX(0.2));
        mouse(lane, MouseEvent.MOUSE_PRESSED, x, 8, 2);
        mouse(lane, MouseEvent.MOUSE_RELEASED, x, 8, 2);
        settle();
        type((TextField) lane.lookup("#" + MarkerLane.RENAME_FIELD_ID), "intro");
        double second = fx(() -> timeline.getScale().toX(1.6));
        mouse(lane, MouseEvent.MOUSE_PRESSED, second, 8, 2);
        mouse(lane, MouseEvent.MOUSE_RELEASED, second, 8, 2);
        settle();
        double end = fx(() -> timeline.getScale().toX(2.1));
        double target = fx(() -> timeline.getScale().toX(2.8));
        mouse(lane, MouseEvent.MOUSE_PRESSED, end - 1, 8, 1);
        mouse(lane, MouseEvent.MOUSE_DRAGGED, target, 8, 1);
        mouse(lane, MouseEvent.MOUSE_RELEASED, target, 8, 1);
        mouse(lane, MouseEvent.MOUSE_PRESSED, second + 20, 8, 2);
        mouse(lane, MouseEvent.MOUSE_RELEASED, second + 20, 8, 2);
        settle();
        type((TextField) lane.lookup("#" + MarkerLane.RENAME_FIELD_ID), "success");

        // expand the badge, select its first position keyframe and ease the segment
        Node cell = fx(() -> timeline.getLayerColumn().layerCell(badge).orElseThrow());
        fire((ButtonBase) cell.lookup(".disclosure"));
        TrackPane tracks = timeline.getTrackPane();
        Point2D key = fx(() -> new Point2D(tracks.timeToX(0.2),
                tracks.rowCenter(tracks.rowOf(badge, "transform.position"))));
        mouse(tracks, MouseEvent.MOUSE_PRESSED, key.getX(), key.getY(), 1);
        mouse(tracks, MouseEvent.MOUSE_RELEASED, key.getX(), key.getY(), 1);
        settle();
        fx(() -> {
            ((ComboBox<String>) window.getScene().lookup("#" + EasingEditor.PRESET_ID)).setValue("Ease Out");
            return null;
        });
        scrub(1.14);
        // make room for the fill of the badge under the easing editor
        for (Class<? extends com.dlsc.animatorfx.editor.inspector.SectionBase> type : java.util.List.of(
                com.dlsc.animatorfx.editor.inspector.sections.LayerSection.class, TransformSection.class,
                GeometrySection.class)) {
            fx(() -> {
                section(type).setCollapsed(true);
                return null;
            });
        }
        fx(() -> {
            canvas.requestFocus();
            return null;
        });
        settle();
    }

    /**
     * More pictures for looking at parts that the main ones do not show: the Document and Assets
     * tabs, a text layer and a slot in the inspector, and the colour picker.
     */
    private void extras(Path out) throws Exception {
        Node side = window.getSidePanel();
        fx(() -> {
            for (var s : inspector.getSections()) {
                if (s instanceof com.dlsc.animatorfx.editor.inspector.SectionBase base) {
                    base.setCollapsed(false);
                }
            }
            doc.getSelection().keyframes().clear();
            return null;
        });
        settle();
        snapshot(side, out.resolve("extra-inspector-shape.png"));

        fx(() -> {
            doc.getSelection().layerIds().setAll(doc.getAnimation().layers().get(5).id());
            return null;
        });
        settle();
        fire((ButtonBase) window.getScene().lookup("#" + com.dlsc.animatorfx.editor.inspector.sections.StrokeSection.ADD_ID));
        snapshot(side, out.resolve("extra-inspector-text.png"));
        fx(() -> {
            doc.getSelection().layerIds().setAll(doc.getAnimation().layers().get(6).id());
            return null;
        });
        settle();
        snapshot(side, out.resolve("extra-inspector-slot.png"));
        fx(() -> {
            doc.getSelection().layerIds().setAll(doc.getAnimation().layers().get(3).id(),
                    doc.getAnimation().layers().get(2).id());
            return null;
        });
        settle();
        snapshot(side, out.resolve("extra-inspector-multi.png"));
        fx(() -> {
            doc.getSelection().layerIds().clear();
            return null;
        });
        settle();
        snapshot(side, out.resolve("extra-inspector-empty.png"));

        Path image = out.resolve("extra-asset.png");
        Files.write(image, PngEncoder.encode(fx(() -> {
            SnapshotParameters parameters = new SnapshotParameters();
            return canvas.snapshot(parameters, null);
        })));
        fx(() -> {
            window.getSidePanel().setActiveTab(SidePanel.ASSETS);
            java.util.List<Path> files = new java.util.ArrayList<>(java.util.List.of(image));
            Path font = Path.of("/System/Library/Fonts/Supplemental/Georgia.ttf");
            if (Files.exists(font)) {
                files.add(font);
            }
            window.getSidePanel().getAssets().addFiles(files);
            window.getSidePanel().getAssets().place(doc.getAnimation().assets().get(0).id());
            window.getSidePanel().getAssets().remove(doc.getAnimation().assets().get(0).id());
            return null;
        });
        settle();
        snapshot(side, out.resolve("extra-assets.png"));
        fx(() -> {
            doc.undo();
            window.getSidePanel().setActiveTab(SidePanel.DOCUMENT);
            return null;
        });
        settle();
        snapshot(side, out.resolve("extra-document.png"));

        fx(() -> {
            window.getSidePanel().setActiveTab(SidePanel.INSPECTOR);
            doc.getSelection().layerIds().setAll(doc.getAnimation().layers().get(3).id());
            return null;
        });
        settle();
        var color = section(FillSection.class).fill().field().getSolidField();
        fx(() -> {
            color.showPicker();
            return null;
        });
        settle();
        snapshot(fx(() -> color.getPopup().getScene().getRoot()), out.resolve("extra-color-popup.png"));
        fx(() -> {
            color.getPopup().hide();
            return null;
        });
    }

    private void snapshot(Node node, Path file) throws Exception {
        WritableImage image = fx(() -> {
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setTransform(Transform.scale(SCALE, SCALE));
            return node.snapshot(parameters, null);
        });
        Files.write(file, PngEncoder.encode(image));
    }

    private <S extends com.dlsc.animatorfx.editor.inspector.InspectorSection> S section(Class<S> type) {
        return inspector.section(type).orElseThrow();
    }

    private void fillHex(String hex) throws Exception {
        type(section(FillSection.class).fill().field().getSolidField().getHexField(), hex);
    }

    private void type(TextField field, String text) throws Exception {
        fx(() -> {
            field.requestFocus();
            field.setText(text);
            Event.fireEvent(field, new ActionEvent());
            return null;
        });
        settle();
    }

    private void fire(ButtonBase button) throws Exception {
        fx(() -> {
            button.fire();
            return null;
        });
        settle();
    }

    /** Drags the playhead in the ruler to a time. */
    private void scrub(double time) throws Exception {
        Node ruler = timeline.getRuler();
        double x = fx(() -> timeline.getScale().toX(time));
        mouse(ruler, MouseEvent.MOUSE_PRESSED, x, 8, 1);
        mouse(ruler, MouseEvent.MOUSE_RELEASED, x, 8, 1);
        settle();
    }

    private void drag(ToolKind tool, double fromX, double fromY, double toX, double toY) throws Exception {
        fx(() -> {
            canvas.setActiveTool(tool);
            return null;
        });
        Vec2 from = fx(() -> canvas.documentToView(new Vec2(fromX, fromY)));
        Vec2 to = fx(() -> canvas.documentToView(new Vec2(toX, toY)));
        mouse(canvas, MouseEvent.MOUSE_PRESSED, from.x(), from.y(), 1);
        for (int i = 1; i <= 4; i++) {
            Vec2 at = from.lerp(to, i / 4.0);
            mouse(canvas, MouseEvent.MOUSE_DRAGGED, at.x(), at.y(), 1);
        }
        mouse(canvas, MouseEvent.MOUSE_RELEASED, to.x(), to.y(), 1);
        settle();
    }

    private void click(ToolKind tool, double x, double y) throws Exception {
        fx(() -> {
            canvas.setActiveTool(tool);
            return null;
        });
        Vec2 at = fx(() -> canvas.documentToView(new Vec2(x, y)));
        mouse(canvas, MouseEvent.MOUSE_PRESSED, at.x(), at.y(), 1);
        mouse(canvas, MouseEvent.MOUSE_RELEASED, at.x(), at.y(), 1);
    }

    private void mouse(Node node, EventType<MouseEvent> type, double x, double y, int clicks) throws Exception {
        fx(() -> {
            Point2D scene = node.localToScene(x, y);
            boolean down = type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_DRAGGED;
            Event.fireEvent(node, new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                    type == MouseEvent.MOUSE_MOVED ? MouseButton.NONE : MouseButton.PRIMARY, clicks,
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
