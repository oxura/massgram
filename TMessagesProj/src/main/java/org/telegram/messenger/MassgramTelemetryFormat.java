package org.telegram.messenger;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class MassgramTelemetryFormat {

    static final int MAX_BREADCRUMBS = 20;
    private static final Gson GSON = new Gson();
    private static final int MAX_STRING_LENGTH = 240;
    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_STACKTRACE_FRAMES = 12;
    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
        "message",
        "message_text",
        "input_text",
        "query",
        "caption",
        "text",
        "draft",
        "search_query"
    ));

    static final class Breadcrumb {
        final long timestamp;
        final String screen;
        final String action;
        final Map<String, Object> context;

        Breadcrumb(long timestamp, String screen, String action, Map<String, Object> context) {
            this.timestamp = timestamp;
            this.screen = sanitizeString(screen, 48);
            this.action = sanitizeString(action, 64);
            this.context = sanitizeContext(context);
        }
    }

    static final class TelemetryEvent {
        String eventId;
        long userId;
        String eventType;
        String severity;
        String fingerprint;
        String title;
        String screen;
        long dialogId;
        String appVersion;
        long versionCode;
        String buildChannel;
        String deviceModel;
        String osVersion;
        ArrayList<Map<String, Object>> breadcrumbs;
        ArrayList<String> stacktrace;
        LinkedHashMap<String, Object> context;
        long occurredAt;
        long receivedAt;
    }

    private MassgramTelemetryFormat() {
    }

    static String buildFingerprint(String eventType, String screen, Throwable throwable) {
        String throwableType = throwable != null ? throwable.getClass().getSimpleName() : "UnknownThrowable";
        String topFrame = "unknown";
        if (throwable != null) {
            StackTraceElement[] stackTrace = throwable.getStackTrace();
            if (stackTrace != null && stackTrace.length > 0 && stackTrace[0] != null) {
                topFrame = sanitizeString(stackTrace[0].getClassName() + "#" + stackTrace[0].getMethodName(), 128);
            }
        }
        return sanitizeString(eventType, 24) + "|"
            + sanitizeString(screen, 48) + "|"
            + sanitizeString(throwableType, 64) + "|"
            + topFrame;
    }

    static List<Map<String, Object>> encodeBreadcrumbs(List<Breadcrumb> source) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        int start = Math.max(0, source.size() - MAX_BREADCRUMBS);
        for (int i = start; i < source.size(); i++) {
            Breadcrumb breadcrumb = source.get(i);
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", breadcrumb.timestamp);
            row.put("screen", breadcrumb.screen);
            row.put("action", breadcrumb.action);
            if (!breadcrumb.context.isEmpty()) {
                row.put("context", breadcrumb.context);
            }
            result.add(row);
        }
        return result;
    }

    static TelemetryEvent createEvent(
        long userId,
        String eventType,
        String severity,
        String screen,
        long dialogId,
        String appVersion,
        long versionCode,
        String buildChannel,
        String deviceModel,
        String osVersion,
        List<Breadcrumb> breadcrumbs,
        Throwable throwable,
        Map<String, Object> context,
        long occurredAt
    ) {
        TelemetryEvent event = new TelemetryEvent();
        event.eventId = UUID.randomUUID().toString();
        event.userId = userId;
        event.eventType = sanitizeString(eventType, 24);
        event.severity = sanitizeString(severity, 24);
        event.screen = sanitizeString(screen, 48);
        event.dialogId = dialogId;
        event.appVersion = sanitizeString(appVersion, 64);
        event.versionCode = versionCode;
        event.buildChannel = "beta".equalsIgnoreCase(buildChannel) ? "beta" : "stable";
        event.deviceModel = sanitizeString(deviceModel, 128);
        event.osVersion = sanitizeString(osVersion, 64);
        event.occurredAt = occurredAt;
        event.receivedAt = 0L;
        event.fingerprint = buildFingerprint(eventType, screen, throwable);
        event.title = buildTitle(throwable);
        event.breadcrumbs = new ArrayList<>(encodeBreadcrumbs(breadcrumbs));
        event.stacktrace = new ArrayList<>(encodeStacktrace(throwable));
        event.context = sanitizeContext(context);
        return event;
    }

    static boolean shouldCaptureFingerprint(HashMap<String, Long> lastCaptureAtByFingerprint, String fingerprint, long now, long cooldownMs) {
        if (lastCaptureAtByFingerprint == null || fingerprint == null) {
            return true;
        }
        Long last = lastCaptureAtByFingerprint.get(fingerprint);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        lastCaptureAtByFingerprint.put(fingerprint, now);
        return true;
    }

    static String serializeQueue(List<TelemetryEvent> events) {
        return GSON.toJson(events != null ? events : new ArrayList<TelemetryEvent>());
    }

    static ArrayList<TelemetryEvent> deserializeQueue(String serialized) {
        ArrayList<TelemetryEvent> events = new ArrayList<>();
        if (serialized == null || serialized.trim().isEmpty()) {
            return events;
        }
        try {
            TelemetryEvent[] parsed = GSON.fromJson(serialized, TelemetryEvent[].class);
            if (parsed != null) {
                for (TelemetryEvent event : parsed) {
                    if (event != null) {
                        if (event.breadcrumbs == null) {
                            event.breadcrumbs = new ArrayList<>();
                        }
                        if (event.stacktrace == null) {
                            event.stacktrace = new ArrayList<>();
                        }
                        if (event.context == null) {
                            event.context = new LinkedHashMap<>();
                        }
                        events.add(event);
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return events;
    }

    static LinkedHashMap<String, Object> mapOf(Object... values) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        if (values == null) {
            return map;
        }
        for (int i = 0; i + 1 < values.length; i += 2) {
            Object key = values[i];
            if (!(key instanceof String)) {
                continue;
            }
            map.put((String) key, values[i + 1]);
        }
        return map;
    }

    private static String buildTitle(Throwable throwable) {
        if (throwable == null) {
            return "Massgram runtime issue";
        }
        StringBuilder builder = new StringBuilder(throwable.getClass().getSimpleName());
        if (throwable.getMessage() != null && !throwable.getMessage().trim().isEmpty()) {
            builder.append(": ").append(throwable.getMessage().trim());
        }
        return sanitizeString(builder.toString(), MAX_TITLE_LENGTH);
    }

    private static List<String> encodeStacktrace(Throwable throwable) {
        ArrayList<String> stacktrace = new ArrayList<>();
        if (throwable == null) {
            return stacktrace;
        }
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        if (stackTrace == null) {
            return stacktrace;
        }
        int count = Math.min(stackTrace.length, MAX_STACKTRACE_FRAMES);
        for (int i = 0; i < count; i++) {
            StackTraceElement frame = stackTrace[i];
            if (frame == null) {
                continue;
            }
            stacktrace.add(sanitizeString(frame.getClassName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber(), 192));
        }
        return stacktrace;
    }

    private static LinkedHashMap<String, Object> sanitizeContext(Map<String, Object> context) {
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        if (context == null || context.isEmpty()) {
            return sanitized;
        }
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String normalizedKey = key.trim().toLowerCase(Locale.US);
            if (normalizedKey.isEmpty() || SENSITIVE_KEYS.contains(normalizedKey)) {
                continue;
            }
            Object value = sanitizeValue(entry.getValue());
            if (value != null) {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    private static Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) value;
            return sanitizeContext(nested);
        }
        if (value instanceof List) {
            ArrayList<Object> list = new ArrayList<>();
            for (Object item : (List<?>) value) {
                Object sanitized = sanitizeValue(item);
                if (sanitized != null) {
                    list.add(sanitized);
                }
            }
            return list;
        }
        return sanitizeString(String.valueOf(value), MAX_STRING_LENGTH);
    }

    private static String sanitizeString(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace('\n', ' ').replace('\r', ' ');
        normalized = normalized.replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private static JSONObject toJson(TelemetryEvent event) {
        JSONObject object = new JSONObject();
        try {
            object.put("event_id", event.eventId);
            object.put("user_id", event.userId);
            object.put("event_type", event.eventType);
            object.put("severity", event.severity);
            object.put("fingerprint", event.fingerprint);
            object.put("title", event.title);
            object.put("screen", event.screen);
            object.put("dialog_id", event.dialogId);
            object.put("app_version", event.appVersion);
            object.put("version_code", event.versionCode);
            object.put("build_channel", event.buildChannel);
            object.put("device_model", event.deviceModel);
            object.put("os_version", event.osVersion);
            object.put("breadcrumbs", new JSONArray(event.breadcrumbs));
            object.put("stacktrace", new JSONArray(event.stacktrace));
            object.put("context", new JSONObject(event.context));
            object.put("occurred_at", event.occurredAt);
            object.put("received_at", event.receivedAt);
        } catch (Exception ignore) {
        }
        return object;
    }


    private static TelemetryEvent fromJson(JSONObject object) {
        TelemetryEvent event = new TelemetryEvent();
        event.eventId = object.optString("event_id", null);
        event.userId = object.optLong("user_id");
        event.eventType = object.optString("event_type", null);
        event.severity = object.optString("severity", null);
        event.fingerprint = object.optString("fingerprint", null);
        event.title = object.optString("title", null);
        event.screen = object.optString("screen", null);
        event.dialogId = object.optLong("dialog_id");
        event.appVersion = object.optString("app_version", null);
        event.versionCode = object.optLong("version_code");
        event.buildChannel = object.optString("build_channel", null);
        event.deviceModel = object.optString("device_model", null);
        event.osVersion = object.optString("os_version", null);
        event.breadcrumbs = new ArrayList<>();
        JSONArray breadcrumbsArray = object.optJSONArray("breadcrumbs");
        if (breadcrumbsArray != null) {
            for (int i = 0; i < breadcrumbsArray.length(); i++) {
                JSONObject breadcrumb = breadcrumbsArray.optJSONObject(i);
                if (breadcrumb == null) {
                    continue;
                }
                event.breadcrumbs.add(jsonObjectToMap(breadcrumb));
            }
        }
        event.stacktrace = new ArrayList<>();
        JSONArray stacktraceArray = object.optJSONArray("stacktrace");
        if (stacktraceArray != null) {
            for (int i = 0; i < stacktraceArray.length(); i++) {
                String frame = stacktraceArray.optString(i, null);
                if (frame != null) {
                    event.stacktrace.add(frame);
                }
            }
        }
        event.context = jsonObjectToMap(object.optJSONObject("context"));
        event.occurredAt = object.optLong("occurred_at");
        event.receivedAt = object.optLong("received_at");
        return event;
    }

    private static LinkedHashMap<String, Object> jsonObjectToMap(JSONObject object) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        if (object == null) {
            return map;
        }
        JSONArray names = object.names();
        if (names == null) {
            return map;
        }
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, null);
            if (name == null) {
                continue;
            }
            Object value = object.opt(name);
            if (value instanceof JSONObject) {
                map.put(name, jsonObjectToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.put(name, jsonArrayToList((JSONArray) value));
            } else if (value != null && value != JSONObject.NULL) {
                map.put(name, value);
            }
        }
        return map;
    }

    private static ArrayList<Object> jsonArrayToList(JSONArray array) {
        ArrayList<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (value instanceof JSONObject) {
                list.add(jsonObjectToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(jsonArrayToList((JSONArray) value));
            } else if (value != null && value != JSONObject.NULL) {
                list.add(value);
            }
        }
        return list;
    }
}
