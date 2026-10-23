# Installs all included essential apps (SmartTube, TV Bro, Xiaomi TV+, Send Files to TV)
# directly onto your Android TV over ADB.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\install-apps.ps1 -TvHost 192.168.1.50
param(
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'Pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

$py = if (Test-Path "$PSScriptRoot\venv\Scripts\python.exe") { "$PSScriptRoot\venv\Scripts\python.exe" } else { "python" }
$tvPy = "$PSScriptRoot\tv.py"

Write-Host "Connecting to TV at $TvHost..." -ForegroundColor Cyan
& $py -I $tvPy --probe

$apks = Get-ChildItem "$PSScriptRoot\..\apps\*.apk"
if (-not $apks) { throw "No APKs found in apps/ directory!" }

foreach ($apk in $apks) {
    Write-Host "Installing $($apk.Name)..." -ForegroundColor Yellow
    & $py -I $tvPy "pm install -r -d /sdcard/temp_install.apk" | Out-Null
    # Push and install
    & $py -I "$PSScriptRoot\b64push.py" $apk.FullName "/sdcard/$($apk.Name)"
    & $py -I $tvPy "pm install -r -g /sdcard/$($apk.Name) && rm /sdcard/$($apk.Name)"
    Write-Host "Installed $($apk.Name) successfully!" -ForegroundColor Green
}

Write-Host "All essential apps installed successfully on your TV!" -ForegroundColor Green
