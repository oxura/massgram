package org.telegram.messenger;

public final class MassgramPremiumTransportPolicy {

    private MassgramPremiumTransportPolicy() {
    }

    public static boolean shouldEncodeNewTextAsPayload(boolean explicitPayloadRequested, String message) {
        return explicitPayloadRequested
            && !isEmpty(message)
            && !MassgramPremiumMessageCodec.hasPayload(message);
    }

    public static boolean shouldEncodeEditedTextAsPayload(String currentMessage, String editedMessage) {
        return !isEmpty(editedMessage)
            && MassgramPremiumMessageCodec.hasPayload(currentMessage)
            && !MassgramPremiumMessageCodec.hasPayload(editedMessage);
    }

    public static boolean canUsePremiumFeatures(boolean actualPremium, boolean massgramPremiumToggle) {
        return actualPremium || massgramPremiumToggle;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
