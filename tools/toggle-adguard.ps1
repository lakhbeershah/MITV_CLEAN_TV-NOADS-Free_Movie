# Toggles AdGuard Private DNS on or off on your Acer TV over Wi-Fi ADB.
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\toggle-adguard.ps1 -TvHost <tv-ip> -Mode on
#   powershell -ExecutionPolicy Bypass -File tools\toggle-adguard.ps1 -TvHost <tv-ip> -Mode off
#   powershell -ExecutionPolicy Bypass -File tools\toggle-adguard.ps1 -TvHost <tv-ip> -Mode status
param(
    [string]$TvHost = $env:TV_HOST,
    [ValidateSet('on', 'off', 'status')]
    [string]$Mode = 'status'
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'Pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

$py = if (Test-Path "$PSScriptRoot\venv\Scripts\python.exe") { "$PSScriptRoot\venv\Scripts\python.exe" } else { "python" }
$tvPy = "$PSScriptRoot\tv.py"

if ($Mode -eq 'on') {
    Write-Host "Enabling AdGuard Private DNS on $TvHost..." -ForegroundColor Cyan
    & $py -I $tvPy "settings put global private_dns_mode hostname && settings put global private_dns_specifier dns.adguard.com"
    Write-Host "AdGuard DNS is now ON. Ads and popups are blocked system-wide." -ForegroundColor Green
} elseif ($Mode -eq 'off') {
    Write-Host "Disabling AdGuard Private DNS (returning to Default Automatic DNS)..." -ForegroundColor Yellow
    & $py -I $tvPy "settings put global private_dns_mode off && settings put global private_dns_specifier ''"
    Write-Host "Private DNS is now OFF. All streaming apps (Hotstar/JioCinema) have default connectivity." -ForegroundColor Green
} else {
    $currentMode = & $py -I $tvPy "settings get global private_dns_mode"
    $currentHost = & $py -I $tvPy "settings get global private_dns_specifier"
    Write-Host "Current Private DNS Status: Mode=$currentMode, Server=$currentHost" -ForegroundColor Cyan
}
