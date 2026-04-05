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
    public void encodeTodoRestoresNativeTodoPayload() {
        TLRPC.TL_messageMediaToDo todo = createTodoMedia();

        String encoded = MassgramPremiumMessageCodec.encodeTodo(todo);
        MassgramPremiumMessageCodec.DecodedPayload payload = MassgramPremiumMessageCodec.decode(encoded);

        assertNotNull(encoded);
        assertEquals(MassgramPremiumMessageCodec.PLACEHOLDER_TEXT, MassgramPremiumMessageCodec.getVisibleText(encoded));
        assertNotNull(payload);
        assertEquals("premium_todo", payload.type);
        assertNotNull(payload.todo);
        assertEquals("test", payload.todo.todo.title.text);
        assertEquals(2, payload.todo.todo.list.size());
        assertEquals("test", payload.todo.todo.list.get(0).title.text);
        assertEquals("test1", payload.todo.todo.list.get(1).title.text);
    }

    @Test
    public void decodeRestoresCustomEmojiEntitiesForPremiumRichText() {
        String original = "\uD83D\uDE80 launch";
        TLRPC.TL_messageEntityCustomEmoji entity = new TLRPC.TL_messageEntityCustomEmoji();
        entity.offset = 0;
        entity.length = 2;
        entity.document_id = 777L;
        entity.document = createPremiumCustomEmojiDocument(false);

        java.util.ArrayList<TLRPC.MessageEntity> entities = new java.util.ArrayList<>();
        entities.add(entity);

        String encoded = MassgramPremiumMessageCodec.encodeText(original, entities);
        MassgramPremiumMessageCodec.DecodedPayload payload = MassgramPremiumMessageCodec.decode(encoded);

        assertNotNull(payload);
        assertEquals(original, payload.text);
        assertNotNull(payload.entities);
        assertEquals(1, payload.entities.size());
        assertTrue(payload.entities.get(0) instanceof TLRPC.TL_messageEntityCustomEmoji);
        assertEquals(777L, ((TLRPC.TL_messageEntityCustomEmoji) payload.entities.get(0)).document_id);
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

    static TLRPC.TL_document createPremiumStickerDocument() {
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

    static TLRPC.TL_document createPremiumCustomEmojiDocument(boolean free) {
        TLRPC.TL_document document = new TLRPC.TL_document();
        document.id = 777L;
        document.access_hash = 123L;
        document.file_reference = new byte[] {4, 5, 6};
        document.date = 10;
        document.mime_type = "application/x-tgsticker";
        document.size = 1024;
        document.dc_id = 2;

        TLRPC.TL_documentAttributeCustomEmoji customEmoji = new TLRPC.TL_documentAttributeCustomEmoji();
        customEmoji.free = free;
        customEmoji.alt = "\uD83D\uDE80";
        customEmoji.stickerset = new TLRPC.TL_inputStickerSetEmpty();
        document.attributes.add(customEmoji);
        return document;
    }

    private static TLRPC.TL_messageMediaToDo createTodoMedia() {
        TLRPC.TL_messageMediaToDo todo = new TLRPC.TL_messageMediaToDo();
        todo.todo = new TLRPC.TodoList();
        todo.todo.title = new TLRPC.TL_textWithEntities();
        todo.todo.title.text = "test";

        TLRPC.TodoItem first = new TLRPC.TodoItem();
        first.id = 1;
        first.title = new TLRPC.TL_textWithEntities();
        first.title.text = "test";
        todo.todo.list.add(first);

        TLRPC.TodoItem second = new TLRPC.TodoItem();
        second.id = 2;
        second.title = new TLRPC.TL_textWithEntities();
        second.title.text = "test1";
        todo.todo.list.add(second);
        return todo;
    }
}
