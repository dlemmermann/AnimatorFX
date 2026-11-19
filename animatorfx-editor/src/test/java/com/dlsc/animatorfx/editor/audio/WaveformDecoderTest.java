package com.dlsc.animatorfx.editor.audio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.UnsupportedAudioFileException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveformDecoderTest {

    private static Waveform decode(byte[] file) throws Exception {
        return WaveformDecoder.decode(new ByteArrayInputStream(file));
    }

    @Test
    void silenceHasNoPeaks() throws Exception {
        Waveform waveform = decode(TestSounds.wav(new double[4000], 8000, 1, 16, false));

        assertEquals(0.5, waveform.duration(), 1e-9);
        assertEquals(100, waveform.peaks().length);
        for (float peak : waveform.peaks()) {
            assertEquals(0, peak);
        }
    }

    @Test
    void aFullScaleSineReachesOneInEveryInterval() throws Exception {
        Waveform waveform = decode(TestSounds.wav(TestSounds.sine(1, 8000, 440, 1), 8000, 1, 16, false));

        assertEquals(1, waveform.duration(), 1e-9);
        assertEquals(Waveform.PEAKS_PER_SECOND, waveform.peaks().length);
        for (float peak : waveform.peaks()) {
            assertEquals(1, peak, 0.02);
        }
    }

    @Test
    void theLoudestChannelCounts() throws Exception {
        double[] left = TestSounds.sine(0.5, 8000, 440, 0.25);
        double[] right = TestSounds.sine(0.5, 8000, 440, 0.75);
        double[] stereo = new double[left.length * 2];
        double[] three = new double[left.length * 3];
        for (int i = 0; i < left.length; i++) {
            stereo[2 * i] = left[i];
            stereo[2 * i + 1] = right[i];
            three[3 * i + 2] = right[i];
        }

        for (Waveform waveform : new Waveform[]{decode(TestSounds.wav(stereo, 8000, 2, 16, false)),
                decode(TestSounds.wav(three, 8000, 3, 16, false))}) {
            assertEquals(0.5, waveform.duration(), 1e-9);
            assertEquals(0.75, waveform.peak(0, 0.5), 0.02);
        }
    }

    @Test
    void sampleSizesAndByteOrdersGiveTheSamePeaks() throws Exception {
        double[] samples = TestSounds.sine(0.25, 8000, 440, 0.5);
        for (int bits : new int[]{8, 16, 24, 32}) {
            assertEquals(0.5, decode(TestSounds.wav(samples, 8000, 1, bits, false)).peak(0, 0.25), 0.02, bits + " bit WAV");
            assertEquals(0.5, decode(TestSounds.wav(samples, 8000, 1, bits, true)).peak(0, 0.25), 0.02, bits + " bit AIFF");
        }
    }

    @Test
    void floatingPointWavIsRead() throws Exception {
        double[] samples = TestSounds.sine(0.25, 8000, 440, 0.5);
        ByteBuffer file = ByteBuffer.allocate(58 + samples.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        file.put("RIFF".getBytes()).putInt(50 + samples.length * 4).put("WAVE".getBytes());
        file.put("fmt ".getBytes()).putInt(18).putShort((short) 3).putShort((short) 1).putInt(8000).putInt(32000)
                .putShort((short) 4).putShort((short) 32).putShort((short) 0);
        file.put("fact".getBytes()).putInt(4).putInt(samples.length);
        file.put("data".getBytes()).putInt(samples.length * 4);
        for (double sample : samples) {
            file.putFloat((float) sample);
        }

        Waveform waveform = decode(file.array());

        assertEquals(0.25, waveform.duration(), 1e-9);
        assertEquals(0.5, waveform.peak(0, 0.25), 0.02);
    }

    @Test
    void theEnvelopeOfASoundShowsInItsPeaks() throws Exception {
        double[] samples = new double[8000];
        System.arraycopy(TestSounds.sine(0.25, 8000, 440, 0.8), 0, samples, 2000, 2000);

        Waveform waveform = decode(TestSounds.wav(samples, 8000, 1, 16, false));

        assertEquals(0, waveform.peak(0, 0.24));
        assertEquals(0.8, waveform.peak(0.26, 0.49), 0.02);
        assertEquals(0, waveform.peak(0.51, 1));
        assertEquals(0.8, waveform.peak(0, 1), 0.02);
    }

    @Test
    void aLongSoundIsStreamed() throws Exception {
        Waveform waveform = decode(TestSounds.wav(TestSounds.sine(90, 8000, 100, 0.3), 8000, 1, 16, false));

        assertEquals(90, waveform.duration(), 1e-6);
        assertEquals(90 * Waveform.PEAKS_PER_SECOND, waveform.peaks().length);
    }

    @Test
    void whatIsNoSoundIsRefused() {
        assertThrows(UnsupportedAudioFileException.class, () -> decode(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));
        assertThrows(UnsupportedAudioFileException.class, () -> decode(new byte[0]));
    }

    @Test
    void peaksAreAggregatedPerRange() {
        float[] peaks = new float[400];
        peaks[10] = 0.3f;
        peaks[11] = 0.9f;
        peaks[399] = 0.5f;
        Waveform waveform = new Waveform(2, peaks);

        assertEquals(0.9f, waveform.peak(0, 2));
        assertEquals(0.3f, waveform.peak(0.05, 0.055));
        assertEquals(0.9f, waveform.peak(0.05, 0.06));
        assertEquals(0.9f, waveform.peak(0.0551, 0.0552), "a range inside one interval");
        assertEquals(0, waveform.peak(0.06, 1.9));
        assertEquals(0.5f, waveform.peak(1.99, 5), "past the end");
        assertEquals(0, waveform.peak(2.5, 3));
        assertEquals(0, waveform.peak(-3, -1));
        assertEquals(0, waveform.peak(1, 0.5));
        assertTrue(new Waveform(0, new float[0]).peak(0, 1) == 0);
    }
}
