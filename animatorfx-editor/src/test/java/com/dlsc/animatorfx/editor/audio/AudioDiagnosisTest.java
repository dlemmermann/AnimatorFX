package com.dlsc.animatorfx.editor.audio;

import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The hidden {@code --diagnose-audio} check, on the media stack of this machine. No sound: the
 * player is muted. Where media does not play (no audio device, no codecs) the two tests that need
 * it are skipped; what is always checked is that a failure is reported as one.
 */
class AudioDiagnosisTest extends ApplicationTest {

    @Test
    void aGeneratedSoundIsOpenedAndPlayedMuted() {
        AudioDiagnosis.Result result = AudioDiagnosis.probe(AudioDiagnosis.tone(), "wav", 3000);
        assumeTrue(result.ok(), "JavaFX media does not play in this environment: " + result.line());
        assertTrue(result.line().startsWith("audio: ok (PLAYING, 0.30 s"), result.line());
    }

    /** AIFF is among the formats the editor imports; this is the one place where one is played. */
    @Test
    void anAiffFileIsPlayedToo() {
        byte[] aiff = TestSounds.wav(TestSounds.sine(0.3, 22_050, 440, 0.1), 22_050, 1, 16, true);
        AudioDiagnosis.Result result = AudioDiagnosis.probe(aiff, "aiff", 3000);
        assumeTrue(result.ok(), "JavaFX media does not play AIFF in this environment: " + result.line());
        assertTrue(result.line().startsWith("audio: ok (PLAYING, 0.30 s"), result.line());
    }

    @Test
    void aFileThatIsNoSoundIsReportedAsAFailure() {
        AudioDiagnosis.Result result = AudioDiagnosis.probe("no sound at all".getBytes(StandardCharsets.US_ASCII),
                "wav", 1500);
        assertEquals(false, result.ok());
        assertTrue(result.line().startsWith("audio: failed: "), result.line());
    }

    @Test
    void theSwitchIsRecognisedAmongTheArguments() {
        assertTrue(AudioDiagnosis.requested(new String[]{"/some/file.afx", "--diagnose-audio"}));
        assertEquals(false, AudioDiagnosis.requested(new String[]{"/some/file.afx"}));
    }
}
