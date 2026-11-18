package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.session.RecoveryStore;
import com.dlsc.animatorfx.editor.session.SessionStore;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Not a test: renders the main window in both themes to PNG files, for looking at the result. It
 * is not picked up by a normal build. Run it in a real window (no Monocle flags) with
 *
 * <pre>
 * mvn -pl animatorfx-editor test -Dtest=ScreenshotHarness -Dsurefire.failIfNoSpecifiedTests=false \
 *   -Dsurefire.jvm.args="--add-reads org.kordamp.ikonli.core=ALL-UNNAMED --enable-native-access=javafx.graphics
 *                        -Danimatorfx.screenshots=DIR [-Danimatorfx.screenshots.document=FILE.afx]"
 * </pre>
 *
 * which writes {@code DIR/task-6-dark.png} and {@code DIR/task-6-light.png}.
 */
class ScreenshotHarness {

    private static final String DIR_PROPERTY = "animatorfx.screenshots";
    private static final double SCALE = 2;

    @Test
    @EnabledIfSystemProperty(named = DIR_PROPERTY, matches = ".+")
    void renderBothThemes() throws Exception {
        Path out = Path.of(System.getProperty(DIR_PROPERTY));
        Files.createDirectories(out);
        Path config = Files.createTempDirectory(out, "config-");
        String document = System.getProperty(DIR_PROPERTY + ".document");

        FxToolkit.registerPrimaryStage();
        MainWindow window = WaitForAsyncUtils.asyncFx(() -> {
            MainWindow w = new MainWindow(new Stage(), new SessionStore(config),
                    new RecoveryStore(config.resolve("recovery")));
            if (document != null) {
                w.getFileActions().open(Path.of(document));
            }
            EditorDocument doc = w.getWorkspace().getActiveDocument();
            doc.setPlayhead(1.14);
            Layers.flatten(doc.getAnimation()).stream().map(Layer::id).skip(1).findFirst()
                    .ifPresent(doc.getSelection().layerIds()::add);
            w.show();
            return w;
        }).get();
        try {
            for (Theme.Mode mode : new Theme.Mode[]{Theme.Mode.DARK, Theme.Mode.LIGHT}) {
                WaitForAsyncUtils.asyncFx(() -> window.getTheme().setMode(mode)).get();
                Thread.sleep(1200);
                WaitForAsyncUtils.waitForFxEvents();
                WritableImage image = WaitForAsyncUtils.asyncFx(() -> {
                    SnapshotParameters parameters = new SnapshotParameters();
                    parameters.setTransform(Transform.scale(SCALE, SCALE));
                    return window.getScene().getRoot().snapshot(parameters, null);
                }).get();
                Path file = out.resolve("task-6-" + mode.name().toLowerCase() + ".png");
                Files.write(file, png(image));
                System.out.println("Wrote " + file + " layout " + WaitForAsyncUtils.asyncFx(window::getLayoutState).get());
            }
        } finally {
            WaitForAsyncUtils.asyncFx(() -> {
                window.shutdown();
                window.getStage().hide();
                return null;
            }).get();
            try (var files = Files.walk(config)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** A minimal PNG encoder (8-bit RGBA), so the harness needs neither java.desktop nor javafx.swing. */
    private static byte[] png(WritableImage image) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        byte[] row = new byte[width * 4];
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(raw)) {
            for (int y = 0; y < height; y++) {
                image.getPixelReader().getPixels(0, y, width, 1, PixelFormat.getByteBgraInstance(), row, 0, width * 4);
                for (int i = 0; i < row.length; i += 4) {
                    byte blue = row[i];
                    row[i] = row[i + 2];
                    row[i + 2] = blue;
                }
                deflater.write(0);
                deflater.write(row);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(header);
        h.writeInt(width);
        h.writeInt(height);
        h.write(new byte[]{8, 6, 0, 0, 0});
        chunk(data, "IHDR", header.toByteArray());
        chunk(data, "IDAT", raw.toByteArray());
        chunk(data, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(DataOutputStream out, String type, byte[] content) throws IOException {
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(content);
        out.writeInt(content.length);
        out.write(name);
        out.write(content);
        out.writeInt((int) crc.getValue());
    }
}
