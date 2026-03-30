package org.telegram.messenger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MassgramAudioProcessor {

    private MassgramAudioProcessor() {
    }

    /**
     * Простое локальное изменение pitch через линейный ресемплинг PCM16.
     * Меняет и тембр, и длительность, но не требует внешнего DSP/NDK стека.
     */
    public static int applyPitchShift(ByteBuffer source, int byteCount, ByteBuffer output, float pitchFactor) {
        if (source == null || output == null || byteCount <= 1 || pitchFactor <= 0f) {
            return 0;
        }
        int inputSamples = byteCount / 2;
        if (inputSamples <= 1) {
            ByteBuffer sourceCopy = source.duplicate().order(ByteOrder.nativeOrder());
            sourceCopy.position(0);
            sourceCopy.limit(byteCount);
            output.clear();
            output.put(sourceCopy);
            output.flip();
            return byteCount;
        }

        ByteBuffer input = source.duplicate().order(ByteOrder.nativeOrder());
        input.position(0);
        input.limit(byteCount);

        int outputSamples = Math.max(1, Math.round(inputSamples / pitchFactor));
        int outputBytes = outputSamples * 2;
        if (output.capacity() < outputBytes) {
            outputBytes = output.capacity() - output.capacity() % 2;
            outputSamples = outputBytes / 2;
        }

        output.clear();
        output.limit(outputBytes);
        for (int i = 0; i < outputSamples; i++) {
            float srcIndex = i * pitchFactor;
            if (srcIndex < 0f) {
                srcIndex = 0f;
            } else if (srcIndex > inputSamples - 1) {
                srcIndex = inputSamples - 1;
            }
            int leftIndex = (int) srcIndex;
            int rightIndex = Math.min(inputSamples - 1, leftIndex + 1);
            float mix = srcIndex - leftIndex;
            short left = input.getShort(leftIndex * 2);
            short right = input.getShort(rightIndex * 2);
            short value = (short) Math.round(left + (right - left) * mix);
            output.putShort(value);
        }
        output.flip();
        return output.limit();
    }
}
