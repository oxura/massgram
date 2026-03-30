param(
    [Parameter(Mandatory = $true)]
    [string]$GitHubRepo,

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
$assetBaseUrl = "https://github.com/$normalizedRepo/releases/latest/download"
$targetApk = Join-Path $OutputDir "massgram.apk"
$targetJson = Join-Path $OutputDir "latest.json"

Copy-Item -Path $ApkPath -Destination $targetApk -Force

$hash = (Get-FileHash -Path $targetApk -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item $targetApk).Length

$payload = [ordered]@{
    versionName = $VersionName
    versionCode = $VersionCode
    apkUrl = "$assetBaseUrl/massgram.apk"
    sha256 = $hash
    apkSize = $size
    changelog = $Changelog
}

($payload | ConvertTo-Json -Depth 5) + "`r`n" | Set-Content -Path $targetJson -Encoding UTF8

Write-Host "Updated:"
Write-Host "  APK:  $targetApk"
Write-Host "  JSON: $targetJson"
Write-Host "  SHA256: $hash"
