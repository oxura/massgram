package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class MassgramReactionSyncManager {

    private static final int MAX_STORED_STATES = 512;
    private static final Object[] LOCKS = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    private static final MassgramReactionSyncManager[] INSTANCES = new MassgramReactionSyncManager[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int i = 0; i < LOCKS.length; i++) {
            LOCKS[i] = new Object();
        }
    }

    public static MassgramReactionSyncManager getInstance(int currentAccount) {
        MassgramReactionSyncManager instance = INSTANCES[currentAccount];
        if (instance == null) {
            synchronized (LOCKS[currentAccount]) {
                instance = INSTANCES[currentAccount];
                if (instance == null) {
                    instance = new MassgramReactionSyncManager(currentAccount);
                    INSTANCES[currentAccount] = instance;
                }
            }
        }
        return instance;
    }

    private final SharedPreferences preferences;
    private final HashMap<String, StoredReactionState> states = new HashMap<>();

    private MassgramReactionSyncManager(int currentAccount) {
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("massgram_reaction_sync_" + currentAccount, Context.MODE_PRIVATE);
        loadStates();
    }

    public synchronized void storeReactionState(long dialogId, MassgramPremiumMessageCodec.ReactionStatePayload payload, String encodedPayload) {
        if (payload == null || dialogId == 0) {
            return;
        }
        String key = makeKey(dialogId, payload.targetMessageId, payload.actorPeerId);
        if (payload.reactions.isEmpty()) {
            states.remove(key);
            preferences.edit().remove(key).apply();
            return;
        }
        states.put(key, new StoredReactionState(dialogId, payload, encodedPayload));
        SharedPreferences.Editor editor = preferences.edit().putString(key, encodedPayload);
        if (states.size() > MAX_STORED_STATES) {
            String oldestKey = findOldestKey();
            if (oldestKey != null) {
                states.remove(oldestKey);
                editor.remove(oldestKey);
            }
        }
        editor.apply();
    }

    public synchronized boolean applyStoredState(long dialogId, MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        return applyStoredState(dialogId, messageObject.messageOwner, UserConfig.getInstance(messageObject.currentAccount).getClientUserId());
    }

    public synchronized boolean applyStoredState(long dialogId, org.telegram.tgnet.TLRPC.Message message, long selfPeerId) {
        if (message == null || dialogId == 0 || message.id == 0) {
            return false;
        }
        boolean changed = false;
        ArrayList<StoredReactionState> matchingStates = new ArrayList<>();
        for (StoredReactionState storedState : states.values()) {
            if (storedState.dialogId == dialogId && storedState.payload.targetMessageId == message.id) {
                matchingStates.add(storedState);
            }
        }
        for (int i = 0; i < matchingStates.size(); i++) {
            changed |= MassgramReactionSyncHelper.applyReactionState(message, matchingStates.get(i).payload, selfPeerId);
        }
        return changed;
    }

    private void loadStates() {
        Map<String, ?> all = preferences.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (!(entry.getValue() instanceof String)) {
                continue;
            }
            String encodedPayload = (String) entry.getValue();
            MassgramPremiumMessageCodec.DecodedPayload decodedPayload = MassgramPremiumMessageCodec.decode(encodedPayload);
            if (decodedPayload == null || decodedPayload.reactionState == null) {
                continue;
            }
            long dialogId = parseDialogId(entry.getKey());
            if (dialogId == 0) {
                continue;
            }
            states.put(entry.getKey(), new StoredReactionState(dialogId, decodedPayload.reactionState, encodedPayload));
        }
    }

    private String findOldestKey() {
        String oldestKey = null;
        StoredReactionState oldestState = null;
        for (Map.Entry<String, StoredReactionState> entry : states.entrySet()) {
            if (oldestState == null || entry.getValue().payload.targetMessageId < oldestState.payload.targetMessageId) {
                oldestState = entry.getValue();
                oldestKey = entry.getKey();
            }
        }
        return oldestKey;
    }

    private static String makeKey(long dialogId, int targetMessageId, long actorPeerId) {
        return dialogId + ":" + targetMessageId + ":" + actorPeerId;
    }

    private static long parseDialogId(String key) {
        int dividerIndex = key.indexOf(':');
        if (dividerIndex <= 0) {
            return 0;
        }
        try {
            return Long.parseLong(key.substring(0, dividerIndex));
        } catch (Exception ignore) {
            return 0;
        }
    }

    private static final class StoredReactionState {
        private final long dialogId;
        private final MassgramPremiumMessageCodec.ReactionStatePayload payload;
        private final String encodedPayload;

        private StoredReactionState(long dialogId, MassgramPremiumMessageCodec.ReactionStatePayload payload, String encodedPayload) {
            this.dialogId = dialogId;
            this.payload = payload;
            this.encodedPayload = encodedPayload;
        }
    }
}
