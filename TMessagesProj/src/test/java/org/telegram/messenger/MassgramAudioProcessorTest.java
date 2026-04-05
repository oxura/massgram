package org.telegram.messenger;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class MassgramAudioProcessorTest {

    @Test
    public void pitchShiftKeepsPcmLengthStable() {
        ByteBuffer input = createSineBuffer(640);
        ByteBuffer output = ByteBuffer.allocateDirect(1280).order(ByteOrder.nativeOrder());

        int written = MassgramAudioProcessor.applyPitchShift(input, 1280, output, 1.4f);

        assertEquals(1280, written);
        assertEquals(1280, output.remaining());
    }

    @Test
    public void pitchShiftChangesWaveformWithoutSilencingIt() {
        ByteBuffer input = createSineBuffer(640);
        ByteBuffer output = ByteBuffer.allocateDirect(1280).order(ByteOrder.nativeOrder());

        MassgramAudioProcessor.applyPitchShift(input, 1280, output, 0.8f);

        ByteBuffer original = input.duplicate().order(ByteOrder.nativeOrder());
        ByteBuffer processed = output.duplicate().order(ByteOrder.nativeOrder());
        long originalSignature = sampleSignature(original, 320);
        long processedSignature = sampleSignature(processed, 320);

        assertNotEquals(originalSignature, processedSignature);
        assertTrue(processedSignature != 0L);
    }

    private static ByteBuffer createSineBuffer(int sampleCount) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < sampleCount; i++) {
            double phaseA = (2.0 * Math.PI * i) / 29.0;
            double phaseB = (2.0 * Math.PI * i) / 47.0;
            double sample = Math.sin(phaseA) * 9000.0 + Math.cos(phaseB) * 3500.0;
            buffer.putShort((short) Math.round(sample));
        }
        buffer.flip();
        return buffer;
    }

    private static long sampleSignature(ByteBuffer buffer, int samplesToRead) {
        long signature = 0L;
        int count = Math.min(samplesToRead, buffer.remaining() / 2);
        for (int i = 0; i < count; i++) {
            signature = signature * 31L + buffer.getShort(i * 2);
        }
        return signature;
    }
}
