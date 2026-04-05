package org.telegram.ui.Components;

final class MassgramVoicePitchLayoutHelper {

    private static final int PITCH_TEXT_FIELD_RIGHT_EXTRA_INSET_DP = 24;
    private static final int PITCH_ATTACH_BUTTON_EXTRA_OFFSET_DP = -30;
    private static final int PITCH_BUTTON_RIGHT_INSET_DP = 4;

    private MassgramVoicePitchLayoutHelper() {
    }

    static int getTextFieldRightExtraInsetDp(boolean pitchButtonVisible) {
        return pitchButtonVisible ? PITCH_TEXT_FIELD_RIGHT_EXTRA_INSET_DP : 0;
    }

    static int getAttachButtonExtraOffsetDp(boolean pitchButtonVisible) {
        return pitchButtonVisible ? PITCH_ATTACH_BUTTON_EXTRA_OFFSET_DP : 0;
    }

    static int getPitchButtonRightInsetDp() {
        return PITCH_BUTTON_RIGHT_INSET_DP;
    }
}
