# Massgram Updates

This folder is a GitHub Releases-ready template for Massgram app updates.

## Files to publish

- `updates/latest.json`
- `updates/massgram.apk`
- `updates/latest-beta.json`
- `updates/massgram-beta.apk`

Upload these files as GitHub Release assets, then set:

```properties
MASSGRAM_UPDATE_URL=https://github.com/oxura/massgram/releases/latest/download/latest.json
MASSGRAM_BETA_UPDATE_URL=https://github.com/oxura/massgram/releases/download/beta/latest-beta.json
```

in [gradle.properties](D:/Projects/flower-web/gradle.properties) before building the app you distribute.

## Release flow

1. Increase `APP_VERSION_CODE` in [gradle.properties](D:/Projects/flower-web/gradle.properties).
2. Build a new signed APK with the same keystore.
3. Run:

```powershell
.\massgram-updates\publish-update.ps1 -GitHubRepo "oxura/massgram" -Channel stable -VersionName "12.5.1" -VersionCode 65819 -Changelog "Massgram update"
```

4. This script replaces `updates/massgram.apk` and refreshes `updates/latest.json` with the real SHA-256 and size.
5. For beta testers use:

```powershell
.\massgram-updates\publish-update.ps1 -GitHubRepo "oxura/massgram" -Channel beta -VersionName "12.5.2-beta1" -VersionCode 65829 -Changelog "Massgram beta update"
```

6. Create a GitHub Release in `oxura/massgram`.
7. Upload stable assets to the release:

- `latest.json`
- `massgram.apk`

8. Keep a separate release on tag `beta` with these assets:

- `latest-beta.json`
- `massgram-beta.apk`

9. Publish the stable release as a full release. Replace beta assets on tag `beta` for each tester cycle.

## Notes

- The app only offers an update when `versionCode` in `latest.json` is greater than the installed app version.
- The publish script now reads the real `versionCode` and `versionName` from the built APK and writes those values into the update manifest. This avoids mismatches caused by ABI-specific package version codes.
- The APK is verified against `sha256` before install.
- Users still need to confirm the Android install prompt.
- GitHub `latest` points to the most recent published non-draft, non-prerelease release.
