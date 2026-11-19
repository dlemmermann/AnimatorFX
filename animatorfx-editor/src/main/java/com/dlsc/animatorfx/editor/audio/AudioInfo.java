package com.dlsc.animatorfx.editor.audio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * What the editor knows about the sound of an audio asset.
 *
 * @param duration   the length in seconds; NaN when it is not known
 * @param sampleRate in Hz; 0 when it is not known
 * @param channels   0 when it is not known
 * @param format     a short name such as {@code WAV} or {@code MP3}
 */
public record AudioInfo(double duration, double sampleRate, int channels, String format) {

    /** The extensions of the sound files the editor takes. */
    public static final java.util.List<String> EXTENSIONS = java.util.List.of("mp3", "wav", "aif", "aiff", "m4a", "aac");

    /** Whether the length of the sound is known. */
    public boolean hasDuration() {
        return Double.isFinite(duration) && duration > 0;
    }

    /**
     * Reads the header of a sound that {@code javax.sound.sampled} knows (WAV, AIFF); empty for
     * everything else. Fast: only the header is looked at.
     */
    public static Optional<AudioInfo> read(byte[] bytes) {
        try {
            AudioFileFormat file = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(bytes));
            AudioFormat format = file.getFormat();
            double rate = format.getFrameRate() > 0 ? format.getFrameRate() : format.getSampleRate();
            long frames = file.getFrameLength();
            if (frames <= 0 && format.getFrameSize() > 0 && file.getByteLength() > 0) {
                frames = file.getByteLength() / format.getFrameSize();
            }
            double duration = frames > 0 && rate > 0 ? frames / rate : Double.NaN;
            return Optional.of(new AudioInfo(duration, Math.max(0, rate), Math.max(0, format.getChannels()),
                    file.getType().toString().toUpperCase(Locale.ROOT)));
        } catch (UnsupportedAudioFileException | IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The name of the format of a file with that extension, for sounds whose header is not read here. */
    public static String formatOf(String extension) {
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "m4a", "aac" -> "AAC";
            case "aif", "aiff" -> "AIFF";
            case "wav" -> "WAVE";
            default -> extension.toUpperCase(Locale.ROOT);
        };
    }

    /** {@code "1.250 s"}, or a dash while the length is not known. */
    public String durationText() {
        return hasDuration() ? String.format(Locale.ROOT, "%.3f s", duration) : "—";
    }

    /** {@code "WAVE · 44.1 kHz · stereo"}: what is known, in that order. */
    public String describe() {
        StringBuilder out = new StringBuilder(format == null || format.isBlank() ? "Audio" : format);
        if (sampleRate > 0) {
            String kilo = String.format(Locale.ROOT, "%.2f", sampleRate / 1000).replaceAll("0+$", "").replaceAll("\\.$", "");
            out.append(" · ").append(kilo).append(" kHz");
        }
        if (channels > 0) {
            out.append(" · ").append(channels == 1 ? "mono" : channels == 2 ? "stereo" : channels + " ch");
        }
        return out.toString();
    }
}
