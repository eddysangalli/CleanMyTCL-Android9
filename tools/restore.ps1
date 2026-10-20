# Windows version of restore.sh: replay the state recorded by disable.ps1 (or warn and fall back
# to "enable every listed package + reset everything" when no record exists). Pass the same list
# you used with tools\disable.ps1.
#
# Usage: tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt [-Stock stock.launcher.package]
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$Stock = 'com.google.android.tvlauncher',
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$List = (Resolve-Path $List).Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

$pkgs = Get-Content $List | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ }
# Same validation as tools\disable.ps1: nothing from a list file reaches the TV shell unvalidated.
# See https://github.com/Gathin23/cleanyourtv/issues/165
foreach ($p in $pkgs) {
    if ($p -notmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$') {
        throw "refusing to run: invalid package name in the debloat list: '$p'"
    }
}
if ($Stock -notmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$') {
    throw "refusing to run: invalid stock launcher package name: '$Stock'"
}

# Replay the state recorded by disable.ps1 (or warn and fall back when no record exists).
# recovery.py validates the record before anything reaches the TV: a hand-edited or damaged
# record fails here (#317, from upstream), not halfway through.
# 2>&1 keeps recovery.py's notes (e.g. which packages were already disabled) on screen.
$cmds = @(& $py -I (Join-Path $repo 'tools\recovery.py') --commands $List $Stock 2>&1)
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
