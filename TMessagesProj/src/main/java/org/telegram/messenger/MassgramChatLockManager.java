package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MassgramChatLockManager {

    private static final String KEY_HASH_PREFIX = "massgram_chat_lock_hash_";
    private static final String KEY_SALT_PREFIX = "massgram_chat_lock_salt_";
    private static final String KEY_HINT_PREFIX = "massgram_chat_lock_hint_";

    private static volatile MassgramChatLockManager instance;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Set<Long> unlockedDialogs = Collections.synchronizedSet(new HashSet<>());

    public static MassgramChatLockManager getInstance() {
        MassgramChatLockManager localInstance = instance;
        if (localInstance == null) {
            synchronized (MassgramChatLockManager.class) {
                localInstance = instance;
                if (localInstance == null) {
                    localInstance = instance = new MassgramChatLockManager();
                }
            }
        }
        return localInstance;
    }

    private MassgramChatLockManager() {
    }

    public boolean isChatLocked(long dialogId) {
        return getPreferences().contains(getHashKey(dialogId));
    }

    public boolean shouldRequestUnlock(long dialogId) {
        return isChatLocked(dialogId) && !isDialogUnlocked(dialogId);
    }

    public boolean isDialogUnlocked(long dialogId) {
        return unlockedDialogs.contains(dialogId);
    }

    public void markDialogUnlocked(long dialogId) {
        if (isChatLocked(dialogId)) {
            unlockedDialogs.add(dialogId);
        }
    }

    public void clearDialogUnlocked(long dialogId) {
        unlockedDialogs.remove(dialogId);
    }

    public void setChatLock(long dialogId, String pin, String hint) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(getSaltKey(dialogId), Base64.encodeToString(salt, Base64.NO_WRAP));
        editor.putString(getHashKey(dialogId), hashPin(pin, salt));
        if (TextUtils.isEmpty(hint)) {
            editor.remove(getHintKey(dialogId));
        } else {
            editor.putString(getHintKey(dialogId), hint.trim());
        }
        editor.apply();
        unlockedDialogs.remove(dialogId);
    }

    public void clearChatLock(long dialogId) {
        getPreferences().edit()
            .remove(getHashKey(dialogId))
            .remove(getSaltKey(dialogId))
            .remove(getHintKey(dialogId))
            .apply();
        unlockedDialogs.remove(dialogId);
    }

    public boolean verifyPin(long dialogId, String pin) {
        String storedHash = getPreferences().getString(getHashKey(dialogId), null);
        byte[] salt = getSalt(dialogId);
        if (storedHash == null || salt == null) {
            return false;
        }
        return MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.UTF_8), hashPin(pin, salt).getBytes(StandardCharsets.UTF_8));
    }

    public String getHint(long dialogId) {
        return getPreferences().getString(getHintKey(dialogId), null);
    }

    private byte[] getSalt(long dialogId) {
        String encodedSalt = getPreferences().getString(getSaltKey(dialogId), null);
        if (encodedSalt == null) {
            return null;
        }
        return Base64.decode(encodedSalt, Base64.DEFAULT);
    }

    private String hashPin(String pin, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest.digest(), Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SharedPreferences getPreferences() {
        return MessagesController.getGlobalMainSettings();
    }

    private String getHashKey(long dialogId) {
        return KEY_HASH_PREFIX + dialogId;
    }

    private String getSaltKey(long dialogId) {
        return KEY_SALT_PREFIX + dialogId;
    }

    private String getHintKey(long dialogId) {
        return KEY_HINT_PREFIX + dialogId;
    }
}
