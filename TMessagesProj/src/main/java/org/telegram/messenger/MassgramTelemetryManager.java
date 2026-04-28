package org.telegram.messenger;

import android.content.pm.PackageInfo;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MassgramTelemetryManager {

    public interface DashboardCallback {
        void onResult(OwnerDashboardData data, String error);
    }

    public interface OwnerIssuesCallback {
        void onResult(OwnerIssuesData data, String error);
    }

    public interface OwnerIssueDetailCallback {
        void onResult(OwnerIssueDetail data, String error);
    }

    public static final class OwnerDashboardData {
        public long loadedAt;
        public long totalUsers;
        public long activeUsers;
        public long offlineUsers;
        public long betaUsers;
        public final ArrayList<OwnerDashboardUser> users = new ArrayList<>();
    }

    public static final class OwnerDashboardUser {
        public long userId;
        public String username;
        public String firstName;
        public String lastName;
        public String displayName;
        public String appVersion;
        public String buildChannel;
        public long lastSeenAt;
        public boolean online;

        public String getSearchKey() {
            StringBuilder builder = new StringBuilder();
            if (!TextUtils.isEmpty(username)) {
                builder.append(username.toLowerCase(Locale.US)).append(' ');
            }
            if (!TextUtils.isEmpty(displayName)) {
                builder.append(displayName.toLowerCase(Locale.US)).append(' ');
            }
            builder.append(userId);
            return builder.toString();
        }
    }

    public static final class OwnerIssuesData {
        public long loadedAt;
        public final OwnerIssueSummary summary = new OwnerIssueSummary();
        public final ArrayList<OwnerIssueEntry> issues = new ArrayList<>();
    }

    public static final class OwnerIssueSummary {
        public long crashUsers24h;
        public long newIssues24h;
        public String topFingerprint;
        public String topTitle;
        public long topCount;
    }

    public static final class OwnerIssueEntry {
        public String fingerprint;
        public String title;
        public String severity;
        public String screen;
        public long totalEvents;
        public long uniqueUsers;
        public long lastOccurredAt;
        public String appVersion;
        public String buildChannel;
        public String deviceModel;
    }

    public static final class OwnerIssueDetail {
        public long loadedAt;
        public String fingerprint;
        public String title;
        public String severity;
        public String screen;
        public long totalEvents;
        public long uniqueUsers;
        public long firstOccurredAt;
        public long lastOccurredAt;
        public final ArrayList<String> affectedVersions = new ArrayList<>();
        public final ArrayList<String> affectedDevices = new ArrayList<>();
        public final ArrayList<String> sampleStacktrace = new ArrayList<>();
        public final ArrayList<OwnerIssueBreadcrumb> sampleBreadcrumbs = new ArrayList<>();
        public final LinkedHashMap<String, String> sampleContext = new LinkedHashMap<>();
        public final ArrayList<OwnerIssueUser> users = new ArrayList<>();
    }

    public static final class OwnerIssueBreadcrumb {
        public long timestamp;
        public String screen;
        public String action;
        public final LinkedHashMap<String, String> context = new LinkedHashMap<>();
    }

    public static final class OwnerIssueUser {
        public long userId;
        public String username;
        public String displayName;
        public String appVersion;
        public String buildChannel;
        public long occurredAt;
        public long dialogId;
    }

    private static final long HEARTBEAT_INTERVAL_MS = 60_000L;
    private static final long DASHBOARD_CACHE_MS = 30_000L;
    private static final long ISSUES_CACHE_MS = 15_000L;
    private static final long EVENT_FLUSH_INTERVAL_MS = 15_000L;
    private static final long EVENT_CAPTURE_COOLDOWN_MS = 60_000L;
    private static final long MAIN_THREAD_STALL_CHECK_MS = 12_000L;
    private static final long MAIN_THREAD_STALL_THRESHOLD_MS = 4_000L;
    private static final int MAX_EVENT_BATCH_SIZE = 20;
    private static volatile MassgramTelemetryManager instance;

    public static MassgramTelemetryManager getInstance() {
        MassgramTelemetryManager localInstance = instance;
        if (localInstance == null) {
            synchronized (MassgramTelemetryManager.class) {
                localInstance = instance;
                if (localInstance == null) {
                    localInstance = instance = new MassgramTelemetryManager();
                }
            }
        }
        return localInstance;
    }

    private final Runnable heartbeatRunnable = this::performHeartbeatIfNeeded;
    private final Runnable flushEventsRunnable = this::performEventFlushIfNeeded;
    private final Runnable stallCheckRunnable = this::performMainThreadStallCheck;
    private final Object dashboardLock = new Object();
    private final Object issuesLock = new Object();
    private final Object eventQueueLock = new Object();
    private final Object breadcrumbLock = new Object();
    private final Object queueFileLock = new Object();
    private final SimpleDateFormat[] isoParsers;
    private final ArrayList<MassgramTelemetryFormat.Breadcrumb> breadcrumbs = new ArrayList<>();
    private final ArrayList<MassgramTelemetryFormat.TelemetryEvent> queuedEvents = new ArrayList<>();
    private final HashMap<String, Long> lastCaptureAtByFingerprint = new HashMap<>();

    private volatile boolean initialized;
    private volatile boolean appActive;
    private volatile boolean heartbeatInFlight;
    private volatile boolean eventFlushInFlight;
    private volatile long lastHeartbeatAt;
    private volatile String lastScreen = "launch";
    private volatile long lastDialogId;
    private volatile long mainThreadStallExpectedAt;
    private long cachedVersionCode = -1;
    private File telemetryQueueFile;
    private OwnerDashboardData cachedDashboard;
    private long cachedDashboardAt;
    private OwnerIssuesData cachedIssues;
    private long cachedIssuesAt;

    private MassgramTelemetryManager() {
        SimpleDateFormat withMillis = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);
        withMillis.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat withoutMillis = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US);
        withoutMillis.setTimeZone(TimeZone.getTimeZone("UTC"));
        isoParsers = new SimpleDateFormat[]{withMillis, withoutMillis};
    }

    public void initialize() {
        if (initialized || !isConfigured()) {
            return;
        }
        synchronized (eventQueueLock) {
            if (initialized || !isConfigured()) {
                return;
            }
            loadPersistedQueueLocked();
            initialized = true;
        }
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(BuildConfig.MASSGRAM_SUPABASE_URL)
            && !TextUtils.isEmpty(BuildConfig.MASSGRAM_SUPABASE_PUBLISHABLE_KEY);
    }

    public boolean isOwnerDashboardAvailable(long userId) {
        return isConfigured() && MassgramConfigManager.getInstance().isOwner(userId);
    }

    public void onAppForeground() {
        if (!isConfigured()) {
            return;
        }
        initialize();
        appActive = true;
        recordBreadcrumb("launch", "app_foreground", null);
        scheduleNextHeartbeat(true);
        scheduleEventFlush(true);
        scheduleMainThreadStallCheck(true);
    }

    public void onAppBackground() {
        appActive = false;
        recordBreadcrumb(lastScreen, "app_background", null);
        AndroidUtilities.cancelRunOnUIThread(heartbeatRunnable);
        AndroidUtilities.cancelRunOnUIThread(flushEventsRunnable);
        AndroidUtilities.cancelRunOnUIThread(stallCheckRunnable);
    }

    public void recordBreadcrumb(String screen, String action, java.util.Map<String, Object> context) {
        if (!isConfigured()) {
            return;
        }
        initialize();
        String resolvedScreen = !TextUtils.isEmpty(screen) ? screen : lastScreen;
        lastScreen = TextUtils.isEmpty(resolvedScreen) ? "unknown" : resolvedScreen;
        Object dialogValue = context != null ? context.get("dialog_id") : null;
        if (dialogValue instanceof Number) {
            lastDialogId = ((Number) dialogValue).longValue();
        }
        MassgramTelemetryFormat.Breadcrumb breadcrumb = new MassgramTelemetryFormat.Breadcrumb(
            System.currentTimeMillis(),
            lastScreen,
            action,
            context
        );
        synchronized (breadcrumbLock) {
            breadcrumbs.add(breadcrumb);
            int overflow = breadcrumbs.size() - MassgramTelemetryFormat.MAX_BREADCRUMBS;
            if (overflow > 0) {
                breadcrumbs.subList(0, overflow).clear();
            }
        }
    }

    public void captureFatal(String screen, long dialogId, Throwable throwable) {
        captureThrowableEvent("fatal", "fatal", screen, dialogId, throwable, null);
    }

    public void captureHandledError(String eventType, String screen, long dialogId, Throwable throwable, java.util.Map<String, Object> context) {
        captureThrowableEvent(TextUtils.isEmpty(eventType) ? "handled_error" : eventType, "error", screen, dialogId, throwable, context);
    }

    public void captureRendererCrash(String screen, long dialogId, String title, java.util.Map<String, Object> context) {
        IllegalStateException throwable = new IllegalStateException(TextUtils.isEmpty(title) ? "Renderer process crashed" : title);
        captureThrowableEvent("renderer_crash", "error", screen, dialogId, throwable, context);
    }

    public void captureMainThreadStall(String screen, long dialogId, long stallDurationMs, java.util.Map<String, Object> context) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (context != null) {
            merged.putAll(context);
        }
        merged.put("stall_duration_ms", stallDurationMs);
        RuntimeException throwable = new RuntimeException("Main thread stall " + stallDurationMs + "ms");
        captureThrowableEvent("main_thread_stall", "warning", screen, dialogId, throwable, merged);
    }

    public void loadOwnerDashboard(long ownerUserId, String query, boolean force, DashboardCallback callback) {
        if (!isOwnerDashboardAvailable(ownerUserId)) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(null, "Owner dashboard is unavailable"));
            return;
        }
        String normalizedQuery = query == null ? "" : query.trim();
        if (!force && TextUtils.isEmpty(normalizedQuery)) {
            synchronized (dashboardLock) {
                if (cachedDashboard != null && System.currentTimeMillis() - cachedDashboardAt < DASHBOARD_CACHE_MS) {
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(cachedDashboard, null));
                    return;
                }
            }
        }

        Utilities.globalQueue.postRunnable(() -> {
            OwnerDashboardData data = null;
            String error = null;
            HttpURLConnection connection = null;
            BufferedInputStream inputStream = null;
            try {
                StringBuilder urlBuilder = new StringBuilder(getOwnerDashboardUrl()).append("?owner_id=").append(ownerUserId);
                if (!TextUtils.isEmpty(normalizedQuery)) {
                    urlBuilder.append("&q=").append(UriEncoder.encode(normalizedQuery));
                }
                connection = openConnection(urlBuilder.toString(), "GET");
                connection.setRequestProperty("x-massgram-owner-id", String.valueOf(ownerUserId));
                int code = connection.getResponseCode();
                inputStream = new BufferedInputStream(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
                String response = readFully(inputStream);
                if (code < 200 || code >= 300) {
                    error = parseError(response, "Failed to load Massgram dashboard");
                } else {
                    data = parseDashboardResponse(response);
                    if (TextUtils.isEmpty(normalizedQuery)) {
                        synchronized (dashboardLock) {
                            cachedDashboard = data;
                            cachedDashboardAt = System.currentTimeMillis();
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                error = "Failed to load Massgram dashboard";
            } finally {
                closeQuietly(inputStream);
                if (connection != null) {
                    connection.disconnect();
                }
            }

            final OwnerDashboardData finalData = data;
            final String finalError = error;
            AndroidUtilities.runOnUIThread(() -> callback.onResult(finalData, finalError));
        });
    }

    public void loadOwnerIssues(long ownerUserId, String query, boolean force, OwnerIssuesCallback callback) {
        if (!isOwnerDashboardAvailable(ownerUserId)) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(null, "Owner issue inbox is unavailable"));
            return;
        }
        String normalizedQuery = query == null ? "" : query.trim();
        if (!force && TextUtils.isEmpty(normalizedQuery)) {
            synchronized (issuesLock) {
                if (cachedIssues != null && System.currentTimeMillis() - cachedIssuesAt < ISSUES_CACHE_MS) {
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(cachedIssues, null));
                    return;
                }
            }
        }
        Utilities.globalQueue.postRunnable(() -> {
            OwnerIssuesData data = null;
            String error = null;
            HttpURLConnection connection = null;
            BufferedInputStream inputStream = null;
            try {
                StringBuilder urlBuilder = new StringBuilder(getOwnerIssuesUrl()).append("?owner_id=").append(ownerUserId);
                if (!TextUtils.isEmpty(normalizedQuery)) {
                    urlBuilder.append("&q=").append(UriEncoder.encode(normalizedQuery));
                }
                connection = openConnection(urlBuilder.toString(), "GET");
                connection.setRequestProperty("x-massgram-owner-id", String.valueOf(ownerUserId));
                int code = connection.getResponseCode();
                inputStream = new BufferedInputStream(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
                String response = readFully(inputStream);
                if (code < 200 || code >= 300) {
                    error = parseError(response, "Failed to load Massgram issues");
                } else {
                    data = parseOwnerIssuesResponse(response);
                    if (TextUtils.isEmpty(normalizedQuery)) {
                        synchronized (issuesLock) {
                            cachedIssues = data;
                            cachedIssuesAt = System.currentTimeMillis();
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                error = "Failed to load Massgram issues";
            } finally {
                closeQuietly(inputStream);
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final OwnerIssuesData finalData = data;
            final String finalError = error;
            AndroidUtilities.runOnUIThread(() -> callback.onResult(finalData, finalError));
        });
    }

    public void loadOwnerIssueDetail(long ownerUserId, String fingerprint, OwnerIssueDetailCallback callback) {
        if (!isOwnerDashboardAvailable(ownerUserId) || TextUtils.isEmpty(fingerprint)) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(null, "Owner issue detail is unavailable"));
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            OwnerIssueDetail data = null;
            String error = null;
            HttpURLConnection connection = null;
            BufferedInputStream inputStream = null;
            try {
                StringBuilder urlBuilder = new StringBuilder(getOwnerIssuesUrl())
                    .append("?owner_id=").append(ownerUserId)
                    .append("&fingerprint=").append(UriEncoder.encode(fingerprint));
                connection = openConnection(urlBuilder.toString(), "GET");
                connection.setRequestProperty("x-massgram-owner-id", String.valueOf(ownerUserId));
                int code = connection.getResponseCode();
                inputStream = new BufferedInputStream(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
                String response = readFully(inputStream);
                if (code < 200 || code >= 300) {
                    error = parseError(response, "Failed to load issue detail");
                } else {
                    data = parseOwnerIssueDetailResponse(response);
                }
            } catch (Exception e) {
                FileLog.e(e);
                error = "Failed to load issue detail";
            } finally {
                closeQuietly(inputStream);
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final OwnerIssueDetail finalData = data;
            final String finalError = error;
            AndroidUtilities.runOnUIThread(() -> callback.onResult(finalData, finalError));
        });
    }

    private void captureThrowableEvent(String eventType, String severity, String screen, long dialogId, Throwable throwable, java.util.Map<String, Object> context) {
        if (!isConfigured() || throwable == null) {
            return;
        }
        initialize();
        long userId = resolveClientUserId();
        if (userId == 0) {
            return;
        }
        String resolvedScreen = !TextUtils.isEmpty(screen) ? screen : lastScreen;
        long resolvedDialogId = dialogId != 0 ? dialogId : lastDialogId;
        LinkedHashMap<String, Object> mergedContext = new LinkedHashMap<>();
        if (context != null) {
            mergedContext.putAll(context);
        }
        if (resolvedDialogId != 0 && !mergedContext.containsKey("dialog_id")) {
            mergedContext.put("dialog_id", resolvedDialogId);
        }
        ArrayList<MassgramTelemetryFormat.Breadcrumb> breadcrumbsSnapshot;
        synchronized (breadcrumbLock) {
            breadcrumbsSnapshot = new ArrayList<>(breadcrumbs);
        }
        long now = System.currentTimeMillis();
        MassgramTelemetryFormat.TelemetryEvent event = MassgramTelemetryFormat.createEvent(
            userId,
            eventType,
            severity,
            resolvedScreen,
            resolvedDialogId,
            BuildVars.BUILD_VERSION_STRING,
            getInstalledVersionCode(),
            getBuildChannel(),
            Build.MANUFACTURER + " " + Build.MODEL,
            "Android " + Build.VERSION.RELEASE,
            breadcrumbsSnapshot,
            throwable,
            mergedContext,
            now
        );
        synchronized (lastCaptureAtByFingerprint) {
            if (!MassgramTelemetryFormat.shouldCaptureFingerprint(lastCaptureAtByFingerprint, event.fingerprint, now, EVENT_CAPTURE_COOLDOWN_MS)) {
                return;
            }
        }
        synchronized (eventQueueLock) {
            queuedEvents.add(event);
            persistQueueLocked();
        }
        scheduleEventFlush(true);
    }

    private void performHeartbeatIfNeeded() {
        if (!appActive || !isConfigured()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (heartbeatInFlight) {
            scheduleNextHeartbeat(false);
            return;
        }
        if (now - lastHeartbeatAt < HEARTBEAT_INTERVAL_MS - 1000L) {
            scheduleNextHeartbeat(false);
            return;
        }
        heartbeatInFlight = true;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                sendHeartbeatBatch();
                lastHeartbeatAt = System.currentTimeMillis();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                heartbeatInFlight = false;
                if (appActive) {
                    scheduleNextHeartbeat(false);
                }
            }
        });
    }

    private void performEventFlushIfNeeded() {
        if (!appActive || !isConfigured()) {
            return;
        }
        initialize();
        if (eventFlushInFlight) {
            scheduleEventFlush(false);
            return;
        }
        final ArrayList<MassgramTelemetryFormat.TelemetryEvent> batch = new ArrayList<>();
        synchronized (eventQueueLock) {
            int count = Math.min(queuedEvents.size(), MAX_EVENT_BATCH_SIZE);
            if (count == 0) {
                return;
            }
            for (int i = 0; i < count; i++) {
                batch.add(queuedEvents.get(i));
            }
        }
        eventFlushInFlight = true;
        Utilities.globalQueue.postRunnable(() -> {
            boolean success = false;
            try {
                sendTelemetryBatch(batch);
                success = true;
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (success) {
                    synchronized (eventQueueLock) {
                        for (int i = 0; i < batch.size() && !queuedEvents.isEmpty(); i++) {
                            queuedEvents.remove(0);
                        }
                        persistQueueLocked();
                    }
                }
                eventFlushInFlight = false;
                if (appActive) {
                    scheduleEventFlush(false);
                }
            }
        });
    }

    private void performMainThreadStallCheck() {
        if (!appActive || !isConfigured()) {
            return;
        }
        long delay = System.currentTimeMillis() - mainThreadStallExpectedAt;
        if (delay >= MAIN_THREAD_STALL_THRESHOLD_MS) {
            captureMainThreadStall(lastScreen, lastDialogId, delay, null);
        }
        scheduleMainThreadStallCheck(false);
    }

    private void sendHeartbeatBatch() throws Exception {
        JSONArray users = new JSONArray();
        long versionCode = getInstalledVersionCode();
        String buildChannel = getBuildChannel();
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            UserConfig userConfig = UserConfig.getInstance(account);
            if (!userConfig.isClientActivated()) {
                continue;
            }
            org.telegram.tgnet.TLRPC.User currentUser = userConfig.getCurrentUser();
            if (currentUser == null || currentUser.id == 0) {
                continue;
            }
            JSONObject payload = new JSONObject();
            payload.put("user_id", currentUser.id);
            payload.put("username", currentUser.username);
            payload.put("first_name", currentUser.first_name);
            payload.put("last_name", currentUser.last_name);
            payload.put("app_version", BuildVars.BUILD_VERSION_STRING);
            payload.put("version_code", versionCode);
            payload.put("package_name", ApplicationLoader.getApplicationId());
            payload.put("build_channel", buildChannel);
            payload.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);
            payload.put("os_version", "Android " + Build.VERSION.RELEASE);
            payload.put("locale", LocaleController.getInstance().getCurrentLocaleInfo() != null ? LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode : Locale.getDefault().toLanguageTag());
            users.put(payload);
        }
        if (users.length() == 0) {
            return;
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("users", users);

        HttpURLConnection connection = null;
        BufferedOutputStream outputStream = null;
        BufferedInputStream inputStream = null;
        try {
            connection = openConnection(getHeartbeatUrl(), "POST");
            connection.setDoOutput(true);
            byte[] body = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            outputStream = new BufferedOutputStream(connection.getOutputStream());
            outputStream.write(body);
            outputStream.flush();

            int code = connection.getResponseCode();
            inputStream = new BufferedInputStream(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
            String response = readFully(inputStream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException(parseError(response, "Massgram heartbeat failed"));
            }
        } finally {
            closeQuietly(outputStream);
            closeQuietly(inputStream);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void sendTelemetryBatch(List<MassgramTelemetryFormat.TelemetryEvent> batch) throws Exception {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        JSONObject requestBody = new JSONObject();
        requestBody.put("events", new JSONArray(MassgramTelemetryFormat.serializeUploadBatch(batch)));
        HttpURLConnection connection = null;
        BufferedOutputStream outputStream = null;
        BufferedInputStream inputStream = null;
        try {
            connection = openConnection(getTelemetryEventUrl(), "POST");
            connection.setDoOutput(true);
            byte[] body = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            outputStream = new BufferedOutputStream(connection.getOutputStream());
            outputStream.write(body);
            outputStream.flush();
            int code = connection.getResponseCode();
            inputStream = new BufferedInputStream(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
            String response = readFully(inputStream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException(parseError(response, "Massgram telemetry upload failed"));
            }
        } finally {
            closeQuietly(outputStream);
            closeQuietly(inputStream);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private OwnerDashboardData parseDashboardResponse(String response) throws Exception {
        JSONObject root = new JSONObject(response);
        JSONObject summary = root.optJSONObject("summary");
        JSONArray users = root.optJSONArray("users");

        OwnerDashboardData data = new OwnerDashboardData();
        data.loadedAt = System.currentTimeMillis();
        if (summary != null) {
            data.totalUsers = summary.optLong("total_users");
            data.activeUsers = summary.optLong("active_users");
            data.offlineUsers = summary.optLong("offline_users");
            data.betaUsers = summary.optLong("beta_users");
        }
        if (users != null) {
            for (int i = 0; i < users.length(); i++) {
                JSONObject object = users.getJSONObject(i);
                OwnerDashboardUser user = new OwnerDashboardUser();
                user.userId = object.optLong("user_id");
                user.username = optNullableString(object, "username");
                user.firstName = optNullableString(object, "first_name");
                user.lastName = optNullableString(object, "last_name");
                user.displayName = optNullableString(object, "display_name");
                user.appVersion = optNullableString(object, "app_version");
                user.buildChannel = optNullableString(object, "build_channel");
                user.online = object.optBoolean("is_online");
                user.lastSeenAt = parseIsoTime(optNullableString(object, "last_seen_at"));
                data.users.add(user);
            }
        }
        return data;
    }

    private OwnerIssuesData parseOwnerIssuesResponse(String response) throws Exception {
        JSONObject root = new JSONObject(response);
        OwnerIssuesData data = new OwnerIssuesData();
        data.loadedAt = System.currentTimeMillis();
        JSONObject summary = root.optJSONObject("summary");
        if (summary != null) {
            data.summary.crashUsers24h = summary.optLong("crash_users_24h");
            data.summary.newIssues24h = summary.optLong("new_issues_24h");
            data.summary.topFingerprint = optNullableString(summary, "top_fingerprint");
            data.summary.topTitle = optNullableString(summary, "top_title");
            data.summary.topCount = summary.optLong("top_count");
        }
        JSONArray issues = root.optJSONArray("issues");
        if (issues != null) {
            for (int i = 0; i < issues.length(); i++) {
                JSONObject object = issues.getJSONObject(i);
                OwnerIssueEntry entry = new OwnerIssueEntry();
                entry.fingerprint = optNullableString(object, "fingerprint");
                entry.title = optNullableString(object, "title");
                entry.severity = optNullableString(object, "severity");
                entry.screen = optNullableString(object, "screen");
                entry.totalEvents = object.optLong("total_events");
                entry.uniqueUsers = object.optLong("unique_users");
                entry.lastOccurredAt = parseIsoTime(optNullableString(object, "last_occurred_at"));
                entry.appVersion = optNullableString(object, "app_version");
                entry.buildChannel = optNullableString(object, "build_channel");
                entry.deviceModel = optNullableString(object, "device_model");
                data.issues.add(entry);
            }
        }
        return data;
    }

    private OwnerIssueDetail parseOwnerIssueDetailResponse(String response) throws Exception {
        JSONObject root = new JSONObject(response);
        JSONObject detailObject = root.optJSONObject("detail");
        if (detailObject == null) {
            return null;
        }
        OwnerIssueDetail detail = new OwnerIssueDetail();
        detail.loadedAt = System.currentTimeMillis();
        detail.fingerprint = optNullableString(detailObject, "fingerprint");
        detail.title = optNullableString(detailObject, "title");
        detail.severity = optNullableString(detailObject, "severity");
        detail.screen = optNullableString(detailObject, "screen");
        detail.totalEvents = detailObject.optLong("total_events");
        detail.uniqueUsers = detailObject.optLong("unique_users");
        detail.firstOccurredAt = parseIsoTime(optNullableString(detailObject, "first_occurred_at"));
        detail.lastOccurredAt = parseIsoTime(optNullableString(detailObject, "last_occurred_at"));
        copyStringArray(detailObject.optJSONArray("affected_versions"), detail.affectedVersions);
        copyStringArray(detailObject.optJSONArray("affected_devices"), detail.affectedDevices);
        copyStringArray(detailObject.optJSONArray("sample_stacktrace"), detail.sampleStacktrace);
        JSONArray breadcrumbsArray = detailObject.optJSONArray("sample_breadcrumbs");
        if (breadcrumbsArray != null) {
            for (int i = 0; i < breadcrumbsArray.length(); i++) {
                JSONObject object = breadcrumbsArray.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                OwnerIssueBreadcrumb breadcrumb = new OwnerIssueBreadcrumb();
                breadcrumb.timestamp = object.optLong("timestamp");
                breadcrumb.screen = optNullableString(object, "screen");
                breadcrumb.action = optNullableString(object, "action");
                JSONObject contextObject = object.optJSONObject("context");
                if (contextObject != null) {
                    JSONArray names = contextObject.names();
                    if (names != null) {
                        for (int j = 0; j < names.length(); j++) {
                            String name = names.optString(j, null);
                            if (name != null) {
                                breadcrumb.context.put(name, String.valueOf(contextObject.opt(name)));
                            }
                        }
                    }
                }
                detail.sampleBreadcrumbs.add(breadcrumb);
            }
        }
        JSONObject sampleContext = detailObject.optJSONObject("sample_context");
        if (sampleContext != null) {
            JSONArray names = sampleContext.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.optString(i, null);
                    if (name != null) {
                        detail.sampleContext.put(name, String.valueOf(sampleContext.opt(name)));
                    }
                }
            }
        }
        JSONArray users = detailObject.optJSONArray("users");
        if (users != null) {
            for (int i = 0; i < users.length(); i++) {
                JSONObject object = users.getJSONObject(i);
                OwnerIssueUser user = new OwnerIssueUser();
                user.userId = object.optLong("user_id");
                user.username = optNullableString(object, "username");
                user.displayName = optNullableString(object, "display_name");
                user.appVersion = optNullableString(object, "app_version");
                user.buildChannel = optNullableString(object, "build_channel");
                user.occurredAt = parseIsoTime(optNullableString(object, "occurred_at"));
                user.dialogId = object.optLong("dialog_id");
                detail.users.add(user);
            }
        }
        return detail;
    }

    private long parseIsoTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0L;
        }
        synchronized (isoParsers) {
            for (SimpleDateFormat parser : isoParsers) {
                try {
                    Date date = parser.parse(value);
                    if (date != null) {
                        return date.getTime();
                    }
                } catch (Exception ignore) {
                }
            }
        }
        return 0L;
    }

    private long getInstalledVersionCode() {
        if (cachedVersionCode > 0) {
            return cachedVersionCode;
        }
        try {
            PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.getApplicationId(), 0);
            cachedVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
        } catch (Exception e) {
            FileLog.e(e);
            cachedVersionCode = 0;
        }
        return cachedVersionCode;
    }

    private long resolveClientUserId() {
        UserConfig selected = UserConfig.getInstance(UserConfig.selectedAccount);
        if (selected != null && selected.isClientActivated() && selected.getClientUserId() != 0) {
            return selected.getClientUserId();
        }
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            UserConfig config = UserConfig.getInstance(account);
            if (config != null && config.isClientActivated() && config.getClientUserId() != 0) {
                return config.getClientUserId();
            }
        }
        return 0L;
    }

    private String getBuildChannel() {
        return BuildVars.BUILD_VERSION_STRING != null && BuildVars.BUILD_VERSION_STRING.toLowerCase(Locale.US).contains("beta") ? "beta" : "stable";
    }

    private void scheduleNextHeartbeat(boolean immediate) {
        AndroidUtilities.cancelRunOnUIThread(heartbeatRunnable);
        if (!appActive || !isConfigured()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - lastHeartbeatAt;
        long delay = immediate ? Math.max(1500L, HEARTBEAT_INTERVAL_MS - elapsed) : HEARTBEAT_INTERVAL_MS;
        if (elapsed >= HEARTBEAT_INTERVAL_MS) {
            delay = 1500L;
        }
        AndroidUtilities.runOnUIThread(heartbeatRunnable, delay);
    }

    private void scheduleEventFlush(boolean immediate) {
        AndroidUtilities.cancelRunOnUIThread(flushEventsRunnable);
        if (!appActive || !isConfigured()) {
            return;
        }
        AndroidUtilities.runOnUIThread(flushEventsRunnable, immediate ? 2_000L : EVENT_FLUSH_INTERVAL_MS);
    }

    private void scheduleMainThreadStallCheck(boolean immediate) {
        AndroidUtilities.cancelRunOnUIThread(stallCheckRunnable);
        if (!appActive || !isConfigured()) {
            return;
        }
        long delay = immediate ? 1_500L : MAIN_THREAD_STALL_CHECK_MS;
        mainThreadStallExpectedAt = System.currentTimeMillis() + delay;
        AndroidUtilities.runOnUIThread(stallCheckRunnable, delay);
    }

    private void loadPersistedQueueLocked() {
        telemetryQueueFile = new File(ApplicationLoader.getFilesDirFixed(), "massgram_telemetry_queue.json");
        if (!telemetryQueueFile.exists()) {
            return;
        }
        synchronized (queueFileLock) {
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(telemetryQueueFile);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    outputStream.write(buffer, 0, read);
                }
                queuedEvents.clear();
                queuedEvents.addAll(MassgramTelemetryFormat.deserializeQueue(outputStream.toString(StandardCharsets.UTF_8.name())));
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                closeQuietly(inputStream);
            }
        }
    }

    private void persistQueueLocked() {
        if (telemetryQueueFile == null) {
            telemetryQueueFile = new File(ApplicationLoader.getFilesDirFixed(), "massgram_telemetry_queue.json");
        }
        synchronized (queueFileLock) {
            FileOutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(telemetryQueueFile, false);
                outputStream.write(MassgramTelemetryFormat.serializeQueue(queuedEvents).getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                closeQuietly(outputStream);
            }
        }
    }

    private HttpURLConnection openConnection(String urlString, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("apikey", BuildConfig.MASSGRAM_SUPABASE_PUBLISHABLE_KEY);
        connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.MASSGRAM_SUPABASE_PUBLISHABLE_KEY);
        connection.setRequestProperty("User-Agent", "Massgram/" + BuildVars.BUILD_VERSION_STRING);
        return connection;
    }

    private String readFully(BufferedInputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toString(StandardCharsets.UTF_8.name());
    }

    private String parseError(String body, String fallback) {
        if (TextUtils.isEmpty(body)) {
            return fallback;
        }
        try {
            JSONObject object = new JSONObject(body);
            String error = object.optString("error");
            if (!TextUtils.isEmpty(error)) {
                return error;
            }
        } catch (Exception ignore) {
        }
        return fallback;
    }

    private void copyStringArray(JSONArray source, ArrayList<String> target) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            String value = source.optString(i, null);
            if (!TextUtils.isEmpty(value)) {
                target.add(value);
            }
        }
    }

    private String optNullableString(JSONObject object, String key) {
        if (object == null || object.isNull(key)) {
            return null;
        }
        String value = object.optString(key, null);
        return TextUtils.isEmpty(value) ? null : value;
    }

    private void closeQuietly(java.io.Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Exception ignore) {
        }
    }

    private String getHeartbeatUrl() {
        return BuildConfig.MASSGRAM_SUPABASE_URL + "/functions/v1/massgram-heartbeat";
    }

    private String getOwnerDashboardUrl() {
        return BuildConfig.MASSGRAM_SUPABASE_URL + "/functions/v1/massgram-owner-dashboard";
    }

    private String getTelemetryEventUrl() {
        return BuildConfig.MASSGRAM_SUPABASE_URL + "/functions/v1/massgram-telemetry-event";
    }

    private String getOwnerIssuesUrl() {
        return BuildConfig.MASSGRAM_SUPABASE_URL + "/functions/v1/massgram-owner-issues";
    }

    private static final class UriEncoder {
        private UriEncoder() {
        }

        private static String encode(String value) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                    builder.append(ch);
                } else {
                    byte[] bytes = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
                    for (byte b : bytes) {
                        builder.append('%');
                        String hex = Integer.toHexString(b & 0xFF).toUpperCase(Locale.US);
                        if (hex.length() == 1) {
                            builder.append('0');
                        }
                        builder.append(hex);
                    }
                }
            }
            return builder.toString();
        }
    }
}
