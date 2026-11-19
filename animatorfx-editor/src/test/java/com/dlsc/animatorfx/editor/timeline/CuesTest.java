package com.dlsc.animatorfx.editor.timeline;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.SoundCue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CuesTest {

    private static final Asset CHIME = new Asset("chime", Asset.Kind.AUDIO, "assets/chime.wav", "door chime.wav");
    private static final Asset BOOM = new Asset("boom", Asset.Kind.AUDIO, "assets/boom.mp3", "boom.mp3");
    private static final Asset PICTURE = new Asset("img", Asset.Kind.IMAGE, "assets/img.png", "img.png");

    private static Animation doc(Asset... assets) {
        return Animation.create("Test", 800, 600, 3).withFrameRate(10).withAssets(List.of(assets));
    }

    @Test
    void cueTimesSnapToFramesInsideTheDocument() {
        Animation animation = doc();

        assertEquals(1.2, Cues.snapped(animation, 1.234, false), 1e-12);
        assertEquals(1.234, Cues.snapped(animation, 1.234, true));
        assertEquals(0, Cues.snapped(animation, -0.4, false));
        assertEquals(3, Cues.snapped(animation, 3.27, false));
        assertEquals(3, Cues.snapped(animation, 9, true));
        assertEquals(2.5, Cues.snapped(animation.withFrameRate(0), 2.5, false));
    }

    @Test
    void aNewCueIsNamedAfterItsSoundAndSitsOnAFrame() {
        Animation before = doc(CHIME);

        Animation after = Cues.add("c", "chime", 1.26).apply(before);

        assertEquals(List.of(new SoundCue("c", "door chime", 1.3, "chime", 1, false)), after.cues());
    }

    @Test
    void theSoundOfANewCueIsTheOneUsedLast() {
        Animation plain = doc(PICTURE, CHIME, BOOM);
        assertEquals(Optional.of("boom"), Cues.defaultAsset(plain, null), "else the newest sound");
        assertEquals(Optional.of("chime"), Cues.defaultAsset(plain, "chime"));
        assertEquals(Optional.of("boom"), Cues.defaultAsset(plain, "gone"));

        Animation withCue = plain.withCues(List.of(SoundCue.create("x", 0, "chime")));
        assertEquals(Optional.of("chime"), Cues.defaultAsset(withCue, null), "the sound of the newest cue");

        Animation withLayer = plain.withLayers(List.<Layer>of(AudioLayer.create("l", "chime", 0, 1)));
        assertEquals(Optional.of("chime"), Cues.defaultAsset(withLayer, null), "the sound of an audio layer");

        assertEquals(Optional.empty(), Cues.defaultAsset(doc(PICTURE), null));
    }

    @Test
    void movingSeveralCuesKeepsThemTogetherInsideTheDocument() {
        Animation before = doc(CHIME).withCues(List.of(new SoundCue("a", "a", 0.5, "chime", 1, false),
                new SoundCue("b", "b", 2.5, "chime", 1, false), new SoundCue("c", "c", 1, "chime", 1, false)));

        Animation right = Cues.move(List.of("a", "b"), 2, false).apply(before);
        assertEquals(List.of(1.0, 3.0, 1.0), right.cues().stream().map(SoundCue::time).toList());

        Animation left = Cues.move(List.of("a", "b"), -2, false).apply(before);
        assertEquals(List.of(0.0, 2.0, 1.0), left.cues().stream().map(SoundCue::time).toList());

        Animation snapped = Cues.move(List.of("c"), 0.26, false).apply(before);
        assertEquals(1.3, snapped.cues().get(2).time(), 1e-12);
        assertEquals("Move Cues", Cues.move(List.of("a", "b"), 1, false).name());
        assertEquals("Move Cue", Cues.move(List.of("a"), 1, false).name());
    }
}
