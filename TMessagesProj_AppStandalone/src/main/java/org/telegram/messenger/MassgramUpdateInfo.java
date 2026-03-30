package org.telegram.messenger;

import androidx.annotation.Nullable;

public class MassgramUpdateInfo extends BetaUpdate {

    public final String apkUrl;
    public final String sha256;
    public final long apkSize;

    public MassgramUpdateInfo(String version, int versionCode, @Nullable String changelog, String apkUrl, String sha256, long apkSize) {
        super(version, versionCode, changelog);
        this.apkUrl = apkUrl;
        this.sha256 = sha256;
        this.apkSize = apkSize;
    }
}
