package org.telegram.messenger;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public final class MassgramReactionSyncHelper {

    private MassgramReactionSyncHelper() {
    }

    public static TLRPC.TL_messageReactions cloneReactions(TLRPC.TL_messageReactions reactions) {
        if (reactions == null) {
            return null;
        }
        SerializedData data = null;
        try {
            data = new SerializedData(reactions.getObjectSize());
            reactions.serializeToStream(data);
            SerializedData in = new SerializedData(data.toByteArray());
            try {
                return TLRPC.TL_messageReactions.TLdeserialize(in, in.readInt32(true), true);
            } finally {
                in.cleanup();
            }
        } catch (Exception e) {
            try {
                FileLog.e(e);
            } catch (Throwable ignore) {
                // Unit tests do not initialize the Android application logger.
            }
            return null;
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    public static boolean applyReactionState(TLRPC.Message message, MassgramPremiumMessageCodec.ReactionStatePayload payload, long selfPeerId) {
        if (message == null || payload == null || message.id != payload.targetMessageId) {
            return false;
        }
        if (message.reactions == null) {
            message.reactions = new TLRPC.TL_messageReactions();
        }
        message.reactions.can_see_list = true;

        ArrayList<TLRPC.Reaction> oldActorReactions = new ArrayList<>();
        for (int i = 0; i < message.reactions.recent_reactions.size(); i++) {
            TLRPC.MessagePeerReaction peerReaction = message.reactions.recent_reactions.get(i);
            if (MessageObject.getPeerId(peerReaction.peer_id) == payload.actorPeerId
                && (peerReaction.reaction instanceof TLRPC.TL_reactionEmoji || peerReaction.reaction instanceof TLRPC.TL_reactionCustomEmoji)) {
                oldActorReactions.add(peerReaction.reaction);
            }
        }

        boolean changed = !sameReactionSet(oldActorReactions, payload.reactions);

        for (int i = 0; i < oldActorReactions.size(); i++) {
            TLRPC.Reaction oldReaction = oldActorReactions.get(i);
            if (containsReaction(payload.reactions, oldReaction)) {
                continue;
            }
            decrementReaction(message.reactions, oldReaction, payload.actorPeerId == selfPeerId);
        }

        for (int i = 0; i < payload.reactions.size(); i++) {
            TLRPC.Reaction newReaction = payload.reactions.get(i);
            TLRPC.ReactionCount reactionCount = findReactionCount(message.reactions, newReaction);
            boolean existedForActor = containsReaction(oldActorReactions, newReaction);
            if (reactionCount == null) {
                reactionCount = new TLRPC.TL_reactionCount();
                reactionCount.reaction = newReaction;
                message.reactions.results.add(reactionCount);
                changed = true;
            }
            if (!existedForActor) {
                reactionCount.count++;
                changed = true;
            }
            if (payload.actorPeerId == selfPeerId) {
                reactionCount.chosen = true;
                reactionCount.chosen_order = i + 1;
            }
        }

        for (int i = message.reactions.results.size() - 1; i >= 0; i--) {
            TLRPC.ReactionCount reactionCount = message.reactions.results.get(i);
            if (reactionCount.count <= 0) {
                message.reactions.results.remove(i);
                changed = true;
            }
        }

        for (int i = message.reactions.recent_reactions.size() - 1; i >= 0; i--) {
            TLRPC.MessagePeerReaction peerReaction = message.reactions.recent_reactions.get(i);
            if (MessageObject.getPeerId(peerReaction.peer_id) == payload.actorPeerId) {
                message.reactions.recent_reactions.remove(i);
                changed = true;
            }
        }
        for (int i = payload.reactions.size() - 1; i >= 0; i--) {
            TLRPC.TL_messagePeerReaction peerReaction = new TLRPC.TL_messagePeerReaction();
            peerReaction.peer_id = createPeer(payload.actorPeerId);
            peerReaction.reaction = payload.reactions.get(i);
            message.reactions.recent_reactions.add(0, peerReaction);
        }
        if (payload.actorPeerId == selfPeerId) {
            for (int i = 0; i < message.reactions.results.size(); i++) {
                TLRPC.ReactionCount reactionCount = message.reactions.results.get(i);
                if (!containsReaction(payload.reactions, reactionCount.reaction)) {
                    reactionCount.chosen = false;
                    reactionCount.chosen_order = 0;
                }
            }
        }

        return changed;
    }

    private static void decrementReaction(TLRPC.TL_messageReactions reactions, TLRPC.Reaction reaction, boolean clearChosen) {
        TLRPC.ReactionCount reactionCount = findReactionCount(reactions, reaction);
        if (reactionCount == null) {
            return;
        }
        reactionCount.count--;
        if (clearChosen) {
            reactionCount.chosen = false;
            reactionCount.chosen_order = 0;
        }
    }

    private static TLRPC.ReactionCount findReactionCount(TLRPC.TL_messageReactions reactions, TLRPC.Reaction reaction) {
        for (int i = 0; i < reactions.results.size(); i++) {
            TLRPC.ReactionCount reactionCount = reactions.results.get(i);
            if (reactionsEqual(reactionCount.reaction, reaction)) {
                return reactionCount;
            }
        }
        return null;
    }

    private static boolean containsReaction(ArrayList<TLRPC.Reaction> reactions, TLRPC.Reaction reaction) {
        for (int i = 0; i < reactions.size(); i++) {
            if (reactionsEqual(reactions.get(i), reaction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameReactionSet(ArrayList<TLRPC.Reaction> left, ArrayList<TLRPC.Reaction> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!reactionsEqual(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean reactionsEqual(TLRPC.Reaction left, TLRPC.Reaction right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof TLRPC.TL_reactionEmoji && right instanceof TLRPC.TL_reactionEmoji) {
            String leftEmoji = ((TLRPC.TL_reactionEmoji) left).emoticon;
            String rightEmoji = ((TLRPC.TL_reactionEmoji) right).emoticon;
            return leftEmoji == null ? rightEmoji == null : leftEmoji.equals(rightEmoji);
        }
        if (left instanceof TLRPC.TL_reactionCustomEmoji && right instanceof TLRPC.TL_reactionCustomEmoji) {
            return ((TLRPC.TL_reactionCustomEmoji) left).document_id == ((TLRPC.TL_reactionCustomEmoji) right).document_id;
        }
        return false;
    }

    private static TLRPC.Peer createPeer(long peerId) {
        if (peerId < 0) {
            TLRPC.TL_peerChat peer = new TLRPC.TL_peerChat();
            peer.chat_id = -peerId;
            return peer;
        }
        TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
        peer.user_id = peerId;
        return peer;
    }
}
