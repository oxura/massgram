package org.telegram.ui.Components;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MassgramVoicePitchHelperTest {

    @Test
    public void formatsNormalIntegerAndHalfStepValues() {
        assertEquals("Normal", MassgramVoicePitchHelper.formatPitchValue(0, "Normal"));
        assertEquals("+4 st", MassgramVoicePitchHelper.formatPitchValue(8, "Normal"));
        assertEquals("-11.5 st", MassgramVoicePitchHelper.formatPitchValue(-23, "Normal"));
    }

    @Test
    public void detectsPresetSelectionByStoredHalfSteps() {
        assertEquals(MassgramVoicePitchHelper.PRESET_DEEP, MassgramVoicePitchHelper.findPresetIndex(-16));
        assertEquals(MassgramVoicePitchHelper.PRESET_LOW, MassgramVoicePitchHelper.findPresetIndex(-8));
        assertEquals(MassgramVoicePitchHelper.PRESET_NORMAL, MassgramVoicePitchHelper.findPresetIndex(0));
        assertEquals(MassgramVoicePitchHelper.PRESET_BRIGHT, MassgramVoicePitchHelper.findPresetIndex(8));
        assertEquals(MassgramVoicePitchHelper.PRESET_HELIUM, MassgramVoicePitchHelper.findPresetIndex(16));
        assertEquals(-1, MassgramVoicePitchHelper.findPresetIndex(5));
    }
}
