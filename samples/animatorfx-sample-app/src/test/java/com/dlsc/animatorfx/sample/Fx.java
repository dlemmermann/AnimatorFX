package com.dlsc.animatorfx.sample;

import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Headless toolkit helpers for the tests, which run on the test thread.
 */
final class Fx {

    private static boolean started;

    private Fx() {
    }

    static synchronized void start() throws Exception {
        if (!started) {
            FxToolkit.registerPrimaryStage();
            started = true;
        }
    }

    static <T> T call(Callable<T> callable) throws Exception {
        start();
        return WaitForAsyncUtils.asyncFx(callable).get(30, TimeUnit.SECONDS);
    }

    static void run(Runnable runnable) throws Exception {
        call(() -> {
            runnable.run();
            return null;
        });
    }

    /** The directory given with {@code -Danimatorfx.screenshots=DIR}, or {@code null}. */
    static Path screenshots() {
        String directory = System.getProperty("animatorfx.screenshots");
        return directory == null || directory.isBlank() ? null : Path.of(directory);
    }

    /** Call on the FX thread. */
    static WritableImage snapshot(Node node, Color background) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(background);
        return node.snapshot(parameters, null);
    }

    /** Writes an RGBA PNG without java.desktop or javafx.swing. */
    static void writePng(WritableImage image, Path file) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader pixels = image.getPixelReader();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(raw)) {
            byte[] row = new byte[1 + width * 4];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = pixels.getArgb(x, y);
                    row[1 + x * 4] = (byte) (argb >> 16);
                    row[2 + x * 4] = (byte) (argb >> 8);
                    row[3 + x * 4] = (byte) argb;
                    row[4 + x * 4] = (byte) (argb >>> 24);
                }
                deflater.write(row);
            }
        }
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(header);
        data.writeInt(width);
        data.writeInt(height);
        data.write(new byte[]{8, 6, 0, 0, 0});

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        chunk(png, "IHDR", header.toByteArray());
        chunk(png, "IDAT", raw.toByteArray());
        chunk(png, "IEND", new byte[0]);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, png.toByteArray());
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] content) throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(content);
        data.writeInt(content.length);
        data.write(name);
        data.write(content);
        data.writeInt((int) crc.getValue());
    }
}
