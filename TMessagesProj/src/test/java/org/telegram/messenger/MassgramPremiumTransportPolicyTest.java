package org.telegram.messenger;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MassgramPremiumTransportPolicyTest {

    @Test
    public void plainTextMessagesStayPlainWithoutExplicitMassgramPayloadRequest() {
        assertFalse(MassgramPremiumTransportPolicy.shouldEncodeNewTextAsPayload(false, "hello"));
        assertFalse(MassgramPremiumTransportPolicy.shouldEncodeNewTextAsPayload(false, "Tasks\n- item 1"));
    }

    @Test
    public void editedMassgramPayloadMessagesStayEncoded() {
        String currentPayload = MassgramPremiumMessageCodec.encodeText("Tasks\n- item 1");

        assertTrue(MassgramPremiumTransportPolicy.shouldEncodeEditedTextAsPayload(currentPayload, "Tasks\n- item 2"));
        assertFalse(MassgramPremiumTransportPolicy.shouldEncodeEditedTextAsPayload("plain text", "Tasks\n- item 2"));
    }

    @Test
    public void localPremiumUnlockCountsAsFeatureAccess() {
        assertTrue(MassgramPremiumTransportPolicy.canUsePremiumFeatures(false, true));
        assertTrue(MassgramPremiumTransportPolicy.canUsePremiumFeatures(true, false));
        assertFalse(MassgramPremiumTransportPolicy.canUsePremiumFeatures(false, false));
    }
}
