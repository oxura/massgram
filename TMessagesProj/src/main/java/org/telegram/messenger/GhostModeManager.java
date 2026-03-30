package org.telegram.messenger;

import android.content.SharedPreferences;

public class GhostModeManager {

    public static final String KEY_GHOST_MODE_ENABLED = "ghost_mode_enabled";
    public static final String KEY_SAVE_DELETED_ENABLED = "save_deleted_enabled";
    public static final String KEY_FORCE_RELAY_CALLS_ENABLED = "massgram_force_relay_calls_enabled";
    public static final String KEY_DISABLE_LINK_PREVIEWS_ENABLED = "massgram_disable_link_previews_enabled";
    private static final String KEY_FORCE_RELAY_CALLS_BACKUP_EXISTS = "massgram_force_relay_calls_backup_exists";
    private static final String KEY_FORCE_RELAY_CALLS_BACKUP_VALUE = "massgram_force_relay_calls_backup_value";
    private static final String KEY_CALLS_P2P_NEW = "calls_p2p_new";

    private static volatile GhostModeManager instance;

    public static GhostModeManager getInstance() {
        GhostModeManager localInstance = instance;
        if (localInstance == null) {
            synchronized (GhostModeManager.class) {
                localInstance = instance;
                if (localInstance == null) {
                    localInstance = instance = new GhostModeManager();
                }
            }
        }
        return localInstance;
    }

    private GhostModeManager() {
    }

    public boolean isGhostModeEnabled() {
        return getPreferences().getBoolean(KEY_GHOST_MODE_ENABLED, false);
    }

    public void setGhostModeEnabled(boolean enabled) {
        SharedPreferences preferences = getPreferences();
        if (preferences.getBoolean(KEY_GHOST_MODE_ENABLED, false) == enabled) {
            return;
        }
        preferences.edit().putBoolean(KEY_GHOST_MODE_ENABLED, enabled).apply();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.massgramSettingsChanged);
    }

    public boolean isSaveDeletedMessagesEnabled() {
        return getPreferences().getBoolean(KEY_SAVE_DELETED_ENABLED, false);
    }

    public void setSaveDeletedMessagesEnabled(boolean enabled) {
        SharedPreferences preferences = getPreferences();
        if (preferences.getBoolean(KEY_SAVE_DELETED_ENABLED, false) == enabled) {
            return;
        }
        preferences.edit().putBoolean(KEY_SAVE_DELETED_ENABLED, enabled).apply();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.massgramSettingsChanged);
    }

    public boolean isForceRelayCallsEnabled() {
        return getPreferences().getBoolean(KEY_FORCE_RELAY_CALLS_ENABLED, false);
    }

    public void setForceRelayCallsEnabled(boolean enabled) {
        SharedPreferences preferences = getPreferences();
        if (preferences.getBoolean(KEY_FORCE_RELAY_CALLS_ENABLED, false) == enabled) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        if (enabled) {
            // Preserve the user's original setting so the toggle can restore it cleanly.
            if (preferences.contains(KEY_CALLS_P2P_NEW)) {
                editor.putBoolean(KEY_FORCE_RELAY_CALLS_BACKUP_EXISTS, true);
                editor.putInt(KEY_FORCE_RELAY_CALLS_BACKUP_VALUE, preferences.getInt(KEY_CALLS_P2P_NEW, 1));
            } else {
                editor.putBoolean(KEY_FORCE_RELAY_CALLS_BACKUP_EXISTS, false);
                editor.remove(KEY_FORCE_RELAY_CALLS_BACKUP_VALUE);
            }
            editor.putInt(KEY_CALLS_P2P_NEW, 0);
        } else if (preferences.getBoolean(KEY_FORCE_RELAY_CALLS_BACKUP_EXISTS, false)) {
            editor.putInt(KEY_CALLS_P2P_NEW, preferences.getInt(KEY_FORCE_RELAY_CALLS_BACKUP_VALUE, 1));
            editor.remove(KEY_FORCE_RELAY_CALLS_BACKUP_EXISTS);
            editor.remove(KEY_FORCE_RELAY_CALLS_BACKUP_VALUE);
        } else {
            editor.remove(KEY_CALLS_P2P_NEW);
            editor.remove(KEY_FORCE_RELAY_CALLS_BACKUP_EXISTS);
            editor.remove(KEY_FORCE_RELAY_CALLS_BACKUP_VALUE);
        }
        editor.putBoolean(KEY_FORCE_RELAY_CALLS_ENABLED, enabled).commit();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.massgramSettingsChanged);
    }

    public boolean isDisableLinkPreviewsEnabled() {
        return getPreferences().getBoolean(KEY_DISABLE_LINK_PREVIEWS_ENABLED, false);
    }

    public void setDisableLinkPreviewsEnabled(boolean enabled) {
        SharedPreferences preferences = getPreferences();
        if (preferences.getBoolean(KEY_DISABLE_LINK_PREVIEWS_ENABLED, false) == enabled) {
            return;
        }
        preferences.edit().putBoolean(KEY_DISABLE_LINK_PREVIEWS_ENABLED, enabled).apply();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.massgramSettingsChanged);
    }

    public boolean shouldBlockReadReceipts() {
        return isGhostModeEnabled();
    }

    public boolean shouldBlockPresenceUpdates() {
        return isGhostModeEnabled();
    }

    public boolean shouldForceRelayCalls() {
        return isForceRelayCallsEnabled();
    }

    public boolean shouldDisableLocalLinkPreviews() {
        return isDisableLinkPreviewsEnabled();
    }

    private SharedPreferences getPreferences() {
        return MessagesController.getGlobalMainSettings();
    }
}
