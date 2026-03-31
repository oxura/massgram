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

$payload = [ordered]@{
    versionName = $VersionName
    versionCode = $VersionCode
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
                    if ($null -ne $entry.versionCode -and [int]$entry.versionCode -ne $VersionCode) {
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
        versionName = $VersionName
        versionCode = $VersionCode
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
