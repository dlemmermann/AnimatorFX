package com.dlsc.animatorfx.editor.audio;

import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hidden {@code --diagnose-audio} check, on the media stack of this machine. No sound: the player is muted. */
class AudioDiagnosisTest extends ApplicationTest {

    @Test
    void aGeneratedSoundIsOpenedAndPlayedMuted() {
        AudioDiagnosis.Result result = AudioDiagnosis.probe(AudioDiagnosis.tone(), "wav", 3000);
        assertTrue(result.ok(), result.line());
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
