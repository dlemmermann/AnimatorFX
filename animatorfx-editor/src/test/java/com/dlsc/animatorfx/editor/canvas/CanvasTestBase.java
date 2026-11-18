package com.dlsc.animatorfx.editor.canvas;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.session.RecoveryStore;
import com.dlsc.animatorfx.editor.session.SessionStore;
import com.dlsc.animatorfx.editor.ui.Dialogs;
import com.dlsc.animatorfx.editor.ui.MainWindow;
import com.dlsc.animatorfx.editor.ui.ToolKind;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;

/**
 * A main window under the headless toolkit with the canvas at 100 %, and helpers that drive the
 * canvas with mouse and key events at document coordinates.
 */
abstract class CanvasTestBase {

    private static final class NoDialogs implements Dialogs {

        @Override
        public Optional<Path> chooseOpenFile(Window owner) {
            return Optional.empty();
        }

        @Override
        public Optional<Path> chooseSaveFile(Window owner, String suggestedName) {
            return Optional.empty();
        }

        @Override
        public CompletableFuture<CloseChoice> confirmClose(String documentTitle) {
            return CompletableFuture.completedFuture(CloseChoice.DISCARD);
        }

        @Override
        public CompletableFuture<Boolean> confirmRevert(String documentTitle) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public void showAbout() {
        }

        @Override
        public void showWarnings(String title, List<String> lines) {
        }
    }

    /** Modifier keys of a pointer event. */
    record Keys(boolean shift, boolean control, boolean alt) {

        static final Keys NONE = new Keys(false, false, false);
        static final Keys SHIFT = new Keys(true, false, false);
        static final Keys CONTROL = new Keys(false, true, false);
        static final Keys ALT = new Keys(false, false, true);
    }

    @TempDir
    Path dir;

    MainWindow window;
    CanvasView canvas;
    EditorDocument doc;

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    @BeforeEach
    void openWindow() {
        window = fx(() -> {
            MainWindow w = new MainWindow(new Stage(), new SessionStore(dir.resolve("config")),
                    new RecoveryStore(dir.resolve("config").resolve("recovery")), new NoDialogs());
            // the headless screen is 1280 x 800
            w.getStage().setWidth(1240);
            w.getStage().setHeight(780);
            w.show();
            return w;
        });
        WaitForAsyncUtils.waitForFxEvents();
        canvas = window.getCanvasView();
        doc = window.getWorkspace().getActiveDocument();
        canvas.getActions().setClipboard(new LayerClipboard());
        run(() -> {
            doc.getViewState().setCanvasZoom(1);
            doc.getViewState().setCanvasPanX(0);
            doc.getViewState().setCanvasPanY(0);
        });
        layout();
    }

    @AfterEach
    void closeWindow() {
        run(() -> {
            window.shutdown();
            window.getStage().hide();
        });
    }

