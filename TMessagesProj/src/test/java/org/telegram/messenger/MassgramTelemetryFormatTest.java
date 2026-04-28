package org.telegram.messenger;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MassgramTelemetryFormatTest {

    @Test
    public void fingerprintUsesThrowableTypeScreenAndTopFrame() {
        Throwable throwable = new IllegalStateException("Boom");
        throwable.setStackTrace(new StackTraceElement[]{
            new StackTraceElement("org.telegram.ui.ChatActivity", "scrollToLastMessage", "ChatActivity.java", 15280)
        });

        String fingerprint = MassgramTelemetryFormat.buildFingerprint("fatal", "chat", throwable);

        assertEquals("fatal|chat|IllegalStateException|org.telegram.ui.ChatActivity#scrollToLastMessage", fingerprint);
    }

    @Test
    public void breadcrumbsAreTrimmedToMostRecentEntries() {
        ArrayList<MassgramTelemetryFormat.Breadcrumb> breadcrumbs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            breadcrumbs.add(new MassgramTelemetryFormat.Breadcrumb(1_000L + i, "chat", "tap_" + i, null));
        }

        List<Map<String, Object>> encoded = MassgramTelemetryFormat.encodeBreadcrumbs(breadcrumbs);

        assertEquals(MassgramTelemetryFormat.MAX_BREADCRUMBS, encoded.size());
        assertEquals("tap_10", encoded.get(0).get("action"));
        assertEquals("tap_29", encoded.get(encoded.size() - 1).get("action"));
    }

    @Test
    public void stacktraceAndContextAreSanitized() {
        Throwable throwable = new IllegalArgumentException("Bad jump");
        throwable.setStackTrace(new StackTraceElement[]{
            new StackTraceElement("org.telegram.ui.ChatActivity", "onPageDownClicked", "ChatActivity.java", 10426),
            new StackTraceElement("org.telegram.ui.ChatActivity", "scrollToLastMessage", "ChatActivity.java", 15270),
            new StackTraceElement("org.telegram.ui.Components.RecyclerAnimationScrollHelper", "scrollToPosition", "RecyclerAnimationScrollHelper.java", 71),
        });

        ArrayList<MassgramTelemetryFormat.Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(new MassgramTelemetryFormat.Breadcrumb(10L, "chat", "open_chat",
            MassgramTelemetryFormat.mapOf("dialog_id", 123L, "message_text", "secret")));
        breadcrumbs.add(new MassgramTelemetryFormat.Breadcrumb(20L, "chat", "jump_to_bottom",
            MassgramTelemetryFormat.mapOf("unread_count", 88, "input_text", "hidden")));

        MassgramTelemetryFormat.TelemetryEvent event = MassgramTelemetryFormat.createEvent(
            42L,
            "fatal",
            "fatal",
            "chat",
            123L,
            "Massgram/11.7 beta",
            11700L,
            "beta",
            "Google Pixel 7",
            "Android 15",
            breadcrumbs,
            throwable,
            MassgramTelemetryFormat.mapOf(
                "unread_count", 88,
                "message_text", "must not leak",
                "query", "also remove"
            ),
            123456L
        );

        assertNotNull(event.fingerprint);
        assertEquals(2, event.breadcrumbs.size());
        assertEquals(3, event.stacktrace.size());
        assertEquals(88, event.context.get("unread_count"));
        assertFalse(event.context.containsKey("message_text"));
        assertFalse(event.context.containsKey("query"));
        assertFalse(event.breadcrumbs.get(0).containsKey("message_text"));
        assertFalse(event.breadcrumbs.get(1).containsKey("input_text"));
    }

    @Test
    public void queueRoundTripPreservesEvents() {
        MassgramTelemetryFormat.TelemetryEvent original = new MassgramTelemetryFormat.TelemetryEvent();
        original.eventId = "evt-1";
        original.userId = 42L;
        original.eventType = "fatal";
        original.severity = "fatal";
        original.fingerprint = "fatal|chat|IllegalStateException|ChatActivity#scroll";
        original.title = "IllegalStateException: bad state";
        original.screen = "chat";
        original.dialogId = 99L;
        original.appVersion = "11.7";
        original.versionCode = 11700L;
        original.buildChannel = "stable";
        original.deviceModel = "Pixel";
        original.osVersion = "Android 15";
        original.occurredAt = 123L;
        original.receivedAt = 0L;
        original.breadcrumbs = new ArrayList<>();
        original.stacktrace = new ArrayList<>();
        original.context = new java.util.LinkedHashMap<>();

        ArrayList<MassgramTelemetryFormat.TelemetryEvent> events = new ArrayList<>();
        events.add(original);

        String serialized = MassgramTelemetryFormat.serializeQueue(events);
        ArrayList<MassgramTelemetryFormat.TelemetryEvent> parsed = MassgramTelemetryFormat.deserializeQueue(serialized);

        assertEquals(1, parsed.size());
        assertEquals(original.eventId, parsed.get(0).eventId);
        assertEquals(original.fingerprint, parsed.get(0).fingerprint);
        assertEquals(original.dialogId, parsed.get(0).dialogId);
    }

    @Test
    public void uploadBatchUsesBackendSnakeCaseFields() {
        MassgramTelemetryFormat.TelemetryEvent original = new MassgramTelemetryFormat.TelemetryEvent();
        original.eventId = "evt-1";
        original.userId = 42L;
        original.eventType = "fatal";
        original.severity = "fatal";
        original.fingerprint = "fatal|chat|IllegalStateException|ChatActivity#scroll";
        original.title = "IllegalStateException: bad state";
        original.screen = "chat";
        original.dialogId = 99L;
        original.appVersion = "12.6.4";
        original.versionCode = 12640L;
        original.buildChannel = "stable";
        original.deviceModel = "Pixel";
        original.osVersion = "Android 15";
        original.occurredAt = 123L;
        original.breadcrumbs = new ArrayList<>();
        original.stacktrace = new ArrayList<>();
        original.context = MassgramTelemetryFormat.mapOf("dialog_id", 99L);

        ArrayList<MassgramTelemetryFormat.TelemetryEvent> events = new ArrayList<>();
        events.add(original);

        String batch = MassgramTelemetryFormat.serializeUploadBatch(events);

        assertTrue(batch.startsWith("["));
        assertTrue(batch.contains("\"user_id\":42"));
        assertTrue(batch.contains("\"event_type\":\"fatal\""));
        assertTrue(batch.contains("\"dialog_id\":99"));
        assertTrue(batch.contains("\"version_code\":12640"));
        assertFalse(batch.contains("userId"));
        assertFalse(batch.contains("eventType"));
        assertFalse(batch.contains("dialogId"));
        assertFalse(batch.contains("versionCode"));
    }

    @Test
    public void duplicateFingerprintIsThrottledWithinCooldown() {
        java.util.HashMap<String, Long> lastSent = new java.util.HashMap<>();
        String fingerprint = "fatal|chat|IllegalStateException|ChatActivity#scroll";

        assertTrue(MassgramTelemetryFormat.shouldCaptureFingerprint(lastSent, fingerprint, 10_000L, 60_000L));
        assertFalse(MassgramTelemetryFormat.shouldCaptureFingerprint(lastSent, fingerprint, 20_000L, 60_000L));
        assertTrue(MassgramTelemetryFormat.shouldCaptureFingerprint(lastSent, fingerprint, 80_001L, 60_000L));
    }
}
