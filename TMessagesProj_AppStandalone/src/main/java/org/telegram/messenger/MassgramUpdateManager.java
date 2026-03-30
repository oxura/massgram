package org.telegram.messenger;

import android.app.Activity;
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
import java.util.Locale;

public class MassgramUpdateManager {

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

    private static volatile MassgramUpdateManager instance;

    private final Object updateLock = new Object();
    private final ArrayList<Runnable> checkCallbacks = new ArrayList<>();

    private volatile MassgramUpdateInfo currentUpdate;
    private volatile boolean checkingUpdate;
    private volatile String lastUpdateError;
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
    }

    public void initialize() {
        restorePersistedUpdate();
        ensureDownloadReceiver();
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(BuildConfig.MASSGRAM_UPDATE_URL);
    }

    @Nullable
    public BetaUpdate getUpdate() {
        if (currentUpdate == null) {
            restorePersistedUpdate();
        }
        return currentUpdate;
    }

    @Nullable
    public String getLastUpdateError() {
        return lastUpdateError;
    }

    public void checkUpdate(boolean force, @Nullable Runnable whenDone) {
        if (whenDone != null) {
            synchronized (updateLock) {
                checkCallbacks.add(whenDone);
            }
        }
        if (!isConfigured()) {
            lastUpdateError = null;
            runPendingCallbacks();
            return;
        }

        final long now = System.currentTimeMillis();
        SharedPreferences preferences = getPreferences();
        long lastCheckTime = preferences.getLong(KEY_LAST_CHECK_TIME, 0L);
        if (!force && now - lastCheckTime < UPDATE_CHECK_INTERVAL_MS) {
            runPendingCallbacks();
            return;
        }

        synchronized (updateLock) {
            if (checkingUpdate) {
                return;
            }
            checkingUpdate = true;
        }

        Utilities.globalQueue.postRunnable(() -> {
            MassgramUpdateInfo update = null;
            String errorMessage = null;
            try {
                update = fetchRemoteUpdate();
            } catch (Exception e) {
                FileLog.e(e);
                errorMessage = LocaleController.getString(R.string.MassgramUpdateCheckFailed);
            }

            final MassgramUpdateInfo fetchedUpdate = update;
            final String fetchedError = errorMessage;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (fetchedError == null) {
                        lastUpdateError = null;
                        applyFetchedUpdate(fetchedUpdate, now);
                    } else {
                        lastUpdateError = fetchedError;
                    }
                } finally {
                    synchronized (updateLock) {
                        checkingUpdate = false;
                    }
                    runPendingCallbacks();
                }
            });
        });
    }

    public void downloadUpdate() {
        MassgramUpdateInfo update = currentUpdate;
        if (update == null || isDownloadingUpdate()) {
            return;
        }

        File targetFile = getUpdateFile(update);
        if (targetFile.exists() && !targetFile.delete()) {
            lastUpdateError = LocaleController.getString(R.string.MassgramUpdateDownloadFailed);
            showErrorBulletin(lastUpdateError);
            return;
        }

        DownloadManager downloadManager = getDownloadManager();
        if (downloadManager == null) {
            lastUpdateError = LocaleController.getString(R.string.MassgramUpdateDownloadFailed);
            showErrorBulletin(lastUpdateError);
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(update.apkUrl));
            request.setTitle(LocaleController.getString(R.string.AppUpdate));
            request.setDescription(LocaleController.formatString("AppBetaUpdateVersion", R.string.AppBetaUpdateVersion, update.version, String.valueOf(update.versionCode)));
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(ApplicationLoader.applicationContext, Environment.DIRECTORY_DOWNLOADS, targetFile.getName());

            long downloadId = downloadManager.enqueue(request);
            getPreferences().edit().putLong(KEY_DOWNLOAD_ID, downloadId).apply();
            lastUpdateError = null;
        } catch (Exception e) {
            FileLog.e(e);
            lastUpdateError = LocaleController.getString(R.string.MassgramUpdateDownloadFailed);
            showErrorBulletin(lastUpdateError);
        }
    }

    public void cancelDownloadingUpdate() {
        long downloadId = getPreferences().getLong(KEY_DOWNLOAD_ID, 0L);
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
        getPreferences().edit().remove(KEY_DOWNLOAD_ID).apply();
    }

    public boolean isDownloadingUpdate() {
        DownloadStatus status = getCurrentDownloadStatus();
        return status != null && (status.status == DownloadManager.STATUS_PENDING || status.status == DownloadManager.STATUS_RUNNING || status.status == DownloadManager.STATUS_PAUSED);
    }

    public float getDownloadingUpdateProgress() {
        DownloadStatus status = getCurrentDownloadStatus();
        if (status == null || status.totalBytes <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, status.downloadedBytes / (float) status.totalBytes));
    }

    @Nullable
    public File getDownloadedUpdateFile() {
        MassgramUpdateInfo update = currentUpdate;
        if (update == null) {
            restorePersistedUpdate();
            update = currentUpdate;
        }
        if (update == null) {
            return null;
        }
        File file = getUpdateFile(update);
        return file.exists() ? file : null;
    }

    public boolean showUpdatePopup(Context context, @Nullable BetaUpdate update) {
        if (!(update instanceof MassgramUpdateInfo) || context == null) {
            return false;
        }
        MassgramUpdateInfo updateInfo = (MassgramUpdateInfo) update;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.AppUpdate));
        builder.setMessage(buildUpdateMessage(updateInfo));

        File downloadedFile = getDownloadedUpdateFile();
        if (downloadedFile != null) {
            builder.setPositiveButton(LocaleController.getString(R.string.AppUpdateNow), (dialog, which) -> openDownloadedUpdateInstall(context));
        } else if (isDownloadingUpdate()) {
            builder.setPositiveButton(LocaleController.getString(R.string.StopDownload), (dialog, which) -> cancelDownloadingUpdate());
        } else {
            builder.setPositiveButton(LocaleController.getString(R.string.AppUpdateDownloadNow), (dialog, which) -> downloadUpdate());
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
        File file = getDownloadedUpdateFile();
        if (file == null || !file.exists()) {
            return false;
        }
        if (!verifyDownloadedFile(file, currentUpdate)) {
            lastUpdateError = LocaleController.getString(R.string.MassgramUpdateInvalidManifest);
            showErrorBulletin(lastUpdateError);
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
            lastUpdateError = LocaleController.getString(R.string.MassgramUpdateDownloadFailed);
            showErrorBulletin(lastUpdateError);
            return false;
        }
    }

    private void applyFetchedUpdate(@Nullable MassgramUpdateInfo update, long checkedAt) {
        MassgramUpdateInfo previousUpdate = currentUpdate;
        getPreferences().edit().putLong(KEY_LAST_CHECK_TIME, checkedAt).apply();

        if (update == null) {
            currentUpdate = null;
            clearPersistedUpdate(false);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            return;
        }

        currentUpdate = update;
        persistUpdate(update, checkedAt);
        if (previousUpdate != null && previousUpdate.versionCode != update.versionCode) {
            File oldFile = getUpdateFile(previousUpdate);
            if (oldFile.exists()) {
                oldFile.delete();
            }
            cancelDownloadingUpdate();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    @Nullable
    private MassgramUpdateInfo fetchRemoteUpdate() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BuildConfig.MASSGRAM_UPDATE_URL).openConnection();
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

    private void restorePersistedUpdate() {
        SharedPreferences preferences = getPreferences();
        int versionCode = preferences.getInt(KEY_VERSION_CODE, 0);
        String version = preferences.getString(KEY_VERSION, null);
        String apkUrl = preferences.getString(KEY_APK_URL, null);
        String sha256 = preferences.getString(KEY_SHA256, null);
        String changelog = preferences.getString(KEY_CHANGELOG, null);
        long apkSize = preferences.getLong(KEY_APK_SIZE, -1L);
        if (versionCode <= BuildConfig.VERSION_CODE || TextUtils.isEmpty(version) || TextUtils.isEmpty(apkUrl) || TextUtils.isEmpty(sha256)) {
            currentUpdate = null;
            clearPersistedUpdate(true);
            return;
        }
        currentUpdate = new MassgramUpdateInfo(version, versionCode, changelog, apkUrl, sha256, apkSize);
    }

    private void persistUpdate(MassgramUpdateInfo update, long checkedAt) {
        getPreferences().edit()
            .putLong(KEY_LAST_CHECK_TIME, checkedAt)
            .putString(KEY_VERSION, update.version)
            .putInt(KEY_VERSION_CODE, update.versionCode)
            .putString(KEY_CHANGELOG, update.changelog)
            .putString(KEY_APK_URL, update.apkUrl)
            .putString(KEY_SHA256, update.sha256)
            .putLong(KEY_APK_SIZE, update.apkSize)
            .apply();
    }

    private void clearPersistedUpdate(boolean keepDownloadId) {
        SharedPreferences.Editor editor = getPreferences().edit()
            .remove(KEY_VERSION)
            .remove(KEY_VERSION_CODE)
            .remove(KEY_CHANGELOG)
            .remove(KEY_APK_URL)
            .remove(KEY_SHA256)
            .remove(KEY_APK_SIZE);
        if (!keepDownloadId) {
            editor.remove(KEY_DOWNLOAD_ID);
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
                long trackedDownloadId = getPreferences().getLong(KEY_DOWNLOAD_ID, 0L);
                if (downloadId <= 0L || trackedDownloadId != downloadId) {
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> handleDownloadFinished(context));
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

    private void handleDownloadFinished(Context context) {
        DownloadStatus status = getCurrentDownloadStatus();
        if (status == null) {
            getPreferences().edit().remove(KEY_DOWNLOAD_ID).apply();
            return;
        }
        if (status.status == DownloadManager.STATUS_SUCCESSFUL) {
            getPreferences().edit().remove(KEY_DOWNLOAD_ID).apply();
            File file = getDownloadedUpdateFile();
            if (file == null || !verifyDownloadedFile(file, currentUpdate)) {
                lastUpdateError = LocaleController.getString(R.string.MassgramUpdateDownloadFailed);
                showErrorBulletin(lastUpdateError);
                return;
            }
            LaunchActivity launchActivity = LaunchActivity.instance;
            if (launchActivity != null) {
                openDownloadedUpdateInstall(launchActivity);
            } else {
                showInfoBulletin(LocaleController.getString(R.string.MassgramUpdateDownloaded));
            }
        } else if (status.status == DownloadManager.STATUS_FAILED) {
            getPreferences().edit().remove(KEY_DOWNLOAD_ID).apply();
            lastUpdateError = LocaleController.getString(R.string.MassgramUpdateDownloadFailed);
            showErrorBulletin(lastUpdateError);
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
    private DownloadStatus getCurrentDownloadStatus() {
        long downloadId = getPreferences().getLong(KEY_DOWNLOAD_ID, 0L);
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

    private File getUpdateFile(MassgramUpdateInfo update) {
        File externalDir = ApplicationLoader.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (externalDir == null) {
            externalDir = ApplicationLoader.getFilesDirFixed("updates");
        }
        if (externalDir != null && !externalDir.exists()) {
            externalDir.mkdirs();
        }
        return new File(externalDir, String.format(Locale.US, "massgram-update-%d.apk", update.versionCode));
    }

    private SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void runPendingCallbacks() {
        ArrayList<Runnable> callbacks;
        synchronized (updateLock) {
            callbacks = new ArrayList<>(checkCallbacks);
            checkCallbacks.clear();
        }
        for (int i = 0; i < callbacks.size(); i++) {
            Runnable callback = callbacks.get(i);
            if (callback != null) {
                callback.run();
            }
        }
    }

    private String buildUpdateMessage(MassgramUpdateInfo update) {
        StringBuilder builder = new StringBuilder();
        if (update.apkSize > 0) {
            builder.append(LocaleController.formatString("AppUpdateVersionAndSize", R.string.AppUpdateVersionAndSize, update.version, AndroidUtilities.formatFileSize(update.apkSize)));
        } else {
            builder.append(LocaleController.formatString("AppBetaUpdateVersion", R.string.AppBetaUpdateVersion, update.version, String.valueOf(update.versionCode)));
        }

        if (isDownloadingUpdate()) {
            builder.append("\n\n").append(LocaleController.formatString(R.string.AppUpdateDownloading, Math.round(getDownloadingUpdateProgress() * 100f)));
        } else if (getDownloadedUpdateFile() != null) {
            builder.append("\n\n").append(LocaleController.getString(R.string.AppUpdateNow));
        }

        builder.append("\n\n");
        if (TextUtils.isEmpty(update.changelog)) {
            builder.append(LocaleController.getString(R.string.AppUpdateChangelogEmpty).replace("**", ""));
        } else {
            builder.append(update.changelog.trim());
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

    private static final class DownloadStatus {
        int status;
        long downloadedBytes;
        long totalBytes;
    }
}
