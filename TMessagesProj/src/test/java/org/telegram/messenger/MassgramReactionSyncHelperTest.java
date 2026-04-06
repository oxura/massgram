package org.telegram.messenger;

import org.junit.Test;
import org.telegram.tgnet.TLRPC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MassgramReactionSyncHelperTest {

    @Test
    public void applyReactionStateAddsChosenCustomReactionForSelf() {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = 55;
        message.dialog_id = 100L;

        java.util.ArrayList<TLRPC.Reaction> reactions = new java.util.ArrayList<>();
        TLRPC.TL_reactionCustomEmoji customEmoji = new TLRPC.TL_reactionCustomEmoji();
        customEmoji.document_id = 777L;
        reactions.add(customEmoji);

        MassgramPremiumMessageCodec.ReactionStatePayload payload =
            new MassgramPremiumMessageCodec.ReactionStatePayload(55, 42L, reactions);

        boolean changed = MassgramReactionSyncHelper.applyReactionState(message, payload, 42L);

        assertTrue(changed);
        assertNotNull(message.reactions);
        assertEquals(1, message.reactions.results.size());
        assertTrue(message.reactions.results.get(0).chosen);
        assertEquals(1, message.reactions.results.get(0).count);
        assertEquals(1, message.reactions.recent_reactions.size());
        assertTrue(message.reactions.recent_reactions.get(0).reaction instanceof TLRPC.TL_reactionCustomEmoji);
        assertEquals(777L, ((TLRPC.TL_reactionCustomEmoji) message.reactions.recent_reactions.get(0).reaction).document_id);
    }

    @Test
    public void applyReactionStateReplacesPreviousReactionForActor() {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = 55;
        message.dialog_id = 100L;
        message.reactions = new TLRPC.TL_messageReactions();
        message.reactions.can_see_list = true;

        TLRPC.TL_reactionEmoji oldReaction = new TLRPC.TL_reactionEmoji();
        oldReaction.emoticon = "\u2764";
        TLRPC.TL_reactionCount oldCount = new TLRPC.TL_reactionCount();
        oldCount.reaction = oldReaction;
        oldCount.count = 1;
        message.reactions.results.add(oldCount);

        TLRPC.TL_messagePeerReaction oldPeerReaction = new TLRPC.TL_messagePeerReaction();
        oldPeerReaction.peer_id = createPeer(7L);
        oldPeerReaction.reaction = oldReaction;
        message.reactions.recent_reactions.add(oldPeerReaction);

        java.util.ArrayList<TLRPC.Reaction> reactions = new java.util.ArrayList<>();
        TLRPC.TL_reactionCustomEmoji customEmoji = new TLRPC.TL_reactionCustomEmoji();
        customEmoji.document_id = 777L;
        reactions.add(customEmoji);

        MassgramPremiumMessageCodec.ReactionStatePayload payload =
            new MassgramPremiumMessageCodec.ReactionStatePayload(55, 7L, reactions);

        boolean changed = MassgramReactionSyncHelper.applyReactionState(message, payload, 42L);

        assertTrue(changed);
        assertEquals(1, message.reactions.results.size());
        assertTrue(message.reactions.results.get(0).reaction instanceof TLRPC.TL_reactionCustomEmoji);
        assertEquals(1, message.reactions.results.get(0).count);
        assertFalse(message.reactions.results.get(0).chosen);
        assertEquals(1, message.reactions.recent_reactions.size());
        assertEquals(7L, MessageObject.getPeerId(message.reactions.recent_reactions.get(0).peer_id));
    }

    @Test
    public void applyReactionStateWithEmptySelectionRemovesActorsReaction() {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = 55;
        message.dialog_id = 100L;
        message.reactions = new TLRPC.TL_messageReactions();
        message.reactions.can_see_list = true;

        TLRPC.TL_reactionCustomEmoji customEmoji = new TLRPC.TL_reactionCustomEmoji();
        customEmoji.document_id = 777L;
        TLRPC.TL_reactionCount count = new TLRPC.TL_reactionCount();
        count.reaction = customEmoji;
        count.count = 1;
        message.reactions.results.add(count);

        TLRPC.TL_messagePeerReaction peerReaction = new TLRPC.TL_messagePeerReaction();
        peerReaction.peer_id = createPeer(7L);
        peerReaction.reaction = customEmoji;
        message.reactions.recent_reactions.add(peerReaction);

        MassgramPremiumMessageCodec.ReactionStatePayload payload =
            new MassgramPremiumMessageCodec.ReactionStatePayload(55, 7L, new java.util.ArrayList<>());

        boolean changed = MassgramReactionSyncHelper.applyReactionState(message, payload, 42L);

        assertTrue(changed);
        assertTrue(message.reactions.results.isEmpty());
        assertTrue(message.reactions.recent_reactions.isEmpty());
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
