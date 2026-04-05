package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

public final class MassgramPremiumMessageCodecHarness {

    public static void main(String[] args) {
        shouldKeepOnlyPlaceholderVisible();
        shouldRestoreOriginalHiddenText();
        shouldEncodePremiumStickerWithPlaceholder();
        shouldIgnorePlainText();
        shouldIgnoreMalformedPayload();
    }

    private static void shouldKeepOnlyPlaceholderVisible() {
        String encoded = MassgramPremiumMessageCodec.encodeText("Tasks\n- item 1\n- item 2");
        assertNotNull(encoded, "encoded message");
        assertEquals(MassgramPremiumMessageCodec.PLACEHOLDER_TEXT, MassgramPremiumMessageCodec.getVisibleText(encoded), "visible placeholder");
        assertTrue(MassgramPremiumMessageCodec.hasPayload(encoded), "payload marker");
    }

    private static void shouldRestoreOriginalHiddenText() {
        String original = "Tasks\n- item 1\n- item 2";
        String encoded = MassgramPremiumMessageCodec.encodeText(original);
        MassgramPremiumMessageCodec.DecodedPayload payload = MassgramPremiumMessageCodec.decode(encoded);
        assertNotNull(payload, "decoded payload");
        assertEquals("premium_text", payload.type, "payload type");
        assertEquals(original, payload.text, "decoded text");
    }

    private static void shouldEncodePremiumStickerWithPlaceholder() {
        TLRPC.TL_document document = createPremiumStickerDocument();
        String encoded = MassgramPremiumMessageCodec.encodeSticker(document);

        assertNotNull(encoded, "encoded sticker payload");
        assertEquals(MassgramPremiumMessageCodec.PLACEHOLDER_TEXT, MassgramPremiumMessageCodec.getVisibleText(encoded), "sticker placeholder");
        assertTrue(encoded.length() > MassgramPremiumMessageCodec.PLACEHOLDER_TEXT.length(), "sticker payload suffix exists");
        assertTrue(encoded.contains("\u2063\u2061\u2062\u2060"), "sticker invisible marker exists");
    }

    private static void shouldIgnorePlainText() {
        assertTrue(!MassgramPremiumMessageCodec.hasPayload("Unsupported Massgram message"), "plain placeholder should not have payload");
        assertEquals("Plain", MassgramPremiumMessageCodec.getVisibleText("Plain"), "plain visible text");
        assertTrue(MassgramPremiumMessageCodec.decode("Plain") == null, "plain text decode should be null");
    }

    private static void shouldIgnoreMalformedPayload() {
        String malformed = MassgramPremiumMessageCodec.PLACEHOLDER_TEXT + "\u2063\u2061\u2062\u2060"
            + "\u2061\u2060\u2063\u2062\u2060\u2060\u2063\u2061\u2060\u2060\u2061\u2061\u2060\u2061\u2061\u2060"
            + "\u2061\u2061\u2060\u2063\u2062\u2061\u2060\u2060\u2061\u2061\u2061\u2061\u2063\u2060\u2060\u2060"
            + "\u2061\u2060\u2061\u2061\u2061\u2062\u2060\u2063\u2063\u2060\u2061\u2061";

        assertTrue(MassgramPremiumMessageCodec.decode(malformed) == null, "malformed payload should decode to null");
        assertEquals(MassgramPremiumMessageCodec.PLACEHOLDER_TEXT, MassgramPremiumMessageCodec.getVisibleText(malformed), "malformed placeholder");
    }

    private static void assertEquals(String expected, String actual, String name) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(name + " mismatch. expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNotNull(Object value, String name) {
        if (value == null) {
            throw new AssertionError(name + " must not be null");
        }
    }

    private static void assertTrue(boolean value, String name) {
        if (!value) {
            throw new AssertionError(name + " assertion failed");
        }
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
