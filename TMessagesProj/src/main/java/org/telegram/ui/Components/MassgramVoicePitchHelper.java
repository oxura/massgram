package org.telegram.ui.Components;

import java.util.Locale;

public final class MassgramVoicePitchHelper {

    public static final int PRESET_DEEP = 0;
    public static final int PRESET_LOW = 1;
    public static final int PRESET_NORMAL = 2;
    public static final int PRESET_BRIGHT = 3;
    public static final int PRESET_HELIUM = 4;

    private static final int[] PRESET_HALF_STEPS = new int[] { -16, -8, 0, 8, 16 };

    private MassgramVoicePitchHelper() {
    }

    public static int getPresetHalfSteps(int presetIndex) {
        if (presetIndex < 0 || presetIndex >= PRESET_HALF_STEPS.length) {
            throw new IllegalArgumentException("Unknown preset index " + presetIndex);
        }
        return PRESET_HALF_STEPS[presetIndex];
    }

    public static int findPresetIndex(int halfSteps) {
        for (int i = 0; i < PRESET_HALF_STEPS.length; i++) {
            if (PRESET_HALF_STEPS[i] == halfSteps) {
                return i;
            }
        }
        return -1;
    }

    public static String formatPitchValue(int halfSteps, String normalLabel) {
        if (halfSteps == 0) {
            return normalLabel;
        }
        float semitones = halfSteps / 2.0f;
        if (halfSteps % 2 == 0) {
            return String.format(Locale.US, "%+.0f st", semitones);
        }
        return String.format(Locale.US, "%+.1f st", semitones);
    }
}
