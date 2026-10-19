# Capture a real TV screenshot as a binary PNG. Safe on Windows PowerShell 5.1.
#
# Two traps this script exists to avoid:
#   1. `adb exec-out screencap -p > file.png` decodes the binary stream as text and
#      re-encodes it as UTF-16. The PNG signature byte 0x89 is lost (it becomes U+FFFD)
#      and the file is not a PNG any more. PowerShell 7 `>` is safe; 5.1 is not.
#   2. `adb shell screencap -p /path/on/tv` followed by `adb pull` writes a 0-byte file on
#      some Android TV 9 firmwares (checked on TCL BeyondTV2 / R851T02 V660), because the
#      on-device screencap cannot write to that path. `exec-out` over the pipe works.
#
# It also wakes the screen first: right after standby, screencap returns nothing at all.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\tcl-capture-screenshot.ps1 -Name eddy-home -TvHost <ip> -Overwrite
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('stock-home','eddy-home','eddy-launcher-apps','eddy-tile-menu','eddy-options','eddy-hdmi','perf-dashboard')]
    [string]$Name,
    [string]$TvHost = $env:TCL_TV_HOST,
    [switch]$Overwrite
)
$ErrorActionPreference = 'Stop'

if (-not $TvHost) { throw 'Supply -TvHost <TV ip> or set $env:TCL_TV_HOST.' }

# ---- locate adb ---------------------------------------------------------------------------------
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'),
        'D:\Android\Sdk\platform-tools',
        (Join-Path $env:ProgramFiles 'Android\platform-tools')
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) {
            $hit = Get-ChildItem -Path $c -Filter adb.exe -Recurse -File -ErrorAction SilentlyContinue |
                   Select-Object -First 1
            if ($hit) { $adb = $hit.FullName; break }
        }
    }
}
if (-not $adb) { throw 'adb.exe not found; install Android SDK Platform-Tools.' }

$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root 'docs\assets'
New-Item -ItemType Directory -Path $dir -Force | Out-Null
$dest = Join-Path $dir ($Name + '.png')
if ((Test-Path -LiteralPath $dest) -and -not $Overwrite) {
    throw "File already exists: $dest. Look at it first, then pass -Overwrite to replace it."
}

$serial = $TvHost + ':5555'
& $adb connect $serial | Out-Host
$state = (& $adb -s $serial get-state 2>&1 | Out-String).Trim()
if ($state -ne 'device') { throw "ADB is not authorized/ready: $state" }

# ---- wake the screen: screencap returns nothing while it sleeps --------------------------------
& $adb -s $serial shell input keyevent KEYCODE_WAKEUP | Out-Null
Start-Sleep -Seconds 2

# ---- capture: raw bytes, straight to the file ---------------------------------------------------
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $adb
$psi.Arguments = "-s $serial exec-out screencap -p"
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$proc = [System.Diagnostics.Process]::Start($psi)
$fs = [System.IO.File]::Create($dest)
$proc.StandardOutput.BaseStream.CopyTo($fs)
$fs.Close()
$proc.WaitForExit()

if ($proc.ExitCode -ne 0) {
    Remove-Item -LiteralPath $dest -Force -ErrorAction SilentlyContinue
    throw "screencap failed (exit $($proc.ExitCode)): $($proc.StandardError.ReadToEnd())"
}

# ---- verify it really is a PNG -----------------------------------------------------------------
$bytes = [System.IO.File]::ReadAllBytes($dest)
$sig = ($bytes[0..7] | ForEach-Object { $_.ToString('x2') }) -join ''
if ($bytes.Length -lt 8 -or $sig -ne '89504e470d0a1a0a') {
    Remove-Item -LiteralPath $dest -Force -ErrorAction SilentlyContinue
    throw "not a valid PNG (signature was '$sig'). The capture was empty or the screen was asleep."
}

$hash = (Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash
Write-Host "wrote $dest ($($bytes.Length) bytes)"
Write-Host "sha256 $hash"
Write-Host "Open the file and check the content before committing it."
