# Capture real TV screenshot as binary PNG; safe on Windows PowerShell 5.1.
# Do NOT use: adb exec-out screencap -p > file.png (text redirection corrupts PNG).
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('stock-home','eddy-home','eddy-settings','eddy-hdmi','perf-dashboard')]
    [string]$Name,
    [string]$TvHost=$env:TCL_TV_HOST,
    [switch]$Overwrite
)
$ErrorActionPreference='Stop'
if (-not $TvHost) { throw 'Supply -TvHost <IP> or set TCL_TV_HOST.' }

$cmd=Get-Command adb -ErrorAction SilentlyContinue
$adbExe=if($cmd){$cmd.Source}else{$null}
if(-not $adbExe){
    $base=Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    $found=@(Get-ChildItem -Path $base -Filter adb.exe -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like '*Google.PlatformTools*' } | Select-Object -First 1)
    if($found.Count -gt 0){$adbExe=$found[0].FullName}
}
if(-not $adbExe){throw 'adb.exe not found; install Android SDK Platform-Tools.'}

$root=Split-Path -Parent $PSScriptRoot
$dir=Join-Path $root 'docs\assets'
New-Item -ItemType Directory -Path $dir -Force | Out-Null
$dest=Join-Path $dir ($Name+'.png')
if((Test-Path -LiteralPath $dest) -and -not $Overwrite){
    throw "File already exists: $dest. Use -Overwrite after checking the TV screen."
}

$serial=$TvHost+':5555'
& $adbExe connect $serial | Out-Host
if($LASTEXITCODE -ne 0){throw 'Could not connect to TV.'}
$state=(& $adbExe -s $serial get-state 2>&1 | Out-String).Trim()
if($LASTEXITCODE -ne 0 -or $state -ne 'device'){
    throw "ADB is not authorized/ready: $state"
}
$remote='/sdcard/Download/cleanmytcl-'+$Name+'.png'
& $adbExe -s $serial shell screencap -p $remote | Out-Host
if($LASTEXITCODE -ne 0){throw 'Screen capture failed.'}
# adb pull preserves binary bytes, unlike PowerShell 5.1 > redirection.
& $adbExe -s $serial pull $remote $dest | Out-Host
if($LASTEXITCODE -ne 0){throw 'adb pull failed.'}

$f=[System.IO.File]::OpenRead($dest)
try{
    $sig=New-Object byte[] 8
    $read=$f.Read($sig,0,8)
}finally{$f.Dispose()}
$expected=[byte[]](137,80,78,71,13,10,26,10)
$ok=$read -eq 8
if($ok){for($i=0;$i -lt 8;$i++){if($sig[$i] -ne $expected[$i]){$ok=$false;break}}}
if(-not $ok){throw "Invalid PNG signature: $dest. Do not publish this file."}
Write-Host "Valid PNG: $dest"
Write-Host ('SHA256: '+(Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash)
Write-Host 'Inspect for correct content and private data before committing.'
