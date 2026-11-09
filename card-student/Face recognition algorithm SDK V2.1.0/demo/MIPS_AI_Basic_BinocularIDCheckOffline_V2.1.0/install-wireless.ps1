# Install Xander Student over wireless ADB
# Usage:
#   .\install-wireless.ps1              # build release + install
#   .\install-wireless.ps1 -SkipBuild   # install last APK only
#   .\install-wireless.ps1 -Ip 192.168.1.50 -Port 5555

param(
    [string]$Ip = "",
    [int]$Port = 5555,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

function Ensure-Adb {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) {
        throw "adb not found on PATH. Install Android platform-tools."
    }
}

Ensure-Adb

if ($Ip -ne "") {
    Write-Host "Connecting wireless ADB $Ip:$Port ..."
    adb connect "$Ip:$Port" | Out-Host
}

$devices = adb devices | Select-String -Pattern "`tdevice$"
if (-not $devices) {
    Write-Host @"
No device in 'device' state.
1) USB once: adb tcpip 5555
2) Wireless: adb connect <tablet-ip>:5555
3) Re-run this script.
"@
    adb devices
    exit 1
}

Write-Host "Devices:"
adb devices

$apk = Join-Path $Root "app\build\outputs\apk\release\app-release.apk"
if (-not $SkipBuild) {
    Write-Host "Building release APK..."
    & .\gradlew.bat assembleRelease --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed"
    }
}

if (-not (Test-Path $apk)) {
    throw "APK missing: $apk"
}

Write-Host "Installing $apk ..."
adb install -r -d $apk
if ($LASTEXITCODE -ne 0) {
    throw "adb install failed"
}

Write-Host "Launching com.xander.student ..."
adb shell am start -n com.xander.student/.LoginActivity
Write-Host "Done. Settings password: HFSecurity"