    static <T> T fx(Callable<T> action) {
        try {
            return WaitForAsyncUtils.asyncFx(action).get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static void run(Runnable action) {
        fx(() -> {
            action.run();
            return null;
        });
    }

    /** Runs CSS and layout, which also refreshes the overlay. */
    void layout() {
        run(() -> {
            window.getScene().getRoot().applyCss();
            window.getScene().getRoot().layout();
        });
    }

    // ------------------------------------------------------------------ document helpers

    static ShapeLayer rect(String id, double x, double y, double width, double height) {
        LayerBase base = LayerBase.create(id, 3).withId(id).withTransform(Transform.IDENTITY
                .withAnchor(Animatable.of(new Vec2(width / 2, height / 2)))
                .withPosition(Animatable.of(new Vec2(x + width / 2, y + height / 2))));
        return new ShapeLayer(base, new Geometry.Rect(Animatable.of(new Vec2(width, height)), Animatable.of(0.0)),
                Animatable.of(new Paint.Solid(Rgba.WHITE)), null, null);
    }

    /** Adds layers on top, bottom first, and lays out. */
    void add(Layer... layers) {
        run(() -> {
            for (Layer layer : layers) {
                doc.execute(Commands.addLayer(null, doc.getAnimation().layers().size(), layer));
            }
        });
        layout();
    }

    Layer layer(String id) {
        return Layers.find(doc.getAnimation(), id).orElseThrow();
    }

    List<Layer> layers() {
        return doc.getAnimation().layers();
    }

    Vec2 position(String id) {
        return layer(id).base().transform().position().at(doc.getPlayhead());
    }

    List<String> selection() {
        return List.copyOf(doc.getSelection().layerIds());
    }

    void select(String... ids) {
        run(() -> doc.getSelection().layerIds().setAll(ids));
        layout();
    }

    void tool(ToolKind kind) {
        run(() -> canvas.setActiveTool(kind));
    }

    // ------------------------------------------------------------------ input

    private void mouse(EventType<MouseEvent> type, Vec2 view, Keys keys, int clicks) {
        run(() -> {
            Point2D scene = canvas.localToScene(view.x(), view.y());
            boolean down = type != MouseEvent.MOUSE_RELEASED && type != MouseEvent.MOUSE_MOVED;
            Event.fireEvent(canvas, new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                    type == MouseEvent.MOUSE_MOVED ? MouseButton.NONE : MouseButton.PRIMARY, clicks,
                    keys.shift(), keys.control(), keys.alt(), false, down, false, false, false, false, false, null));
        });
    }

    Vec2 view(double docX, double docY) {
        return fx(() -> canvas.documentToView(new Vec2(docX, docY)));
    }

    void pressView(Vec2 view, Keys keys) {
        mouse(MouseEvent.MOUSE_PRESSED, view, keys, 1);
    }

    void dragView(Vec2 view, Keys keys) {
        mouse(MouseEvent.MOUSE_DRAGGED, view, keys, 1);
    }

    void releaseView(Vec2 view, Keys keys) {
        mouse(MouseEvent.MOUSE_RELEASED, view, keys, 1);
        layout();
    }

    void press(double docX, double docY) {
        pressView(view(docX, docY), Keys.NONE);
    }

    void drag(double docX, double docY) {
        dragView(view(docX, docY), Keys.NONE);
    }

    void release(double docX, double docY) {
        releaseView(view(docX, docY), Keys.NONE);
    }

    void move(double docX, double docY) {
        mouse(MouseEvent.MOUSE_MOVED, view(docX, docY), Keys.NONE, 0);
        layout();
    }

    /** Press, drag in three steps and release, all in document coordinates. */
    void dragFromTo(double fromX, double fromY, double toX, double toY, Keys keys) {
        pressView(view(fromX, fromY), keys);
        for (int i = 1; i <= 3; i++) {
            dragView(view(fromX + (toX - fromX) * i / 3, fromY + (toY - fromY) * i / 3), keys);
        }
        releaseView(view(toX, toY), keys);
    }

    void dragFromTo(double fromX, double fromY, double toX, double toY) {
        dragFromTo(fromX, fromY, toX, toY, Keys.NONE);
    }

    void click(double docX, double docY, Keys keys) {
        pressView(view(docX, docY), keys);
        releaseView(view(docX, docY), keys);
    }

    void click(double docX, double docY) {
        click(docX, docY, Keys.NONE);
    }

    void doubleClick(double docX, double docY) {
        click(docX, docY);
        mouse(MouseEvent.MOUSE_PRESSED, view(docX, docY), Keys.NONE, 2);
        mouse(MouseEvent.MOUSE_RELEASED, view(docX, docY), Keys.NONE, 2);
        layout();
    }

    void key(KeyCode code, boolean shift) {
        run(() -> {
            javafx.scene.Node target = window.getScene().getFocusOwner() != null
                    ? window.getScene().getFocusOwner() : canvas;
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false));
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, shift, false, false, false));
        });
        layout();
    }

    void key(KeyCode code) {
        key(code, false);
    }
}
