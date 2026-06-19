package com.eyecrasher.lazodiscs.voice;

import com.eyecrasher.lazodiscs.LazoDiscs;
import com.eyecrasher.lazodiscs.text.LazoDiscsText;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Loads an HTTP/HTTPS audio file on a background thread and returns 48 kHz mono PCM samples.
 *
 * IMPORTANT: ArrayAudioFrameProvider is a sample-array provider, not a true streaming queue.
 * So we preload the decoded samples first, add them to the provider, and only then start AudioSender.
 */
public final class HttpPcmFeeder implements AutoCloseable {
    private static final float TARGET_SAMPLE_RATE = 48000.0F;

    private final String url;
    private AudioResolver.ResolvedAudio resolvedAudio;
    private final float volume;
    private final Consumer<short[]> onReady;
    private final Consumer<String> onFailure;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Future<?> task;

    public HttpPcmFeeder(String url, float volume, Consumer<short[]> onReady, Consumer<String> onFailure) {
        this.url = url;
        this.volume = Math.max(0.0F, volume);
        this.onReady = onReady;
        this.onFailure = onFailure;
    }

    public void start() {
        task = AudioLoadExecutor.submit(this::run);
    }

    private void run() {
        try {
            resolvedAudio = AudioResolver.resolve(url, closed);
            if (closed.get()) return;

            short[] samples;
            if (looksLikeMp3(resolvedAudio.displayName())) {
                samples = loadMp3();
            } else {
                try {
                    samples = loadJavaSound();
                } catch (Exception javaSoundError) {
                    samples = loadMp3();
                }
            }

            if (closed.get()) return;
            if (samples.length == 0) {
                LazoDiscs.LOGGER.warn("LazoDiscs decoded zero samples from '{}'", resolvedAudio.displayName());
                fail(LazoDiscsText.audioDecodedZeroSamples());
                return;
            }

            LazoDiscs.LOGGER.info("LazoDiscs decoded {} PCM samples from '{}'", samples.length, resolvedAudio.displayName());
            onReady.accept(samples);
        } catch (Exception e) {
            if (!closed.get()) {
                LazoDiscs.LOGGER.warn("LazoDiscs could not load audio '{}': {}", url, e.toString());
                fail(messageOf(e));
            }
        }
    }

    private void fail(String reason) {
        if (onFailure == null) return;
        try {
            onFailure.accept(reason == null || reason.isBlank() ? LazoDiscsText.unknown() : reason);
        } catch (Throwable t) {
            LazoDiscs.LOGGER.debug("LazoDiscs HTTP failure callback failed: {}", t.toString());
        }
    }

    private static String messageOf(Throwable t) {
        if (t == null) return LazoDiscsText.unknown();
        String message = t.getMessage();
        Throwable cause = t.getCause();
        if ((message == null || message.isBlank()) && cause != null) return messageOf(cause);
        if (cause != null && message != null && message.equals(cause.toString())) return messageOf(cause);
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }

    private boolean looksLikeMp3(String rawUrl) {
        String lower = rawUrl.toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".mp3") || lower.endsWith(".mpeg") || lower.endsWith(".mpga");
    }

    private InputStream openStream() throws Exception {
        AudioResolver.ResolvedAudio audio = resolvedAudio != null ? resolvedAudio : AudioResolver.ResolvedAudio.url(url);
        if (audio.isFile()) {
            return new BufferedInputStream(Files.newInputStream(audio.file()));
        }
        URLConnection connection = new URL(audio.url()).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "LazoDiscs/1.0.0");
        return new BufferedInputStream(connection.getInputStream());
    }

    private short[] loadJavaSound() throws Exception {
        try (InputStream input = openStream();
             AudioInputStream original = AudioSystem.getAudioInputStream(input)) {

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    TARGET_SAMPLE_RATE,
                    16,
                    1,
                    2,
                    TARGET_SAMPLE_RATE,
                    false
            );

            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(targetFormat, original)) {
                ShortBuilder out = new ShortBuilder(48000 * 30);
                byte[] bytes = new byte[8192];
                int read;
                while (!closed.get() && (read = pcm.read(bytes)) >= 0) {
                    if (read == 0) continue;
                    int samplesCount = read / 2;
                    for (int i = 0; i < samplesCount; i++) {
                        int lo = bytes[i * 2] & 0xFF;
                        int hi = bytes[i * 2 + 1];
                        int value = (hi << 8) | lo;
                        out.add(scale(value));
                    }
                }
                return out.toArray();
            }
        }
    }

    private short[] loadMp3() throws Exception {
        try (InputStream input = openStream(); Mp3FrameDecoder decoder = new Mp3FrameDecoder(input)) {
            ShortBuilder out = new ShortBuilder(48000 * 30);
            Resampler resampler = null;
            Mp3FrameDecoder.PcmFrame frame;
            while (!closed.get() && (frame = decoder.readFrame()) != null) {
                short[] mono = toMono(frame.samples(), frame.length(), frame.channels());
                if (resampler == null) {
                    resampler = new Resampler(frame.sampleRate(), TARGET_SAMPLE_RATE);
                }
                short[] resampled = resampler.resample(mono);
                for (short sample : resampled) {
                    out.add(sample);
                }
            }
            return out.toArray();
        }
    }

    private short[] toMono(short[] input, int length, int channels) {
        if (channels <= 1) {
            short[] out = new short[length];
            for (int i = 0; i < length; i++) out[i] = scale(input[i]);
            return out;
        }

        int frames = length / channels;
        short[] out = new short[frames];
        for (int frame = 0; frame < frames; frame++) {
            int sum = 0;
            for (int ch = 0; ch < channels; ch++) {
                sum += input[frame * channels + ch];
            }
            out[frame] = scale(sum / channels);
        }
        return out;
    }

    private short scale(int value) {
        int scaled = Math.round(value * volume);
        if (scaled > Short.MAX_VALUE) scaled = Short.MAX_VALUE;
        if (scaled < Short.MIN_VALUE) scaled = Short.MIN_VALUE;
        return (short) scaled;
    }

    @Override
    public void close() {
        closed.set(true);
        if (task != null) task.cancel(true);
    }

    private static final class ShortBuilder {
        private short[] data;
        private int size;

        private ShortBuilder(int initialCapacity) {
            this.data = new short[Math.max(1024, initialCapacity)];
        }

        private void add(short value) {
            if (size >= data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size++] = value;
        }

        private short[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    private static final class Resampler {
        private final double step;
        private double offset = 0.0D;
        private Short previousSample = null;

        private Resampler(float sourceRate, float targetRate) {
            this.step = sourceRate / targetRate;
        }

        private short[] resample(short[] chunk) {
            if (chunk.length == 0) return new short[0];

            short[] input;
            if (previousSample != null) {
                input = new short[chunk.length + 1];
                input[0] = previousSample;
                System.arraycopy(chunk, 0, input, 1, chunk.length);
            } else {
                input = chunk;
            }

            int estimated = Math.max(1, (int) Math.ceil((input.length - 1 - offset) / step) + 2);
            short[] temp = new short[estimated];
            int out = 0;
            double pos = offset;
            while (pos < input.length - 1) {
                int i = (int) pos;
                double frac = pos - i;
                int value = (int) Math.round(input[i] * (1.0D - frac) + input[i + 1] * frac);
                if (out >= temp.length) temp = Arrays.copyOf(temp, temp.length * 2);
                temp[out++] = (short) value;
                pos += step;
            }

            offset = pos - (input.length - 1);
            previousSample = input[input.length - 1];
            return Arrays.copyOf(temp, out);
        }
    }
}
