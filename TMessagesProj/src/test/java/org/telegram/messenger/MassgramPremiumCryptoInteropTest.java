package org.telegram.messenger;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.security.SecureRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MassgramPremiumCryptoInteropTest {

    @Test
    public void premiumPayloadRoundTripsThroughAesTransport() throws Exception {
        String premiumPayload = MassgramPremiumMessageCodec.encodeText("Tasks\n- item 1");
        String encryptedPayload = MassgramCryptoManager.encryptTextWithKey("Batyi0015", premiumPayload, new SecureRandom());

        String decryptedPayload = MassgramCryptoManager.decryptTextWithKey("Batyi0015", encryptedPayload);
        MassgramPremiumMessageCodec.DecodedPayload decodedPayload = MassgramPremiumMessageCodec.decode(decryptedPayload);

        assertNotNull(decodedPayload);
        assertEquals("Tasks\n- item 1", decodedPayload.text);
    }

    @Test
    public void plainTextRoundTripsThroughAesTransport() throws Exception {
        String encryptedText = MassgramCryptoManager.encryptTextWithKey("Batyi0015", "hello", new SecureRandom());
        String decryptedText = MassgramCryptoManager.decryptTextWithKey("Batyi0015", encryptedText);

        assertEquals("hello", decryptedText);
        assertTrue(encryptedText.startsWith("\uD83D\uDD12MG1:"));
    }

    @Test
    public void capabilityMarkerDoesNotCorruptExistingPremiumTextPayload() {
        String premiumPayload = MassgramPremiumMessageCodec.encodeText("Tasks\n- item 1");
        MassgramCryptoManager cryptoManager = createCryptoManager();

        assertTrue(MassgramPremiumMessageCodec.hasPayload(premiumPayload));
        String withMarker = cryptoManager.appendCapabilityMarker(123L, premiumPayload);

        assertEquals(premiumPayload, withMarker);
        assertTrue(MassgramPremiumMessageCodec.hasPayload(withMarker));
        assertFalse(cryptoManager.containsCapabilityMarker(withMarker));
    }

    private static MassgramCryptoManager createCryptoManager() {
        try {
            Constructor<MassgramCryptoManager> constructor = MassgramCryptoManager.class.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(0);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
