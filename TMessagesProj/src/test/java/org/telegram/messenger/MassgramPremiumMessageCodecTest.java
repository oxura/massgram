package org.telegram.messenger;

import org.junit.Test;
import org.telegram.tgnet.TLRPC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MassgramPremiumMessageCodecTest {

    @Test
    public void encodeTodoTextKeepsOnlyPlaceholderVisible() {
        String encoded = MassgramPremiumMessageCodec.encodeText("Tasks\n- item 1\n- item 2");

        assertNotNull(encoded);
        assertEquals(MassgramPremiumMessageCodec.PLACEHOLDER_TEXT, MassgramPremiumMessageCodec.getVisibleText(encoded));
        assertTrue(MassgramPremiumMessageCodec.hasPayload(encoded));
    }

    @Test
    public void decodeRestoresOriginalHiddenText() {
        String original = "Tasks\n- item 1\n- item 2";
        String encoded = MassgramPremiumMessageCodec.encodeText(original);

        MassgramPremiumMessageCodec.DecodedPayload payload = MassgramPremiumMessageCodec.decode(encoded);

        assertNotNull(payload);
        assertEquals("premium_text", payload.type);
        assertEquals(original, payload.text);
    }

    @Test
    public void encodeStickerKeepsOnlyPlaceholderVisible() {
        TLRPC.TL_document document = createPremiumStickerDocument();

        String encoded = MassgramPremiumMessageCodec.encodeSticker(document);

        assertNotNull(encoded);
        assertEquals(MassgramPremiumMessageCodec.PLACEHOLDER_TEXT, MassgramPremiumMessageCodec.getVisibleText(encoded));
        assertTrue(encoded.length() > MassgramPremiumMessageCodec.PLACEHOLDER_TEXT.length());
        assertTrue(encoded.contains("\u2063\u2061\u2062\u2060"));
    }

    @Test
    public void plainTextHasNoPayload() {
        assertFalse(MassgramPremiumMessageCodec.hasPayload("Unsupported Massgram message"));
        assertEquals("Plain", MassgramPremiumMessageCodec.getVisibleText("Plain"));
        assertEquals(null, MassgramPremiumMessageCodec.decode("Plain"));
    }

    @Test
    public void malformedPayloadDoesNotThrowAndFallsBackToNull() {
        String malformed = MassgramPremiumMessageCodec.PLACEHOLDER_TEXT + "\u2063\u2061\u2062\u2060"
            + "\u2061\u2060\u2063\u2062\u2060\u2060\u2063\u2061\u2060\u2060\u2061\u2061\u2060\u2061\u2061\u2060"
            + "\u2061\u2061\u2060\u2063\u2062\u2061\u2060\u2060\u2061\u2061\u2061\u2061\u2063\u2060\u2060\u2060"
            + "\u2061\u2060\u2061\u2061\u2061\u2062\u2060\u2063\u2063\u2060\u2061\u2061";

        assertNull(MassgramPremiumMessageCodec.decode(malformed));
        assertEquals(MassgramPremiumMessageCodec.PLACEHOLDER_TEXT, MassgramPremiumMessageCodec.getVisibleText(malformed));
    }

    private static TLRPC.TL_document createPremiumStickerDocument() {
        TLRPC.TL_document document = new TLRPC.TL_document();
        document.id = 42L;
        document.access_hash = 84L;
        document.file_reference = new byte[] {1, 2, 3};
        document.date = 10;
        document.mime_type = "video/webm";
        document.size = 2048;
        document.dc_id = 4;
        document.flags = 3;

        TLRPC.TL_photoSizeEmpty thumb = new TLRPC.TL_photoSizeEmpty();
        thumb.type = "s";
        document.thumbs.add(thumb);

        TLRPC.TL_videoSize effect = new TLRPC.TL_videoSize();
        effect.type = "f";
        effect.w = 512;
        effect.h = 512;
        effect.size = 128;
        document.video_thumbs.add(effect);

        TLRPC.TL_documentAttributeSticker stickerAttribute = new TLRPC.TL_documentAttributeSticker();
        stickerAttribute.alt = "sticker";
        stickerAttribute.stickerset = new TLRPC.TL_inputStickerSetEmpty();
        document.attributes.add(stickerAttribute);

        TLRPC.TL_documentAttributeVideo videoAttribute = new TLRPC.TL_documentAttributeVideo();
        videoAttribute.w = 512;
        videoAttribute.h = 512;
        videoAttribute.duration = 1;
        document.attributes.add(videoAttribute);

        return document;
    }
}
