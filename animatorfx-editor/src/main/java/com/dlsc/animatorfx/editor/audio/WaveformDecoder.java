package com.dlsc.animatorfx.editor.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Reads the exact waveform of a sound that {@code javax.sound.sampled} can decode: WAV and AIFF
 * with PCM samples (signed or unsigned, 8 to 32 bits, either byte order, any number of channels)
 * or floating-point samples. The sound is streamed; only the peaks are kept.
 */
public final class WaveformDecoder {

    private static final int BUFFER_FRAMES = 4096;

    private WaveformDecoder() {
    }

    /**
     * @param in the content of the sound file; closed when done
     * @throws UnsupportedAudioFileException if the content is no sound this decoder reads
     */
    public static Waveform decode(InputStream in) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(
                in.markSupported() ? in : new BufferedInputStream(in));
             AudioInputStream stream = readable(source)) {
            return read(stream);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedAudioFileException(e.getMessage());
        }
    }

    /** The stream itself when its samples can be read here, else converted to 16-bit PCM. */
    private static AudioInputStream readable(AudioInputStream source) throws UnsupportedAudioFileException {
        AudioFormat format = source.getFormat();
        AudioFormat.Encoding encoding = format.getEncoding();
        int bits = format.getSampleSizeInBits();
        boolean pcm = (AudioFormat.Encoding.PCM_SIGNED.equals(encoding) || AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding))
                && (bits == 8 || bits == 16 || bits == 24 || bits == 32);
        boolean floating = AudioFormat.Encoding.PCM_FLOAT.equals(encoding) && (bits == 32 || bits == 64);
        if ((pcm || floating) && format.getChannels() > 0 && format.getFrameSize() > 0) {
            return source;
        }
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.getSampleRate(), 16,
                format.getChannels(), 2 * format.getChannels(), format.getSampleRate(), false);
        if (!AudioSystem.isConversionSupported(target, format)) {
            throw new UnsupportedAudioFileException("Cannot decode " + format);
        }
        return AudioSystem.getAudioInputStream(target, source);
    }

    private static Waveform read(AudioInputStream stream) throws IOException, UnsupportedAudioFileException {
        AudioFormat format = stream.getFormat();
        double rate = format.getFrameRate() > 0 ? format.getFrameRate() : format.getSampleRate();
        if (!(rate > 0)) {
            throw new UnsupportedAudioFileException("The sound has no sample rate");
        }
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int sampleSize = frameSize / channels;
        boolean floating = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding());
        boolean signed = !AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding());
        boolean bigEndian = format.isBigEndian();

        float[] peaks = new float[1024];
        int used = 0;
        long frames = 0;
        byte[] buffer = new byte[BUFFER_FRAMES * frameSize];
        int filled = 0;
        int read;
        while ((read = stream.read(buffer, filled, buffer.length - filled)) > 0) {
            filled += read;
            int whole = filled / frameSize;
            for (int f = 0; f < whole; f++) {
                float peak = 0;
                for (int c = 0; c < channels; c++) {
                    peak = Math.max(peak, amplitude(buffer, f * frameSize + c * sampleSize, sampleSize, floating,
                            signed, bigEndian));
                }
                int bucket = (int) Math.min(Integer.MAX_VALUE - 8, (long) (frames * Waveform.PEAKS_PER_SECOND / rate));
                if (bucket >= peaks.length) {
                    peaks = Arrays.copyOf(peaks, Math.max(bucket + 1, peaks.length * 2));
                }
                if (peak > peaks[bucket]) {
                    peaks[bucket] = peak;
                }
                used = Math.max(used, bucket + 1);
                frames++;
            }
            int rest = filled - whole * frameSize;
            System.arraycopy(buffer, whole * frameSize, buffer, 0, rest);
            filled = rest;
        }
        return new Waveform(frames / rate, Arrays.copyOf(peaks, used));
    }

    /** The absolute value of one sample, {@code 0..1}. */
    private static float amplitude(byte[] data, int at, int size, boolean floating, boolean signed, boolean bigEndian) {
        long bits = 0;
        for (int i = 0; i < size; i++) {
            int b = data[at + (bigEndian ? i : size - 1 - i)] & 0xff;
            bits = (bits << 8) | b;
        }
        double value;
        if (floating) {
            value = size == 4 ? Float.intBitsToFloat((int) bits) : Double.longBitsToDouble(bits);
        } else {
            double half = Math.scalb(1.0, size * 8 - 1);
            if (signed) {
                // sign-extend
                long shift = 64 - size * 8L;
                value = ((bits << shift) >> shift) / half;
            } else {
                value = (bits - half) / half;
            }
        }
        double abs = Math.abs(value);
        return Double.isNaN(abs) ? 0 : (float) Math.min(1, abs);
    }
}
