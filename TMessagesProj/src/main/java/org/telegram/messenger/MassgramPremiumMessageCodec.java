package org.telegram.messenger;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class MassgramPremiumMessageCodec {

    public static final String PLACEHOLDER_TEXT = "Unsupported Massgram message";

    private static final String MARKER = "\u2063\u2061\u2062\u2060";
    private static final String TYPE_PREMIUM_TEXT = "premium_text";
    private static final String TYPE_PREMIUM_STICKER = "premium_sticker";
    private static final String PROTOCOL_PREFIX = "MGP1:";
    private static final String TEXT_PROTOCOL_PREFIX = PROTOCOL_PREFIX + TYPE_PREMIUM_TEXT + ":";
    private static final String STICKER_PROTOCOL_PREFIX = PROTOCOL_PREFIX + TYPE_PREMIUM_STICKER + ":";
    private static final char[] INVISIBLE_ALPHABET = {'\u2060', '\u2061', '\u2062', '\u2063'};

    private MassgramPremiumMessageCodec() {
    }

    public static String encodeText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String json = "{\"text\":\"" + escapeJson(text) + "\",\"format\":\"plain\"}";
        return wrapProtocol(TEXT_PROTOCOL_PREFIX + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
    }

    public static String encodeSticker(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        byte[] serializedDocument = serializeDocument(document);
        if (serializedDocument == null || serializedDocument.length == 0) {
            return null;
        }
        return wrapProtocol(STICKER_PROTOCOL_PREFIX + Base64.getEncoder().encodeToString(serializedDocument));
    }

    public static boolean hasPayload(String text) {
        return decode(text) != null;
    }

    public static String getVisibleText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int markerIndex = text.indexOf(MARKER);
        if (markerIndex < 0) {
            return text;
        }
        return text.substring(0, markerIndex);
    }

    public static DecodedPayload decode(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int markerIndex = text.indexOf(MARKER);
        if (markerIndex < 0) {
            return null;
        }
        String invisibleData = text.substring(markerIndex + MARKER.length());
        byte[] protocolBytes = decodeInvisible(invisibleData);
        if (protocolBytes == null || protocolBytes.length == 0) {
            return null;
        }
        String protocol = new String(protocolBytes, StandardCharsets.UTF_8);
        if (protocol.startsWith(TEXT_PROTOCOL_PREFIX)) {
            String base64Json = protocol.substring(TEXT_PROTOCOL_PREFIX.length());
            String json;
            try {
                json = new String(Base64.getDecoder().decode(base64Json), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignore) {
                return null;
            }
            String decodedText;
            try {
                decodedText = extractJsonString(json, "text");
            } catch (RuntimeException ignore) {
                return null;
            }
            if (decodedText == null || decodedText.isEmpty()) {
                return null;
            }
            return new DecodedPayload(TYPE_PREMIUM_TEXT, decodedText, null);
        }
        if (protocol.startsWith(STICKER_PROTOCOL_PREFIX)) {
            String base64Document = protocol.substring(STICKER_PROTOCOL_PREFIX.length());
            byte[] serializedDocument;
            try {
                serializedDocument = Base64.getDecoder().decode(base64Document);
            } catch (IllegalArgumentException ignore) {
                return null;
            }
            TLRPC.Document document = deserializeDocument(serializedDocument);
            if (document == null) {
                return null;
            }
            return new DecodedPayload(TYPE_PREMIUM_STICKER, null, document);
        }
        return null;
    }

    private static String encodeInvisible(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 4);
        for (byte current : bytes) {
            int value = current & 0xFF;
            builder.append(INVISIBLE_ALPHABET[(value >> 6) & 0x03]);
            builder.append(INVISIBLE_ALPHABET[(value >> 4) & 0x03]);
            builder.append(INVISIBLE_ALPHABET[(value >> 2) & 0x03]);
            builder.append(INVISIBLE_ALPHABET[value & 0x03]);
        }
        return builder.toString();
    }

    private static byte[] decodeInvisible(String value) {
        if (value == null || value.isEmpty() || value.length() % 4 != 0) {
            return null;
        }
        byte[] bytes = new byte[value.length() / 4];
        for (int i = 0, outIndex = 0; i < value.length(); i += 4, outIndex++) {
            int a = indexOfInvisibleChar(value.charAt(i));
            int b = indexOfInvisibleChar(value.charAt(i + 1));
            int c = indexOfInvisibleChar(value.charAt(i + 2));
            int d = indexOfInvisibleChar(value.charAt(i + 3));
            if (a < 0 || b < 0 || c < 0 || d < 0) {
                return null;
            }
            bytes[outIndex] = (byte) ((a << 6) | (b << 4) | (c << 2) | d);
        }
        return bytes;
    }

    private static int indexOfInvisibleChar(char value) {
        for (int i = 0; i < INVISIBLE_ALPHABET.length; i++) {
            if (INVISIBLE_ALPHABET[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                    break;
            }
        }
        return builder.toString();
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        int index = start + pattern.length();
        StringBuilder builder = new StringBuilder();
        while (index < json.length()) {
            char ch = json.charAt(index++);
            if (ch == '\\') {
                if (index >= json.length()) {
                    return null;
                }
                char escaped = json.charAt(index++);
                switch (escaped) {
                    case '\\':
                    case '"':
                    case '/':
                        builder.append(escaped);
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        if (index + 3 >= json.length()) {
                            return null;
                        }
                        String hex = json.substring(index, index + 4);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException ignore) {
                            return null;
                        }
                        index += 4;
                        break;
                    default:
                        return null;
                }
            } else if (ch == '"') {
                return builder.toString();
            } else {
                builder.append(ch);
            }
        }
        return null;
    }

    private static String wrapProtocol(String protocol) {
        return PLACEHOLDER_TEXT + MARKER + encodeInvisible(protocol.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] serializeDocument(TLRPC.Document document) {
        try {
            TLRPC.TL_document payloadDocument = new TLRPC.TL_document();
            payloadDocument.id = document.id;
            payloadDocument.access_hash = document.access_hash;
            payloadDocument.file_reference = document.file_reference != null ? document.file_reference : new byte[0];
            payloadDocument.date = document.date;
            payloadDocument.mime_type = document.mime_type != null ? document.mime_type : "";
            payloadDocument.size = document.size;
            payloadDocument.dc_id = document.dc_id;
            if (document.thumbs != null) {
                payloadDocument.thumbs.addAll(document.thumbs);
            }
            if (document.video_thumbs != null) {
                payloadDocument.video_thumbs.addAll(document.video_thumbs);
            }
            if (document.attributes != null) {
                payloadDocument.attributes.addAll(document.attributes);
            }
            if (!payloadDocument.thumbs.isEmpty()) {
                payloadDocument.flags |= 1;
            }
            if (!payloadDocument.video_thumbs.isEmpty()) {
                payloadDocument.flags |= 2;
            }
            SerializedData data = new SerializedData(payloadDocument.getObjectSize());
            payloadDocument.serializeToStream(data);
            return data.toByteArray();
        } catch (Exception e) {
            logCodecError(e);
            return null;
        }
    }

    private static TLRPC.Document deserializeDocument(byte[] serializedDocument) {
        if (serializedDocument == null || serializedDocument.length == 0) {
            return null;
        }
        try {
            SerializedData data = new SerializedData(serializedDocument);
            return TLRPC.Document.TLdeserialize(data, data.readInt32(true), true);
        } catch (Exception e) {
            logCodecError(e);
            return null;
        }
    }

    private static void logCodecError(Exception e) {
        try {
            FileLog.e(e);
        } catch (Throwable ignore) {
            // Unit tests do not initialize the Android application logger.
        }
    }

    public static final class DecodedPayload {
        public final String type;
        public final String text;
        public final TLRPC.Document document;

        public DecodedPayload(String type, String text, TLRPC.Document document) {
            this.type = type;
            this.text = text;
            this.document = document;
        }
    }
}
