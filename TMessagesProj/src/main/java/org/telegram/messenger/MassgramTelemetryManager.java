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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MassgramTelemetryManager {

    public interface DashboardCallback {
        void onResult(OwnerDashboardData data, String error);
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

    private static final long HEARTBEAT_INTERVAL_MS = 60_000L;
    private static final long DASHBOARD_CACHE_MS = 30_000L;
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
    private final Object dashboardLock = new Object();
    private final SimpleDateFormat[] isoParsers;

    private volatile boolean appActive;
    private volatile boolean heartbeatInFlight;
    private volatile long lastHeartbeatAt;
    private long cachedVersionCode = -1;
    private OwnerDashboardData cachedDashboard;
    private long cachedDashboardAt;

    private MassgramTelemetryManager() {
        SimpleDateFormat withMillis = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);
        withMillis.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat withoutMillis = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US);
        withoutMillis.setTimeZone(TimeZone.getTimeZone("UTC"));
        isoParsers = new SimpleDateFormat[]{withMillis, withoutMillis};
    }

    public void initialize() {
        // Singleton warm-up.
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
        appActive = true;
        scheduleNextHeartbeat(true);
    }

    public void onAppBackground() {
        appActive = false;
        AndroidUtilities.cancelRunOnUIThread(heartbeatRunnable);
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
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception ignore) {
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }

            final OwnerDashboardData finalData = data;
            final String finalError = error;
            AndroidUtilities.runOnUIThread(() -> callback.onResult(finalData, finalError));
        });
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

    private void sendHeartbeatBatch() throws Exception {
        JSONArray users = new JSONArray();
        long versionCode = getInstalledVersionCode();
        String buildChannel = BuildVars.BUILD_VERSION_STRING != null && BuildVars.BUILD_VERSION_STRING.toLowerCase(Locale.US).contains("beta") ? "beta" : "stable";
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
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception ignore) {
            }
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignore) {
            }
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
                user.username = object.optString("username", null);
                user.firstName = object.optString("first_name", null);
                user.lastName = object.optString("last_name", null);
                user.displayName = object.optString("display_name", null);
                user.appVersion = object.optString("app_version", null);
                user.buildChannel = object.optString("build_channel", null);
                user.online = object.optBoolean("is_online");
                user.lastSeenAt = parseIsoTime(object.optString("last_seen_at", null));
                data.users.add(user);
            }
        }
        return data;
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

    private String getHeartbeatUrl() {
        return BuildConfig.MASSGRAM_SUPABASE_URL + "/functions/v1/massgram-heartbeat";
    }

    private String getOwnerDashboardUrl() {
        return BuildConfig.MASSGRAM_SUPABASE_URL + "/functions/v1/massgram-owner-dashboard";
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
