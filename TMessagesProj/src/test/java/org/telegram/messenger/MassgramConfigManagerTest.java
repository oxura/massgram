package org.telegram.messenger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MassgramConfigManagerTest {

    @Test
    public void legacySemitonesMigrateToHalfSteps() {
        assertEquals(-24, MassgramConfigManager.migrateLegacySemitonesToHalfSteps(-12));
        assertEquals(-3 * 2, MassgramConfigManager.migrateLegacySemitonesToHalfSteps(-3));
        assertEquals(0, MassgramConfigManager.migrateLegacySemitonesToHalfSteps(0));
        assertEquals(7 * 2, MassgramConfigManager.migrateLegacySemitonesToHalfSteps(7));
        assertEquals(24, MassgramConfigManager.migrateLegacySemitonesToHalfSteps(12));
    }

    @Test
    public void halfStepsClampToSafeRange() {
        assertEquals(-24, MassgramConfigManager.clampVoicePitchHalfSteps(-200));
        assertEquals(5, MassgramConfigManager.clampVoicePitchHalfSteps(5));
        assertEquals(24, MassgramConfigManager.clampVoicePitchHalfSteps(200));
    }

    @Test
    public void halfStepsProduceExpectedPitchFactor() {
        assertEquals(1.0f, MassgramConfigManager.voicePitchFactorForHalfSteps(0), 0.0001f);
        assertEquals((float) Math.pow(2.0d, 0.5d), MassgramConfigManager.voicePitchFactorForHalfSteps(12), 0.0001f);
        assertEquals((float) Math.pow(2.0d, -0.5d), MassgramConfigManager.voicePitchFactorForHalfSteps(-12), 0.0001f);
    }
}
