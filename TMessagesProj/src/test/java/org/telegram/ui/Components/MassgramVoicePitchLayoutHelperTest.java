package org.telegram.ui.Components;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MassgramVoicePitchLayoutHelperTest {

    @Test
    public void doesNotReservePitchSpaceWhenPitchButtonHidden() {
        assertEquals(0, MassgramVoicePitchLayoutHelper.getTextFieldRightExtraInsetDp(false));
        assertEquals(0, MassgramVoicePitchLayoutHelper.getAttachButtonExtraOffsetDp(false));
    }

    @Test
    public void reservesOnlyCompactPitchSpaceWhenPitchButtonVisible() {
        assertEquals(24, MassgramVoicePitchLayoutHelper.getTextFieldRightExtraInsetDp(true));
        assertEquals(-30, MassgramVoicePitchLayoutHelper.getAttachButtonExtraOffsetDp(true));
    }

    @Test
    public void keepsPitchButtonAnchoredInCompactSlotNearRecordButton() {
        assertEquals(4, MassgramVoicePitchLayoutHelper.getPitchButtonRightInsetDp());
    }
}
