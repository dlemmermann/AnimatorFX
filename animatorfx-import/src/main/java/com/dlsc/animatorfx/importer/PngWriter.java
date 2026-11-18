package com.dlsc.animatorfx.importer;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Encodes an image as an 8-bit RGBA PNG, without java.desktop or javafx.swing.
 */
final class PngWriter {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngWriter() {
    }

    static byte[] encode(Image image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] argb = new int[width];
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(raw)) {
                byte[] row = new byte[width * 4];
                for (int y = 0; y < height; y++) {
                    image.getPixelReader().getPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), argb, 0, width);
                    for (int x = 0; x < width; x++) {
                        row[x * 4] = (byte) (argb[x] >> 16);
                        row[x * 4 + 1] = (byte) (argb[x] >> 8);
                        row[x * 4 + 2] = (byte) argb[x];
                        row[x * 4 + 3] = (byte) (argb[x] >>> 24);
                    }
                    // filter type 0: the row as it is
                    deflater.write(0);
                    deflater.write(row);
                }
            }
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream fields = new DataOutputStream(header);
            fields.writeInt(width);
            fields.writeInt(height);
            // 8 bits per channel, colour type 6 (RGBA), default compression, filter and interlace
            fields.write(new byte[]{8, 6, 0, 0, 0});

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(SIGNATURE);
            chunk(out, "IHDR", header.toByteArray());
            chunk(out, "IDAT", raw.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        DataOutputStream stream = new DataOutputStream(out);
        stream.writeInt(data.length);
        stream.write(name);
        stream.write(data);
        stream.writeInt((int) crc.getValue());
    }
}
