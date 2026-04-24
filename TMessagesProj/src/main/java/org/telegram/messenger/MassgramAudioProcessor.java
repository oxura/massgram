package org.telegram.messenger;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

public final class MassgramAudioProcessor {

    private static final float PITCH_BYPASS_EPSILON = 0.0001f;

    private final SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    private final ArrayDeque<ByteBuffer> passthroughOutput = new ArrayDeque<>();

    private boolean configured;
    private boolean passthrough;
    private boolean inputEnded;

    public boolean configure(int sampleRate, int channelCount, float pitchFactor) {
        reset();
        if (sampleRate <= 0 || channelCount <= 0 || pitchFactor <= 0f) {
            return false;
        }
        passthrough = Math.abs(pitchFactor - 1.0f) < PITCH_BYPASS_EPSILON;
        if (passthrough) {
            configured = true;
            return true;
        }
        try {
            sonicAudioProcessor.setSpeed(1.0f);
            sonicAudioProcessor.setPitch(pitchFactor);
            sonicAudioProcessor.setOutputSampleRateHz(SonicAudioProcessor.SAMPLE_RATE_NO_CHANGE);
            sonicAudioProcessor.configure(new AudioProcessor.AudioFormat(sampleRate, channelCount, C.ENCODING_PCM_16BIT));
            sonicAudioProcessor.flush();
            configured = true;
            return true;
        } catch (AudioProcessor.UnhandledAudioFormatException e) {
            FileLog.e(e);
            reset();
            return false;
        }
    }

    public void queueInput(ByteBuffer input, int byteCount) {
        if (!configured || input == null || byteCount <= 0) {
            return;
        }
        ByteBuffer copy = input.duplicate().order(ByteOrder.nativeOrder());
        copy.position(0);
        copy.limit(Math.min(byteCount, copy.capacity()));
        if (passthrough) {
            ByteBuffer output = ByteBuffer.allocateDirect(copy.remaining()).order(ByteOrder.nativeOrder());
            output.put(copy);
            output.flip();
            passthroughOutput.add(output);
            return;
        }
        sonicAudioProcessor.queueInput(copy);
    }

    public void queueEndOfStream() {
        if (!configured || inputEnded) {
            return;
        }
        inputEnded = true;
        if (!passthrough) {
            sonicAudioProcessor.queueEndOfStream();
        }
    }

    public ByteBuffer getOutput() {
        if (!configured) {
            return AudioProcessor.EMPTY_BUFFER;
        }
        if (passthrough) {
            ByteBuffer output = passthroughOutput.pollFirst();
            return output != null ? output : AudioProcessor.EMPTY_BUFFER;
        }
        return sonicAudioProcessor.getOutput();
    }

    public boolean isEnded() {
        if (!configured) {
            return true;
        }
        if (passthrough) {
            return inputEnded && passthroughOutput.isEmpty();
        }
        return sonicAudioProcessor.isEnded();
    }

    public void reset() {
        sonicAudioProcessor.reset();
        passthroughOutput.clear();
        configured = false;
        passthrough = false;
        inputEnded = false;
    }
}
