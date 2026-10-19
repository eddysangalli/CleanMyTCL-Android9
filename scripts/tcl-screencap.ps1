# Capture a TV screen as a binary PNG. Safe on Windows PowerShell 5.1.
#
# Why this exists:
#   `adb exec-out screencap -p > file.png` decodes the binary stream as text and
#   re-encodes it as UTF-16, so the PNG signature byte 0x89 is lost (it becomes
#   U+FFFD) and the file is not a PNG. `adb shell screencap -p <remote>` + pull
#   writes a 0-byte file on this firmware. `exec-out` over the pipe is the
#   method that works; this script writes the raw bytes.
#
# Usage: powershell -ExecutionPolicy Bypass -File scripts\tcl-screencap.ps1 -Out <file.png> [-Serial <host:port>]
param(
    [Parameter(Mandatory = $true)][string]$Out,
    [string]$Serial = '',
    [string]$Adb = ''
)
$ErrorActionPreference = 'Stop'

if (-not $Serial) { $Serial = $env:TCL_TV_HOST + ':5555' }
if (-not $Serial -or $Serial -eq ':5555') { throw 'Set TCL_TV_HOST or pass -Serial <host:port>.' }

if (-not $Adb) {
    $Adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
}
if (-not $Adb) {
    foreach ($c in @("$env:LOCALAPPDATA\Microsoft\WinGet\Packages", 'D:\Android\Sdk\platform-tools', "$env:ProgramFiles\Android\platform-tools")) {
        if (Test-Path $c) {
            $hit = Get-ChildItem -Path $c -Filter adb.exe -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($hit) { $Adb = $hit.FullName; break }
        }
    }
}
if (-not $Adb) { throw 'adb.exe not found.' }

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $Adb
$psi.Arguments = "-s $Serial exec-out screencap -p"
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$proc = [System.Diagnostics.Process]::Start($psi)

$dir = Split-Path -Parent $Out
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
$fs = [System.IO.File]::Create($Out)
$proc.StandardOutput.BaseStream.CopyTo($fs)
$fs.Close()
$proc.WaitForExit()

if ($proc.ExitCode -ne 0) {
    Remove-Item -LiteralPath $Out -Force -ErrorAction SilentlyContinue
    throw "screencap failed: $($proc.StandardError.ReadToEnd())"
}

$bytes = [System.IO.File]::ReadAllBytes($Out)
if ($bytes.Length -lt 8) {
    Remove-Item -LiteralPath $Out -Force -ErrorAction SilentlyContinue
    throw 'screencap returned nothing (is the screen awake?).'
}
$sig = ($bytes[0..7] | ForEach-Object { $_.ToString('x2') }) -join ''
if ($sig -ne '89504e470d0a1a0a') {
    Remove-Item -LiteralPath $Out -Force -ErrorAction SilentlyContinue
    throw "not a PNG (signature '$sig')."
}
Write-Output "$($bytes.Length)"
