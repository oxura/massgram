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

Write-Host "Updated:"
Write-Host "  APK:  $targetApk"
Write-Host "  JSON: $targetJson"
Write-Host "  SHA256: $hash"
