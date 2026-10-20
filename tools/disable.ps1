# Windows version of disable.sh: disable every package listed in a debloat file on the TV
# (one package per line, # comments allowed). Packages are disabled for user 0, never uninstalled.
# Undo with tools\restore.ps1 <same file>.
#
# Usage: tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$List = (Resolve-Path $List).Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

# A debloat list is a text file. If it comes from somewhere else (or a typo lands in it), a
# "package name" can carry shell metacharacters, and the line that reaches the TV is a shell
# command. Validate every name before building any command.
# See https://github.com/Gathin23/cleanyourtv/issues/165
$pkgs = Get-Content $List | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ }
foreach ($p in $pkgs) {
    if ($p -notmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$') {
        throw "refusing to run: invalid package name in the debloat list: '$p' (letters, digits, dot and underscore only; each segment starts with a letter)"
    }
}

# Home guard (#239/#350): never disable the app that owns Home while no other real HOME activity
# remains enabled. Fallback homes (com.android.tv.settings/.FallbackHome and vendor equivalents) do
# not count as a usable launcher. $env:FORCE_HOME=1 overrides the guard.
if (-not $env:FORCE_HOME) {
    $homeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
    try {
        $homeLine = @(& $py -I (Join-Path $repo 'tools\tv.py') $homeQuery 2>$null |
            Where-Object { $_ -match '/' }) | Select-Object -Last 1
    } catch {
        $homeLine = $null
    }
    $homePkg = "$homeLine".Trim() -split '/' | Select-Object -First 1
    if ($homePkg -and ($pkgs -contains $homePkg)) {
        $claimQuery = 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
        try {
            $claims = @(& $py -I (Join-Path $repo 'tools\tv.py') $claimQuery 2>$null | ForEach-Object {
                if ($_ -match '^\s*([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)$' -and $Matches[2] -notmatch 'FallbackHome$') {
                    $Matches[1]
                }
            } | Sort-Object -Unique)
        } catch {
            $claims = @()
        }
        $others = @($claims | Where-Object { $_ -ne $homePkg -and $pkgs -notcontains $_ })
        if ($others.Count -eq 0) {
            Write-Warning "Skipping ${homePkg}: it owns Home, and no other real app that can answer Home is installed and staying enabled. Make the new launcher Home first, then run this list again to disable the old one; or set `$env:FORCE_HOME=1 if you can still reach the TV."
            $pkgs = @($pkgs | Where-Object { $_ -ne $homePkg })
        }
    }
}

$cmds = @($pkgs | ForEach-Object { "pm disable-user --user 0 $_" }) + 'pm list packages -d'
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
