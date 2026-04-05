package org.telegram.messenger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MassgramAudioProcessor {

    private static final int FRAME_SIZE = 256;
    private static final int SYNTHESIS_HOP = 128;

    private MassgramAudioProcessor() {
    }

    /**
     * Changes voice pitch while keeping the output duration close to the input duration.
     * This path is intentionally simple and local: short-window overlap-add time stretch
     * followed by linear resampling back to the original sample count.
     */
    public static int applyPitchShift(ByteBuffer source, int byteCount, ByteBuffer output, float pitchFactor) {
        if (source == null || output == null || byteCount <= 1 || pitchFactor <= 0f) {
            return 0;
        }
        int inputSamples = byteCount / 2;
        if (inputSamples <= 1) {
            return copyPcm(source, byteCount, output);
        }
        if (Math.abs(pitchFactor - 1.0f) < 0.001f) {
            return copyPcm(source, byteCount, output);
        }

        short[] inputSamplesArray = readSamples(source, inputSamples);
        short[] stretchedSamples = timeStretch(inputSamplesArray, pitchFactor);
        if (stretchedSamples == null || stretchedSamples.length == 0) {
            return copyPcm(source, byteCount, output);
        }
        short[] pitchedSamples = resampleToLength(stretchedSamples, inputSamples);
        if (pitchedSamples == null || pitchedSamples.length == 0) {
            return copyPcm(source, byteCount, output);
        }
        return writeSamples(pitchedSamples, output);
    }

    private static int copyPcm(ByteBuffer source, int byteCount, ByteBuffer output) {
        ByteBuffer sourceCopy = source.duplicate().order(ByteOrder.nativeOrder());
        sourceCopy.position(0);
        sourceCopy.limit(byteCount);
        output.clear();
        int outBytes = Math.min(byteCount, output.capacity() - output.capacity() % 2);
        sourceCopy.limit(outBytes);
        output.put(sourceCopy);
        output.flip();
        return outBytes;
    }

    private static short[] readSamples(ByteBuffer source, int sampleCount) {
        ByteBuffer input = source.duplicate().order(ByteOrder.nativeOrder());
        input.position(0);
        input.limit(sampleCount * 2);
        short[] samples = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = input.getShort();
        }
        return samples;
    }

    private static int writeSamples(short[] samples, ByteBuffer output) {
        int maxSamples = Math.min(samples.length, output.capacity() / 2);
        output.clear();
        output.limit(maxSamples * 2);
        output.order(ByteOrder.nativeOrder());
        for (int i = 0; i < maxSamples; i++) {
            output.putShort(samples[i]);
        }
        output.flip();
        return maxSamples * 2;
    }

    private static short[] timeStretch(short[] input, float stretchFactor) {
        if (input.length < FRAME_SIZE * 2) {
            return input.clone();
        }
        float[] window = createHannWindow(FRAME_SIZE);
        float analysisHop = SYNTHESIS_HOP / stretchFactor;
        if (analysisHop <= 0f) {
            analysisHop = 1f;
        }
        int estimatedLength = Math.max(FRAME_SIZE, Math.round(input.length * stretchFactor) + FRAME_SIZE);
        float[] accum = new float[estimatedLength];
        float[] weights = new float[estimatedLength];

        float inputPosition = 0f;
        int outputPosition = 0;
        while (inputPosition + FRAME_SIZE < input.length && outputPosition + FRAME_SIZE < estimatedLength) {
            for (int i = 0; i < FRAME_SIZE; i++) {
                float sample = sampleLinear(input, inputPosition + i);
                float weight = window[i];
                accum[outputPosition + i] += sample * weight;
                weights[outputPosition + i] += weight;
            }
            inputPosition += analysisHop;
            outputPosition += SYNTHESIS_HOP;
        }

        int usedLength = Math.min(estimatedLength, outputPosition + FRAME_SIZE);
        short[] output = new short[usedLength];
        for (int i = 0; i < usedLength; i++) {
            float sample = weights[i] > 0.0001f ? accum[i] / weights[i] : 0f;
            output[i] = clampToShort(sample);
        }
        return output;
    }

    private static short[] resampleToLength(short[] input, int outputLength) {
        if (outputLength <= 0) {
            return null;
        }
        if (input.length == outputLength) {
            return input.clone();
        }
        short[] output = new short[outputLength];
        float scale = input.length / (float) outputLength;
        for (int i = 0; i < outputLength; i++) {
            output[i] = clampToShort(sampleLinear(input, i * scale));
        }
        return output;
    }

    private static float sampleLinear(short[] input, float index) {
        if (index <= 0f) {
            return input[0];
        }
        int lastIndex = input.length - 1;
        if (index >= lastIndex) {
            return input[lastIndex];
        }
        int leftIndex = (int) index;
        int rightIndex = Math.min(lastIndex, leftIndex + 1);
        float mix = index - leftIndex;
        return input[leftIndex] + (input[rightIndex] - input[leftIndex]) * mix;
    }

    private static float[] createHannWindow(int size) {
        float[] window = new float[size];
        for (int i = 0; i < size; i++) {
            window[i] = (float) (0.5 - 0.5 * Math.cos((2.0 * Math.PI * i) / (size - 1)));
        }
        return window;
    }

    private static short clampToShort(float value) {
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) Math.round(value);
    }
}
