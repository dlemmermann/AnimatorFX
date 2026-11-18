package com.dlsc.animatorfx.importer;

import java.util.Base64;

/**
 * An embedded PNG or JPEG image.
 *
 * @param extension {@code png} or {@code jpg}
 * @param width     the pixel width, or 0 if the header could not be read
 */
record ImageData(byte[] bytes, String extension, int width, int height) {

    /**
     * Decodes a base64 {@code data:} URI holding a PNG or JPEG image.
     *
     * @return the image, or {@code null} if the text is anything else
     */
    static ImageData fromDataUri(String uri) {
        if (uri == null) {
            return null;
        }
        String trimmed = uri.trim();
        int comma = trimmed.indexOf(',');
        if (!trimmed.regionMatches(true, 0, "data:", 0, 5) || comma < 0
                || !trimmed.substring(0, comma).toLowerCase().endsWith(";base64")) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(trimmed.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return of(bytes);
    }

    /**
     * @return the image, or {@code null} if the bytes are neither PNG nor JPEG
     */
    static ImageData of(byte[] bytes) {
        if (bytes.length >= 24 && u(bytes, 0) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return new ImageData(bytes, "png", int32(bytes, 16), int32(bytes, 20));
        }
        if (bytes.length >= 4 && u(bytes, 0) == 0xFF && u(bytes, 1) == 0xD8) {
            int[] size = jpegSize(bytes);
            return new ImageData(bytes, "jpg", size[0], size[1]);
        }
        return null;
    }

    /** Walks the segments up to the start-of-frame header, which holds the size. */
    private static int[] jpegSize(byte[] bytes) {
        int pos = 2;
        while (pos + 9 < bytes.length && u(bytes, pos) == 0xFF) {
            int marker = u(bytes, pos + 1);
            if (marker == 0xFF) {
                pos++;
                continue;
            }
            boolean startOfFrame = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8
                    && marker != 0xCC;
            if (startOfFrame) {
                return new int[]{u(bytes, pos + 7) << 8 | u(bytes, pos + 8), u(bytes, pos + 5) << 8 | u(bytes, pos + 6)};
            }
            pos += 2 + (u(bytes, pos + 2) << 8 | u(bytes, pos + 3));
        }
        return new int[]{0, 0};
    }

    private static int u(byte[] bytes, int index) {
        return bytes[index] & 0xFF;
    }

    private static int int32(byte[] bytes, int index) {
        return u(bytes, index) << 24 | u(bytes, index + 1) << 16 | u(bytes, index + 2) << 8 | u(bytes, index + 3);
    }
}
