package com.eyecrasher.lazodiscs.voice;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;

import java.io.IOException;
import java.io.InputStream;

final class Mp3FrameDecoder implements AutoCloseable {
    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();

    Mp3FrameDecoder(InputStream input) {
        this.bitstream = new Bitstream(input);
    }

    PcmFrame readFrame() throws IOException {
        try {
            Header header = bitstream.readFrame();
            if (header == null) return null;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            short[] data = output.getBuffer();
            int length = output.getBufferLength();
            int channels = output.getChannelCount();
            int sampleRate = output.getSampleFrequency();

            short[] copy = new short[length];
            System.arraycopy(data, 0, copy, 0, length);
            return new PcmFrame(copy, length, channels, sampleRate);
        } catch (JavaLayerException e) {
            throw new IOException(e);
        } finally {
            try {
                bitstream.closeFrame();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            bitstream.close();
        } catch (JavaLayerException e) {
            throw new IOException(e);
        }
    }

    record PcmFrame(short[] samples, int length, int channels, int sampleRate) {
    }
}
