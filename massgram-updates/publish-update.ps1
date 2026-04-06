param(
    [Parameter(Mandatory = $true)]
    [string]$GitHubRepo,

    [ValidateSet("stable", "beta")]
    [string]$Channel = "stable",

    [string]$ApkPath = "D:\Projects\flower-web\TMessagesProj_AppStandalone\build\outputs\apk\afat\debug\app.apk",
    [string]$VersionName = "12.5.1",
    [int]$VersionCode = 65819,
    [string]$Changelog = "Massgram update",
    [string]$OutputDir = "D:\Projects\flower-web\massgram-updates\updates"
)

$ErrorActionPreference = "Stop"

function Resolve-AaptPath {
    $candidates = @()
    if ($env:ANDROID_HOME) {
        $candidates += Join-Path $env:ANDROID_HOME "build-tools"
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += Join-Path $env:ANDROID_SDK_ROOT "build-tools"
    }
    $candidates += Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools"

    foreach ($root in $candidates) {
        if (!(Test-Path $root)) {
            continue
        }
        $aapt = Get-ChildItem -Path $root -Recurse -Filter aapt.exe -File -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($aapt) {
            return $aapt.FullName
        }
    }
    throw "Could not find aapt.exe in Android build-tools."
}

function Get-ApkVersionInfo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $aaptPath = Resolve-AaptPath
    $badging = & $aaptPath dump badging $Path | Select-String "package: name="
    if (!$badging) {
        throw "Could not read APK badging from $Path"
    }
    $line = $badging.Line
    if ($line -notmatch "versionCode='(\d+)'.*?\sversionName='([^']+)'") {
        throw "Could not parse version info from APK badging: $line"
    }
    return [pscustomobject]@{
        VersionCode = [int]$matches[1]
        VersionName = [string]$matches[2]
    }
}

if (!(Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath"
}

if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$normalizedRepo = $GitHubRepo.Trim().Trim('/')
if ($Channel -eq "beta") {
    $assetBaseUrl = "https://github.com/$normalizedRepo/releases/download/beta"
    $apkName = "massgram-beta.apk"
    $jsonName = "latest-beta.json"
} else {
    $assetBaseUrl = "https://github.com/$normalizedRepo/releases/latest/download"
    $apkName = "massgram.apk"
    $jsonName = "latest.json"
}
$targetApk = Join-Path $OutputDir $apkName
$targetJson = Join-Path $OutputDir $jsonName

Copy-Item -Path $ApkPath -Destination $targetApk -Force

$hash = (Get-FileHash -Path $targetApk -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item $targetApk).Length
$apkVersion = Get-ApkVersionInfo -Path $targetApk
$effectiveVersionCode = $apkVersion.VersionCode
$effectiveVersionName = $apkVersion.VersionName

if ($VersionCode -ne $effectiveVersionCode) {
    Write-Warning "Requested VersionCode $VersionCode does not match APK VersionCode $effectiveVersionCode. Using APK value."
}
if ($VersionName -ne $effectiveVersionName) {
    Write-Warning "Requested VersionName '$VersionName' does not match APK VersionName '$effectiveVersionName'. Using APK value."
}

$payload = [ordered]@{
    versionName = $effectiveVersionName
    versionCode = $effectiveVersionCode
    apkUrl = "$assetBaseUrl/$apkName"
    sha256 = $hash
    apkSize = $size
    changelog = $Changelog
}

($payload | ConvertTo-Json -Depth 5) + "`r`n" | Set-Content -Path $targetJson -Encoding UTF8

if ($Channel -eq "stable") {
    $targetChangelogJson = Join-Path $OutputDir "changelog-stable.json"
    $history = @()
    if (Test-Path $targetChangelogJson) {
        $existingRaw = Get-Content -Path $targetChangelogJson -Raw
        if (![string]::IsNullOrWhiteSpace($existingRaw)) {
            $existing = $existingRaw | ConvertFrom-Json
            if ($existing -is [System.Collections.IEnumerable]) {
                foreach ($entry in $existing) {
                    if ($null -ne $entry.versionCode -and [int]$entry.versionCode -ne $effectiveVersionCode) {
                        $history += [ordered]@{
                            versionName = [string]$entry.versionName
                            versionCode = [int]$entry.versionCode
                            publishedAt = [string]$entry.publishedAt
                            changelog = [string]$entry.changelog
                        }
                    }
                }
            }
        }
    }

    $currentEntry = [ordered]@{
        versionName = $effectiveVersionName
        versionCode = $effectiveVersionCode
        publishedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        changelog = $Changelog
    }

    $fullHistory = @($currentEntry) + $history | Select-Object -First 10
    ($fullHistory | ConvertTo-Json -Depth 5) + "`r`n" | Set-Content -Path $targetChangelogJson -Encoding UTF8
}

Write-Host "Updated:"
Write-Host "  APK:  $targetApk"
Write-Host "  JSON: $targetJson"
if ($Channel -eq "stable") {
    Write-Host "  CHANGELOG: $(Join-Path $OutputDir 'changelog-stable.json')"
}
Write-Host "  SHA256: $hash"
