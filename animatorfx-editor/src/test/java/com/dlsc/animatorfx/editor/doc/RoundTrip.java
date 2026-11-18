package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Writes a document as the editor saves it and reads it back, as opening the file would. */
public final class RoundTrip {

    private RoundTrip() {
    }

    public static Animation of(Animation animation) {
        return of(new AnimationFile(animation, Map.of())).animation();
    }

    public static AnimationFile of(AnimationFile file) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            AnimationIO.write(file, out);
            AnimationFile back = AnimationIO.read(new ByteArrayInputStream(out.toByteArray()));
            assertEquals(file.animation(), back.animation());
            return back;
        } catch (IOException e) {
            throw new AssertionError("The document cannot be read back: " + e.getMessage(), e);
        }
    }
}
