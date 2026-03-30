package org.telegram.messenger;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.json.JSONObject;
import org.telegram.messenger.web.BuildConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;

public class MassgramUpdateManager {

    public enum Channel {
        STABLE("stable"),
        BETA("beta");

        final String key;

        Channel(String key) {
            this.key = key;
        }
    }

    private static final String PREFS_NAME = "massgram_update";
    private static final String KEY_LAST_CHECK_TIME = "last_check_time";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_VERSION = "version";
    private static final String KEY_VERSION_CODE = "version_code";
    private static final String KEY_CHANGELOG = "changelog";
    private static final String KEY_APK_URL = "apk_url";
    private static final String KEY_SHA256 = "sha256";
    private static final String KEY_APK_SIZE = "apk_size";
    private static final long UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final long[] BETA_TESTER_IDS = {8474618900L, 1339538506L, 6539627752L};

    private static volatile MassgramUpdateManager instance;

    private final EnumMap<Channel, ChannelState> states = new EnumMap<>(Channel.class);
    private BroadcastReceiver downloadReceiver;
    private boolean receiverRegistered;

    public static MassgramUpdateManager getInstance() {
        MassgramUpdateManager localInstance = instance;
        if (localInstance == null) {
            synchronized (MassgramUpdateManager.class) {
                localInstance = instance;
                if (localInstance == null) {
                    localInstance = instance = new MassgramUpdateManager();
                }
            }
        }
        return localInstance;
    }

    private MassgramUpdateManager() {
        states.put(Channel.STABLE, new ChannelState());
        states.put(Channel.BETA, new ChannelState());
    }

    public void initialize() {
        restorePersistedUpdate(Channel.STABLE);
        restorePersistedUpdate(Channel.BETA);
        ensureDownloadReceiver();
    }

    public boolean isConfigured() {
        return isConfigured(Channel.STABLE);
    }

    public boolean isConfigured(Channel channel) {
        return !TextUtils.isEmpty(getManifestUrl(channel));
    }

    public boolean isBetaConfigured() {
        return isConfigured(Channel.BETA);
    }

