package com.dlsc.animatorfx.editor.ui;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * A minimal PNG encoder (8-bit RGBA) for tests, so they need neither java.desktop nor javafx.swing.
 */
public final class PngEncoder {

    /** Gives the colour of a pixel as 0xAARRGGBB. */
    public interface Pixels {
        int argb(int x, int y);
    }

    private PngEncoder() {
    }

    public static byte[] encode(WritableImage image) {
        PixelReader reader = image.getPixelReader();
        int width = (int) image.getWidth();
        int[] row = new int[width];
        int[] cached = {-1};
        return encode(width, (int) image.getHeight(), (x, y) -> {
            if (cached[0] != y) {
                reader.getPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), row, 0, width);
                cached[0] = y;
            }
            return row[x];
        });
    }

    /** An image of one colour, given as 0xAARRGGBB. */
    public static byte[] solid(int width, int height, int argb) {
        return encode(width, height, (x, y) -> argb);
    }

    public static byte[] encode(int width, int height, Pixels pixels) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(raw)) {
                byte[] row = new byte[width * 4];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = pixels.argb(x, y);
                        row[x * 4] = (byte) (argb >> 16);
                        row[x * 4 + 1] = (byte) (argb >> 8);
                        row[x * 4 + 2] = (byte) argb;
                        row[x * 4 + 3] = (byte) (argb >>> 24);
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
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void chunk(DataOutputStream out, String type, byte[] content) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(content);
        out.writeInt(content.length);
        out.write(name);
        out.write(content);
        out.writeInt((int) crc.getValue());
    }
}
