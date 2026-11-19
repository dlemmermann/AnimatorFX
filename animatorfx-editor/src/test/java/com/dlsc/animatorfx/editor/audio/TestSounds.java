package com.dlsc.animatorfx.editor.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Sound files made in memory for tests: no test plays them audibly. */
public final class TestSounds {

    private TestSounds() {
    }

    /** A sine tone at full scale times {@code level}, 16-bit mono WAV. */
    public static byte[] tone(double seconds, double level) {
        return wav(sine(seconds, 8000, 440, level), 8000, 1, 16, false);
    }

    /** A few decaying tones with pauses between them: a waveform that reads as one. */
    public static byte[] chimes(double seconds) {
        int rate = 22050;
        double[] samples = new double[(int) Math.round(seconds * rate)];
        double[] starts = {0.02, 0.24, 0.43, 0.62, 0.8};
        double[] pitches = {523.25, 659.25, 783.99, 659.25, 1046.5};
        double[] levels = {0.95, 0.7, 0.85, 0.5, 0.9};
        for (int n = 0; n < starts.length; n++) {
            int from = (int) (starts[n] * seconds * rate);
            for (int i = from; i < samples.length; i++) {
                double t = (i - from) / (double) rate;
                double envelope = levels[n] * Math.exp(-t * 9 / seconds) * Math.min(1, t * 400);
                samples[i] += envelope * (0.8 * Math.sin(2 * Math.PI * pitches[n] * t)
                        + 0.2 * Math.sin(2 * Math.PI * pitches[n] * 2.01 * t));
            }
        }
        for (int i = 0; i < samples.length; i++) {
            samples[i] = Math.clamp(samples[i], -1, 1);
        }
        return wav(samples, rate, 1, 16, false);
    }

    public static double[] sine(double seconds, int rate, double frequency, double level) {
        double[] samples = new double[(int) Math.round(seconds * rate)];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = level * Math.sin(2 * Math.PI * frequency * i / rate);
        }
        return samples;
    }

    /**
     * A WAV file; with several channels the samples are interleaved. 8-bit WAV is unsigned, as
     * the format has it.
     */
    public static byte[] wav(double[] interleaved, int rate, int channels, int bits, boolean bigEndianAiff) {
        int size = bits / 8;
        boolean signed = bits > 8 || bigEndianAiff;
        byte[] data = new byte[interleaved.length * size];
        double max = Math.scalb(1.0, bits - 1) - 1;
        for (int i = 0; i < interleaved.length; i++) {
            long value = Math.round(interleaved[i] * max);
            if (!signed) {
                value += 128;
            }
            for (int b = 0; b < size; b++) {
                int shift = 8 * (bigEndianAiff ? size - 1 - b : b);
                data[i * size + b] = (byte) (value >> shift);
            }
        }
        AudioFormat format = new AudioFormat(signed ? AudioFormat.Encoding.PCM_SIGNED : AudioFormat.Encoding.PCM_UNSIGNED,
                rate, bits, channels, size * channels, rate, bigEndianAiff);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(data), format,
                interleaved.length / channels)) {
            AudioSystem.write(stream, bigEndianAiff ? AudioFileFormat.Type.AIFF : AudioFileFormat.Type.WAVE, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
