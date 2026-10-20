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
                # Trim first: a trailing CR from the TV would stop the `$` anchor from matching any
                # line, and every claim would look like it had no class.
                if ("$_".Trim() -match '^([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)$' -and $Matches[2] -notmatch 'FallbackHome$') {
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

# Same as disable.sh (#321): a package that asks for android.permission.TV_INPUT_HARDWARE is the software
# layer under the HDMI, AV and tuner inputs. Disable one and the port still notices the cable but shows
# "No Signal" for good; re-enabling the pieces in batches does not bring the picture back, a full restore
# and a cold boot do. Keep them; $env:TVINPUT_ALLOW=1 disables them anyway.
if (-not $env:TVINPUT_ALLOW -and $pkgs.Count -gt 0) {
    # Every name was validated as a bare package name above, so none of them can add shell syntax here.
    # Trailing `; true`: the loop's status is the last package's `grep -q`, which is nonzero whenever the
    # last listed package is not an input driver, and would look like a failed check.
    $loop = 'for p in ' + ($pkgs -join ' ') + '; do dumpsys package $p | grep -q TV_INPUT_HARDWARE && echo $p; done; true'
    # Windows PowerShell turns redirected stderr into an error that 'Stop' would throw (see getprop).
    $ErrorActionPreference = 'Continue'
    $tvhw = @(& $py -I (Join-Path $repo 'tools\tv.py') $loop 2>$null)
    $checked = $LASTEXITCODE -eq 0
    $ErrorActionPreference = 'Stop'
    if (-not $checked) {
        Write-Warning 'Could not check which listed packages drive the HDMI/AV inputs (tv.py failed). Check every input after this run: a port that notices the cable and stays black means one of them was disabled, and only a full restore and a cold boot bring it back (#321).'
    } else {
        foreach ($p in ($tvhw | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
            if ($pkgs -contains $p) {
                Write-Warning "Skipping ${p}: it asks for android.permission.TV_INPUT_HARDWARE, so it is the software layer under the HDMI/AV/tuner inputs. With it disabled those inputs still notice the cable but stay black (No Signal), and re-enabling the pieces in batches does not restore the picture: a full restore plus a cold boot does (#321). Set `$env:TVINPUT_ALLOW=1 to disable it."
                $pkgs = @($pkgs | Where-Object { $_ -ne $p })
            }
        }
    }
}

# Network ADB warning (#319): on firmware with ro.adb.secure=0, or with the property not set at all,
# port 5555 hands a shell to any device on the network without the "Allow" prompt. Read it and say
# so once, before the first command reaches the TV, so the note is on screen even if the run dies
# halfway. A read that fails is not a guess: it only skips the note. Windows PowerShell turns
# redirected stderr into an error record that 'Stop' would throw, so relax it for the read only.
$adbSecure = $null
$ErrorActionPreference = 'Continue'
try { $adbSecure = ((& $py -I (Join-Path $repo 'tools\tv.py') 'getprop ro.adb.secure' 2>$null) -join '').Trim() } catch { $adbSecure = $null }
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0) { $adbSecure = $null }
if ($null -ne $adbSecure -and ($adbSecure -eq '0' -or $adbSecure -eq '')) {
    $shown = if ($adbSecure -eq '') { '<empty>' } else { $adbSecure }
    Write-Warning "This TV reports ro.adb.secure=$shown, so network ADB on port 5555 accepts any device on your network without the `"Allow`" prompt. When you are done, turn USB debugging and Network/ADB debugging off on the TV, reboot it, and check that the port is gone."
}

# Record the state the list is about to change, before the first command reaches the TV (#317,
# from upstream). Without it, restore.ps1 would enable every listed package even ones that were
# disabled before cleanup, and would reset Home and animation scales to fixed values instead of
# what they were. A retry after a half-applied list must not overwrite the first record, so this
# runs before the disable batch: if it fails (e.g. tv.py cannot reach the TV), no package is
# disabled and nothing reaches the device. The script's own exit (SystemExit) bubbles up here.
& $py -I (Join-Path $repo 'tools\recovery.py') --save $List

$cmds = @($pkgs | ForEach-Object { "pm disable-user --user 0 $_" }) + 'pm list packages -d'
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