    public boolean isBetaTester(long userId) {
        for (int i = 0; i < BETA_TESTER_IDS.length; i++) {
            if (BETA_TESTER_IDS[i] == userId) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public BetaUpdate getUpdate() {
        return getUpdate(Channel.STABLE);
    }

    @Nullable
    public BetaUpdate getUpdate(Channel channel) {
        ChannelState state = getState(channel);
        if (state.currentUpdate == null) {
            restorePersistedUpdate(channel);
        }
        return state.currentUpdate;
    }

    @Nullable
    public String getLastUpdateError() {
        return getLastUpdateError(Channel.STABLE);
    }

    @Nullable
    public String getLastUpdateError(Channel channel) {
        return getState(channel).lastUpdateError;
    }

    public void checkUpdate(boolean force, @Nullable Runnable whenDone) {
        checkUpdate(Channel.STABLE, force, whenDone);
    }

    public void checkUpdate(Channel channel, boolean force, @Nullable Runnable whenDone) {
        ChannelState state = getState(channel);
        if (whenDone != null) {
            synchronized (state.lock) {
                state.checkCallbacks.add(whenDone);
            }
        }
        if (!isConfigured(channel)) {
            state.lastUpdateError = null;
            runPendingCallbacks(channel);
            return;
        }

        final long now = System.currentTimeMillis();
        SharedPreferences preferences = getPreferences();
        long lastCheckTime = preferences.getLong(channelKey(channel, KEY_LAST_CHECK_TIME), 0L);
        if (!force && now - lastCheckTime < UPDATE_CHECK_INTERVAL_MS) {
            runPendingCallbacks(channel);
            return;
        }

        synchronized (state.lock) {
            if (state.checkingUpdate) {
                return;
            }
            state.checkingUpdate = true;
        }

        Utilities.globalQueue.postRunnable(() -> {
            MassgramUpdateInfo update = null;
            String errorMessage = null;
            try {
                update = fetchRemoteUpdate(channel);
            } catch (Exception e) {
                FileLog.e(e);
                errorMessage = LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaUpdateCheckFailed : R.string.MassgramUpdateCheckFailed);
            }

            final MassgramUpdateInfo fetchedUpdate = update;
            final String fetchedError = errorMessage;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (fetchedError == null) {
                        state.lastUpdateError = null;
                        applyFetchedUpdate(channel, fetchedUpdate, now);
                    } else {
                        state.lastUpdateError = fetchedError;
                    }
                } finally {
                    synchronized (state.lock) {
                        state.checkingUpdate = false;
                    }
                    runPendingCallbacks(channel);
                }
            });
        });
    }

    public void downloadUpdate() {
        downloadUpdate(Channel.STABLE);
    }

    public void downloadUpdate(Channel channel) {
        ChannelState state = getState(channel);
        MassgramUpdateInfo update = state.currentUpdate;
        if (update == null || isDownloadingUpdate(channel)) {
            return;
        }

        File targetFile = getUpdateFile(channel, update);
        if (targetFile.exists() && !targetFile.delete()) {
            state.lastUpdateError = LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaUpdateDownloadFailed : R.string.MassgramUpdateDownloadFailed);
            showErrorBulletin(state.lastUpdateError);
            return;
        }

        DownloadManager downloadManager = getDownloadManager();
        if (downloadManager == null) {
            state.lastUpdateError = LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaUpdateDownloadFailed : R.string.MassgramUpdateDownloadFailed);
            showErrorBulletin(state.lastUpdateError);
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(update.apkUrl));
            request.setTitle(LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaCheckUpdates : R.string.AppUpdate));
            request.setDescription(LocaleController.formatString("AppBetaUpdateVersion", R.string.AppBetaUpdateVersion, update.version, String.valueOf(update.versionCode)));
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(ApplicationLoader.applicationContext, Environment.DIRECTORY_DOWNLOADS, targetFile.getName());

            long downloadId = downloadManager.enqueue(request);
            getPreferences().edit().putLong(channelKey(channel, KEY_DOWNLOAD_ID), downloadId).apply();
            state.lastUpdateError = null;
        } catch (Exception e) {
            FileLog.e(e);
            state.lastUpdateError = LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaUpdateDownloadFailed : R.string.MassgramUpdateDownloadFailed);
            showErrorBulletin(state.lastUpdateError);
        }
    }

    public void cancelDownloadingUpdate() {
        cancelDownloadingUpdate(Channel.STABLE);
    }

    public void cancelDownloadingUpdate(Channel channel) {
        long downloadId = getPreferences().getLong(channelKey(channel, KEY_DOWNLOAD_ID), 0L);
        if (downloadId == 0L) {
            return;
        }
        DownloadManager downloadManager = getDownloadManager();
        if (downloadManager != null) {
            try {
                downloadManager.remove(downloadId);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        getPreferences().edit().remove(channelKey(channel, KEY_DOWNLOAD_ID)).apply();
    }

    public boolean isDownloadingUpdate() {
        return isDownloadingUpdate(Channel.STABLE);
    }

    public boolean isDownloadingUpdate(Channel channel) {
        DownloadStatus status = getCurrentDownloadStatus(channel);
        return status != null && (status.status == DownloadManager.STATUS_PENDING || status.status == DownloadManager.STATUS_RUNNING || status.status == DownloadManager.STATUS_PAUSED);
    }

    public float getDownloadingUpdateProgress() {
        return getDownloadingUpdateProgress(Channel.STABLE);
    }

    public float getDownloadingUpdateProgress(Channel channel) {
        DownloadStatus status = getCurrentDownloadStatus(channel);
        if (status == null || status.totalBytes <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, status.downloadedBytes / (float) status.totalBytes));
    }

    @Nullable
    public File getDownloadedUpdateFile() {
        return getDownloadedUpdateFile(Channel.STABLE);
    }

    @Nullable
    public File getDownloadedUpdateFile(Channel channel) {
        ChannelState state = getState(channel);
        MassgramUpdateInfo update = state.currentUpdate;
        if (update == null) {
            restorePersistedUpdate(channel);
            update = state.currentUpdate;
        }
        if (update == null) {
            return null;
        }
        File file = getUpdateFile(channel, update);
        return file.exists() ? file : null;
    }

    public boolean showUpdatePopup(Context context, @Nullable BetaUpdate update) {
        return showUpdatePopup(context, update, Channel.STABLE);
    }

    public boolean showUpdatePopup(Context context, @Nullable BetaUpdate update, Channel channel) {
        if (!(update instanceof MassgramUpdateInfo) || context == null) {
            return false;
        }
        MassgramUpdateInfo updateInfo = (MassgramUpdateInfo) update;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaCheckUpdates : R.string.AppUpdate));
        builder.setMessage(buildUpdateMessage(channel, updateInfo));

        File downloadedFile = getDownloadedUpdateFile(channel);
        if (downloadedFile != null) {
            builder.setPositiveButton(LocaleController.getString(R.string.AppUpdateNow), (dialog, which) -> openDownloadedUpdateInstall(context, channel));
        } else if (isDownloadingUpdate(channel)) {
            builder.setPositiveButton(LocaleController.getString(R.string.StopDownload), (dialog, which) -> cancelDownloadingUpdate(channel));
        } else {
            builder.setPositiveButton(LocaleController.getString(R.string.AppUpdateDownloadNow), (dialog, which) -> downloadUpdate(channel));
        }
        builder.setNegativeButton(LocaleController.getString(R.string.AppUpdateRemindMeLater), null);
        try {
            builder.show();
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
        return true;
    }

    public boolean openDownloadedUpdateInstall(Context context) {
        return openDownloadedUpdateInstall(context, Channel.STABLE);
    }

    public boolean openDownloadedUpdateInstall(Context context, Channel channel) {
        ChannelState state = getState(channel);
        File file = getDownloadedUpdateFile(channel);
        if (file == null || !file.exists()) {
            return false;
        }
        if (!verifyDownloadedFile(file, state.currentUpdate)) {
            state.lastUpdateError = LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaUpdateInvalidManifest : R.string.MassgramUpdateInvalidManifest);
            showErrorBulletin(state.lastUpdateError);
            return false;
        }
        if (!ApplicationLoader.applicationLoaderInstance.checkApkInstallPermissions(context)) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file),
                "application/vnd.android.package-archive"
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            state.lastUpdateError = LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaUpdateDownloadFailed : R.string.MassgramUpdateDownloadFailed);
            showErrorBulletin(state.lastUpdateError);
            return false;
        }
    }

    private void applyFetchedUpdate(Channel channel, @Nullable MassgramUpdateInfo update, long checkedAt) {
        ChannelState state = getState(channel);
        MassgramUpdateInfo previousUpdate = state.currentUpdate;
        getPreferences().edit().putLong(channelKey(channel, KEY_LAST_CHECK_TIME), checkedAt).apply();

        if (update == null) {
            state.currentUpdate = null;
            clearPersistedUpdate(channel, false);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            return;
        }

        state.currentUpdate = update;
        persistUpdate(channel, update, checkedAt);
        if (previousUpdate != null && previousUpdate.versionCode != update.versionCode) {
            File oldFile = getUpdateFile(channel, previousUpdate);
            if (oldFile.exists()) {
                oldFile.delete();
            }
            cancelDownloadingUpdate(channel);
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    @Nullable
    private MassgramUpdateInfo fetchRemoteUpdate(Channel channel) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(getManifestUrl(channel)).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setUseCaches(false);

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("Update manifest HTTP " + responseCode);
            }

            String response = readStream(connection.getInputStream());
            JSONObject jsonObject = new JSONObject(response);

            String version = jsonObject.optString("versionName", jsonObject.optString("version", null));
            int versionCode = jsonObject.optInt("versionCode", 0);
            String apkUrl = jsonObject.optString("apkUrl", null);
            String sha256 = jsonObject.optString("sha256", null);
            String changelog = jsonObject.optString("changelog", null);
            long apkSize = jsonObject.has("apkSize") ? jsonObject.optLong("apkSize", -1L) : jsonObject.optLong("size", -1L);

            if (TextUtils.isEmpty(version) || versionCode <= 0 || TextUtils.isEmpty(apkUrl) || TextUtils.isEmpty(sha256)) {
                throw new IllegalStateException("Update manifest is missing required fields");
            }
            if (versionCode <= BuildConfig.VERSION_CODE) {
                return null;
            }
            return new MassgramUpdateInfo(version, versionCode, changelog, apkUrl, sha256, apkSize);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void restorePersistedUpdate(Channel channel) {
        ChannelState state = getState(channel);
        SharedPreferences preferences = getPreferences();
        int versionCode = preferences.getInt(channelKey(channel, KEY_VERSION_CODE), 0);
        String version = preferences.getString(channelKey(channel, KEY_VERSION), null);
        String apkUrl = preferences.getString(channelKey(channel, KEY_APK_URL), null);
        String sha256 = preferences.getString(channelKey(channel, KEY_SHA256), null);
        String changelog = preferences.getString(channelKey(channel, KEY_CHANGELOG), null);
        long apkSize = preferences.getLong(channelKey(channel, KEY_APK_SIZE), -1L);
        if (versionCode <= BuildConfig.VERSION_CODE || TextUtils.isEmpty(version) || TextUtils.isEmpty(apkUrl) || TextUtils.isEmpty(sha256)) {
            state.currentUpdate = null;
            clearPersistedUpdate(channel, true);
            return;
        }
        state.currentUpdate = new MassgramUpdateInfo(version, versionCode, changelog, apkUrl, sha256, apkSize);
    }

    private void persistUpdate(Channel channel, MassgramUpdateInfo update, long checkedAt) {
        getPreferences().edit()
            .putLong(channelKey(channel, KEY_LAST_CHECK_TIME), checkedAt)
            .putString(channelKey(channel, KEY_VERSION), update.version)
            .putInt(channelKey(channel, KEY_VERSION_CODE), update.versionCode)
            .putString(channelKey(channel, KEY_CHANGELOG), update.changelog)
            .putString(channelKey(channel, KEY_APK_URL), update.apkUrl)
            .putString(channelKey(channel, KEY_SHA256), update.sha256)
            .putLong(channelKey(channel, KEY_APK_SIZE), update.apkSize)
            .apply();
    }

    private void clearPersistedUpdate(Channel channel, boolean keepDownloadId) {
        SharedPreferences.Editor editor = getPreferences().edit()
            .remove(channelKey(channel, KEY_VERSION))
            .remove(channelKey(channel, KEY_VERSION_CODE))
            .remove(channelKey(channel, KEY_CHANGELOG))
            .remove(channelKey(channel, KEY_APK_URL))
            .remove(channelKey(channel, KEY_SHA256))
            .remove(channelKey(channel, KEY_APK_SIZE));
        if (!keepDownloadId) {
            editor.remove(channelKey(channel, KEY_DOWNLOAD_ID));
        }
        editor.apply();
    }

    private void ensureDownloadReceiver() {
        if (receiverRegistered || ApplicationLoader.applicationContext == null) {
            return;
        }
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    return;
                }
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (downloadId <= 0L) {
                    return;
                }
                for (Channel channel : Channel.values()) {
                    long trackedDownloadId = getPreferences().getLong(channelKey(channel, KEY_DOWNLOAD_ID), 0L);
                    if (trackedDownloadId == downloadId) {
                        AndroidUtilities.runOnUIThread(() -> handleDownloadFinished(context, channel));
                        break;
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ApplicationLoader.applicationContext.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            ApplicationLoader.applicationContext.registerReceiver(downloadReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void handleDownloadFinished(Context context, Channel channel) {
        ChannelState state = getState(channel);
        DownloadStatus status = getCurrentDownloadStatus(channel);
        if (status == null) {
            getPreferences().edit().remove(channelKey(channel, KEY_DOWNLOAD_ID)).apply();
            return;
        }
        if (status.status == DownloadManager.STATUS_SUCCESSFUL) {
            getPreferences().edit().remove(channelKey(channel, KEY_DOWNLOAD_ID)).apply();
            File file = getDownloadedUpdateFile(channel);
            if (file == null || !verifyDownloadedFile(file, state.currentUpdate)) {
                state.lastUpdateError = LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaUpdateDownloadFailed : R.string.MassgramUpdateDownloadFailed);
                showErrorBulletin(state.lastUpdateError);
                return;
            }
            LaunchActivity launchActivity = LaunchActivity.instance;
            if (launchActivity != null) {
                openDownloadedUpdateInstall(launchActivity, channel);
            } else {
                showInfoBulletin(LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaUpdateDownloaded : R.string.MassgramUpdateDownloaded));
            }
        } else if (status.status == DownloadManager.STATUS_FAILED) {
            getPreferences().edit().remove(channelKey(channel, KEY_DOWNLOAD_ID)).apply();
            state.lastUpdateError = LocaleController.getString(channel == Channel.BETA ? R.string.MassgramBetaUpdateDownloadFailed : R.string.MassgramUpdateDownloadFailed);
            showErrorBulletin(state.lastUpdateError);
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    private boolean verifyDownloadedFile(@Nullable File file, @Nullable MassgramUpdateInfo update) {
        if (file == null || update == null || !file.exists()) {
            return false;
        }
        try {
            String fileHash = computeSha256(file);
            boolean matches = fileHash.equalsIgnoreCase(update.sha256);
            if (!matches) {
                file.delete();
            }
            return matches;
        } catch (Exception e) {
            FileLog.e(e);
            file.delete();
            return false;
        }
    }

    @Nullable
    private DownloadStatus getCurrentDownloadStatus(Channel channel) {
        long downloadId = getPreferences().getLong(channelKey(channel, KEY_DOWNLOAD_ID), 0L);
        if (downloadId == 0L) {
            return null;
        }
        DownloadManager downloadManager = getDownloadManager();
        if (downloadManager == null) {
            return null;
        }
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            DownloadStatus status = new DownloadStatus();
            status.status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            status.downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            status.totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            return status;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    @Nullable
    private DownloadManager getDownloadManager() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return (DownloadManager) ApplicationLoader.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    private File getUpdateFile(Channel channel, MassgramUpdateInfo update) {
        File externalDir = ApplicationLoader.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (externalDir == null) {
            externalDir = ApplicationLoader.getFilesDirFixed("updates");
        }
        if (externalDir != null && !externalDir.exists()) {
            externalDir.mkdirs();
        }
        String name = channel == Channel.BETA ? "massgram-beta-update-%d.apk" : "massgram-update-%d.apk";
        return new File(externalDir, String.format(Locale.US, name, update.versionCode));
    }

    private SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void runPendingCallbacks(Channel channel) {
        ChannelState state = getState(channel);
        ArrayList<Runnable> callbacks;
        synchronized (state.lock) {
            callbacks = new ArrayList<>(state.checkCallbacks);
            state.checkCallbacks.clear();
        }
        for (int i = 0; i < callbacks.size(); i++) {
            Runnable callback = callbacks.get(i);
            if (callback != null) {
                callback.run();
            }
        }
    }

    private String buildUpdateMessage(Channel channel, MassgramUpdateInfo update) {
        StringBuilder builder = new StringBuilder();
        if (update.apkSize > 0) {
            builder.append(LocaleController.formatString("AppUpdateVersionAndSize", R.string.AppUpdateVersionAndSize, update.version, AndroidUtilities.formatFileSize(update.apkSize)));
        } else {
            builder.append(LocaleController.formatString("AppBetaUpdateVersion", R.string.AppBetaUpdateVersion, update.version, String.valueOf(update.versionCode)));
        }

        if (isDownloadingUpdate(channel)) {
            builder.append("\n\n").append(LocaleController.formatString(R.string.AppUpdateDownloading, Math.round(getDownloadingUpdateProgress(channel) * 100f)));
        } else if (getDownloadedUpdateFile(channel) != null) {
            builder.append("\n\n").append(LocaleController.getString(R.string.AppUpdateNow));
        }

        builder.append("\n\n");
        if (TextUtils.isEmpty(update.changelog)) {
            builder.append(LocaleController.getString(R.string.AppUpdateChangelogEmpty).replace("**", ""));
        } else {
            builder.append(update.changelog.trim());
        }
        if (channel == Channel.BETA) {
            builder.append("\n\n").append(LocaleController.getString(R.string.MassgramBetaUpdateInfo));
        }
        return builder.toString();
    }

    private void showErrorBulletin(String text) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment != null) {
            BulletinFactory.of(fragment).createErrorBulletin(text).show();
        }
    }

    private void showInfoBulletin(String text) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment != null) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, text).show();
        }
    }

    private String readStream(InputStream inputStream) throws Exception {
        try (InputStream stream = inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString("UTF-8");
        }
    }

    private String computeSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (int i = 0; i < hash.length; i++) {
            builder.append(String.format(Locale.US, "%02x", hash[i]));
        }
        return builder.toString();
    }

    private ChannelState getState(Channel channel) {
        return states.get(channel);
    }

    private String getManifestUrl(Channel channel) {
        return channel == Channel.BETA ? BuildConfig.MASSGRAM_BETA_UPDATE_URL : BuildConfig.MASSGRAM_UPDATE_URL;
    }

    private String channelKey(Channel channel, String key) {
        return channel.key + "_" + key;
    }

    private static final class ChannelState {
        final Object lock = new Object();
        final ArrayList<Runnable> checkCallbacks = new ArrayList<>();
        volatile MassgramUpdateInfo currentUpdate;
        volatile boolean checkingUpdate;
        volatile String lastUpdateError;
    }

    private static final class DownloadStatus {
        int status;
        long downloadedBytes;
        long totalBytes;
    }
}
