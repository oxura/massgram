package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseArray;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MassgramCryptoManager {

    private static final String KEY_ENABLED_PREFIX = "massgram_crypto_enabled_";
    private static final String KEY_DIALOG_KEY_PREFIX = "massgram_crypto_key_";
    private static final String KEY_PEER_DETECTED_PREFIX = "massgram_crypto_peer_detected_";

    private static final String DEFAULT_DIALOG_KEY = "Batyi0015";
    private static final String PAYLOAD_PREFIX = "\uD83D\uDD12MG1:";

    private static final SparseArray<MassgramCryptoManager> instances = new SparseArray<>();

    private final int currentAccount;
    private final SecureRandom secureRandom = new SecureRandom();

    public static MassgramCryptoManager getInstance(int account) {
        synchronized (instances) {
            MassgramCryptoManager instance = instances.get(account);
            if (instance == null) {
                instance = new MassgramCryptoManager(account);
                instances.put(account, instance);
            }
            return instance;
        }
    }

    private MassgramCryptoManager(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public boolean supportsDialog(long dialogId) {
        return DialogObject.isUserDialog(dialogId);
    }

    public boolean isEncryptionEnabled(long dialogId) {
        return supportsDialog(dialogId) && getPreferences().getBoolean(getEnabledKey(dialogId), false);
    }

    public void setEncryptionEnabled(long dialogId, boolean enabled) {
        if (!supportsDialog(dialogId)) {
            return;
        }
        SharedPreferences.Editor editor = getPreferences().edit();
        if (enabled) {
            editor.putBoolean(getEnabledKey(dialogId), true);
            if (TextUtils.isEmpty(getPreferences().getString(getDialogKeyKey(dialogId), null))) {
                editor.putString(getDialogKeyKey(dialogId), DEFAULT_DIALOG_KEY);
            }
        } else {
            editor.remove(getEnabledKey(dialogId));
        }
        editor.apply();
    }

    public boolean isPeerDetected(long dialogId) {
        return supportsDialog(dialogId) && getPreferences().getBoolean(getPeerDetectedKey(dialogId), false);
    }

    public void markPeerDetected(long dialogId) {
        if (!supportsDialog(dialogId) || isPeerDetected(dialogId)) {
            return;
        }
        getPreferences().edit().putBoolean(getPeerDetectedKey(dialogId), true).apply();
    }

    public boolean looksLikeEncryptedPayload(String text) {
        return !TextUtils.isEmpty(text) && text.startsWith(PAYLOAD_PREFIX);
    }

    public String encryptOutgoingText(long dialogId, String text) {
        if (!supportsDialog(dialogId) || TextUtils.isEmpty(text) || looksLikeEncryptedPayload(text)) {
            return text;
        }
        try {
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, buildSecretKey(getDialogKey(dialogId)), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PAYLOAD_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public String getDisplayText(long dialogId, String text, boolean incoming) {
        if (!supportsDialog(dialogId) || TextUtils.isEmpty(text) || !looksLikeEncryptedPayload(text)) {
            return text;
        }
        try {
            byte[] payload = Base64.decode(text.substring(PAYLOAD_PREFIX.length()), Base64.DEFAULT);
            if (payload.length <= 16) {
                return text;
            }

            byte[] iv = new byte[16];
            byte[] encrypted = new byte[payload.length - 16];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, buildSecretKey(getDialogKey(dialogId)), new IvParameterSpec(iv));
            String decrypted = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            if (incoming) {
                markPeerDetected(dialogId);
            }
            return decrypted;
        } catch (Exception e) {
            return text;
        }
    }

    private SecretKeySpec buildSecretKey(String rawKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    private String getDialogKey(long dialogId) {
        return getPreferences().getString(getDialogKeyKey(dialogId), DEFAULT_DIALOG_KEY);
    }

    private SharedPreferences getPreferences() {
        return MessagesController.getMainSettings(currentAccount);
    }

    private String getEnabledKey(long dialogId) {
        return KEY_ENABLED_PREFIX + dialogId;
    }

    private String getDialogKeyKey(long dialogId) {
        return KEY_DIALOG_KEY_PREFIX + dialogId;
    }

    private String getPeerDetectedKey(long dialogId) {
        return KEY_PEER_DETECTED_PREFIX + dialogId;
    }
}
