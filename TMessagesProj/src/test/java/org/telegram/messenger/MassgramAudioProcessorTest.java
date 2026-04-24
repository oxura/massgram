package org.telegram.messenger;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MassgramAudioProcessorTest {

    @Test
    public void disabledProcessorPassesPcmThroughUnchanged() {
        MassgramAudioProcessor processor = new MassgramAudioProcessor();
        assertTrue(processor.configure(48000, 1, 1.0f));

        ByteBuffer input = createSineBuffer(640);
        processor.queueInput(input, input.remaining());
        processor.queueEndOfStream();

        short[] outputSamples = collectAllSamples(processor);

        assertArrayEquals(toShortArray(createSineBuffer(640)), outputSamples);
        assertTrue(processor.isEnded());
    }

    @Test
    public void activeProcessorKeepsSampleCountCloseToInput() {
        MassgramAudioProcessor processor = new MassgramAudioProcessor();
        assertTrue(processor.configure(48000, 1, 1.4f));

        int inputSamples = 640 * 100;
        ByteBuffer input = createSineBuffer(inputSamples);
        processor.queueInput(input, input.remaining());
        processor.queueEndOfStream();

        short[] outputSamples = collectAllSamples(processor);

        assertTrue("outputSamples.length=" + outputSamples.length, Math.abs(outputSamples.length - inputSamples) <= 320);
    }

    @Test
    public void activeProcessorReadsConsumedRecordingBufferFromStart() {
        MassgramAudioProcessor processor = new MassgramAudioProcessor();
        assertTrue(processor.configure(48000, 1, 1.4f));

        int inputSamples = 640 * 20;
        ByteBuffer input = createSineBuffer(inputSamples);
        while (input.hasRemaining()) {
            input.getShort();
        }

        processor.queueInput(input, inputSamples * 2);
        processor.queueEndOfStream();

        short[] outputSamples = collectAllSamples(processor);

        assertTrue("outputSamples.length=" + outputSamples.length, outputSamples.length > 0);
        assertTrue("outputSamples.length=" + outputSamples.length, Math.abs(outputSamples.length - inputSamples) <= 320);
    }

    @Test
    public void streamingChunksDoNotCreateBoundarySeams() {
        short[] wholeSamples = processSignal(1.4f, 640 * 20, 640 * 20);
        short[] chunkedSamples = processSignal(1.4f, 640 * 20, 640);

        assertEquals(wholeSamples.length, chunkedSamples.length);
        assertTrue(maxAbsDifference(wholeSamples, chunkedSamples) <= 8);
    }

    @Test
    public void queueEndOfStreamDrainsBufferedOutput() {
        MassgramAudioProcessor processor = new MassgramAudioProcessor();
        assertTrue(processor.configure(48000, 1, 0.8f));

        ByteBuffer input = createSineBuffer(640 * 3);
        processor.queueInput(input, input.remaining());

        int beforeEndBytes = drainAvailableBytes(processor);
        processor.queueEndOfStream();
        int afterEndBytes = drainAvailableBytes(processor);

        assertTrue(beforeEndBytes > 0);
        assertTrue(afterEndBytes > 0);
        assertTrue(processor.isEnded());
    }

    private static short[] processSignal(float pitchFactor, int totalSamples, int chunkSamples) {
        MassgramAudioProcessor processor = new MassgramAudioProcessor();
        processor.configure(48000, 1, pitchFactor);
        ByteBuffer signal = createSineBuffer(totalSamples);
        for (int offset = 0; offset < totalSamples; offset += chunkSamples) {
            int sampleCount = Math.min(chunkSamples, totalSamples - offset);
            ByteBuffer chunk = slice(signal, offset * 2, sampleCount * 2);
            processor.queueInput(chunk, chunk.remaining());
        }
        processor.queueEndOfStream();
        return collectAllSamples(processor);
    }

    private static int maxAbsDifference(short[] left, short[] right) {
        int max = 0;
        for (int i = 0; i < left.length; i++) {
            max = Math.max(max, Math.abs(left[i] - right[i]));
        }
        return max;
    }

    private static int drainAvailableBytes(MassgramAudioProcessor processor) {
        int total = 0;
        while (true) {
            ByteBuffer output = processor.getOutput();
            if (!output.hasRemaining()) {
                break;
            }
            total += output.remaining();
        }
        return total;
    }

    private static short[] collectAllSamples(MassgramAudioProcessor processor) {
        ArrayList<Short> samples = new ArrayList<>();
        while (true) {
            ByteBuffer output = processor.getOutput();
            if (output.hasRemaining()) {
                ByteBuffer copy = output.duplicate().order(ByteOrder.nativeOrder());
                while (copy.hasRemaining()) {
                    samples.add(copy.getShort());
                }
            } else if (processor.isEnded()) {
                break;
            }
        }
        short[] result = new short[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            result[i] = samples.get(i);
        }
        return result;
    }

    private static short[] toShortArray(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate().order(ByteOrder.nativeOrder());
        short[] result = new short[copy.remaining() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = copy.getShort();
        }
        return result;
    }

    private static ByteBuffer slice(ByteBuffer buffer, int byteOffset, int byteCount) {
        ByteBuffer copy = buffer.duplicate().order(ByteOrder.nativeOrder());
        copy.position(byteOffset);
        copy.limit(byteOffset + byteCount);
        return copy.slice().order(ByteOrder.nativeOrder());
    }

    private static ByteBuffer createSineBuffer(int sampleCount) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < sampleCount; i++) {
            double phaseA = (2.0 * Math.PI * i) / 29.0;
            double phaseB = (2.0 * Math.PI * i) / 47.0;
            double phaseC = (2.0 * Math.PI * i) / 211.0;
            double sample = Math.sin(phaseA) * 9000.0 + Math.cos(phaseB) * 3500.0 + Math.sin(phaseC) * 5000.0;
            buffer.putShort((short) Math.round(sample));
        }
        buffer.flip();
        return buffer;
    }
}
