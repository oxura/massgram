package org.telegram.messenger;

import android.content.SharedPreferences;
import android.util.Base64;
import android.util.SparseArray;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.telegram.tgnet.TLRPC;

public class MassgramCryptoManager {

    private static final String KEY_ENABLED_PREFIX = "massgram_crypto_enabled_";
    private static final String KEY_DIALOG_KEY_PREFIX = "massgram_crypto_key_";
    private static final String KEY_PEER_DETECTED_PREFIX = "massgram_crypto_peer_detected_";

    private static final String DEFAULT_DIALOG_KEY = "Batyi0015";
    private static final String PAYLOAD_PREFIX = "\uD83D\uDD12MG1:";
    private static final String CAPABILITY_MARKER = "\u2063\u2060\u2062\u2063";

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

    public boolean supportsUserPeer(TLRPC.User user) {
        return user != null && supportsUserPeer(user.id, user.bot, UserObject.isUserSelf(user));
    }

    static boolean supportsUserPeer(long dialogId, boolean bot, boolean self) {
        return DialogObject.isUserDialog(dialogId) && !bot && !self;
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
            if (isEmpty(getPreferences().getString(getDialogKeyKey(dialogId), null))) {
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
        String sanitized = stripCapabilityMarker(text);
        return !isEmpty(sanitized) && sanitized.startsWith(PAYLOAD_PREFIX);
    }

    public boolean containsCapabilityMarker(String text) {
        return !isEmpty(text) && text.contains(CAPABILITY_MARKER);
    }

    public String appendCapabilityMarker(long dialogId, String text) {
        if (!supportsDialog(dialogId) || isEmpty(text) || looksLikeEncryptedPayload(text) || MassgramPremiumMessageCodec.hasPayload(text)) {
            return text;
        }
        String stripped = stripCapabilityMarker(text);
        if (isEmpty(stripped)) {
            return stripped;
        }
        return stripped + CAPABILITY_MARKER;
    }

    public String stripCapabilityMarker(String text) {
        if (isEmpty(text) || !text.contains(CAPABILITY_MARKER)) {
            return text;
        }
        return text.replace(CAPABILITY_MARKER, "");
    }

    public String encryptOutgoingText(long dialogId, String text) {
        String visibleText = stripCapabilityMarker(text);
        if (!supportsDialog(dialogId) || isEmpty(visibleText) || looksLikeEncryptedPayload(visibleText)) {
            return text;
        }
        try {
            return encryptTextWithKey(getDialogKey(dialogId), visibleText, secureRandom);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public MassgramPremiumMessageCodec.DecodedPayload decodePremiumPayload(long dialogId, String text, boolean incoming) {
        boolean supportsDialog = supportsDialog(dialogId);
        String resolvedText = resolveTransportText(dialogId, text, incoming, supportsDialog);
        MassgramPremiumMessageCodec.DecodedPayload premiumPayload = MassgramPremiumMessageCodec.decode(resolvedText);
        if (premiumPayload != null && incoming && supportsDialog) {
            markPeerDetected(dialogId);
        }
        return premiumPayload;
    }

    public String getDisplayText(long dialogId, String text, boolean incoming) {
        boolean supportsDialog = supportsDialog(dialogId);
        String resolvedText = resolveTransportText(dialogId, text, incoming, supportsDialog);
        MassgramPremiumMessageCodec.DecodedPayload premiumPayload = MassgramPremiumMessageCodec.decode(resolvedText);
        if (premiumPayload != null) {
            if (premiumPayload.text != null) {
                return premiumPayload.text;
            }
            return "";
        }
        String visibleText = MassgramPremiumMessageCodec.getVisibleText(resolvedText);
        if (visibleText != null && !visibleText.equals(resolvedText)) {
            return visibleText;
        }
        return resolvedText;
    }

    public String resolveStoredMessageText(long dialogId, String text) {
        return resolveTransportText(dialogId, text, false, supportsDialog(dialogId));
    }

    private String resolveTransportText(long dialogId, String text, boolean incoming, boolean supportsDialog) {
        boolean hasCapabilityMarker = containsCapabilityMarker(text);
        String sanitized = stripCapabilityMarker(text);
        if (incoming && supportsDialog && hasCapabilityMarker) {
            markPeerDetected(dialogId);
        }
        if (!supportsDialog || isEmpty(sanitized) || !looksLikeEncryptedPayload(sanitized)) {
            return sanitized;
        }
        try {
            String decrypted = decryptTextWithKey(getDialogKey(dialogId), sanitized);
            if (decrypted == null) {
                return sanitized;
            }
            if (incoming) {
                markPeerDetected(dialogId);
            }
            return decrypted;
        } catch (Exception e) {
            return sanitized;
        }
    }

    static String encryptTextWithKey(String rawKey, String text, SecureRandom secureRandom) throws Exception {
        if (text == null || text.length() == 0) {
            return text;
        }
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, buildSecretKey(rawKey), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        return PAYLOAD_PREFIX + java.util.Base64.getEncoder().withoutPadding().encodeToString(payload);
    }

    static String decryptTextWithKey(String rawKey, String payloadText) throws Exception {
        if (payloadText == null || payloadText.length() == 0 || !payloadText.startsWith(PAYLOAD_PREFIX)) {
            return payloadText;
        }
        byte[] payload = java.util.Base64.getDecoder().decode(payloadText.substring(PAYLOAD_PREFIX.length()));
        if (payload.length <= 16) {
            return null;
        }
        byte[] iv = new byte[16];
        byte[] encrypted = new byte[payload.length - 16];
        System.arraycopy(payload, 0, iv, 0, iv.length);
        System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, buildSecretKey(rawKey), new IvParameterSpec(iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKeySpec buildSecretKey(String rawKey) throws Exception {
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

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
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
