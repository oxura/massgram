package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class MassgramConfigManager {

    public static final long OWNER_USER_ID = 6539627752L;

    private static final String KEY_VOICE_PITCH_ENABLED = "massgram_voice_pitch_enabled";
    private static final String KEY_VOICE_PITCH_SEMITONES = "massgram_voice_pitch_semitones";
    private static final String KEY_BLOCK_SPONSORED_MESSAGES = "massgram_block_sponsored_messages";
    private static final String KEY_DISABLE_LOCAL_STATS = "massgram_disable_local_stats";
    private static final String KEY_EXPANDED_UI_LIMITS = "massgram_expanded_ui_limits";

    private static final int DEFAULT_VOICE_PITCH_SEMITONES = 0;
    private static final int MIN_VOICE_PITCH_SEMITONES = -12;
    private static final int MAX_VOICE_PITCH_SEMITONES = 12;

    private static volatile MassgramConfigManager instance;

    public static MassgramConfigManager getInstance() {
        MassgramConfigManager localInstance = instance;
        if (localInstance == null) {
            synchronized (MassgramConfigManager.class) {
                localInstance = instance;
                if (localInstance == null) {
                    localInstance = instance = new MassgramConfigManager();
                }
            }
        }
        return localInstance;
    }

    private MassgramConfigManager() {
    }

    public boolean isVoicePitchEnabled() {
        return getPreferences().getBoolean(KEY_VOICE_PITCH_ENABLED, false);
    }

    public void setVoicePitchEnabled(boolean enabled) {
        SharedPreferences preferences = getPreferences();
        if (preferences.getBoolean(KEY_VOICE_PITCH_ENABLED, false) == enabled) {
            return;
        }
        preferences.edit().putBoolean(KEY_VOICE_PITCH_ENABLED, enabled).apply();
        notifyChanged();
    }

    public int getVoicePitchSemitones() {
        return clampSemitones(getPreferences().getInt(KEY_VOICE_PITCH_SEMITONES, DEFAULT_VOICE_PITCH_SEMITONES));
    }

    public void setVoicePitchSemitones(int semitones) {
        int clamped = clampSemitones(semitones);
        SharedPreferences preferences = getPreferences();
        if (preferences.getInt(KEY_VOICE_PITCH_SEMITONES, DEFAULT_VOICE_PITCH_SEMITONES) == clamped) {
            return;
        }
        preferences.edit().putInt(KEY_VOICE_PITCH_SEMITONES, clamped).apply();
        notifyChanged();
    }

    public float getVoicePitchFactor() {
        return (float) Math.pow(2.0d, getVoicePitchSemitones() / 12.0d);
    }

    public boolean isSponsoredMessagesBlocked() {
        return getPreferences().getBoolean(KEY_BLOCK_SPONSORED_MESSAGES, true);
    }

    public void setSponsoredMessagesBlocked(boolean enabled) {
        SharedPreferences preferences = getPreferences();
        if (preferences.getBoolean(KEY_BLOCK_SPONSORED_MESSAGES, true) == enabled) {
            return;
        }
        preferences.edit().putBoolean(KEY_BLOCK_SPONSORED_MESSAGES, enabled).apply();
        notifyChanged();
    }

    public boolean isLocalStatsDisabled() {
        return getPreferences().getBoolean(KEY_DISABLE_LOCAL_STATS, true);
    }

    public void setLocalStatsDisabled(boolean enabled) {
        SharedPreferences preferences = getPreferences();
        if (preferences.getBoolean(KEY_DISABLE_LOCAL_STATS, true) == enabled) {
            return;
        }
        preferences.edit().putBoolean(KEY_DISABLE_LOCAL_STATS, enabled).apply();
        notifyChanged();
    }

    public boolean isExpandedUiLimitsEnabled() {
        return getPreferences().getBoolean(KEY_EXPANDED_UI_LIMITS, true);
    }

    public boolean isOwner(long userId) {
        return userId == OWNER_USER_ID;
    }

    private int clampSemitones(int value) {
        return Math.max(MIN_VOICE_PITCH_SEMITONES, Math.min(MAX_VOICE_PITCH_SEMITONES, value));
    }

    private void notifyChanged() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.massgramSettingsChanged);
    }

    private SharedPreferences getPreferences() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            throw new IllegalStateException("Application context is not initialized");
        }
        return context.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
    }
}
